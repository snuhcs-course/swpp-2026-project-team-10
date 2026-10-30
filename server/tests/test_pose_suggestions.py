# AI-generated with Claude Code, 2026-10-29, reviewed by Jaewan Park and Dongje Park
# POST /pose-suggestions partitions (Testing Documentation 2.1.6). Scene: a JPEG within the contract, and one that is
# not a JPEG, too large, or carries EXIF. The model's answer: four usable poses; JSON in a code fence; more than four;
# a duplicate (in another case); a pose of 100 characters and one of 101; poses with line breaks; an item that is not
# a string; none usable (an empty list, no `poses`, `poses` not a list, not JSON, an empty message). Upstream: a
# failing status, no answer in time, an unreadable body, no connection, no key. Limits: each window full, counted
# apart from pose requests; a refused scene not counted; the session store down. Privacy: no pose or body logged.
# With a description: one in Korean with extra whitespace, of 100 characters and of 101, blank, and empty (asks for
# suggestions); three usable variations, more than three, one equal to the description or to another, none, a failing
# or slow model, no key; counted like suggestions; neither the description nor a variation logged. Selfie (FR-1.14):
# `selfie` true, false, left out, and not a boolean; with and without a description; a failing model with a selfie.
import base64
import json

import httpx2
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from pix_server.main import create_app
from pix_server.poses.prompts import DEFAULT_POSES, DESCRIPTION_MAX_LENGTH, PROMPTS, variation_prompt

DEFAULTS = [default.pose for default in DEFAULT_POSES.values()]
SUGGESTED = ["Lean on the door and smile", "Walk toward the camera", "Sit on the step", "Wave and laugh"]
DESCRIPTION = "벽에 기대서 카메라 보며 살짝 웃기"
VARIATIONS = [
    "Lean on the wall, arms crossed, look at the camera, smile slightly",
    "Shoulder on the wall, one hand in a pocket, slight smile at the camera",
    "Back to the wall, hands behind it, look at the camera with a slight smile",
]


@pytest.fixture(autouse=True)
def model_suggests(image_api):
    image_api.suggests(SUGGESTED)


def suggest(client, image, description=None, selfie=None):
    data = {} if description is None else {"description": description}
    if selfie is not None:
        data["selfie"] = selfie
    return client.post("/api/v1/pose-suggestions", files={"image": ("scene.jpg", image, "image/jpeg")}, data=data)


def assert_error(response, status, code):
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"error"} and body["error"]["code"] == code
    return body["error"]["message"]


def test_suggestions_come_from_the_model_for_the_scene(client, image_api, make_image, settings):
    scene = make_image()
    response = suggest(client, scene)

    assert response.status_code == 200
    assert response.json() == {"poses": SUGGESTED}
    [request] = image_api.suggestion_requests
    assert str(request.url) == "https://openrouter.ai/api/v1/chat/completions"
    assert request.headers["Authorization"] == "Bearer test-key"
    [payload] = image_api.suggestion_payloads
    assert payload["model"] == settings.suggestion_model
    [message] = payload["messages"]
    text, image = message["content"]
    assert text == {"type": "text", "text": PROMPTS.suggestion}
    assert image["image_url"]["url"] == "data:image/jpeg;base64," + base64.b64encode(scene).decode()
    assert payload["response_format"]["type"] == "json_schema"
    assert payload["reasoning"] == {"effort": "minimal"}
    assert image_api.requests == []  # the image API is not asked yet


def test_suggested_poses_go_into_the_pose_prompt(client, image_api, make_image):
    poses = suggest(client, make_image()).json()["poses"]
    for pose in poses:
        data = {"seed": "1", "description": pose}
        files = {"image": ("scene.jpg", make_image(), "image/jpeg")}
        assert client.post("/api/v1/poses", files=files, data=data).status_code == 200
    for pose, payload in zip(poses, image_api.payloads):
        assert f'"{pose}"' in payload["prompt"]


@pytest.mark.parametrize(
    ("answer", "poses"),
    [
        ("```json\n" + json.dumps({"poses": SUGGESTED}) + "\n```", SUGGESTED),
        (json.dumps({"poses": [*SUGGESTED, "Jump with both arms up"]}), SUGGESTED),
        (
            json.dumps({"poses": ["Turn and\nlook back ", "  Sit  down", "Wave", "Jump"]}),
            ["Turn and look back", "Sit down", "Wave", "Jump"],
        ),
    ],
    ids=["code fence", "five", "line breaks"],
)
def test_usable_answer_in_another_shape_is_read(client, image_api, make_image, answer, poses):
    image_api.answers(answer)
    assert suggest(client, make_image()).json()["poses"] == poses


def test_unusable_poses_are_replaced_by_default_poses_in_order(client, image_api, make_image):
    longest, too_long = "a" * DESCRIPTION_MAX_LENGTH, "b" * (DESCRIPTION_MAX_LENGTH + 1)
    image_api.suggests(["Wave", "WAVE", too_long, 7, "", longest])
    assert suggest(client, make_image()).json()["poses"] == ["Wave", longest, *DEFAULTS[:2]]


def test_default_pose_is_not_repeated_when_the_model_suggests_it(client, image_api, make_image):
    image_api.suggests([DEFAULTS[0].upper()])
    assert suggest(client, make_image()).json()["poses"] == [DEFAULTS[0].upper(), *DEFAULTS[1:]]


@pytest.mark.parametrize(
    "answer",
    [json.dumps({"poses": []}), json.dumps({"ideas": SUGGESTED}), json.dumps({"poses": "Wave"}), "Wave", "", None],
    ids=["empty list", "no poses", "not a list", "not JSON", "empty", "no content"],
)
def test_answer_without_usable_poses_gives_the_default_poses(client, image_api, make_image, answer):
    image_api.answers(answer)
    assert suggest(client, make_image()).json()["poses"] == DEFAULTS


@pytest.mark.parametrize("status", [400, 402, 404, 429, 500, 502])
def test_failing_model_gives_the_default_poses(client, image_api, make_image, status):
    image_api.suggestion_fails(status)
    assert suggest(client, make_image()).json()["poses"] == DEFAULTS


@pytest.mark.parametrize("body", [{}, {"choices": []}, {"choices": [{}]}, "<html>bad gateway</html>"])
def test_unreadable_body_gives_the_default_poses(client, image_api, make_image, body):
    image_api.suggestion_body = body
    assert suggest(client, make_image()).json()["poses"] == DEFAULTS


def test_slow_model_gives_the_default_poses_in_time(client, image_api, make_image, settings):
    settings.suggestion_timeout_seconds = 0.05
    image_api.suggestion_delay = 5
    response = suggest(client, make_image())
    assert response.json()["poses"] == DEFAULTS
    assert response.elapsed.total_seconds() < 2


def test_unreachable_model_gives_the_default_poses(settings, make_image, open_store, clock):
    def unreachable(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ConnectError("no route to host", request=request)

    app = create_app(settings, clock=clock, image_api=httpx2.MockTransport(unreachable), redis=open_store)
    with TestClient(app) as client:
        assert suggest(client, make_image()).json()["poses"] == DEFAULTS


@pytest.mark.parametrize("key", [None, SecretStr("")])
def test_missing_api_key_gives_the_default_poses_without_a_call(client, image_api, make_image, settings, key):
    settings.openrouter_api_key = key
    assert suggest(client, make_image()).json()["poses"] == DEFAULTS
    assert image_api.suggestion_requests == []


def test_scene_outside_the_contract_is_invalid(client, image_api, make_image):
    for image in (make_image("PNG"), make_image(size=(1025, 768)), make_image(exif=True), b"not an image"):
        assert_error(suggest(client, image), 400, "INVALID_IMAGE")
    assert_error(client.post("/api/v1/pose-suggestions"), 400, "INVALID_REQUEST")
    assert image_api.suggestion_requests == []


def test_suggestions_are_limited_apart_from_poses(client, image_api, make_image, settings, clock):
    assert_error(suggest(client, make_image("PNG")), 400, "INVALID_IMAGE")
    for _ in range(settings.suggestion_rate_limit):
        assert suggest(client, make_image()).status_code == 200
    assert_error(suggest(client, make_image()), 429, "RATE_LIMITED")
    assert len(image_api.suggestion_requests) == settings.suggestion_rate_limit
    # Pose requests have windows of their own.
    files = {"image": ("scene.jpg", make_image(), "image/jpeg")}
    assert client.post("/api/v1/poses", files=files, data={"seed": "1", "description": "Wave"}).status_code == 200

    clock.advance(settings.pose_rate_window_seconds)
    assert suggest(client, make_image()).status_code == 200


def test_global_suggestion_limit_refuses_every_client(client, image_api, make_image, settings, clock):
    settings.suggestion_global_limit = 2
    for _ in range(2):
        assert suggest(client, make_image()).status_code == 200
    assert "today" in assert_error(suggest(client, make_image()), 429, "RATE_LIMITED")
    clock.advance(settings.pose_global_window_seconds)
    assert suggest(client, make_image()).status_code == 200


def test_suggestions_are_refused_while_the_session_store_is_down(client, image_api, make_image, outage):
    outage(True)
    assert_error(suggest(client, make_image()), 503, "UNAVAILABLE")
    assert image_api.suggestion_requests == []
    outage(False)
    assert suggest(client, make_image()).status_code == 200


@pytest.mark.parametrize("status", [200, 500])
def test_poses_and_upstream_body_are_not_logged(client, image_api, make_image, caplog, status):
    if status == 200:
        image_api.suggests(["SECRET-POSE leaning on the red door"])
    else:
        image_api.suggestion_fails(status, "invalid prompt: SECRET-PROMPT data:image/jpeg;base64,SECRET-PHOTO")
    assert suggest(client, make_image()).status_code == 200
    assert "suggested poses usable" in caplog.text or "OpenRouter answered" in caplog.text
    assert "SECRET" not in caplog.text
    assert "red door" not in caplog.text


def test_description_comes_back_first_with_three_variations_for_the_scene(client, image_api, make_image, settings):
    image_api.suggests(VARIATIONS)
    scene = make_image()
    response = suggest(client, scene, DESCRIPTION)

    assert response.status_code == 200
    assert response.json() == {"poses": [DESCRIPTION, *VARIATIONS]}
    [payload] = image_api.suggestion_payloads
    assert payload["model"] == settings.suggestion_model
    text, image = payload["messages"][0]["content"]
    assert text == {"type": "text", "text": variation_prompt(DESCRIPTION)}
    assert f'"{DESCRIPTION}"' in text["text"]
    assert image["image_url"]["url"] == "data:image/jpeg;base64," + base64.b64encode(scene).decode()
    assert payload["reasoning"] == {"effort": "minimal"}
    assert image_api.requests == []


def test_description_is_folded_like_a_pose_request(client, image_api, make_image):
    image_api.suggests(VARIATIONS)
    poses = suggest(client, make_image(), "  벽에 기대서\n카메라 보며   살짝 웃기 ").json()["poses"]
    assert poses[0] == DESCRIPTION
    assert f'"{DESCRIPTION}"' in image_api.suggestion_payloads[0]["messages"][0]["content"][0]["text"]


@pytest.mark.parametrize(
    ("variations", "kept"),
    [
        ([*VARIATIONS, "Jump with both arms up"], VARIATIONS),
        # Models sometimes put the description first, as the first of the four photos.
        ([DESCRIPTION, *VARIATIONS], VARIATIONS),
        ([DESCRIPTION, VARIATIONS[0], VARIATIONS[0].upper(), "x" * (DESCRIPTION_MAX_LENGTH + 1)], VARIATIONS[:1]),
        ([], []),
    ],
    ids=["four", "the description first", "the description, a duplicate, and one too long", "none"],
)
def test_description_fills_in_for_variations_that_cannot_be_used(client, image_api, make_image, variations, kept):
    image_api.suggests(variations)
    poses = suggest(client, make_image(), DESCRIPTION).json()["poses"]
    assert poses == [DESCRIPTION, *kept] + [DESCRIPTION] * (3 - len(kept))


def test_failing_or_slow_model_repeats_the_description(client, image_api, make_image, settings):
    image_api.suggestion_fails(500)
    assert suggest(client, make_image(), DESCRIPTION).json()["poses"] == [DESCRIPTION] * 4

    image_api.suggests(VARIATIONS)
    settings.suggestion_timeout_seconds = 0.05
    image_api.suggestion_delay = 5
    response = suggest(client, make_image(), DESCRIPTION)
    assert response.json()["poses"] == [DESCRIPTION] * 4
    assert response.elapsed.total_seconds() < 2


def test_missing_api_key_repeats_the_description_without_a_call(client, image_api, make_image, settings):
    settings.openrouter_api_key = None
    assert suggest(client, make_image(), DESCRIPTION).json()["poses"] == [DESCRIPTION] * 4
    assert image_api.suggestion_requests == []


def test_description_of_the_longest_length_is_accepted(client, image_api, make_image):
    longest = "가" * DESCRIPTION_MAX_LENGTH
    assert suggest(client, make_image(), longest).json()["poses"][0] == longest


@pytest.mark.parametrize(
    ("description", "message"),
    [("   \n ", "must not be blank"), ("가" * (DESCRIPTION_MAX_LENGTH + 1), "at most 100")],
    ids=["blank", "too long"],
)
def test_invalid_description_is_refused_before_a_call_and_not_counted(
    client, image_api, make_image, settings, description, message
):
    assert message in assert_error(suggest(client, make_image(), description), 400, "INVALID_REQUEST")
    assert image_api.suggestion_requests == []
    for _ in range(settings.suggestion_rate_limit):
        assert suggest(client, make_image(), DESCRIPTION).status_code == 200
    assert_error(suggest(client, make_image(), DESCRIPTION), 429, "RATE_LIMITED")


def test_empty_description_asks_for_suggested_poses(client, image_api, make_image):
    assert suggest(client, make_image(), "").json()["poses"] == SUGGESTED
    assert image_api.suggestion_payloads[0]["messages"][0]["content"][0]["text"] == PROMPTS.suggestion


@pytest.mark.parametrize("status", [200, 500])
def test_description_and_variations_are_not_logged(client, image_api, make_image, caplog, status):
    if status == 200:
        image_api.suggests(["SECRET-VARIATION by the red door"])
    else:
        image_api.suggestion_fails(status, "invalid prompt: SECRET-PROMPT")
    assert suggest(client, make_image(), "SECRET-DESCRIPTION 빨간 문 앞").status_code == 200
    assert "variations usable" in caplog.text or "OpenRouter answered" in caplog.text
    assert "SECRET" not in caplog.text
    assert "빨간" not in caplog.text and "red door" not in caplog.text


@pytest.mark.parametrize(("selfie", "prompt"), [("true", PROMPTS.selfie.suggestion), ("false", PROMPTS.suggestion)])
def test_selfie_says_which_suggestion_prompt_the_model_gets(client, image_api, make_image, selfie, prompt):
    assert suggest(client, make_image(), selfie=selfie).json() == {"poses": SUGGESTED}
    [payload] = image_api.suggestion_payloads
    text, _ = payload["messages"][0]["content"]
    assert text == {"type": "text", "text": prompt}


def test_failing_model_gives_the_selfie_default_poses_for_a_selfie(client, image_api, make_image):
    image_api.suggestion_fails(502)
    assert suggest(client, make_image(), selfie="true").json()["poses"] == list(PROMPTS.selfie.default_poses)


def test_description_of_a_selfie_is_varied_with_the_selfie_prompt(client, image_api, make_image):
    image_api.suggests(VARIATIONS)
    assert suggest(client, make_image(), DESCRIPTION, selfie="true").json() == {"poses": [DESCRIPTION, *VARIATIONS]}
    [payload] = image_api.suggestion_payloads
    text, _ = payload["messages"][0]["content"]
    assert text == {"type": "text", "text": variation_prompt(DESCRIPTION, selfie=True)}


def test_selfie_that_is_not_a_boolean_is_invalid(client, image_api, make_image):
    assert_error(suggest(client, make_image(), selfie="maybe"), 400, "INVALID_REQUEST")
    assert image_api.suggestion_requests == []
