# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
"""The description filter: a decision model on OpenRouter's Decisions API judges every description and pose before the
other models read it, and the request is refused when it asks for harmful content or tries to manipulate the models
(Design Documentation 2.6.3)."""

import asyncio
import logging

import httpx2

from pix_server.config import Settings
from pix_server.errors import ApiError
from pix_server.poses.generator import status_phrase
from pix_server.poses.prompts import FILTER_QUESTIONS, PROMPTS, one_line

logger = logging.getLogger(__name__)

API_URL = "https://openrouter.ai/api/alpha/decisions"


class DescriptionFilter:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def check(self, text: str, field: str = "description") -> None:
        """Raises 422 REJECTED, naming the request's `field`, when the probability of either question in the prompts
        file's [filter] table reaches PIX_FILTER_CUTOFF. The text is judged as sent, on one line. A filter that fails,
        answers late, or answers something unreadable lets the text through (fail open): the prompts' own rules and the
        image service's filter still apply, and a pose is never lost to an outage of the filter."""
        answers = await self._ask(one_line(text))
        if answers is None:
            return
        cutoff = self._settings.filter_cutoff
        if any(probability >= cutoff for probability in answers.values()):
            # Only the probabilities: the text is the user's own words (Design Documentation 2.8, Privacy).
            judged = ", ".join(f"{name} {probability:.2f}" for name, probability in answers.items())
            logger.warning("The filter refused a %s (%s)", field, judged)
            raise ApiError(422, "REJECTED", f"This {field} was refused")

    async def _ask(self, text: str) -> dict[str, float] | None:
        """The probability of yes for each question, or None when there is no usable answer."""
        key = self._settings.openrouter_api_key
        if key is None or not key.get_secret_value():
            logger.error("OPENROUTER_API_KEY is not set, so descriptions go through unfiltered")
            return None
        payload = {
            "model": self._settings.filter_model,
            "state": {"description": text},
            "questions": PROMPTS.filter_questions,
        }
        timeout = self._settings.filter_timeout_seconds
        headers = {"Authorization": f"Bearer {key.get_secret_value()}"}
        try:
            async with asyncio.timeout(timeout):
                response = await self._http.post(API_URL, json=payload, headers=headers)
        except TimeoutError:
            logger.warning("The filter did not answer within %g s; the description goes through", timeout)
            return None
        except httpx2.HTTPError as exc:
            logger.warning("OpenRouter could not be reached for the filter: %s", type(exc).__name__)
            return None
        try:
            body = response.json()
            if response.status_code != 200 or "error" in body:
                # As for the other models, only the status reaches the log, never the body.
                status = response.status_code
                logger.warning("OpenRouter answered %s (%s) to the filter", status, status_phrase(status))
                return None
            answers = {name: float(body["answers"][name]["noul"]) for name in FILTER_QUESTIONS}
        except (ValueError, LookupError, TypeError, AttributeError):
            logger.warning("OpenRouter answered %s with an unreadable body to the filter", response.status_code)
            return None
        if not all(0 <= p <= 1 for p in answers.values()):
            logger.warning("The filter answered a probability outside 0 to 1")
            return None
        return answers
