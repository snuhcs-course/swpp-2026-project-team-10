# AI-generated with Claude Code, 2026-10-04, reviewed by Jaewan Park
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
from pix_server.poses.limiter import RateLimiter
from pix_server.poses.templates import TEMPLATES


def create_pose(client, image, template_id="wave", seed="1829304756"):
    return client.post(
        "/api/v1/poses",
        files={"image": ("scene.jpg", image, "image/jpeg")},
        data={"templateId": template_id, "seed": seed},
    )


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
    assert TEMPLATES["wave"].description in payload["prompt"]
    assert "{pose}" not in payload["prompt"]
    [reference] = payload["input_references"]
    assert reference["image_url"]["url"] == "data:image/jpeg;base64," + base64.b64encode(scene).decode()


def test_every_template_sends_its_own_pose_description(client, image_api, make_image):
    for template_id in TEMPLATES:
        assert create_pose(client, make_image(), template_id).json()["templateId"] == template_id
    prompts = [payload["prompt"] for payload in image_api.payloads]
    assert len(set(prompts)) == len(TEMPLATES)
    for template, prompt in zip(TEMPLATES.values(), prompts):
        assert template.description in prompt


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
    missing_fields = client.post("/api/v1/poses", files={"image": ("scene.jpg", make_image(), "image/jpeg")})
    assert "templateId" in assert_error(missing_fields, 400, "INVALID_REQUEST")
    assert image_api.requests == []


def test_declined_request_is_rejected(client, image_api, make_image):
    image_api.fails(403, "Input flagged by moderation")
    message = assert_error(create_pose(client, make_image()), 422, "REJECTED")
    assert "moderation" not in message


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
    for _ in range(settings.pose_rate_limit):
        assert create_pose(client, make_image()).status_code == 200
    assert_error(create_pose(client, make_image()), 429, "RATE_LIMITED")
    assert len(image_api.requests) == settings.pose_rate_limit

    clock.advance(settings.pose_rate_window_seconds)
    assert create_pose(client, make_image()).status_code == 200


def test_rate_limiter_slides_and_separates_clients(clock):
    limiter = RateLimiter(limit=2, window_seconds=60, clock=clock)
    assert limiter.allow("a")
    clock.advance(30)
    assert limiter.allow("a")
    assert not limiter.allow("a")
    assert limiter.allow("b")
    clock.advance(30)
    assert limiter.allow("a")
    assert not limiter.allow("a")


async def test_dropped_request_cancels_the_image_api_call(settings, image_api, make_image, unused_tcp_port):
    # A real server and socket, because the test client cannot close a connection in the middle of a request.
    image_api.delay = 30
    app = create_app(settings, image_api=httpx2.MockTransport(image_api))
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
