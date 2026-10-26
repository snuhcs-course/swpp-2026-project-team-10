# AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
"""How to hold the phone for the candidate the user chose, from a vision model through OpenRouter's chat completions
(Design Documentation 2.6.3)."""

import asyncio
import base64
import json
import logging
import re
from dataclasses import dataclass
from typing import Literal

import httpx2

from pix_server.config import Settings
from pix_server.errors import ApiError
from pix_server.poses.generator import status_phrase
from pix_server.poses.prompts import PROMPTS

logger = logging.getLogger(__name__)

API_URL = "https://openrouter.ai/api/v1/chat/completions"
# The thinking budget is a share of max_tokens, so this leaves room for it as well as for the answer, about 100 tokens.
MAX_TOKENS = 4000
TEXT_MAX_LENGTH = 160  # the banner on Camera shows two or three lines

Framing = Literal["closer", "back", "keep"]
# Where the photographer holds the phone, by their own posture, lowest first.
Level = Literal["crouching", "waist", "chest", "eye", "above head"]
LEVELS: tuple[Level, ...] = ("crouching", "waist", "chest", "eye", "above head")
Angle = Literal["up", "level", "down"]
Height = Literal["raise", "lower", "keep"]

# The fields come in this order so the model decides the shot and the pose before the height and the angle, and writes
# the sentence last. OpenRouter passes the schema to every model that takes one; the answer is still checked.
ANSWER_SCHEMA = {
    "type": "object",
    "properties": {
        "shot": {"type": "string", "enum": ["full body", "knees up", "waist up", "chest up"]},
        "pose": {"type": "string", "enum": ["standing", "walking", "leaning", "sitting", "crouching", "other"]},
        "camera_now": {"type": "string", "enum": list(LEVELS)},
        "camera_best": {"type": "string", "enum": list(LEVELS)},
        "angle": {"type": "string", "enum": ["up", "level", "down"]},
        "framing": {"type": "string", "enum": ["closer", "back", "keep"]},
        "text": {"type": "string"},
    },
    "required": ["shot", "pose", "camera_now", "camera_best", "angle", "framing", "text"],
    "additionalProperties": False,
}
# A number, a unit, or a degree is hard to follow while shooting, so a sentence with one is not shown: digits, numbers
# in words ("two steps"), "one" before a step or a unit, units, and degrees.
NUMBER_WORDS = (
    "two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|fifty|hundred|dozen"
)
MEASURE = re.compile(
    rf"\d|°|\b({NUMBER_WORDS})\b|\bone\s+(steps?|paces?|meters?|metres?|foot|feet|inch(es)?|degrees?)\b"
    r"|\b(meters?|metres?|cm|centimet\w*|inch(es)?|feet|foot|degrees?)\b",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class PoseTip:
    text: str
    framing: Framing
    hold: Level
    height: Height
    angle: Angle


def usable_tip(answer: str) -> PoseTip | None:
    """The tip in the model's answer, or None when it cannot be shown. The height move compares where the phone was
    for the scene with where it should be."""
    match = re.search(r"\{.*\}", answer, re.DOTALL)  # some models wrap the JSON in a code fence
    try:
        fields = json.loads(match.group(0) if match else answer)
        text = " ".join(fields["text"].split())
        now, best, angle, framing = fields["camera_now"], fields["camera_best"], fields["angle"], fields["framing"]
    except (ValueError, TypeError, KeyError, AttributeError):
        return None
    if now not in LEVELS or best not in LEVELS or angle not in ("up", "level", "down"):
        return None
    if framing not in ("closer", "back", "keep"):
        return None
    if not 0 < len(text) <= TEXT_MAX_LENGTH or MEASURE.search(text):
        return None
    step = LEVELS.index(best) - LEVELS.index(now)
    height: Height = "raise" if step > 0 else "lower" if step < 0 else "keep"
    return PoseTip(text, framing, best, height, angle)


class PoseTipper:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def tip(self, scene: bytes, candidate: bytes, pose: str) -> PoseTip:
        """The tip for taking `candidate`, made with `pose` from `scene`, or the ApiError the endpoint answers with."""
        key = self._settings.openrouter_api_key
        if key is None or not key.get_secret_value():
            logger.error("OPENROUTER_API_KEY is not set, so tips cannot be made")
            raise ApiError(502, "UPSTREAM_ERROR", "The image service is not configured")
        payload = {
            "model": self._settings.tip_model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": PROMPTS.tip},
                        {"type": "text", "text": "Photo 1, the scene:"},
                        {"type": "image_url", "image_url": {"url": data_url(scene)}},
                        {"type": "text", "text": f'Photo 2, the photo they want, with the pose "{pose}":'},
                        {"type": "image_url", "image_url": {"url": data_url(candidate)}},
                    ],
                }
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": "tip", "strict": True, "schema": ANSWER_SCHEMA},
            },
            "max_tokens": MAX_TOKENS,
            "reasoning": {"effort": self._settings.tip_reasoning},
        }
        timeout = self._settings.tip_timeout_seconds
        headers = {"Authorization": f"Bearer {key.get_secret_value()}"}
        try:
            async with asyncio.timeout(timeout):
                response = await self._http.post(API_URL, json=payload, headers=headers)
        except TimeoutError:
            raise ApiError(504, "UPSTREAM_TIMEOUT", f"The tip model did not answer within {timeout:g} s") from None
        except httpx2.HTTPError as exc:
            logger.warning("OpenRouter could not be reached for a tip: %s", type(exc).__name__)
            raise ApiError(502, "UPSTREAM_ERROR", "The tip model could not be reached") from exc

        if response.status_code == 403:
            logger.warning("OpenRouter declined a tip request")
            raise ApiError(422, "REJECTED", "The tip model declined these photos")
        try:
            body = response.json()
            if response.status_code != 200 or "error" in body:
                # An upstream body can quote the prompt or describe the photos, so only the status reaches the log.
                status = response.status_code
                logger.warning("OpenRouter answered %s (%s) to a tip request", status, status_phrase(status))
                raise ApiError(502, "UPSTREAM_ERROR", "The tip model failed")
            tip = usable_tip(body["choices"][0]["message"]["content"] or "")
        except (ValueError, LookupError, TypeError, AttributeError) as exc:
            logger.warning("OpenRouter answered %s with an unreadable body to a tip request", response.status_code)
            raise ApiError(502, "UPSTREAM_ERROR", "The tip model returned an unreadable answer") from exc
        if tip is None:
            # Never the answer itself: it describes the person in the photos (Design Documentation 2.8, Privacy).
            logger.warning("The tip model's answer could not be used")
            raise ApiError(502, "UPSTREAM_ERROR", "The tip model returned an unusable answer")
        return tip


def data_url(image: bytes) -> str:
    return "data:image/jpeg;base64," + base64.b64encode(image).decode()
