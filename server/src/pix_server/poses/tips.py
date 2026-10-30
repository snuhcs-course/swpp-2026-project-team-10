# AI-generated with Claude Code, 2026-10-31, reviewed by Dongje Park
"""How to hold the phone for the candidate the user chose, from a vision model through OpenRouter's chat completions
(Design Documentation 2.6.3)."""

import asyncio
import base64
import json
import logging
import re
from dataclasses import dataclass
from typing import Literal, get_args

import httpx2

from pix_server.config import Settings
from pix_server.errors import ApiError
from pix_server.poses.generator import status_phrase
from pix_server.poses.prompts import PROMPTS

logger = logging.getLogger(__name__)

API_URL = "https://openrouter.ai/api/v1/chat/completions"
# The thinking budget is a share of max_tokens, so this leaves room for it as well as for the answer, about 100 tokens.
MAX_TOKENS = 4000
# The banner on Camera shows two or three lines. tip_prompt limits English in words (twenty-five), so this leaves it
# room; tip_korean limits Korean in characters, so the server keeps to the same number.
TEXT_MAX_LENGTH = 160
KOREAN_TEXT_MAX_LENGTH = 80

Framing = Literal["closer", "back", "keep"]
# Where the photographer holds the phone, by their own posture, lowest first.
Level = Literal["crouching", "waist", "chest", "eye", "above head"]
LEVELS: tuple[Level, ...] = ("crouching", "waist", "chest", "eye", "above head")
Angle = Literal["up", "level", "down"]
Height = Literal["raise", "lower", "keep"]
# The language of the phone's screens, in which the sentence is written (FR-10.2).
Language = Literal["en", "ko"]
LANGUAGES: tuple[Language, ...] = get_args(Language)

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
# in words ("two steps"), "one" before a step or a unit, units, and degrees; in Korean, a count of steps ("한 걸음",
# "한두 걸음", "열한 걸음만"), units, and a degree in words ("십오 도로").
NUMBER_WORDS = (
    "two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|fifty|hundred|dozen"
)
# Korean counts run their words together ("한두", "서너", "대여섯", "열한", "이십"), and 몇 and 반 count too ("몇 걸음",
# "반 걸음"). A step unit takes any ending ("한 걸음만"); 발 and 보 count steps only after a native count, 반, or 몇,
# and with the endings of an amount ("한 발만큼"), because "두 발로 서서" is about feet and 이발 or 구보 are words of
# their own. A unit at the start of a word, after a count or not, takes any ending ("몇 미터까지", "삼십센티만큼"), so
# 파라미터 is not one, and a word that only begins like one (미터기) is refused with it; 밀리 is one only after a count,
# because "밀리지 않게" is a verb. A degree of a whole Sino-Korean number takes any ending ("백이십 도만큼"); written
# with one digit it needs a space and takes only particles ("구 도까지"), so 구도 (the composition) or "이 도시" (this
# city) are not one, though "이 도로" (this road) is.
KOREAN_NATIVE = "(?:한|두|세|서|석|네|너|덧|댓|대|다섯|여섯|일곱|여덟|아홉|열|스무|스물)+"
KOREAN_SINO = "(?:일|이|삼|사|오|육|칠|팔|구|십|백)+"
KOREAN_DIGIT = "[일이삼사오육칠팔구]"
KOREAN_TENS = f"(?:{KOREAN_DIGIT}?[십백])+{KOREAN_DIGIT}?"
KOREAN_COUNT = f"{KOREAN_NATIVE}|(?:수|몇)?{KOREAN_SINO}|반|몇|수"
KOREAN_UNITS = "센티미터|밀리미터|미터|센티|센치|인치|피트"
# The particles after an amount, one after another ("쯤에서"); after 발 and 보, only those of an amount, because "두 발로"
# is about feet.
KOREAN_ENDINGS = "(?:만|씩|쯤|가량|정도|만큼|까지|보다|부터|로|를|의|에|에서|은|는|이|가|도)*"
STEP_ENDINGS = "(?:만|씩|쯤|가량|정도|만큼)*"
MEASURE = re.compile(
    rf"\d|°|\b({NUMBER_WORDS})\b|\bone\s+(steps?|paces?|meters?|metres?|foot|feet|inch(es)?|degrees?)\b"
    r"|\b(meters?|metres?|cm|centimet\w*|inch(es)?|feet|foot|degrees?)\b"
    rf"|(?<![가-힣])({KOREAN_COUNT})\s*(걸음|발짝|발자국|뼘)"
    rf"|(?<![가-힣])({KOREAN_UNITS})|(?<![가-힣])({KOREAN_COUNT})\s*({KOREAN_UNITS}|밀리)"
    rf"|(?<![가-힣])({KOREAN_NATIVE}|반|몇)\s*(발|보){STEP_ENDINGS}(?![가-힣])"
    rf"|(?<![가-힣]){KOREAN_TENS}\s*도"
    rf"|(?<![가-힣]){KOREAN_DIGIT}\s+도{KOREAN_ENDINGS}(?![가-힣])",
    re.IGNORECASE,
)
# The letters of each language's sentence; digits and punctuation are not letters (Hangul jamo are not used).
SCRIPTS: dict[Language, re.Pattern[str]] = {"en": re.compile("[A-Za-z]"), "ko": re.compile("[가-힣]")}


@dataclass(frozen=True)
class PoseTip:
    text: str
    framing: Framing
    hold: Level
    height: Height
    angle: Angle


def usable_tip(answer: str, language: Language = "en") -> PoseTip | None:
    """The tip in the model's answer, or None when it cannot be shown, also when its sentence is not in `language`. The
    height move compares where the phone was for the scene with where it should be."""
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
    limit = KOREAN_TEXT_MAX_LENGTH if language == "ko" else TEXT_MAX_LENGTH
    if not 0 < len(text) <= limit or MEASURE.search(text):
        return None
    # A model can leave out the Korean instruction, or answer in the language of the photos or the pose: every letter
    # must be of the screens' language, so a sentence in another script, or with no letters, is not shown.
    letters = [char for char in text if char.isalpha()]
    if not letters or not all(SCRIPTS[language].fullmatch(char) for char in letters):
        return None
    step = LEVELS.index(best) - LEVELS.index(now)
    height: Height = "raise" if step > 0 else "lower" if step < 0 else "keep"
    return PoseTip(text, framing, best, height, angle)


class PoseTipper:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def tip(self, scene: bytes, candidate: bytes, pose: str, language: Language = "en") -> PoseTip:
        """The tip for taking `candidate`, made with `pose` from `scene`, its sentence in `language`, or the ApiError
        the endpoint answers with."""
        instructions = [{"type": "text", "text": PROMPTS.tip}]
        if language == "ko":
            instructions.append({"type": "text", "text": PROMPTS.tip_korean})
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
                        *instructions,
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
            tip = usable_tip(body["choices"][0]["message"]["content"] or "", language)
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
