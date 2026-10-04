"""One pose candidate per call through OpenRouter's Image API."""

import asyncio
import base64
import io
import logging
from http import HTTPStatus

import httpx2
from PIL import Image

from pix_server.config import Settings
from pix_server.errors import ApiError
from pix_server.poses.templates import PROMPT, PoseTemplate

logger = logging.getLogger(__name__)

API_URL = "https://openrouter.ai/api/v1/images"
MAX_SCENE_SIDE = 1024
SEED_LIMIT = 2**31  # Seedream rejects seeds above 2**31 - 1, and the app sends a 64-bit value
RESULT_JPEG_QUALITY = 90


def check_scene(data: bytes) -> None:
    """Accept only what the app sends (Design Documentation 2.5.3): a JPEG, long side at most 1024 px, no EXIF."""
    try:
        with Image.open(io.BytesIO(data), formats=["JPEG"]) as image:
            if max(image.size) > MAX_SCENE_SIDE:
                raise ApiError(400, "INVALID_IMAGE", f"The image's long side must be at most {MAX_SCENE_SIDE} px")
            # EXIF can carry the location, and the photo goes on to an external service (NFR-13).
            if image.getexif():
                raise ApiError(400, "INVALID_IMAGE", "The image must not contain EXIF metadata")
            image.load()
    except (OSError, Image.DecompressionBombError) as exc:
        raise ApiError(400, "INVALID_IMAGE", "The image must be a readable JPEG") from exc


def status_phrase(status: int) -> str:
    try:
        return HTTPStatus(status).phrase
    except ValueError:
        return "unrecognized status"


def as_jpeg(data: bytes) -> bytes:
    """Models answer in different formats; the app expects JPEG."""
    with Image.open(io.BytesIO(data), formats=["JPEG", "PNG", "WEBP"]) as image:
        if image.format == "JPEG":
            return data
        buffer = io.BytesIO()
        image.convert("RGB").save(buffer, "JPEG", quality=RESULT_JPEG_QUALITY)
        return buffer.getvalue()


class PoseGenerator:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def generate(self, scene: bytes, template: PoseTemplate, seed: int) -> bytes:
        """Return the candidate as JPEG bytes, or raise the ApiError the endpoint should answer with."""
        key = self._settings.openrouter_api_key
        if key is None or not key.get_secret_value():
            logger.error("OPENROUTER_API_KEY is not set, so poses cannot be generated")
            raise ApiError(502, "UPSTREAM_ERROR", "The image service is not configured")

        model, _, quality = self._settings.pose_model.partition("@")
        # OpenRouter ignores a field the chosen model does not support, so the same payload fits every model.
        payload = {
            "model": model,
            "prompt": PROMPT.format(pose=template.description),
            "input_references": [
                {
                    "type": "image_url",
                    "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(scene).decode()},
                }
            ],
            "aspect_ratio": "3:4",  # the app's fixed frame (AD-10)
            "resolution": "1K",
            "seed": seed % SEED_LIMIT,
        }
        if quality:
            payload["quality"] = quality

        timeout = self._settings.pose_upstream_timeout_seconds
        headers = {"Authorization": f"Bearer {key.get_secret_value()}"}
        try:
            async with asyncio.timeout(timeout):
                response = await self._http.post(API_URL, json=payload, headers=headers)
        except TimeoutError:
            raise ApiError(504, "UPSTREAM_TIMEOUT", f"The image service did not answer within {timeout:g} s") from None
        except httpx2.HTTPError as exc:
            logger.warning("OpenRouter could not be reached: %s", type(exc).__name__)
            raise ApiError(502, "UPSTREAM_ERROR", "The image service could not be reached") from exc

        if response.status_code == 403:
            # OpenRouter's status for a content filter or a model refusal.
            logger.warning("OpenRouter declined a pose request")
            raise ApiError(422, "REJECTED", "The image service declined this request")
        try:
            body = response.json()
            if response.status_code != 200 or "error" in body:
                # An upstream body can quote the prompt or the photo, so only the status reaches the log.
                logger.warning("OpenRouter answered %s (%s)", response.status_code, status_phrase(response.status_code))
                raise ApiError(502, "UPSTREAM_ERROR", "The image service failed")
            image = base64.b64decode(body["data"][0]["b64_json"])
            return await asyncio.to_thread(as_jpeg, image)
        except (ValueError, LookupError, TypeError, OSError) as exc:
            logger.warning("OpenRouter answered %s with an unreadable body", response.status_code)
            raise ApiError(502, "UPSTREAM_ERROR", "The image service returned an unreadable answer") from exc
