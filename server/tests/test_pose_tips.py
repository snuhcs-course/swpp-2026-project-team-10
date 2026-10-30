# AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
# POST /pose-tips partitions (Testing Documentation 2.1.6). Photos: a scene and a candidate within the contract, and
# either one not a JPEG, too large, carrying EXIF, or missing. Pose: one in Korean with extra whitespace, of 100
# characters and of 101, blank, or missing. The model's answer: usable, with each framing it may judge; JSON in a code
# fence; a sentence over lines; unusable (not JSON, a field missing, a value outside the schema, an empty
# sentence, one over 160 characters, one with a number in digits or in words, "one" before a step, a unit, or a
# degree, an empty message), and a sentence whose words only contain a number ("often", "someone"). The height move:
# the phone lower, higher, or where it was. Upstream: a refusal (403), a failing status, an unreadable body, no answer
# in time, no connection, no key. The phone: staying connected, or dropping the request while the model works.
# Limits: each window full, counted apart from poses and suggestions; refused input not counted; the session store
# down. Privacy: neither the pose nor the answer nor the upstream body is logged. Language (FR-10.2): `ko`, `en`, left
# out, and another value; a sentence in the language asked for, in the other one, in both, in another script (Japanese),
# with one, or with no letters; a Korean sentence that is
# usable, one with words that only contain a count ("한쪽", "두 손") or that look like one (구도, 두 발로, 이발), and
# one with a count of steps, with or without a space, of words run together ("한두", "열한", "이십"), or with an ending
# ("한 걸음만"), a unit, or a degree in digits or in words, of one digit or more, with or without an ending; a Korean
# sentence of 80 characters and of 81.
import asyncio
import base64
import json

import httpx2
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from pix_server.main import create_app
from pix_server.poses.prompts import DESCRIPTION_MAX_LENGTH, PROMPTS

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
KOREAN_TEXT = "줌을 당기거나 조금 다가가서 허리 높이에서 살짝 위로 기울이세요 - 다리가 길어 보여요."


@pytest.fixture(autouse=True)
def model_answers(image_api):
    image_api.tips(ANSWER)


@pytest.fixture
def photos(make_image):
    """A 9:16 scene, as Camera may take it, and a 3:4 candidate, so the two are told apart in the payload."""
    return make_image(size=(576, 1024)), make_image(size=(768, 1024))


def ask(client, photos, pose=POSE, files=None, language=None):
    scene, candidate = photos
    data = {"pose": pose} if pose is not None else {}
    if language is not None:
        data["language"] = language
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
        {"type": "text", "text": PROMPTS.tip},
        {"type": "text", "text": "Photo 1, the scene:"},
        image_url(scene),
        {"type": "text", "text": f'Photo 2, the photo they want, with the pose "{POSE}":'},
        image_url(candidate),
    ]


@pytest.mark.parametrize(("language", "korean"), [("ko", True), ("en", False), (None, False)])
def test_language_says_whether_the_sentence_is_asked_in_korean(client, image_api, photos, language, korean):
    if korean:
        image_api.tips(ANSWER | {"text": KOREAN_TEXT})
    assert ask(client, photos, language=language).status_code == 200
    [payload] = image_api.tip_payloads
    texts = [part["text"] for part in payload["messages"][0]["content"] if part["type"] == "text"]
    assert texts[0] == PROMPTS.tip
    assert (PROMPTS.tip_korean in texts) is korean
    if korean:
        assert texts[1] == PROMPTS.tip_korean  # right after the rules it changes, before the photos


def test_language_the_app_does_not_have_is_refused_before_a_call(client, image_api, photos):
    assert_error(ask(client, photos, language="ja"), 400, "INVALID_REQUEST")
    assert image_api.tip_requests == []


@pytest.mark.parametrize(
    "text",
    [
        KOREAN_TEXT,
        "한쪽 어깨가 보이게 가슴 높이에서 두 손으로 수평으로 드세요 - 몸의 비율이 그대로 보여요.",
        "두 발로 단단히 서서 구도를 맞추고 가슴 높이에서 드세요 - 이발한 머리까지 깔끔하게 담겨요.",
    ],
    ids=["usable", "words containing a count", "words that look like a count"],
)
def test_korean_sentence_is_shown(client, image_api, photos, text):
    image_api.tips(ANSWER | {"text": text})
    response = ask(client, photos, language="ko")
    assert response.status_code == 200
    assert response.json()["text"] == text


@pytest.mark.parametrize(
    "text",
    [
        "한 걸음 다가가서 허리 높이에서 드세요.",
        "두걸음 물러나서 가슴 높이에서 드세요.",
        "1미터 뒤로 물러나서 드세요.",
        "삼십 센티 낮춰서 드세요.",
        "십오 도 위로 기울이세요.",
        "15° 위로 기울이세요.",
        "한두 걸음 다가가서 드세요.",
        "열한 걸음 물러나서 드세요.",
        "이십 걸음 뒤에서 드세요.",
        "한 걸음만 다가가서 드세요.",
        "한 발만 물러나서 드세요.",
        "삼십 도로 기울이세요.",
        "구 도로 기울이세요.",
        "오 도만 기울이세요.",
    ],
    ids=[
        "count of steps",
        "count without a space",
        "unit with digits",
        "unit in words",
        "degrees in words",
        "degree sign",
        "count of two words",
        "count of ten and one",
        "Sino-Korean count",
        "count with an ending",
        "native count of 발",
        "degrees with an ending",
        "one digit of degrees with an ending",
        "one digit of degrees with another ending",
    ],
)
def test_korean_sentence_with_a_count_a_unit_or_a_degree_is_unusable(client, image_api, photos, text):
    image_api.tips(ANSWER | {"text": text})
    assert_error(ask(client, photos, language="ko"), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize(("length", "shown"), [(80, True), (81, False)])
def test_korean_sentence_is_shown_up_to_eighty_characters(client, image_api, photos, length, shown):
    # tip_korean asks for at most eighty characters, and the server keeps to them; English has 160.
    text = KOREAN_TEXT + "아" * (length - len(KOREAN_TEXT))
    image_api.tips(ANSWER | {"text": text})
    response = ask(client, photos, language="ko")
    if shown:
        assert response.json()["text"] == text
    else:
        assert_error(response, 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize(
    ("language", "text"),
    [
        ("ko", ANSWER["text"]),
        ("ko", "가슴 높이에서 keep it level - 신체 비율이 유지돼요."),
        ("en", "가슴 높이에서 수평으로 드세요 - 신체 비율이 유지돼요."),
        ("en", "Hold the phone at your 가슴 and keep it level."),
        ("en", "胸の高さで水平に持ってください。"),
        ("en", "... - !"),
        ("ko", "가슴 높이에서 수평으로 드세요 - です."),
    ],
    ids=[
        "English asked in Korean",
        "both asked in Korean",
        "Korean asked in English",
        "both asked in English",
        "another script asked in English",
        "no letters",
        "another script within Korean",
    ],
)
def test_sentence_not_in_the_language_asked_for_is_unusable(client, image_api, photos, language, text):
    # A model can leave out the Korean instruction, or answer in the language of a pose typed in Korean.
    image_api.tips(ANSWER | {"text": text})
    assert_error(ask(client, photos, language=language), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("framing", ["closer", "back", "keep"])
def test_framing_is_the_models(client, image_api, photos, framing):
    image_api.tips(ANSWER | {"framing": framing})
    assert ask(client, photos).json()["framing"] == framing


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
        json.dumps(
            ANSWER | {"text": "Someone this tall often looks best from your waist, with the phone tilted up a little."}
        ),
    ],
    ids=["code fence", "sentence over lines", "words containing a number"],
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
        json.dumps(ANSWER | {"text": "Take two steps back and hold the phone at your waist."}),
        json.dumps(ANSWER | {"text": "Take one step closer and hold the phone at your waist."}),
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
        "number in words",
        "one step",
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


async def test_dropped_request_cancels_the_model_call(live_server, image_api, photos):
    # The phone drops the request when the user takes another candidate before the tip arrives.
    image_api.tip_delay = 30
    scene, candidate = photos
    async with httpx2.AsyncClient(base_url=live_server) as phone:
        with pytest.raises(httpx2.ReadTimeout):
            await phone.post(
                "/api/v1/pose-tips",
                files={
                    "scene": ("scene.jpg", scene, "image/jpeg"),
                    "candidate": ("candidate.jpg", candidate, "image/jpeg"),
                },
                data={"pose": POSE},
                timeout=0.2,
            )
    assert len(image_api.tip_requests) == 1
    async with asyncio.timeout(2):
        await image_api.cancelled.wait()


def test_request_that_stays_connected_is_not_cancelled(client, image_api, photos):
    image_api.tip_delay = 0.6
    assert ask(client, photos).status_code == 200
    assert not image_api.cancelled.is_set()


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
