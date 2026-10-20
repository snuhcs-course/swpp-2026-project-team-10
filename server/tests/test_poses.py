# AI-generated with Claude Code, 2026-10-20, reviewed by Jaewan Park
# POST /poses partitions (Testing Documentation 2.1.6). What to pose: a template only, a description only, both,
# neither, an unknown template, and an empty description next to a template. Description: Korean, with extra
# whitespace and line breaks, blank, 100 characters (after trimming) and 101. Scene: a JPEG within the contract, at
# 1024 px, and one that is not a JPEG, too large, carries EXIF, or is cut short. Image API: a candidate in JPEG, PNG,
# or WEBP; a refusal (403) of a template and of a description; another failing status; an unreadable answer; no
# answer in time; no key. Limits: each window full, and a request refused before the image API, which is not counted.
import asyncio
import base64
import io
import tempfile

import httpx2
import pytest
import uvicorn
from PIL import Image
from pydantic import SecretStr
from starlette.formparsers import MultiPartParser

from pix_server.main import MAX_REQUEST_BYTES, create_app
from pix_server.poses.limiter import PoseLimiter
from pix_server.poses.templates import DESCRIPTION_MAX_LENGTH, TEMPLATES
from pix_server.store import Window


def create_pose(client, image, template_id="wave", seed="1829304756", description=None):
    data = {"seed": seed}
    if template_id is not None:
        data["templateId"] = template_id
    if description is not None:
        data["description"] = description
    return client.post("/api/v1/poses", files={"image": ("scene.jpg", image, "image/jpeg")}, data=data)


def describe_pose(client, image, description):
    return create_pose(client, image, template_id=None, description=description)


def assert_error(response, status, code):
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"error"}
    assert set(body["error"]) == {"code", "message"}
    assert body["error"]["code"] == code
    assert body["error"]["message"]
    return body["error"]["message"]


def test_pose_templates_expose_only_id_and_label(client):
    response = client.get("/api/v1/pose-templates")
    assert response.status_code == 200
    assert response.json() == [
        {"id": "hands_on_hips", "label": "Hands on hips"},
        {"id": "wave", "label": "Wave"},
        {"id": "walking", "label": "Walking"},
        {"id": "arms_crossed", "label": "Arms crossed"},
    ]


def test_pose_is_generated_from_the_scene_and_template(client, image_api, make_image, settings):
    scene, candidate = make_image(), make_image(size=(832, 1110))
    image_api.returns(candidate)

    response = create_pose(client, scene)

    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"templateId", "image", "elapsedMs"}
    assert body["templateId"] == "wave"
    assert base64.b64decode(body["image"]) == candidate
    assert isinstance(body["elapsedMs"], int) and body["elapsedMs"] >= 0

    [request] = image_api.requests
    assert str(request.url) == "https://openrouter.ai/api/v1/images"
    assert request.headers["Authorization"] == "Bearer test-key"
    [payload] = image_api.payloads
    assert payload["model"] == settings.pose_model
    assert payload["seed"] == 1829304756
    assert payload["aspect_ratio"] == "3:4"
    assert payload["resolution"] == "1K"
    assert "quality" not in payload
    assert TEMPLATES["wave"].pose in payload["prompt"]
    assert "{pose}" not in payload["prompt"]
    [reference] = payload["input_references"]
    assert reference["image_url"]["url"] == "data:image/jpeg;base64," + base64.b64encode(scene).decode()


def test_every_template_sends_its_own_pose_description(client, image_api, make_image):
    for template_id in TEMPLATES:
        assert create_pose(client, make_image(), template_id).json()["templateId"] == template_id
    prompts = [payload["prompt"] for payload in image_api.payloads]
    assert len(set(prompts)) == len(TEMPLATES)
    for template, prompt in zip(TEMPLATES.values(), prompts):
        assert template.pose in prompt


def test_pose_is_generated_from_a_description(client, image_api, make_image):
    candidate = make_image(size=(832, 1110))
    image_api.returns(candidate)

    response = describe_pose(client, make_image(), "벽에 기대서 카메라 보며 살짝 웃기")

    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"templateId", "image", "elapsedMs"}
    assert body["templateId"] is None
    assert base64.b64decode(body["image"]) == candidate
    [payload] = image_api.payloads
    assert "벽에 기대서 카메라 보며 살짝 웃기" in payload["prompt"]
    assert "{pose}" not in payload["prompt"]
    assert not any(template.pose in payload["prompt"] for template in TEMPLATES.values())


def test_description_reaches_the_prompt_on_one_line(client, image_api, make_image):
    assert describe_pose(client, make_image(), "  벽에 기대서\n한 손은\t\t주머니에 \r\n").status_code == 200
    [payload] = image_api.payloads
    assert "벽에 기대서 한 손은 주머니에" in payload["prompt"]
    assert not any(character in payload["prompt"] for character in "\n\r\t")


@pytest.mark.parametrize(
    "description",
    ["가" * DESCRIPTION_MAX_LENGTH, " " + "가" * DESCRIPTION_MAX_LENGTH + "\n", "a" * 50 + "  " + "b" * 49],
)
def test_description_of_the_longest_length_is_accepted(client, image_api, make_image, description):
    assert describe_pose(client, make_image(), description).status_code == 200
    assert " ".join(description.split()) in image_api.payloads[0]["prompt"]


@pytest.mark.parametrize("description", ["가" * (DESCRIPTION_MAX_LENGTH + 1), " \n\t "])
def test_description_too_long_or_blank_is_invalid(client, image_api, make_image, description):
    message = assert_error(describe_pose(client, make_image(), description), 400, "INVALID_REQUEST")
    assert message.startswith("description")
    assert description not in message
    assert image_api.requests == []


def test_template_and_description_together_are_invalid(client, image_api, make_image):
    response = create_pose(client, make_image(), "wave", description="Looking back over the shoulder")
    assert "templateId or description" in assert_error(response, 400, "INVALID_REQUEST")
    assert image_api.requests == []


def test_request_without_template_or_description_is_invalid(client, image_api, make_image):
    for response in (create_pose(client, make_image(), None), describe_pose(client, make_image(), "")):
        assert "templateId or description" in assert_error(response, 400, "INVALID_REQUEST")
    assert image_api.requests == []


def test_empty_description_next_to_a_template_asks_for_the_template(client, image_api, make_image):
    response = create_pose(client, make_image(), "wave", description="")
    assert response.status_code == 200
    assert response.json()["templateId"] == "wave"
    assert TEMPLATES["wave"].pose in image_api.payloads[0]["prompt"]


def test_model_and_quality_come_from_settings(client, image_api, make_image, settings):
    settings.pose_model = "openai/gpt-image-2.5-flare@low"
    assert create_pose(client, make_image()).status_code == 200
    [payload] = image_api.payloads
    assert payload["model"] == "openai/gpt-image-2.5-flare"
    assert payload["quality"] == "low"


@pytest.mark.parametrize(
    ("seed", "sent"),
    [("0", 0), ("2147483647", 2147483647), ("2147483648", 0), ("9223372036854775807", 2147483647), ("-1", 2147483647)],
)
def test_seed_is_folded_into_the_range_the_image_api_accepts(client, image_api, make_image, seed, sent):
    assert create_pose(client, make_image(), seed=seed).status_code == 200
    assert image_api.payloads[0]["seed"] == sent


@pytest.mark.parametrize("image_format", ["PNG", "WEBP"])
def test_candidate_in_another_format_is_returned_as_jpeg(client, image_api, make_image, image_format):
    image_api.returns(make_image(image_format, size=(1152, 1536)))
    response = create_pose(client, make_image())
    assert response.status_code == 200
    with Image.open(io.BytesIO(base64.b64decode(response.json()["image"]))) as candidate:
        assert candidate.format == "JPEG"
        assert candidate.size == (1152, 1536)


def test_unknown_template_is_rejected_without_calling_the_image_api(client, image_api, make_image):
    assert_error(create_pose(client, make_image(), template_id="cartwheel"), 400, "UNKNOWN_TEMPLATE")
    assert image_api.requests == []


@pytest.mark.parametrize("image", [b"", b"not an image"])
def test_unreadable_image_is_invalid(client, image_api, image):
    assert_error(create_pose(client, image), 400, "INVALID_IMAGE")
    assert image_api.requests == []


def test_image_outside_the_contract_is_invalid(client, image_api, make_image):
    too_large = make_image(size=(1025, 768))
    with_metadata = make_image(exif=True)
    truncated = make_image()[:-200]
    for image in (make_image("PNG"), too_large, with_metadata, truncated):
        assert_error(create_pose(client, image), 400, "INVALID_IMAGE")
    assert "1024 px" in assert_error(create_pose(client, too_large), 400, "INVALID_IMAGE")
    assert "EXIF" in assert_error(create_pose(client, with_metadata), 400, "INVALID_IMAGE")
    assert image_api.requests == []


def test_image_at_the_size_limit_is_accepted(client, make_image):
    assert create_pose(client, make_image(size=(1024, 768))).status_code == 200


def test_malformed_request_uses_the_api_error_shape(client, image_api, make_image):
    assert "seed" in assert_error(create_pose(client, make_image(), seed="abc"), 400, "INVALID_REQUEST")
    missing_image = client.post("/api/v1/poses", data={"templateId": "wave", "seed": "1"})
    assert "image" in assert_error(missing_image, 400, "INVALID_REQUEST")
    missing_seed = client.post(
        "/api/v1/poses", files={"image": ("scene.jpg", make_image(), "image/jpeg")}, data={"templateId": "wave"}
    )
    assert "seed" in assert_error(missing_seed, 400, "INVALID_REQUEST")
    assert image_api.requests == []


def test_declined_request_is_rejected(client, image_api, make_image):
    image_api.fails(403, "Input flagged by moderation")
    message = assert_error(create_pose(client, make_image()), 422, "REJECTED")
    assert "moderation" not in message
    assert "description" not in message


def test_declined_description_asks_for_another(client, image_api, make_image):
    image_api.fails(403, "Input flagged by moderation")
    message = assert_error(describe_pose(client, make_image(), "Looking back over the shoulder"), 422, "REJECTED")
    assert message == "Try a different description"


def test_failure_with_a_description_still_answers_its_own_error(client, image_api, make_image):
    image_api.fails(500)
    assert_error(describe_pose(client, make_image(), "Looking back over the shoulder"), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("status", [400, 401, 402, 429, 500, 502, 524])
def test_image_api_failure_is_an_upstream_error(client, image_api, make_image, status):
    image_api.fails(status)
    assert_error(create_pose(client, make_image()), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize(
    "body",
    [
        {},
        {"data": []},
        {"data": [{"b64_json": "not base64!"}]},
        {"data": [{"b64_json": base64.b64encode(b"not an image").decode()}]},
        "<html>bad gateway</html>",
    ],
)
def test_unreadable_answer_is_an_upstream_error(client, image_api, make_image, body):
    image_api.body = body
    assert_error(create_pose(client, make_image()), 502, "UPSTREAM_ERROR")


def test_slow_image_api_times_out(client, image_api, make_image, settings):
    settings.pose_upstream_timeout_seconds = 0.05
    image_api.delay = 5
    message = assert_error(create_pose(client, make_image()), 504, "UPSTREAM_TIMEOUT")
    assert message == "The image service did not answer within 0.05 s"


@pytest.mark.parametrize("key", [None, SecretStr("")])
def test_missing_api_key_is_an_upstream_error(client, image_api, make_image, settings, key):
    settings.openrouter_api_key = key
    assert_error(create_pose(client, make_image()), 502, "UPSTREAM_ERROR")
    assert image_api.requests == []


def test_rate_limit_counts_only_requests_sent_to_the_image_api(client, image_api, make_image, settings, clock):
    assert_error(create_pose(client, make_image("PNG")), 400, "INVALID_IMAGE")
    assert_error(create_pose(client, make_image(), template_id="cartwheel"), 400, "UNKNOWN_TEMPLATE")
    assert_error(describe_pose(client, make_image(), "가" * 101), 400, "INVALID_REQUEST")
    assert_error(create_pose(client, make_image(), "wave", description="Waving"), 400, "INVALID_REQUEST")
    for _ in range(settings.pose_rate_limit):
        assert create_pose(client, make_image()).status_code == 200
    assert_error(create_pose(client, make_image()), 429, "RATE_LIMITED")
    assert len(image_api.requests) == settings.pose_rate_limit

    clock.advance(settings.pose_rate_window_seconds)
    assert create_pose(client, make_image()).status_code == 200


def test_global_limit_refuses_every_client_once_the_day_is_used_up(client, image_api, make_image, settings, clock):
    settings.pose_global_limit = 3
    for _ in range(3):
        assert create_pose(client, make_image()).status_code == 200
    message = assert_error(create_pose(client, make_image()), 429, "RATE_LIMITED")
    assert "today" in message
    assert len(image_api.requests) == 3
    clock.advance(settings.pose_global_window_seconds)
    assert create_pose(client, make_image()).status_code == 200


async def test_window_slides_and_separates_keys(clock, make_registry):
    store = (await make_registry()).store
    a, b = Window("rate:test:a", 2, 60), Window("rate:test:b", 2, 60)
    assert await store.allow(a) is None
    clock.advance(30)
    assert await store.allow(a) is None
    assert await store.allow(a) == a
    assert await store.allow(b) is None
    clock.advance(30)
    assert await store.allow(a) is None
    assert await store.allow(a) == a


async def test_windows_are_shared_by_all_workers(make_registry):
    first, second = (await make_registry()).store, (await make_registry()).store
    window = Window("rate:test:a", 2, 60)
    assert await first.allow(window) is None
    assert await second.allow(window) is None
    assert await first.allow(window) == window
    assert await second.allow(window) == window


async def test_pose_limiter_counts_a_refused_request_in_neither_window(settings, make_registry):
    settings.pose_rate_limit, settings.pose_global_limit = 2, 3
    limiter = PoseLimiter((await make_registry()).store, settings)
    assert [await limiter.refuse("a") for _ in range(3)] == [None, None, "client"]
    # The third request of "a" was refused by its own window, so the global window holds two and has room for one.
    assert await limiter.refuse("b") is None
    assert await limiter.refuse("c") == "global"
    # Refused by the global window, so "c" has not used its own.
    settings.pose_global_limit = 10
    assert [await limiter.refuse("c") for _ in range(3)] == [None, None, "client"]


def test_pose_request_is_refused_while_the_session_store_is_down(client, image_api, make_image, outage):
    outage(True)
    assert_error(create_pose(client, make_image()), 503, "UNAVAILABLE")
    assert image_api.requests == []  # A request that cannot be counted is not sent to the image API.
    outage(False)
    assert create_pose(client, make_image()).status_code == 200


async def test_dropped_request_cancels_the_image_api_call(settings, image_api, make_image, unused_tcp_port, open_store):
    # A real server and socket, because the test client cannot close a connection in the middle of a request.
    image_api.delay = 30
    app = create_app(settings, image_api=httpx2.MockTransport(image_api), redis=open_store)
    server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=unused_tcp_port, log_level="warning"))
    serving = asyncio.create_task(server.serve())
    try:
        while not server.started:
            await asyncio.sleep(0.01)
        async with httpx2.AsyncClient(base_url=f"http://127.0.0.1:{unused_tcp_port}") as phone:
            with pytest.raises(httpx2.ReadTimeout):
                await phone.post(
                    "/api/v1/poses",
                    files={"image": ("scene.jpg", make_image(), "image/jpeg")},
                    data={"templateId": "wave", "seed": "1"},
                    timeout=0.2,
                )
        assert len(image_api.requests) == 1
        async with asyncio.timeout(2):
            await image_api.cancelled.wait()
    finally:
        server.should_exit = True
        await serving


def test_request_that_stays_connected_is_not_cancelled(client, image_api, make_image):
    image_api.delay = 0.6
    assert create_pose(client, make_image()).status_code == 200
    assert not image_api.cancelled.is_set()


@pytest.fixture
def spooled_to_disk(monkeypatch):
    """Records every upload that Starlette moves from memory to a temporary file."""
    rollovers = []
    rollover = tempfile.SpooledTemporaryFile.rollover

    def record(file):
        rollovers.append(file)
        rollover(file)

    monkeypatch.setattr(tempfile.SpooledTemporaryFile, "rollover", record)
    return rollovers


def test_oversized_request_is_refused_before_it_is_parsed(client, image_api, make_image, spooled_to_disk):
    response = create_pose(client, make_image() + b"\0" * MAX_REQUEST_BYTES)
    assert response.status_code == 413
    assert spooled_to_disk == []
    assert image_api.requests == []


def test_oversized_request_without_a_declared_length_is_refused(client, image_api, make_image, spooled_to_disk):
    upload = httpx2.Request(
        "POST",
        "http://testserver/api/v1/poses",
        files={"image": ("scene.jpg", make_image() + b"\0" * 4 * MAX_REQUEST_BYTES, "image/jpeg")},
        data={"templateId": "wave", "seed": "1"},
    )
    body = upload.read()
    chunks = (body[start : start + 65_536] for start in range(0, len(body), 65_536))
    response = client.post("/api/v1/poses", content=chunks, headers={"Content-Type": upload.headers["Content-Type"]})
    assert "content-length" not in response.request.headers
    assert response.status_code == 413
    assert spooled_to_disk == []
    assert image_api.requests == []


def test_largest_accepted_upload_stays_in_memory(client, make_image, spooled_to_disk):
    # A valid JPEG followed by padding still decodes, so this request is parsed and reaches the image API.
    image = make_image()
    image += b"\0" * (MAX_REQUEST_BYTES - len(image) - 2048)
    response = create_pose(client, image)
    assert int(response.request.headers["content-length"]) > MAX_REQUEST_BYTES - 2048
    assert response.status_code == 200
    assert spooled_to_disk == []
    assert MAX_REQUEST_BYTES <= MultiPartParser.spool_max_size


@pytest.mark.parametrize("status", [200, 400, 502])
def test_upstream_body_is_not_logged(client, image_api, make_image, caplog, status):
    image_api.fails(status, "invalid prompt: SECRET-PROMPT data:image/jpeg;base64,SECRET-PHOTO")
    assert_error(create_pose(client, make_image()), 502, "UPSTREAM_ERROR")
    assert "OpenRouter answered" in caplog.text
    assert str(status) in caplog.text
    assert "SECRET" not in caplog.text


@pytest.mark.parametrize("status", [403, 502])
def test_description_is_not_logged(client, image_api, make_image, caplog, status):
    image_api.fails(status)
    assert describe_pose(client, make_image(), "SECRET-DESCRIPTION 벽에 기대서").status_code in (422, 502)
    assert "OpenRouter" in caplog.text
    assert "SECRET" not in caplog.text
    assert "벽에" not in caplog.text
