# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
"""The person check: a decision model on OpenRouter's Decisions API looks at the scene photo of every pose request
before the image model is asked, and the request is refused when the photo shows no person (FR-4.13, Design
Documentation 2.6.3). The phone checks first; this catches what it lets through, such as an animal, and requests
that do not come from the app."""

import asyncio
import base64
import logging

import httpx2

from pix_server.config import Settings
from pix_server.errors import ApiError
from pix_server.poses.generator import status_phrase
from pix_server.poses.prompts import PERSON_QUESTION, PROMPTS

logger = logging.getLogger(__name__)

API_URL = "https://openrouter.ai/api/alpha/decisions"


class PersonCheck:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def check(self, scene: bytes) -> None:
        """Raises 422 NO_PERSON when the probability that the scene, a JPEG as sent, shows a person is below
        PIX_PERSON_CUTOFF. A check that fails, answers late, or answers something unreadable lets the scene through
        (fail open): the phone has checked it already, and a pose is never lost to an outage of the check. Jev
        answers 400 for an image under 256 px on a side, and such a scene goes through too."""
        probability = await self._ask(scene)
        if probability is None or probability >= self._settings.person_cutoff:
            return
        # Only the probability: the photo shows a real place, and maybe a person (Design Documentation 2.8, Privacy).
        logger.warning("The person check found no person (person %.2f)", probability)
        raise ApiError(422, "NO_PERSON", "The photo shows no person")

    async def _ask(self, scene: bytes) -> float | None:
        """The probability of yes, or None when there is no usable answer."""
        key = self._settings.openrouter_api_key
        if key is None or not key.get_secret_value():
            logger.error("OPENROUTER_API_KEY is not set, so scenes go through unchecked")
            return None
        photo = {
            "type": "image_url",
            "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(scene).decode()},
        }
        payload = {
            "model": self._settings.person_model,
            "state": {"photo": photo},
            "questions": PROMPTS.person_question,
        }
        timeout = self._settings.person_timeout_seconds
        headers = {"Authorization": f"Bearer {key.get_secret_value()}"}
        try:
            async with asyncio.timeout(timeout):
                response = await self._http.post(API_URL, json=payload, headers=headers)
        except TimeoutError:
            logger.warning("The person check did not answer within %g s; the scene goes through", timeout)
            return None
        except httpx2.HTTPError as exc:
            logger.warning("OpenRouter could not be reached for the person check: %s", type(exc).__name__)
            return None
        try:
            body = response.json()
            if response.status_code != 200 or "error" in body:
                # As for the other models, only the status reaches the log, never the body.
                status = response.status_code
                logger.warning("OpenRouter answered %s (%s) to the person check", status, status_phrase(status))
                return None
            probability = float(body["answers"][PERSON_QUESTION]["noul"])
        except (ValueError, LookupError, TypeError, AttributeError):
            logger.warning("OpenRouter answered %s with an unreadable body to the person check", response.status_code)
            return None
        if not 0 <= probability <= 1:
            logger.warning("The person check answered a probability outside 0 to 1")
            return None
        return probability
