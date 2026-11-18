# AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park and Dongje Park
"""Try the pose tip prompt on scene photos and candidates made once (Design Documentation 2.6.3).

It does not import pix_server. Run it through uv from server/ with OPENROUTER_API_KEY in server/.env:

    uv run --env-file .env scripts/try_pose_tips.py candidates scripts/out/tip-scenes/*.jpg
    uv run --env-file .env scripts/try_pose_tips.py set scene.jpg --name k01
    uv run --env-file .env scripts/try_pose_tips.py tips
    uv run --env-file .env scripts/try_pose_tips.py tips --prompt-file scripts/out/tip_prompt.txt --label v2 --rounds 2
    uv run --env-file .env scripts/try_pose_tips.py tips --language ko --label ko

`candidates` makes one candidate per scene, the way the app and the server do: the scene prepared like the app's
(upright, long side 1024 px, JPEG 85, no EXIF), a pose the suggestion model chose for it (the n-th scene takes the
n-th of the four, so the poses differ), and the server's pose prompt, model, ratio (the app's frame closest to the
scene's), and 1K resolution. Each pair goes to scripts/out/tips/pairs/<scene>/ and is never made again: a scene that
already has a candidate is skipped, so the image model is billed once per scene. Delete a pair's folder to make it
again. `set` makes the four candidates of one scene, as the app gets them without a description, into
pairs/<name>-1 to -4, also once, with a sheet (sets/<name>.png) to pick one.

`tips` sends every pair to the vision model as the server does, in two requests at once: the scene with the server's
tip prompt from src/pix_server/poses/prompts.toml, or the one in --prompt-file, and the candidate with that prompt, the
candidate's addition, and its pose, the candidate as the app would send it (long side 768 px, JPEG 85), each with the
server's schema and reasoning effort. It writes the tip's sentences from the two answers as the server does (keep
PHOTO_FIELDS, `seen`, and `tip_lines` in step with poses/tips.py). It writes results.json, report.html, and contact
sheets (sheet-<n>.png) to scripts/out/tips/runs/<timestamp>[-label]/, with the prompt it sent and the language; with
--rounds, it also counts the pairs that got the same decisions every time. Every call is billed to the OpenRouter
key, at a fraction of a cent.
"""

import argparse
import asyncio
import base64
import html
import io
import json
import math
import os
import random
import re
import statistics
import sys
import textwrap
import time
import tomllib
from dataclasses import asdict, dataclass, field
from datetime import datetime
from pathlib import Path

import httpx2
from PIL import Image, ImageDraw, ImageFont, ImageOps

CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"
IMAGES_URL = "https://openrouter.ai/api/v1/images"
PROMPTS_FILE = Path(__file__).resolve().parents[1] / "src" / "pix_server" / "poses" / "prompts.toml"
OUT = Path(__file__).parent / "out" / "tips"
PAIRS = OUT / "pairs"

# The server's defaults (config.py): PIX_POSE_MODEL and PIX_SUGGESTION_MODEL.
POSE_MODEL = "openai/gpt-image-2.5-flare@low"
VISION_MODEL = "google/gemini-3.1-flash-lite"
SCENE_LONG_SIDE = 1024
CANDIDATE_LONG_SIDE = 768
JPEG_QUALITY = 85
CONCURRENCY = 4
# The candidate's ratio, as the server's `ASPECT_RATIOS` in poses/generator.py: the app's frame closest to the scene's.
ASPECT_RATIOS = {"3:4": 3 / 4, "9:16": 9 / 16, "1:1": 1.0}
# A tip says what to do in words; a number, a unit, or a degree is hard to follow while shooting. The server writes
# every sentence itself and its words hold none (tests/test_prompts.py), so this only confirms it: the evaluation
# counts the tips it finds one in (eval_poses.py).
NUMBER_WORDS = (
    "two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|fifty|hundred|dozen"
)
ENGLISH_MEASURES = (
    rf"\d|°|\b({NUMBER_WORDS})\b|\bone\s+(steps?|paces?|meters?|metres?|foot|feet|inch(es)?|degrees?)\b",
    r"\b(meters?|metres?|cm|centimet\w*|inch(es)?|feet|foot|degrees?)\b",
)
# Korean, word by word. Every measure takes the same counts, COUNT: native ones, also run together ("한두", "서너",
# "대여섯", "열한"), Sino-Korean numbers ("이십", "백이십", "수십"), 몇, 반, 수, and 영. Only a measure that is also an
# ordinary word narrows its counts or endings, as its comment says.
NATIVE = "(?:한|두|세|서|석|네|너|덧|댓|대|다섯|여섯|일곱|여덟|아홉|열|스무|스물)+"
SINO = "(?:일|이|삼|사|오|육|칠|팔|구|십|백)+"
DIGIT = "[일이삼사오육칠팔구]"
TENS = f"(?:{DIGIT}?[십백])+{DIGIT}?"
COUNT = f"(?:{NATIVE}|(?:수|몇)?{SINO}|반|몇|수|영)"
UNITS = "센티미터|밀리미터|미터|센티|센치|인치|피트"
WORD_START = "(?<![가-힣])"
WORD_END = "(?![가-힣])"
# The particles after an amount, one after another ("쯤에서"), and those that only follow an amount ("만큼").
PARTICLES = "(?:만|씩|쯤|가량|정도|만큼|까지|보다|부터|로|를|의|에|에서|은|는|이|가|도)*"
AMOUNT_PARTICLES = "(?:만|씩|쯤|가량|정도|만큼)*"
KOREAN_MEASURES = (
    # Steps after a count, joined or not, whatever follows ("한 걸음", "두걸음만", "몇 걸음", "반 걸음").
    rf"{WORD_START}{COUNT}\s*(?:걸음|발짝|발자국|뼘)",
    # A unit at the start of a word, after a count or none, whatever follows ("미터보다", "몇 미터까지", "삼십센티만큼"),
    # so 파라미터 is not one, and a word that only begins like one (미터기) is refused with it.
    rf"{WORD_START}(?:{COUNT}\s*)?(?:{UNITS})",
    # 밀리 only after a count, because "밀리지 않게" is a verb.
    rf"{WORD_START}{COUNT}\s*밀리",
    # 발 and 보 only after a count that is not also a word ("이 발" is this foot), with the particles of an amount
    # ("한 발만큼"), because "두 발로 서서" is about feet and 이발 or 구보 are words of their own.
    rf"{WORD_START}(?:{NATIVE}|반|몇)\s*[발보]{AMOUNT_PARTICLES}{WORD_END}",
    # Degrees after a number with tens, whatever follows ("백이십 도만큼"); after one digit or zero, only with a space
    # and particles ("구 도까지", "영 도로"), because 구도, 영도, and "이 도시" are words, though "이 도로" (this road) is
    # refused with them.
    rf"{WORD_START}{TENS}\s*도",
    rf"{WORD_START}(?:영|{DIGIT})\s+도{PARTICLES}{WORD_END}",
)
MEASURE = re.compile("|".join(ENGLISH_MEASURES + KOREAN_MEASURES), re.IGNORECASE)
# Where the photographer holds the phone, by their own posture, lowest first, and the rest of the server's schemas and
# of what it makes of the answers (poses/tips.py): the model describes the scene and the candidate, each in a request
# of its own, and the script compares the two and writes the sentences as the server does.
LEVELS = ["crouching", "waist", "chest", "eye", "above head"]
ANGLES = ["up", "level", "down"]
SHOTS = ["full body", "knees up", "waist up", "chest up"]
BODY_SHARE = {"full body": 1.0, "knees up": 0.75, "waist up": 0.5, "chest up": 0.3}
CLOSE_SHOTS = ("waist up", "chest up")
USUAL_HOLDS = ("chest", "eye")
FAR_STEPS = 2
POSTURES = ["standing", "seated", "on the ground"]
PARTS = ["knees", "waist", "chest", "face", "above"]
HOLDS = {
    "standing": ["crouching", "waist", "chest", "eye", "above head"],
    "seated": ["crouching", "crouching", "waist", "chest", "eye"],
    "on the ground": ["crouching", "crouching", "crouching", "waist", "chest"],
}
HORIZONS = ["none", "level", "tilted"]
BOX_EDGE = 1000
CLOSER_RATIO = 1.18
MUCH_RATIO = 1.4
SIDE_SHARE = 0.12
TIGHT_ROOM = 0.06
PLENTY_ROOM = 0.25
BOX_SCHEMA = {"type": "array", "items": {"type": "integer"}, "minItems": 4, "maxItems": 4}
PHOTO_FIELDS = {
    "box": BOX_SCHEMA,
    "head": BOX_SCHEMA,
    "shot": {"type": "string", "enum": SHOTS},
    "posture": {"type": "string", "enum": POSTURES},
    "level": {"type": "string", "enum": PARTS},
    "tilt": {"type": "string", "enum": ANGLES},
    "horizon": {"type": "string", "enum": HORIZONS},
}
PHOTO_SCHEMA = {
    "type": "object",
    "properties": PHOTO_FIELDS,
    "required": list(PHOTO_FIELDS),
    "additionalProperties": False,
}


def box_of(value: object) -> tuple[float, float, float, float] | None:
    if not isinstance(value, list) or len(value) != 4:
        return None
    if not all(
        isinstance(edge, int | float) and not isinstance(edge, bool) and 0 <= edge <= BOX_EDGE for edge in value
    ):
        return None
    top, left, bottom, right = (edge / BOX_EDGE for edge in value)
    return None if top >= bottom or left >= right else (top, left, bottom, right)


def seen(photo: object) -> dict | None:
    """A photo's description with what the server reads from it: how tall the whole person would be against the
    photo, their head's height and top edge, the side they are on, where the phone is held, and the level line; None when the server would not read it."""
    if not isinstance(photo, dict):
        return None
    box, shot, posture, level = box_of(photo.get("box")), photo.get("shot"), photo.get("posture"), photo.get("level")
    if box is None or shot not in SHOTS or posture not in POSTURES or level not in PARTS:
        return None
    if photo.get("tilt") not in ANGLES or photo.get("horizon") not in HORIZONS:
        return None
    top, left, bottom, right = box
    head = box_of(photo.get("head"))
    middle = (left + right) / 2
    side = "left" if middle < 0.5 - SIDE_SHARE else "right" if middle > 0.5 + SIDE_SHARE else "middle"
    return {
        "body": (bottom - top) / BODY_SHARE[shot],
        "shot": shot,
        "head": head[2] - head[0] if head else None,
        "head_top": head[0] if head else None,
        "side": side,
        "camera": HOLDS[posture][PARTS.index(level)],
        "tilt": photo["tilt"],
        "horizon": photo["horizon"],
    }


def move(ratio: float) -> str:
    return "closer" if ratio >= CLOSER_RATIO else "back" if ratio <= 1 / CLOSER_RATIO else "keep"


def tip_lines(answer: dict, language: str, words: dict) -> dict | None:
    """The decisions and the sentences the server makes of the model's two answers, `now` for the scene and `want` for
    the candidate; None when it would refuse them. As on the server, a tip says only what is not as
    usual, and one sentence when there is nothing of that."""
    now, want = seen(answer.get("now")), seen(answer.get("want"))
    if now is None or want is None:
        return None
    framing, much = "unsure", False
    if now["head"] is not None and want["head"] is not None:
        by_head, by_body = want["head"] / now["head"], want["body"] / now["body"]
        if move(by_head) == move(by_body):
            framing = move(by_head)
            much = {
                "closer": min(by_head, by_body) >= MUCH_RATIO,
                "back": max(by_head, by_body) <= 1 / MUCH_RATIO,
            }.get(framing, False)
    hold, angle = want["camera"], want["tilt"]
    step = LEVELS.index(hold) - LEVELS.index(now["camera"])
    lines = []
    if framing == "closer":
        lines.append(words[("zoom" if want["shot"] in CLOSE_SHOTS else "closer") + ("_much" if much else "")])
    elif framing == "back":
        lines.append(words["back_much" if much else "back"])
    elif framing == "unsure":
        lines.append(words["guide"])
    if hold not in USUAL_HOLDS or angle != "level" or abs(step) >= FAR_STEPS or angle != now["tilt"]:
        lines.append(words["phone"].format(hold=words[hold.replace(" ", "_")], tilt=words[angle]))
    if want["side"] != "middle":
        lines.append(words[want["side"]])
    if want["head_top"] is not None and not TIGHT_ROOM <= want["head_top"] <= PLENTY_ROOM:
        lines.append(words["room_none" if want["head_top"] < TIGHT_ROOM else "room_plenty"])
    if now["horizon"] == "tilted" and want["horizon"] != "tilted":
        lines.append(words["upright"])
    if not lines:
        lines.append(words["stay"])
    return {
        "framing": framing,
        "much": much,
        "hold": hold,
        "height": "raise" if step > 0 else "lower" if step < 0 else "keep",
        "angle": angle,
        "lines": lines,
    }


def one_line(text: str) -> str:
    """Each run of whitespace becomes one space, as the server reads its prompts."""
    return " ".join(text.split())


def server_prompts() -> dict:
    with PROMPTS_FILE.open("rb") as file:
        return tomllib.load(file)


def jpeg(image: Image.Image, long_side: int) -> bytes:
    image = image.copy()
    image.thumbnail((long_side, long_side))
    buffer = io.BytesIO()
    image.convert("RGB").save(buffer, "JPEG", quality=JPEG_QUALITY)
    return buffer.getvalue()


def data_url(data: bytes) -> str:
    return "data:image/jpeg;base64," + base64.b64encode(data).decode()


def chat_payload(
    prompt: str, parts: list[dict], schema: dict, name: str, max_tokens: int, reasoning: str = "minimal"
) -> dict:
    return {
        "model": VISION_MODEL,
        "messages": [{"role": "user", "content": [{"type": "text", "text": prompt}, *parts]}],
        "response_format": {"type": "json_schema", "json_schema": {"name": name, "strict": True, "schema": schema}},
        # The thinking budget is a share of max_tokens, so a higher effort needs room for the answer as well.
        "max_tokens": max_tokens,
        "reasoning": {"effort": reasoning},
        "usage": {"include": True},
    }


def answer_json(body: dict) -> dict:
    text = body["choices"][0]["message"]["content"] or ""
    match = re.search(r"\{.*\}", text, re.DOTALL)  # some models wrap the JSON in a code fence
    return json.loads(match.group(0) if match else text)


# ---- candidates ---------------------------------------------------------------------------------------------------


async def suggested_poses(client: httpx2.AsyncClient, scene: bytes, prompts: dict) -> list[str]:
    """The four poses the suggestion model gives for the scene, topped up with the default poses, as on the server."""
    defaults = [pose["pose"] for pose in prompts["default_poses"]]
    schema = {
        "type": "object",
        "properties": {"poses": {"type": "array", "items": {"type": "string"}}},
        "required": ["poses"],
        "additionalProperties": False,
    }
    parts = [{"type": "image_url", "image_url": {"url": data_url(scene)}}]
    payload = chat_payload(one_line(prompts["suggestion_prompt"]), parts, schema, "poses", 400)
    try:
        response = await client.post(CHAT_URL, json=payload)
        poses = [one_line(pose) for pose in answer_json(response.json())["poses"] if 0 < len(pose.strip()) <= 100]
    except (httpx2.HTTPError, ValueError, KeyError, IndexError, TypeError):
        poses = []
    return (poses + defaults)[:4]


def aspect_ratio(scene: bytes) -> str:
    with Image.open(io.BytesIO(scene)) as image:
        width, height = image.size
    return min(ASPECT_RATIOS, key=lambda name: abs(math.log(width / height / ASPECT_RATIOS[name])))


def prepared_scene(path: Path) -> bytes:
    with Image.open(path) as image:
        return jpeg(ImageOps.exif_transpose(image), SCENE_LONG_SIDE)


async def make_pair(client: httpx2.AsyncClient, name: str, scene: bytes, pose: str, prompts: dict) -> float:
    """One candidate for the pose, the way the server asks for it, kept in pairs/<name>/; the cost, 0 on failure."""
    model, _, quality = POSE_MODEL.partition("@")
    payload = {
        "model": model,
        "prompt": one_line(prompts["pose_prompt"]).replace("{pose}", pose),
        "input_references": [{"type": "image_url", "image_url": {"url": data_url(scene)}}],
        "aspect_ratio": aspect_ratio(scene),
        "resolution": "1K",
        "seed": random.randrange(2**31),
        **({"quality": quality} if quality else {}),
    }
    start = time.perf_counter()
    response = await client.post(IMAGES_URL, json=payload)
    seconds = time.perf_counter() - start
    body = response.json()
    if response.status_code != 200 or not body.get("data"):
        print(f"{name}  failed: {response.status_code} {json.dumps(body.get('error') or body)[:200]}")
        return 0.0
    with Image.open(io.BytesIO(base64.b64decode(body["data"][0]["b64_json"]))) as image:
        candidate = image.convert("RGB")
    folder = PAIRS / name
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "scene.jpg").write_bytes(scene)
    candidate.save(folder / "candidate.jpg", "JPEG", quality=90)
    cost = (body.get("usage") or {}).get("cost") or 0.0
    record = {"pose": pose, "model": POSE_MODEL, "seconds": round(seconds, 1), "cost": cost, "size": candidate.size}
    (folder / "pair.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{name}  {seconds:5.1f} s  ${cost:.3f}  {candidate.width}x{candidate.height}  {pose}")
    return cost


async def make_candidate(client: httpx2.AsyncClient, path: Path, number: int, prompts: dict) -> float:
    if (PAIRS / path.stem / "candidate.jpg").is_file():
        print(f"{path.stem}  kept (made before)")
        return 0.0
    scene = prepared_scene(path)
    poses = await suggested_poses(client, scene, prompts)
    return await make_pair(client, path.stem, scene, poses[(number - 1) % len(poses)], prompts)


async def run_candidates(args: argparse.Namespace, client: httpx2.AsyncClient) -> None:
    prompts = server_prompts()
    gate = asyncio.Semaphore(CONCURRENCY)

    async def one(path: Path, number: int) -> float:
        async with gate:
            return await make_candidate(client, path, number, prompts)

    costs = await asyncio.gather(*(one(path, number) for number, path in enumerate(sorted(args.scenes), 1)))
    print(f"\nTotal cost ${sum(costs):.3f}. Pairs: {PAIRS}")


def write_pick_sheet(name: str) -> Path:
    """The scene and its four candidates side by side, numbered, to choose one."""
    folders = [PAIRS / f"{name}-{number}" for number in range(1, 5)]
    folders = [folder for folder in folders if (folder / "candidate.jpg").is_file()]
    box, gap, label = 420, 20, 70
    canvas = Image.new("RGB", (gap + (len(folders) + 1) * (box + gap), box * 4 // 3 + label + 2 * gap), "white")
    draw = ImageDraw.Draw(canvas)
    pictures = [("Scene", folders[0] / "scene.jpg")] + [
        (
            f"{folder.name[-1]}. {json.loads((folder / 'pair.json').read_text(encoding='utf-8'))['pose']}",
            folder / "candidate.jpg",
        )
        for folder in folders
    ]
    for column, (caption, file) in enumerate(pictures):
        left = gap + column * (box + gap)
        with Image.open(file) as image:
            picture = image.convert("RGB")
        picture.thumbnail((box, box * 4 // 3))
        canvas.paste(picture, (left + (box - picture.width) // 2, gap))
        y = gap + box * 4 // 3 + 8
        for line in textwrap.wrap(caption, 44)[:3]:
            draw.text((left, y), line, font=font(17, bold=caption[0].isdigit()), fill="black")
            y += 22
    out = OUT / "sets" / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    return out


async def run_set(args: argparse.Namespace, client: httpx2.AsyncClient) -> None:
    """Four candidates for one scene, as the app gets them without a description: pairs/<name>-1 … <name>-4."""
    name = args.name or args.scene.stem
    if any((PAIRS / f"{name}-{number}" / "candidate.jpg").is_file() for number in range(1, 5)):
        print(f"{name}  kept (made before)")
    else:
        prompts = server_prompts()
        scene = prepared_scene(args.scene)
        poses = await suggested_poses(client, scene, prompts)
        costs = await asyncio.gather(
            *(make_pair(client, f"{name}-{number}", scene, pose, prompts) for number, pose in enumerate(poses, 1))
        )
        print(f"\nTotal cost ${sum(costs):.3f}.")
    print(f"Pick sheet: {write_pick_sheet(name)}")


# ---- tips ---------------------------------------------------------------------------------------------------------


@dataclass
class Tip:
    scene: str
    pose: str
    round: int
    seconds: float = 0.0
    cost: float | None = None
    error: str | None = None
    reasoning_tokens: int | None = None
    answer: dict = field(default_factory=dict)
    flags: list[str] = field(default_factory=list)


def check(tip: Tip, language: str, words: dict) -> None:
    made = tip_lines(tip.answer, language, words)
    if made is None:
        tip.flags.append("description the server refuses")
        return
    tip.answer |= made


async def ask_tip(client: httpx2.AsyncClient, folder: Path, prompt: str, number: int, args) -> Tip:
    pose = json.loads((folder / "pair.json").read_text(encoding="utf-8"))["pose"]
    tip = Tip(folder.name, pose, number)
    scene = (folder / "scene.jpg").read_bytes()
    with Image.open(folder / "candidate.jpg") as image:
        candidate = jpeg(image, CANDIDATE_LONG_SIDE)
    # The server asks about each photo by itself: the scene with the tip prompt alone, the candidate with its addition
    # and the pose with the photo.
    added = [{"type": "text", "text": args.candidate}]
    asked = (
        ("tip_scene", PHOTO_SCHEMA, [{"type": "text", "text": "The photo:"}], scene),
        (
            "tip_candidate",
            PHOTO_SCHEMA,
            [*added, {"type": "text", "text": f'The photo, made for the pose "{pose}":'}],
            candidate,
        ),
    )
    start = time.perf_counter()
    try:
        responses = await asyncio.gather(
            *(
                client.post(
                    CHAT_URL,
                    json=chat_payload(
                        prompt,
                        [*parts, {"type": "image_url", "image_url": {"url": data_url(photo)}}],
                        schema,
                        name,
                        args.max_tokens,
                        args.reasoning,
                    ),
                )
                for name, schema, parts, photo in asked
            )
        )
        bodies = [response.json() for response in responses]
        failed = [
            (response, body)
            for response, body in zip(responses, bodies, strict=True)
            if response.status_code != 200 or "error" in body
        ]
        if failed:
            response, body = failed[0]
            tip.error = f"{response.status_code} {json.dumps(body.get('error') or body)[:200]}"
        else:
            now, want = (answer_json(body) for body in bodies)
            tip.answer = {"now": now, "want": want}
            usages = [body.get("usage") or {} for body in bodies]
            tip.cost = sum(usage.get("cost") or 0 for usage in usages)
            tip.reasoning_tokens = sum(
                (usage.get("completion_tokens_details") or {}).get("reasoning_tokens") or 0 for usage in usages
            )
            check(tip, args.language, args.words)
    except (httpx2.HTTPError, ValueError, KeyError, IndexError, TypeError, AttributeError) as error:
        tip.error = f"{type(error).__name__}: {error}"
    tip.seconds = time.perf_counter() - start
    said = " | ".join(tip.answer.get("lines", []))
    print(f"{tip.scene}  round {number}  {tip.seconds:4.1f} s  {tip.error or said or tip.flags}")
    return tip


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "Arial Bold.ttf" if bold else "Arial.ttf"
    try:
        return ImageFont.truetype(f"/System/Library/Fonts/Supplemental/{name}", size)
    except OSError:
        return ImageFont.load_default(size)


def axes(answer: dict) -> str:
    """What the server decided: closer or back, the height move, and the tilt."""
    said = {"closer": "get closer", "back": "step back", "keep": "same distance"}
    framing = said.get(answer.get("framing", ""), "no distance (the measures differ)")
    hold = answer.get("hold", "")
    height = {"raise": f"raise to {hold}", "lower": f"lower to {hold}"}.get(answer.get("height", ""), f"keep at {hold}")
    angle = {"up": "tilt up", "down": "tilt down", "level": "hold level"}.get(answer.get("angle", ""), "")
    return " · ".join(part for part in (framing, height, angle) if part)


def lines_text(answer: dict) -> list[str]:
    """The tip's sentences as the app shows them, one on a line."""
    return list(answer.get("lines", []))


def write_sheets(out: Path, tips: list[Tip], per_sheet: int = 8) -> None:
    """One row per tip: the scene, the candidate, and the tip, for review."""
    box, width, row_height = 300, 1500, 330
    regular, bold, small = font(20), font(21, bold=True), font(16)
    for sheet, start in enumerate(range(0, len(tips), per_sheet), 1):
        rows = tips[start : start + per_sheet]
        canvas = Image.new("RGB", (width, row_height * len(rows)), "white")
        draw = ImageDraw.Draw(canvas)
        for index, tip in enumerate(rows):
            top = index * row_height + 15
            for column, name in enumerate(("scene.jpg", "candidate.jpg")):
                with Image.open(PAIRS / tip.scene / name) as image:
                    thumb = image.convert("RGB")
                thumb.thumbnail((box, box))
                canvas.paste(thumb, (15 + column * (box + 15) + (box - thumb.width) // 2, top))
            left, y = 2 * (box + 15) + 25, top
            shots = " -> ".join(str((tip.answer.get(key) or {}).get("shot", "")) for key in ("now", "want"))
            header = f"{tip.scene}   round {tip.round}   {tip.seconds:.1f} s   {shots} · {tip.answer.get('pose', '')}"
            draw.text((left, y), header, font=small, fill="#666")
            y += 26
            for line in textwrap.wrap(f"Pose: {tip.pose}", 78):
                draw.text((left, y), line, font=small, fill="#333")
                y += 21
            y += 8
            if tip.error:
                draw.text((left, y), tip.error[:90], font=regular, fill="#c00")
                continue
            draw.text((left, y), axes(tip.answer), font=bold, fill="#0a58ca")
            y += 34
            for said in lines_text(tip.answer):
                for line in textwrap.wrap(said, 64):
                    draw.text((left, y), line, font=regular, fill="black")
                    y += 27
            if tip.flags:
                draw.text((left, y + 6), "Flags: " + ", ".join(tip.flags), font=small, fill="#c00")
            draw.line((0, top + row_height - 15, width, top + row_height - 15), fill="#ddd", width=2)
        canvas.save(out / f"sheet-{sheet}.png")


def write_report(out: Path, prompt: str, tips: list[Tip]) -> None:
    rows = []
    for tip in tips:
        pair = f"../../pairs/{tip.scene}"
        result = (
            html.escape(tip.error)
            if tip.error
            else (
                f"<b>{html.escape(axes(tip.answer))}</b><br>{'<br>'.join(map(html.escape, lines_text(tip.answer)))}"
                + (f'<br><span class="bad">{html.escape(", ".join(tip.flags))}</span>' if tip.flags else "")
            )
        )
        rows.append(
            f'<tr><td><img src="{pair}/scene.jpg"></td><td><img src="{pair}/candidate.jpg"></td>'
            f"<td>{html.escape(tip.scene)} · round {tip.round} · {tip.seconds:.1f} s<br>"
            f"<small>{html.escape(tip.pose)}</small><p>{result}</p></td></tr>"
        )
    page = (
        '<!doctype html><meta charset="utf-8"><title>Pose tips</title><style>body{font:15px/1.4 system-ui;'
        "margin:24px}td{border-bottom:1px solid #ccc;padding:8px;vertical-align:top}img{width:220px}"
        f".bad{{color:#c00}}</style><h1>Pose tips</h1><p>{html.escape(prompt)}</p><table>{''.join(rows)}</table>"
    )
    (out / "report.html").write_text(page, encoding="utf-8")


async def run_tips(args: argparse.Namespace, client: httpx2.AsyncClient) -> None:
    prompt = one_line(
        args.prompt_file.read_text(encoding="utf-8") if args.prompt_file else server_prompts()["tip_prompt"]
    )
    args.candidate = one_line(server_prompts()["tip_candidate"])
    args.words = {name: one_line(text) for name, text in server_prompts()["tip_words"][args.language].items()}
    folders = sorted(folder for folder in PAIRS.glob("*") if (folder / "candidate.jpg").is_file())
    if args.only:
        folders = [folder for folder in folders if folder.name in args.only]
    if not folders:
        sys.exit(f"No pairs in {PAIRS}; run the candidates step first.")
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = OUT / "runs" / (f"{stamp}-{args.label}" if args.label else stamp)
    out.mkdir(parents=True, exist_ok=True)
    # What the model is sent with the candidate, so the run can be repeated from its folder; the scene gets the first
    # paragraph alone.
    sent = "\n\n".join((prompt, args.candidate))
    (out / "prompt.txt").write_text(sent + "\n", encoding="utf-8")

    gate = asyncio.Semaphore(CONCURRENCY)

    async def one(folder: Path, number: int) -> Tip:
        async with gate:
            return await ask_tip(client, folder, prompt, number, args)

    jobs = [one(folder, number) for number in range(1, args.rounds + 1) for folder in folders]
    tips = sorted(await asyncio.gather(*jobs), key=lambda tip: (tip.scene, tip.round))
    settings = {
        "model": VISION_MODEL,
        "reasoning": args.reasoning,
        "max_tokens": args.max_tokens,
        "language": args.language,
    }
    record = {**settings, "prompt": sent, "tips": [asdict(tip) for tip in tips]}
    (out / "results.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    write_sheets(out, tips)
    write_report(out, sent, tips)

    seconds = sorted(tip.seconds for tip in tips if not tip.error)
    if seconds:
        ninetieth = seconds[int(0.9 * (len(seconds) - 1))]
        print(
            f"\n{len(seconds)}/{len(tips)} answered: median {statistics.median(seconds):.1f} s, "
            f"90th pct. {ninetieth:.1f} s, slowest {seconds[-1]:.1f} s"
        )
    if args.rounds > 1:
        # The same pair should get the same decisions every time.
        decided = ("framing", "much", "hold", "angle")
        rounds: dict[str, set[tuple]] = {}
        for tip in tips:
            if not tip.error:
                rounds.setdefault(tip.scene, set()).add(tuple(tip.answer.get(key) for key in decided))
        same = sum(len(answers) == 1 for answers in rounds.values())
        print(f"Same decisions in every round: {same}/{len(rounds)} pairs")
    thinking = [tip.reasoning_tokens for tip in tips if tip.reasoning_tokens is not None]
    if thinking:
        print(f"Reasoning tokens: median {statistics.median(thinking):.0f}, most {max(thinking)}")
    flagged = sum(bool(tip.flags) for tip in tips)
    cost = sum(tip.cost or 0 for tip in tips)
    print(f"Flagged {flagged}. Cost ${cost:.4f}. Run: {out}")


async def run(args: argparse.Namespace) -> None:
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        sys.exit("Set OPENROUTER_API_KEY first, for example with uv run --env-file .env.")
    headers = {"Authorization": f"Bearer {key}"}
    async with httpx2.AsyncClient(headers=headers, timeout=httpx2.Timeout(120)) as client:
        steps = {"candidates": run_candidates, "set": run_set, "tips": run_tips}
        await steps[args.step](args, client)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    steps = parser.add_subparsers(dest="step", required=True)
    candidates = steps.add_parser("candidates", help="make one candidate per scene, once")
    candidates.add_argument("scenes", nargs="+", type=Path, help="scene photos with the person in frame")
    four = steps.add_parser("set", help="make four candidates for one scene, once, to pick one")
    four.add_argument("scene", type=Path, help="a scene photo with the person in frame")
    four.add_argument("--name", help="the pairs' name (default: the photo's file name)")
    tips = steps.add_parser("tips", help="ask for a tip for every pair made before")
    tips.add_argument("--prompt-file", type=Path, help="tip prompt (default: the server's)")
    tips.add_argument("--label", help="appended to the run folder's name")
    tips.add_argument("--rounds", type=int, default=1, help="calls per pair, to see the spread")
    tips.add_argument("--language", default="en", choices=["en", "ko"], help="the tip's language, as the app sends")
    tips.add_argument("--only", nargs="+", metavar="SCENE", help="only these pairs, by folder name")
    reasoning = ["minimal", "low", "medium", "high"]
    tips.add_argument("--reasoning", default="low", choices=reasoning, help="default: the server's")
    tips.add_argument("--max-tokens", type=int, help="default: 300 at minimal reasoning, otherwise 4000")
    args = parser.parse_args()
    if args.step == "tips" and args.max_tokens is None:
        args.max_tokens = 300 if args.reasoning == "minimal" else 4000
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
