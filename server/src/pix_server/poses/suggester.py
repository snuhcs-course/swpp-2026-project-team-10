# AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
"""Four poses for a scene from a vision model, through OpenRouter's chat completions (Design Documentation 2.6.3):
suggested poses, or a description and three variations of it."""

import asyncio
import base64
import json
import logging
import re

import httpx2

from pix_server.config import Settings
from pix_server.poses.generator import status_phrase
from pix_server.poses.prompts import (
    DEFAULT_POSES,
    DESCRIPTION_MAX_LENGTH,
    POSE_COUNT,
    PROMPTS,
    one_line,
    variation_prompt,
)

logger = logging.getLogger(__name__)

API_URL = "https://openrouter.ai/api/v1/chat/completions"
MAX_TOKENS = 400  # four short poses take about 100
# OpenRouter passes a JSON schema to every model that takes one; the answer is still checked, for those that do not.
ANSWER_SCHEMA = {
    "type": "object",
    "properties": {"poses": {"type": "array", "items": {"type": "string"}}},
    "required": ["poses"],
    "additionalProperties": False,
}


def usable_poses(answer: str) -> list[str]:
    """The poses in the model's answer that a pose request can send: the first POSE_COUNT different ones of 1 to
    DESCRIPTION_MAX_LENGTH characters, each on one line. Anything else in the answer is ignored."""
    match = re.search(r"\{.*\}", answer, re.DOTALL)  # some models wrap the JSON in a code fence
    try:
        poses = json.loads(match.group(0) if match else answer)["poses"]
    except (ValueError, TypeError, KeyError):
        return []
    if not isinstance(poses, list):
        return []
    usable: list[str] = []
    for pose in poses:
        text = one_line(pose) if isinstance(pose, str) else ""
        if 0 < len(text) <= DESCRIPTION_MAX_LENGTH and text.casefold() not in {kept.casefold() for kept in usable}:
            usable.append(text)
    return usable[:POSE_COUNT]


def with_default_poses(poses: list[str]) -> list[str]:
    """Tops `poses` up to POSE_COUNT with the default poses, in the file's order."""
    taken = {pose.casefold() for pose in poses}
    defaults = [default.pose for default in DEFAULT_POSES.values() if default.pose.casefold() not in taken]
    return (poses + defaults)[:POSE_COUNT]


class PoseSuggester:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def suggest(self, scene: bytes) -> list[str]:
        """POSE_COUNT poses for the scene: the model's usable poses, topped up with the default poses. A model that
        fails, answers late, or answers something unusable still gives a full set, so this never raises for it."""
        poses = await self._ask(scene, PROMPTS.suggestion)
        if len(poses) < POSE_COUNT:
            # Only the count: the poses describe the person in the photo (Design Documentation 2.8, Privacy).
            logger.warning("%d of %d suggested poses usable; the default poses fill the rest", len(poses), POSE_COUNT)
        return with_default_poses(poses)

    async def vary(self, scene: bytes, description: str) -> list[str]:
        """POSE_COUNT poses for a description: the description itself, as checked_pose folds it, then the model's
        variations of it. The description fills in for any variation the model could not give, so this never raises
        for the model either; a description that checked_pose refuses raises ApiError before any call."""
        prompt = variation_prompt(description)
        description = one_line(description)
        wanted = POSE_COUNT - 1
        variations = [pose for pose in await self._ask(scene, prompt) if pose.casefold() != description.casefold()]
        variations = variations[:wanted]
        if len(variations) < wanted:
            # Only the count, never the description or the variations (Design Documentation 2.8, Privacy).
            logger.warning("%d of %d variations usable; the description fills the rest", len(variations), wanted)
        return [description, *variations] + [description] * (wanted - len(variations))

    async def _ask(self, scene: bytes, prompt: str) -> list[str]:
        key = self._settings.openrouter_api_key
        if key is None or not key.get_secret_value():
            logger.error("OPENROUTER_API_KEY is not set, so poses cannot be suggested")
            return []
        payload = {
            "model": self._settings.suggestion_model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(scene).decode()},
                        },
                    ],
                }
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": "poses", "strict": True, "schema": ANSWER_SCHEMA},
            },
            "max_tokens": MAX_TOKENS,
            "temperature": 1.0,  # other poses on every call, for the next set from the same photo
            # The least thinking a model allows: some refuse to answer without any, and more only adds seconds.
            "reasoning": {"effort": "minimal"},
        }
        timeout = self._settings.suggestion_timeout_seconds
        headers = {"Authorization": f"Bearer {key.get_secret_value()}"}
        try:
            async with asyncio.timeout(timeout):
                response = await self._http.post(API_URL, json=payload, headers=headers)
        except TimeoutError:
            logger.warning("The suggestion model did not answer within %g s", timeout)
            return []
        except httpx2.HTTPError as exc:
            logger.warning("OpenRouter could not be reached for suggestions: %s", type(exc).__name__)
            return []
        try:
            body = response.json()
            if response.status_code != 200 or "error" in body:
                # As for the image API, only the status reaches the log, never the body.
                status = response.status_code
                logger.warning("OpenRouter answered %s (%s) to a suggestion request", status, status_phrase(status))
                return []
            return usable_poses(body["choices"][0]["message"]["content"] or "")
        except (ValueError, LookupError, TypeError, AttributeError):
            logger.warning(
                "OpenRouter answered %s with an unreadable body to a suggestion request", response.status_code
            )
            return []
