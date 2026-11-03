# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
# The description filter partitions (Testing Documentation 2.1.6; Design Documentation 2.6.3). Endpoint: POST /poses
# with a description, with a selfie's description, and with a template (not judged); POST /pose-suggestions with a
# description and without one (not judged); POST /pose-tips. The filter's answer: both probabilities low; either one
# at the cutoff 0.7, just under it (0.69), and at 1; both high; another cutoff from the settings. Fail open: a refusal
# (403) or another failing status, an error in a 200 answer, an unreadable answer (no answers, a question missing, a
# probability that is not a number, below 0, or above 1), no answer within the timeout, no connection, no
# key. The request: the model from the settings, the text as sent on one line with its quotation marks, and the
# questions of prompts.toml, while the prompts get single quotation marks. Limits: a refused request counted in its
# window. Privacy: neither the text nor the filter's body logged; a refusal names the question and its probability.
import base64

import httpx2
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from pix_server.main import create_app
from pix_server.poses.prompts import PROMPTS

DESCRIPTION = "벽에 기대서 카메라 보며 웃기"


def describe_pose(client, image, description=DESCRIPTION, selfie=None):
    data = {"seed": "1829304756", "description": description}
    if selfie is not None:
        data["selfie"] = selfie
    return client.post("/api/v1/poses", files={"image": ("scene.jpg", image, "image/jpeg")}, data=data)


def template_pose(client, image):
    data = {"seed": "1829304756", "templateId": "wave"}
    return client.post("/api/v1/poses", files={"image": ("scene.jpg", image, "image/jpeg")}, data=data)


def suggest(client, image, description=None):
    data = {} if description is None else {"description": description}
    return client.post("/api/v1/pose-suggestions", files={"image": ("scene.jpg", image, "image/jpeg")}, data=data)


def ask_tip(client, image, pose=DESCRIPTION):
    files = {"scene": ("scene.jpg", image, "image/jpeg"), "candidate": ("candidate.jpg", image, "image/jpeg")}
    return client.post("/api/v1/pose-tips", files=files, data={"pose": pose})


def assert_rejected(response, field="description"):
    assert response.status_code == 422
    assert response.json() == {"error": {"code": "REJECTED", "message": f"This {field} was refused"}}


def test_description_the_filter_lets_through_reaches_the_image_api(client, image_api, make_image):
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.filter_requests) == 1
    assert len(image_api.requests) == 1


def test_filter_gets_the_model_the_text_as_sent_and_the_questions(client, image_api, make_image, settings):
    assert describe_pose(client, make_image(), description='  웃으면서\n  "치즈" 하기 ').status_code == 200
    [payload] = image_api.filter_payloads
    assert payload == {
        "model": settings.filter_model,
        "state": {"description": '웃으면서 "치즈" 하기'},
        "questions": PROMPTS.filter_questions,
    }
    assert "웃으면서 '치즈' 하기" in image_api.payloads[0]["prompt"]


@pytest.mark.parametrize(
    ("malicious", "injection"),
    [(0.7, 0.02), (0.02, 0.7), (1.0, 0.0), (0.95, 0.95)],
    ids=["harmful at the cutoff", "manipulative at the cutoff", "certain", "both"],
)
def test_description_at_or_above_the_cutoff_is_refused_before_the_image_api(
    client, image_api, make_image, malicious, injection
):
    image_api.judges(malicious=malicious, injection=injection)
    assert_rejected(describe_pose(client, make_image()))
    assert image_api.requests == []


@pytest.mark.parametrize(("malicious", "injection"), [(0.69, 0.69), (0.0, 0.0)], ids=["just under", "none"])
def test_description_under_the_cutoff_goes_through(client, image_api, make_image, malicious, injection):
    image_api.judges(malicious=malicious, injection=injection)
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


def test_cutoff_comes_from_the_settings(client, image_api, make_image, settings):
    settings.filter_cutoff = 0.9
    image_api.judges(malicious=0.89, injection=0.0)
    assert describe_pose(client, make_image()).status_code == 200
    image_api.judges(malicious=0.9, injection=0.0)
    assert_rejected(describe_pose(client, make_image()))


def test_selfie_description_is_judged_too(client, image_api, make_image):
    image_api.judges(malicious=0.9, injection=0.0)
    assert_rejected(describe_pose(client, make_image(), selfie="true"))
    assert image_api.requests == []


def test_template_is_not_judged(client, image_api, make_image):
    image_api.judges(malicious=1.0, injection=1.0)
    assert template_pose(client, make_image()).status_code == 200
    assert image_api.filter_requests == []


def test_suggestions_for_a_refused_description_are_refused_before_the_model(client, image_api, make_image):
    image_api.judges(malicious=0.0, injection=0.8)
    assert_rejected(suggest(client, make_image(), description=DESCRIPTION))
    assert image_api.suggestion_requests == []


def test_suggestions_without_a_description_are_not_judged(client, image_api, make_image):
    image_api.judges(malicious=1.0, injection=1.0)
    assert suggest(client, make_image()).status_code == 200
    assert image_api.filter_requests == []


def test_suggestions_for_a_description_the_filter_lets_through_vary_it(client, image_api, make_image):
    assert suggest(client, make_image(), description=DESCRIPTION).json()["poses"][0] == DESCRIPTION
    assert [payload["state"]["description"] for payload in image_api.filter_payloads] == [DESCRIPTION]


def test_tip_for_a_refused_pose_is_refused_before_the_model(client, image_api, make_image):
    image_api.judges(malicious=0.85, injection=0.0)
    assert_rejected(ask_tip(client, make_image()), "pose")
    assert image_api.tip_requests == []


def test_tip_for_a_pose_the_filter_lets_through_quotes_it_with_single_marks(client, image_api, make_image):
    assert ask_tip(client, make_image(), pose='say "cheese"').status_code == 200
    assert image_api.filter_payloads[0]["state"] == {"description": 'say "cheese"'}
    texts = [part.get("text") for part in image_api.tip_payloads[0]["messages"][0]["content"]]
    assert "Photo 2, the photo they want, with the pose \"say 'cheese'\":" in texts


@pytest.mark.parametrize("status", [403, 429, 500, 502])
def test_failing_filter_lets_the_description_through(client, image_api, make_image, status):
    image_api.filter_fails(status)
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


@pytest.mark.parametrize(
    "body",
    [
        {"error": {"code": 502, "message": "Provider returned error"}},
        {"model": "typesafe/jev-1.14"},
        {"answers": {"malicious": {"type": "noul", "noul": 0.9}}},
        {"answers": {"malicious": {"noul": "high"}, "injection": {"noul": 0.0}}},
        {"answers": {"malicious": {"noul": -0.1}, "injection": {"noul": 0.0}}},
        {"answers": {"malicious": {"noul": 1.5}, "injection": {"noul": 0.0}}},
        ["not", "an", "object"],
    ],
    ids=["error in a 200", "no answers", "question missing", "not a number", "below 0", "above 1", "a list"],
)
def test_unreadable_filter_answer_lets_the_description_through(client, image_api, make_image, body):
    image_api.filter_body = body
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


def test_slow_filter_lets_the_description_through(client, image_api, make_image, settings, caplog):
    settings.filter_timeout_seconds = 0.05
    image_api.filter_delay = 5
    image_api.judges(malicious=1.0, injection=1.0)
    assert describe_pose(client, make_image()).status_code == 200
    assert "The filter did not answer within 0.05 s" in caplog.text


def test_unreachable_filter_lets_the_description_through(settings, make_image, open_store, clock, image_api):
    async def only_the_filter_unreachable(request: httpx2.Request) -> httpx2.Response:
        if request.url.path == "/api/alpha/decisions":
            raise httpx2.ConnectError("no route to host", request=request)
        return await image_api(request)

    app = create_app(
        settings, clock=clock, image_api=httpx2.MockTransport(only_the_filter_unreachable), redis=open_store
    )
    with TestClient(app) as client:
        assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


@pytest.mark.parametrize("key", [None, SecretStr("")])
def test_missing_api_key_asks_the_filter_nothing(client, image_api, make_image, settings, key):
    settings.openrouter_api_key = key
    assert suggest(client, make_image(), description=DESCRIPTION).status_code == 200
    assert image_api.filter_requests == []


def test_refused_requests_count_in_their_window(client, image_api, make_image, settings):
    image_api.judges(malicious=0.9, injection=0.0)
    for _ in range(settings.pose_rate_limit):
        assert_rejected(describe_pose(client, make_image()))
    assert describe_pose(client, make_image()).status_code == 429
    assert len(image_api.filter_requests) == settings.pose_rate_limit


def test_refusal_logs_the_questions_but_not_the_description(client, image_api, make_image, caplog):
    image_api.judges(malicious=0.93, injection=0.04)
    assert_rejected(describe_pose(client, make_image(), description="SECRET-DESCRIPTION 옷 벗기기"))
    assert "The filter refused a description (malicious 0.93, injection 0.04)" in caplog.text
    assert "SECRET" not in caplog.text
    assert "벗기기" not in caplog.text


def test_failing_filter_logs_the_status_but_not_its_body(client, image_api, make_image, caplog):
    image_api.filter_fails(400, "invalid state: SECRET-DESCRIPTION " + base64.b64encode(b"x").decode())
    assert describe_pose(client, make_image(), description="SECRET-DESCRIPTION").status_code == 200
    assert "OpenRouter answered 400 (Bad Request) to the filter" in caplog.text
    assert "SECRET" not in caplog.text
