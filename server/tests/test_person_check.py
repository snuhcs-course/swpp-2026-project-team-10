# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
# The person check partitions (Testing Documentation 2.1.6; FR-4.13; Design Documentation 2.6.3). Endpoint: POST /poses
# with a description, with a template, and with a selfie's description, all checked; POST /pose-suggestions and POST
# /pose-tips, not checked. The check's answer: a person (0.97, 1), at the cutoff 0.5, just under it (0.49), none (0);
# another cutoff from the settings. With the description filter: the filter refuses and the scene shows no person too,
# the filter lets the description through and the scene shows no person. Fail open: a failing status (400 for a small
# image, 403, 413 for a large one, 429, 500, 502), an error in a 200 answer, an unreadable answer (no answers, the
# question missing, a probability that is not a number, below 0, or above 1, a list), no answer within the timeout,
# no connection, no key. The request: the model from the settings, the scene exactly as sent as a JPEG data URL under
# `photo`, and the question of prompts.toml. Limits: a refused scene counted in its window. Privacy: neither the photo
# nor the check's body logged; a refusal logs the probability.
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


def assert_no_person(response):
    assert response.status_code == 422
    assert response.json() == {"error": {"code": "NO_PERSON", "message": "The photo shows no person"}}


def test_scene_with_a_person_reaches_the_image_api(client, image_api, make_image):
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.person_requests) == 1
    assert len(image_api.requests) == 1


def test_check_gets_the_model_the_scene_as_sent_and_the_question(client, image_api, make_image, settings):
    settings.person_model = "typesafe/jev-1.13"
    scene = make_image()
    assert describe_pose(client, scene).status_code == 200
    [payload] = image_api.person_payloads
    assert payload == {
        "model": "typesafe/jev-1.13",
        "state": {
            "photo": {
                "type": "image_url",
                "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(scene).decode()},
            }
        },
        "questions": PROMPTS.person_question,
    }
    assert payload["questions"] == {"person": {"type": "noul", "instructions": "Does `photo` show a person?"}}


@pytest.mark.parametrize("person", [0.49, 0.0], ids=["just under", "none"])
def test_scene_without_a_person_is_refused_before_the_image_api(client, image_api, make_image, person):
    image_api.sees(person=person)
    assert_no_person(describe_pose(client, make_image()))
    assert image_api.requests == []


@pytest.mark.parametrize("person", [0.5, 1.0], ids=["at the cutoff", "certain"])
def test_scene_at_or_above_the_cutoff_goes_through(client, image_api, make_image, person):
    image_api.sees(person=person)
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


def test_cutoff_comes_from_the_settings(client, image_api, make_image, settings):
    settings.person_cutoff = 0.2
    image_api.sees(person=0.2)
    assert describe_pose(client, make_image()).status_code == 200
    image_api.sees(person=0.19)
    assert_no_person(describe_pose(client, make_image()))


def test_template_scene_is_checked_too(client, image_api, make_image):
    image_api.sees(person=0.03)
    assert_no_person(template_pose(client, make_image()))
    assert image_api.filter_requests == []
    assert image_api.requests == []


def test_selfie_scene_is_checked_too(client, image_api, make_image):
    image_api.sees(person=0.03)
    assert_no_person(describe_pose(client, make_image(), selfie="true"))
    assert image_api.requests == []


def test_suggestions_and_tips_are_not_checked(client, image_api, make_image):
    image_api.sees(person=0.0)
    files = {"image": ("scene.jpg", make_image(), "image/jpeg")}
    assert client.post("/api/v1/pose-suggestions", files=files).status_code == 200
    assert client.post("/api/v1/pose-suggestions", files=files, data={"description": DESCRIPTION}).status_code == 200
    tip_files = {
        "scene": ("scene.jpg", make_image(), "image/jpeg"),
        "candidate": ("candidate.jpg", make_image(), "image/jpeg"),
    }
    assert client.post("/api/v1/pose-tips", files=tip_files, data={"pose": DESCRIPTION}).status_code == 200
    assert image_api.person_requests == []


def test_refused_description_wins_over_a_scene_without_a_person(client, image_api, make_image):
    image_api.judges(malicious=0.95, injection=0.0)
    image_api.sees(person=0.0)
    response = describe_pose(client, make_image())
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "REJECTED"
    assert len(image_api.filter_requests) == 1
    assert len(image_api.person_requests) == 1
    assert image_api.requests == []


def test_description_the_filter_lets_through_still_needs_a_person(client, image_api, make_image):
    image_api.sees(person=0.1)
    assert_no_person(describe_pose(client, make_image()))
    assert len(image_api.filter_requests) == 1


@pytest.mark.parametrize("status", [400, 403, 413, 429, 500, 502])
def test_failing_check_lets_the_scene_through(client, image_api, make_image, status):
    image_api.person_fails(status)
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


@pytest.mark.parametrize(
    "body",
    [
        {"error": {"code": 502, "message": "Provider returned error"}},
        {"model": "typesafe/jev-1.14"},
        {"answers": {"people": {"type": "noul", "noul": 0.0}}},
        {"answers": {"person": {"noul": "none"}}},
        {"answers": {"person": {"noul": -0.1}}},
        {"answers": {"person": {"noul": 1.5}}},
        ["not", "an", "object"],
    ],
    ids=["error in a 200", "no answers", "question missing", "not a number", "below 0", "above 1", "a list"],
)
def test_unreadable_check_answer_lets_the_scene_through(client, image_api, make_image, body):
    image_api.person_body = body
    assert describe_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


def test_slow_check_lets_the_scene_through(client, image_api, make_image, settings, caplog):
    settings.person_timeout_seconds = 0.05
    image_api.person_delay = 5
    image_api.sees(person=0.0)
    assert describe_pose(client, make_image()).status_code == 200
    assert "The person check did not answer within 0.05 s" in caplog.text


def test_unreachable_check_lets_the_scene_through(settings, make_image, open_store, clock, image_api):
    async def only_the_check_unreachable(request: httpx2.Request) -> httpx2.Response:
        if request.url.path == "/api/alpha/decisions" and b'"person"' in request.content:
            raise httpx2.ConnectError("no route to host", request=request)
        return await image_api(request)

    app = create_app(
        settings, clock=clock, image_api=httpx2.MockTransport(only_the_check_unreachable), redis=open_store
    )
    with TestClient(app) as client:
        assert template_pose(client, make_image()).status_code == 200
    assert len(image_api.requests) == 1


@pytest.mark.parametrize("key", [None, SecretStr("")])
def test_missing_api_key_asks_the_check_nothing(client, image_api, make_image, settings, key):
    settings.openrouter_api_key = key
    describe_pose(client, make_image())
    assert image_api.person_requests == []


def test_refused_scenes_count_in_their_window(client, image_api, make_image, settings):
    image_api.sees(person=0.0)
    for _ in range(settings.pose_rate_limit):
        assert_no_person(template_pose(client, make_image()))
    assert template_pose(client, make_image()).status_code == 429
    assert len(image_api.person_requests) == settings.pose_rate_limit


def test_refusal_logs_the_probability_but_not_the_photo(client, image_api, make_image, caplog):
    image_api.sees(person=0.04)
    scene = make_image()
    assert_no_person(describe_pose(client, scene))
    assert "The person check found no person (person 0.04)" in caplog.text
    assert base64.b64encode(scene).decode()[:40] not in caplog.text


def test_failing_check_logs_the_status_but_not_its_body(client, image_api, make_image, caplog):
    image_api.person_fails(413, "image too large: SECRET-PHOTO-DETAIL")
    assert describe_pose(client, make_image()).status_code == 200
    assert "OpenRouter answered 413 (Request Entity Too Large) to the person check" in caplog.text
    assert "SECRET" not in caplog.text
