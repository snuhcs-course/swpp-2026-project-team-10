# AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
"""How to take the candidate the user chose: a vision model, through OpenRouter's chat completions, describes the scene
and the candidate, each in a request of its own, and the server compares the two descriptions and writes the tip's
sentences (Design Documentation 2.6.3)."""

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
# A request the model has not answered after this long is sent a second time, and the first answer wins. A tip needs
# two answers, so one slow one would cost the whole tip: nine in ten answers come within 3.6 s, and without the second
# request 26 of 300 tips ran into the time limit on a slow day (2026-11-17).
HEDGE_SECONDS = 4.0

# The distance the server decided: `unsure` when the two measures of the person's size do not agree, and the tip
# then names none.
Framing = Literal["closer", "back", "keep", "unsure"]
# Where the photographer holds the phone, by their own posture, lowest first.
Level = Literal["crouching", "waist", "chest", "eye", "above head"]
LEVELS: tuple[Level, ...] = ("crouching", "waist", "chest", "eye", "above head")
Angle = Literal["up", "level", "down"]
ANGLES: tuple[Angle, ...] = get_args(Angle)
Height = Literal["raise", "lower", "keep"]
# How much of the person a photo shows, widest first, with about how much of a standing person's height that is.
SHOTS = ("full body", "knees up", "waist up", "chest up")
BODY_SHARE = {"full body": 1.0, "knees up": 0.75, "waist up": 0.5, "chest up": 0.3}
# The shots a photographer reaches with the zoom: a phone held that close bends the face.
CLOSE_SHOTS: tuple[str, ...] = ("waist up", "chest up")
# Where a standing photographer holds the phone unasked: a tip says such a hold only when the scene was taken far
# from it, FAR_STEPS heights or more away or with another tilt. A height next to the scene's is not said, since the
# model tells neighbouring heights apart poorly (20 of 100 tips had a usual hold that differed from the scene's, 13 of
# them by one height; 2026-11-18).
USUAL_HOLDS: tuple[str, ...] = ("chest", "eye")
FAR_STEPS = 2
# How the person is, and the part of them the camera is level with, lowest first.
POSTURES = ("standing", "seated", "on the ground")
PARTS = ("knees", "waist", "chest", "face", "above")
# Where the photographer holds the phone to be level with that part: a person on a seat is about a level lower than
# one standing, and one on the ground lower still.
HOLDS: dict[str, tuple[Level, ...]] = {
    "standing": ("crouching", "waist", "chest", "eye", "above head"),
    "seated": ("crouching", "crouching", "waist", "chest", "eye"),
    "on the ground": ("crouching", "crouching", "crouching", "waist", "chest"),
}
# Whether a long level line shows behind the person, and whether it leans.
HORIZONS = ("none", "level", "tilted")
# A box runs from 0 to BOX_EDGE across the photo and down it.
BOX_EDGE = 1000
# The candidate is closer or farther only when a measure of the person is this many times bigger or smaller in it,
# so a box drawn a little differently in the two photos does not move the photographer; and much closer or farther
# when both measures say this many times.
CLOSER_RATIO = 1.18
MUCH_RATIO = 1.4
# The person is on a side when the box's middle is this far from the photo's.
SIDE_SHARE = 0.12
Side = Literal["left", "middle", "right"]
# The room above the head, as a share of the photo's height: the head nearly touches the top edge under the first,
# and the photo leaves plenty above it over the second. Between them is what a photographer leaves unasked.
TIGHT_ROOM = 0.06
PLENTY_ROOM = 0.25
# The language of the phone's screens, in which the tip is written (FR-10.2).
Language = Literal["en", "ko"]
LANGUAGES: tuple[Language, ...] = get_args(Language)

BOX_SCHEMA = {"type": "array", "items": {"type": "integer"}, "minItems": 4, "maxItems": 4}
PHOTO_FIELDS = {
    "box": BOX_SCHEMA,
    "head": BOX_SCHEMA,
    "shot": {"type": "string", "enum": list(SHOTS)},
    "posture": {"type": "string", "enum": list(POSTURES)},
    "level": {"type": "string", "enum": list(PARTS)},
    "tilt": {"type": "string", "enum": list(ANGLES)},
    "horizon": {"type": "string", "enum": list(HORIZONS)},
}
# What the model says of one photo. The scene and the candidate get the same fields, each in its own request, so the
# model looks at each by itself and the server does the comparing. OpenRouter passes the schema to every model that
# takes one; the answer is still checked.
PHOTO_SCHEMA = {
    "type": "object",
    "properties": PHOTO_FIELDS,
    "required": list(PHOTO_FIELDS),
    "additionalProperties": False,
}
# The schemas' names, by which a request is the scene's or the candidate's.
SCENE_NAME = "tip_scene"
CANDIDATE_NAME = "tip_candidate"


@dataclass(frozen=True)
class PoseTip:
    # What the app shows: a sentence on each line, in the order a photographer acts on them. Every word is the
    # server's own ([tip_words]); the model only described the photos.
    text: str
    framing: Framing
    hold: Level
    height: Height
    angle: Angle


@dataclass(frozen=True)
class Seen:
    """What the model says of one photo: the box around the person as shares of the photo, top, left, bottom, and
    right, the head's top edge and its height when the model boxed it, how much of the person shows, how they are,
    the part of them the camera is level with, its tilt, and the level line behind them."""

    top: float
    left: float
    bottom: float
    right: float
    head_top: float | None
    head: float | None
    shot: str
    posture: str
    level: str
    tilt: Angle
    horizon: str

    @property
    def body(self) -> float:
        """How tall the whole person would be against the photo's height, also when the photo cuts them off."""
        return (self.bottom - self.top) / BODY_SHARE[self.shot]

    @property
    def side(self) -> Side:
        middle = (self.left + self.right) / 2
        return "left" if middle < 0.5 - SIDE_SHARE else "right" if middle > 0.5 + SIDE_SHARE else "middle"

    @property
    def camera(self) -> Level:
        """Where the photographer holds the phone to be level with that part of a person who is like this."""
        return HOLDS[self.posture][PARTS.index(self.level)]


def box_of(value: object) -> tuple[float, float, float, float] | None:
    """A box as shares of the photo, top, left, bottom, and right, or None when it is not one: not four numbers
    inside the photo, or without height or width."""
    if not isinstance(value, list) or len(value) != 4:
        return None
    if not all(
        isinstance(edge, int | float) and not isinstance(edge, bool) and 0 <= edge <= BOX_EDGE for edge in value
    ):
        return None
    top, left, bottom, right = (edge / BOX_EDGE for edge in value)
    return None if top >= bottom or left >= right else (top, left, bottom, right)


def seen(photo: object) -> Seen | None:
    """A photo's description as the model gave it, or None when it is not one: no box around the person, or a value
    outside the schema. A head that is not a box, as when it does not show, only leaves its measures out."""
    if not isinstance(photo, dict):
        return None
    box = box_of(photo.get("box"))
    shot, posture, level, tilt = (photo.get(key) for key in ("shot", "posture", "level", "tilt"))
    horizon = photo.get("horizon")
    if box is None or shot not in SHOTS or posture not in POSTURES or level not in PARTS or tilt not in ANGLES:
        return None
    if horizon not in HORIZONS:
        return None
    head = box_of(photo.get("head"))
    head_top, head_height = (head[0], head[2] - head[0]) if head else (None, None)
    return Seen(*box, head_top, head_height, shot, posture, level, tilt, horizon)


def move(ratio: float) -> Framing:
    return "closer" if ratio >= CLOSER_RATIO else "back" if ratio <= 1 / CLOSER_RATIO else "keep"


def framing_of(now: Seen, want: Seen) -> tuple[Framing, bool]:
    """The distance, and whether the move is a big one, from two measures of how much bigger the person is in the
    candidate than in the scene: their head, whose size does not change with the pose or with how much of the body
    shows, and how tall the whole person would be. Each is wrong now and then, the head under a hat or a hood and the
    body when the shot is misread, so a distance counts only when both give the same one, and is `unsure`
    otherwise; the move is a big one only when both say so."""
    if now.head is None or want.head is None:
        return "unsure", False
    by_head, by_body = want.head / now.head, want.body / now.body
    framing = move(by_head)
    if framing != move(by_body):
        return "unsure", False
    much = {
        "closer": min(by_head, by_body) >= MUCH_RATIO,
        "back": max(by_head, by_body) <= 1 / MUCH_RATIO,
    }.get(framing, False)
    return framing, much


def fields_of(answer: str) -> dict | None:
    """The JSON object in a model's answer, or None; some models wrap it in a code fence."""
    match = re.search(r"\{.*\}", answer, re.DOTALL)
    try:
        fields = json.loads(match.group(0) if match else answer)
    except (ValueError, TypeError):
        return None
    return fields if isinstance(fields, dict) else None


def usable_tip(scene: str, candidate: str, language: Language = "en") -> PoseTip | None:
    """The tip for the model's two answers, or None when either description cannot be read.

    A tip is a few sentences, one on a line, that say only what is not as usual (#213), in the order a photographer
    acts on them:
    - the move closer or farther back, a big one said so, and with the zoom when the candidate is a close shot; or
      to match the person to the guide's outline, when the two measures of the distance do not agree, so that a tip
      never leaves the distance out where it may have changed;
    - the phone's height and tilt, unless it is level at the chest or the eyes, where a standing photographer holds
      it, and the scene was taken about so too: within a height of it and with the same tilt;
    - the side of the frame the person is on when it is not the middle, and the room above the head when the photo
      leaves almost none or plenty;
    - to hold the phone upright when a level line leans in the scene and not in the candidate, where it may lean on
      purpose.
    With nothing of these to say, the distance being the same, the tip is one sentence: to take the photo as it is."""
    scene_fields, fields = fields_of(scene), fields_of(candidate)
    if scene_fields is None or fields is None:
        return None
    now, want = seen(scene_fields), seen(fields)
    if now is None or want is None:
        return None
    words = PROMPTS.tip_words[language]
    framing, much = framing_of(now, want)
    hold, angle = want.camera, want.tilt
    step = LEVELS.index(hold) - LEVELS.index(now.camera)
    height: Height = "raise" if step > 0 else "lower" if step < 0 else "keep"
    sentences = []
    if framing == "closer":
        sentences.append(words[("zoom" if want.shot in CLOSE_SHOTS else "closer") + ("_much" if much else "")])
    elif framing == "back":
        sentences.append(words["back_much" if much else "back"])
    elif framing == "unsure":
        sentences.append(words["guide"])
    if hold not in USUAL_HOLDS or angle != "level" or abs(step) >= FAR_STEPS or angle != now.tilt:
        sentences.append(words["phone"].format(hold=words[hold.replace(" ", "_")], tilt=words[angle]))
    if want.side != "middle":
        sentences.append(words[want.side])
    if want.head_top is not None and not TIGHT_ROOM <= want.head_top <= PLENTY_ROOM:
        sentences.append(words["room_none" if want.head_top < TIGHT_ROOM else "room_plenty"])
    if now.horizon == "tilted" and want.horizon != "tilted":
        sentences.append(words["upright"])
    if not sentences:
        sentences.append(words["stay"])
    return PoseTip("\n".join(sentences), framing, hold, height, angle)


class PoseTipper:
    def __init__(self, settings: Settings, http: httpx2.AsyncClient) -> None:
        self._settings = settings
        self._http = http

    async def tip(
        self,
        scene: bytes,
        candidate: bytes,
        pose: str,
        language: Language = "en",
    ) -> PoseTip:
        """The tip for taking `candidate`, made with `pose` from `scene`, written in `language`, or the ApiError the
        endpoint answers with."""
        key = self._settings.openrouter_api_key
        if key is None or not key.get_secret_value():
            logger.error("OPENROUTER_API_KEY is not set, so tips cannot be made")
            raise ApiError(502, "UPSTREAM_ERROR", "The image service is not configured")
        instructions = [{"type": "text", "text": PROMPTS.tip}]
        asked = (
            (SCENE_NAME, PHOTO_SCHEMA, [*instructions, {"type": "text", "text": "The photo:"}], scene),
            (
                CANDIDATE_NAME,
                PHOTO_SCHEMA,
                [
                    *instructions,
                    {"type": "text", "text": PROMPTS.tip_candidate},
                    {"type": "text", "text": f'The photo, made for the pose "{pose}":'},
                ],
                candidate,
            ),
        )
        timeout = self._settings.tip_timeout_seconds
        headers = {"Authorization": f"Bearer {key.get_secret_value()}"}
        # Both at once, so the tip takes as long as the slower one.
        calls = [asyncio.ensure_future(self._ask(self._payload(*one), headers)) for one in asked]
        try:
            async with asyncio.timeout(timeout):
                responses = await asyncio.gather(*calls)
        except TimeoutError:
            raise ApiError(504, "UPSTREAM_TIMEOUT", f"The tip model did not answer within {timeout:g} s") from None
        except httpx2.HTTPError as exc:
            logger.warning("OpenRouter could not be reached for a tip: %s", type(exc).__name__)
            raise ApiError(502, "UPSTREAM_ERROR", "The tip model could not be reached") from exc
        finally:
            # A failure of one request must not leave the other running, nor a dropped request either of them.
            for call in calls:
                call.cancel()

        answers = [self._answer(response) for response in responses]
        tip = usable_tip(*answers, language)
        if tip is None:
            # Never the answer itself: it describes the person in the photos (Design Documentation 2.8, Privacy).
            logger.warning("The tip model's answer could not be used")
            raise ApiError(502, "UPSTREAM_ERROR", "The tip model returned an unusable answer")
        return tip

    async def _ask(self, payload: dict, headers: dict) -> httpx2.Response:
        """The model's response to one request, which is sent again when it is slow (HEDGE_SECONDS): whichever of the
        two answers first, and the other is dropped."""
        calls = [asyncio.ensure_future(self._http.post(API_URL, json=payload, headers=headers))]
        try:
            done, _ = await asyncio.wait(calls, timeout=HEDGE_SECONDS)
            if not done:
                calls.append(asyncio.ensure_future(self._http.post(API_URL, json=payload, headers=headers)))
                done, _ = await asyncio.wait(calls, return_when=asyncio.FIRST_COMPLETED)
            return next(iter(done)).result()
        finally:
            for call in calls:
                call.cancel()

    def _payload(self, name: str, schema: dict, instructions: list[dict], photo: bytes) -> dict:
        return {
            "model": self._settings.tip_model,
            "messages": [
                {
                    "role": "user",
                    "content": [*instructions, {"type": "image_url", "image_url": {"url": data_url(photo)}}],
                }
            ],
            "response_format": {"type": "json_schema", "json_schema": {"name": name, "strict": True, "schema": schema}},
            "max_tokens": MAX_TOKENS,
            "reasoning": {"effort": self._settings.tip_reasoning},
        }

    @staticmethod
    def _answer(response: httpx2.Response) -> str:
        """The model's message in an OpenRouter response, or the ApiError the endpoint answers with."""
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
            return body["choices"][0]["message"]["content"] or ""
        except (ValueError, LookupError, TypeError, AttributeError) as exc:
            logger.warning("OpenRouter answered %s with an unreadable body to a tip request", response.status_code)
            raise ApiError(502, "UPSTREAM_ERROR", "The tip model returned an unreadable answer") from exc


def data_url(image: bytes) -> str:
    return "data:image/jpeg;base64," + base64.b64encode(image).decode()
