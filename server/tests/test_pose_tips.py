# AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
# POST /pose-tips partitions (Testing Documentation 2.1.6). Photos: a scene and a candidate within the contract, and
# either one not a JPEG, too large, carrying EXIF, or missing. Pose: one in Korean with extra whitespace, of 100
# characters and of 101, blank, or missing. Framing from the app: left out or empty (the model judges), each of the
# three values (it replaces the model's and goes into the prompt), or another value. The model's answer: usable; JSON
# in a code fence; a sentence over lines; unusable (not JSON, a field missing, a value outside the schema, an empty
# sentence, one over 160 characters, one with a number, a unit, or a degree, an empty message). The height move: the
# phone lower, higher, or where it was. Upstream: a refusal (403), a failing status, an unreadable body, no answer in
# time, no connection, no key. Limits: each window full, counted apart from poses and suggestions; refused input not
# counted; the session store down. Privacy: neither the pose nor the answer nor the upstream body is logged.
import base64
import json

import httpx2
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from pix_server.main import create_app
from pix_server.poses.prompts import DESCRIPTION_MAX_LENGTH, tip_prompt

POSE = "Wave at the camera with one hand"
ANSWER = {
    "shot": "full body",
    "pose": "standing",
    "camera_now": "chest",
    "camera_best": "waist",
    "angle": "up",
    "framing": "closer",
    "text": "Zoom in or take a step closer, hold the phone at your waist and tilt it up a little - the legs look longer.",
}


@pytest.fixture(autouse=True)
def model_answers(image_api):
    image_api.tips(ANSWER)


@pytest.fixture
def photos(make_image):
    """A 9:16 scene, as Camera may take it, and a 3:4 candidate, so the two are told apart in the payload."""
    return make_image(size=(576, 1024)), make_image(size=(768, 1024))


def ask(client, photos, pose=POSE, framing=None, files=None):
    scene, candidate = photos
    data = {"pose": pose} if pose is not None else {}
    if framing is not None:
        data["framing"] = framing
    if files is None:
        files = {"scene": ("scene.jpg", scene, "image/jpeg"), "candidate": ("candidate.jpg", candidate, "image/jpeg")}
    return client.post("/api/v1/pose-tips", files=files, data=data)


def assert_error(response, status, code):
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"error"} and body["error"]["code"] == code
    return body["error"]["message"]


def image_url(image: bytes) -> dict:
    return {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(image).decode()}}


def test_tip_comes_from_the_model_for_the_scene_and_the_candidate(client, image_api, photos, settings):
    response = ask(client, photos)

    assert response.status_code == 200
    assert response.json() == {
        "text": ANSWER["text"],
        "framing": "closer",
        "hold": "waist",
        "height": "lower",
        "angle": "up",
    }
    [request] = image_api.tip_requests
    assert str(request.url) == "https://openrouter.ai/api/v1/chat/completions"
    assert request.headers["Authorization"] == "Bearer test-key"
    assert image_api.suggestion_requests == [] and image_api.requests == []
    [payload] = image_api.tip_payloads
    assert payload["model"] == settings.tip_model
    assert payload["reasoning"] == {"effort": settings.tip_reasoning}
    assert payload["response_format"]["json_schema"]["name"] == "tip"
    assert payload["response_format"]["json_schema"]["strict"] is True
    [message] = payload["messages"]
    scene, candidate = photos
    # The scene first and the candidate second, as the prompt names them.
    assert message["content"] == [
        {"type": "text", "text": tip_prompt(None)},
        {"type": "text", "text": "Photo 1, the scene:"},
        image_url(scene),
        {"type": "text", "text": f'Photo 2, the photo they want, with the pose "{POSE}":'},
        image_url(candidate),
    ]


@pytest.mark.parametrize("framing", ["closer", "back", "keep"])
def test_measured_framing_replaces_the_models_and_goes_into_the_prompt(client, image_api, photos, framing):
    response = ask(client, photos, framing=framing)

    assert response.json()["framing"] == framing
    [payload] = image_api.tip_payloads
    assert payload["messages"][0]["content"][0]["text"] == tip_prompt(framing)


def test_empty_framing_lets_the_model_judge(client, image_api, photos):
    assert ask(client, photos, framing="").json()["framing"] == "closer"
    assert image_api.tip_payloads[0]["messages"][0]["content"][0]["text"] == tip_prompt(None)


def test_other_framing_is_refused_before_a_call(client, image_api, photos):
    assert_error(ask(client, photos, framing="far"), 400, "INVALID_REQUEST")
    assert image_api.tip_requests == []


@pytest.mark.parametrize(
    ("now", "best", "height"),
    [
        ("chest", "waist", "lower"),
        ("crouching", "chest", "raise"),
        ("eye", "eye", "keep"),
        ("waist", "above head", "raise"),
    ],
)
def test_height_move_compares_where_the_phone_was_with_where_it_should_be(client, image_api, photos, now, best, height):
    image_api.tips(ANSWER | {"camera_now": now, "camera_best": best})
    body = ask(client, photos).json()
    assert (body["hold"], body["height"]) == (best, height)


@pytest.mark.parametrize(
    "content",
    [
        f"```json\n{json.dumps(ANSWER)}\n```",
        json.dumps(
            ANSWER
            | {"text": "Zoom in or take a step closer,\n  hold the phone at your waist and tilt it up a\nlittle."}
        ),
    ],
    ids=["code fence", "sentence over lines"],
)
def test_usable_answer_in_another_shape_is_read(client, image_api, photos, content):
    image_api.tip_answers(content)
    response = ask(client, photos)
    assert response.status_code == 200
    assert "\n" not in response.json()["text"] and "  " not in response.json()["text"]


@pytest.mark.parametrize(
    "content",
    [
        "Hold the phone at your waist.",
        json.dumps({key: value for key, value in ANSWER.items() if key != "angle"}),
        json.dumps(ANSWER | {"camera_best": "knee"}),
        json.dumps(ANSWER | {"angle": "sideways"}),
        json.dumps(ANSWER | {"framing": "far"}),
        json.dumps(ANSWER | {"text": "  "}),
        json.dumps(ANSWER | {"text": "Hold the phone at your waist. " * 6}),
        json.dumps(ANSWER | {"text": "Take 2 steps back and hold the phone at your waist."}),
        json.dumps(ANSWER | {"text": "Step back about a meter and hold the phone at your waist."}),
        json.dumps(ANSWER | {"text": "Tilt the phone up by fifteen degrees."}),
        json.dumps(ANSWER | {"text": "Tilt the phone up 15°."}),
        json.dumps(ANSWER | {"text": 42}),
        None,
    ],
    ids=[
        "not JSON",
        "field missing",
        "unknown height",
        "unknown angle",
        "unknown framing",
        "empty sentence",
        "too long",
        "number",
        "unit",
        "degrees",
        "degree sign",
        "sentence not a string",
        "empty message",
    ],
)
def test_unusable_answer_is_an_upstream_error(client, image_api, photos, content):
    image_api.tip_answers(content)
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


def test_declined_photos_are_rejected(client, image_api, photos):
    image_api.tip_fails(403, "Content moderation")
    assert_error(ask(client, photos), 422, "REJECTED")


@pytest.mark.parametrize("status", [400, 429, 500, 503])
def test_failing_model_is_an_upstream_error(client, image_api, photos, status):
    image_api.tip_fails(status)
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("body", [{"choices": []}, {"choices": [{}]}, ["not", "an", "object"]])
def test_unreadable_body_is_an_upstream_error(client, image_api, photos, body):
    image_api.tip_status, image_api.tip_body = 200, body
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


def test_slow_model_times_out(client, image_api, photos, settings):
    settings.tip_timeout_seconds = 0.05
    image_api.tip_delay = 5
    response = ask(client, photos)
    assert_error(response, 504, "UPSTREAM_TIMEOUT")
    assert response.elapsed.total_seconds() < 2


def test_unreachable_model_is_an_upstream_error(settings, photos, open_store, clock):
    def unreachable(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ConnectError("no route to host", request=request)

    app = create_app(settings, clock=clock, image_api=httpx2.MockTransport(unreachable), redis=open_store)
    with TestClient(app) as client:
        assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("key", [None, SecretStr("")])
def test_missing_api_key_is_an_upstream_error_without_a_call(client, image_api, photos, settings, key):
    settings.openrouter_api_key = key
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")
    assert image_api.tip_requests == []


def test_photos_outside_the_contract_are_invalid(client, image_api, photos, make_image):
    scene, candidate = photos
    for image in (make_image("PNG"), make_image(size=(768, 1025)), make_image(exif=True), b"not an image"):
        assert_error(ask(client, (image, candidate)), 400, "INVALID_IMAGE")
        assert_error(ask(client, (scene, image)), 400, "INVALID_IMAGE")
    only_scene = {"scene": ("scene.jpg", scene, "image/jpeg")}
    assert_error(ask(client, photos, files=only_scene), 400, "INVALID_REQUEST")
    only_candidate = {"candidate": ("candidate.jpg", candidate, "image/jpeg")}
    assert_error(ask(client, photos, files=only_candidate), 400, "INVALID_REQUEST")
    assert image_api.tip_requests == []


def test_pose_is_folded_like_a_description(client, image_api, photos):
    assert ask(client, photos, pose="  벽에 기대서\n  카메라 보며 웃기  ").status_code == 200
    [payload] = image_api.tip_payloads
    assert (
        payload["messages"][0]["content"][3]["text"]
        == 'Photo 2, the photo they want, with the pose "벽에 기대서 카메라 보며 웃기":'
    )


def test_pose_of_the_longest_length_is_accepted(client, photos):
    assert ask(client, photos, pose="a" * DESCRIPTION_MAX_LENGTH).status_code == 200


@pytest.mark.parametrize("pose", [" \n ", "a" * (DESCRIPTION_MAX_LENGTH + 1), None])
def test_invalid_pose_is_refused_before_a_call_and_not_counted(client, image_api, photos, settings, pose):
    message = assert_error(ask(client, photos, pose=pose), 400, "INVALID_REQUEST")
    assert "pose" in message
    for _ in range(settings.tip_rate_limit):
        assert ask(client, photos).status_code == 200
    assert len(image_api.tip_requests) == settings.tip_rate_limit


def test_tips_are_limited_apart_from_poses_and_suggestions(client, image_api, photos, settings, clock, make_image):
    for _ in range(settings.tip_rate_limit):
        assert ask(client, photos).status_code == 200
    assert_error(ask(client, photos), 429, "RATE_LIMITED")
    assert len(image_api.tip_requests) == settings.tip_rate_limit
    # Poses and suggestions have windows of their own.
    files = {"image": ("scene.jpg", make_image(), "image/jpeg")}
    assert client.post("/api/v1/poses", files=files, data={"seed": "1", "description": "Wave"}).status_code == 200
    assert client.post("/api/v1/pose-suggestions", files=files).status_code == 200

    clock.advance(settings.pose_rate_window_seconds)
    assert ask(client, photos).status_code == 200


def test_global_tip_limit_refuses_every_client(client, photos, settings, clock):
    settings.tip_global_limit = 2
    for _ in range(2):
        assert ask(client, photos).status_code == 200
    assert "today" in assert_error(ask(client, photos), 429, "RATE_LIMITED")
    clock.advance(settings.pose_global_window_seconds)
    assert ask(client, photos).status_code == 200


def test_tips_are_refused_while_the_session_store_is_down(client, image_api, photos, outage):
    outage(True)
    assert_error(ask(client, photos), 503, "UNAVAILABLE")
    assert image_api.tip_requests == []
    outage(False)
    assert ask(client, photos).status_code == 200


@pytest.mark.parametrize("status", [200, 500])
def test_pose_answer_and_upstream_body_are_not_logged(client, image_api, photos, caplog, status):
    if status == 200:
        image_api.tip_answers(json.dumps(ANSWER | {"text": "SECRET-TIP: stand by the red door, 3 steps"}))
    else:
        image_api.tip_fails(status, "invalid prompt: SECRET-PROMPT data:image/jpeg;base64,SECRET-PHOTO")
    assert_error(ask(client, photos, pose="SECRET-POSE by the red door"), 502, "UPSTREAM_ERROR")
    assert "could not be used" in caplog.text or "OpenRouter answered" in caplog.text
    assert "SECRET" not in caplog.text
    assert "red door" not in caplog.text
