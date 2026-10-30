# AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park and Dongje Park
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

`tips` sends every pair to the vision model with the server's tip prompt from src/pix_server/poses/prompts.toml, or
the one in --prompt-file, the scene first and the candidate second, the candidate as the app would send it (long side
768 px, JPEG 85), with the server's schema and reasoning effort (keep TIP_SCHEMA in step with poses/tips.py). It
writes results.json, report.html, and contact sheets (sheet-<n>.png) to scripts/out/tips/runs/<timestamp>[-label]/,
with the prompt it used; with --rounds, it also counts the pairs that got the same decisions every time. Every call
is billed to the OpenRouter key, at a fraction of a cent.
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
TEXT_MAX_LENGTH = 140
# A tip says how to hold the camera in words; a number, a unit, or a degree is hard to follow while shooting. The same
# check as the server's `MEASURE` in poses/tips.py.
NUMBER_WORDS = (
    "two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|fifty|hundred|dozen"
)
KOREAN_COUNTS = "한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|스무"
KOREAN_DEGREES = "[일이삼사오육칠팔구]?[십백][일이삼사오육칠팔구]?|[일이삼사오육칠팔구]"
MEASURE = re.compile(
    rf"\d|°|\b({NUMBER_WORDS})\b|\bone\s+(steps?|paces?|meters?|metres?|foot|feet|inch(es)?|degrees?)\b"
    r"|\b(meters?|metres?|cm|centimet\w*|inch(es)?|feet|foot|degrees?)\b"
    rf"|(?<![가-힣])({KOREAN_COUNTS})\s*(걸음|발짝|발자국|발|뼘|보)(?![가-힣])|미터|센티|센치|밀리|인치|피트"
    rf"|(?<![가-힣])({KOREAN_DEGREES})\s*도(?![가-힣])",
    re.IGNORECASE,
)

# Where the photographer holds the camera, by their own posture, lowest first. The model places the camera of photo 1
# and the best one for the pose, in the order of the schema, so it decides the shot and the pose first; the script
# compares the two for the height move instead of asking for it.
LEVELS = ["crouching", "waist", "chest", "eye", "above head"]
TIP_SCHEMA = {
    "type": "object",
    "properties": {
        "shot": {"type": "string", "enum": ["full body", "knees up", "waist up", "chest up"]},
        "pose": {"type": "string", "enum": ["standing", "walking", "leaning", "sitting", "crouching", "other"]},
        "camera_now": {"type": "string", "enum": LEVELS},
        "camera_best": {"type": "string", "enum": LEVELS},
        "angle": {"type": "string", "enum": ["up", "level", "down"]},
        "framing": {"type": "string", "enum": ["closer", "back", "keep"]},
        "text": {"type": "string"},
    },
    "required": ["shot", "pose", "camera_now", "camera_best", "angle", "framing", "text"],
    "additionalProperties": False,
}
ANGLE_WORDS = re.compile(r"\b(tilt|tilted|tilting|level|angle|straight)\b|기울|수평|똑바로|각도", re.IGNORECASE)


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


def check(tip: Tip) -> None:
    now, best = (tip.answer.get(key) for key in ("camera_now", "camera_best"))
    if now in LEVELS and best in LEVELS:
        step = LEVELS.index(best) - LEVELS.index(now)
        tip.answer["height"] = "raise" if step > 0 else "lower" if step < 0 else "keep"
    text = tip.answer.get("text", "")
    if not ANGLE_WORDS.search(text):
        tip.flags.append("no angle in text")
    if not 0 < len(text) <= TEXT_MAX_LENGTH:
        tip.flags.append(f"text length {len(text)}")
    if MEASURE.search(text):
        tip.flags.append("number or unit")


async def ask_tip(client: httpx2.AsyncClient, folder: Path, prompt: str, number: int, args) -> Tip:
    pose = json.loads((folder / "pair.json").read_text(encoding="utf-8"))["pose"]
    tip = Tip(folder.name, pose, number)
    scene = (folder / "scene.jpg").read_bytes()
    with Image.open(folder / "candidate.jpg") as image:
        candidate = jpeg(image, CANDIDATE_LONG_SIDE)
    # The server adds the Korean instruction right after the tip prompt when the phone's screens are in Korean.
    korean = [{"type": "text", "text": args.korean}] if args.korean else []
    parts = [
        *korean,
        {"type": "text", "text": "Photo 1, the scene:"},
        {"type": "image_url", "image_url": {"url": data_url(scene)}},
        {"type": "text", "text": f'Photo 2, the photo they want, with the pose "{pose}":'},
        {"type": "image_url", "image_url": {"url": data_url(candidate)}},
    ]
    start = time.perf_counter()
    try:
        payload = chat_payload(prompt, parts, TIP_SCHEMA, "tip", args.max_tokens, args.reasoning)
        response = await client.post(CHAT_URL, json=payload)
        body = response.json()
        if response.status_code != 200 or "error" in body:
            tip.error = f"{response.status_code} {json.dumps(body.get('error') or body)[:200]}"
        else:
            tip.answer = answer_json(body)
            usage = body.get("usage") or {}
            tip.cost = usage.get("cost")
            tip.reasoning_tokens = (usage.get("completion_tokens_details") or {}).get("reasoning_tokens")
            check(tip)
    except (httpx2.HTTPError, ValueError, KeyError, IndexError, TypeError) as error:
        tip.error = f"{type(error).__name__}: {error}"
    tip.seconds = time.perf_counter() - start
    print(f"{tip.scene}  round {number}  {tip.seconds:4.1f} s  {tip.error or tip.answer.get('text', '')}")
    return tip


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "Arial Bold.ttf" if bold else "Arial.ttf"
    try:
        return ImageFont.truetype(f"/System/Library/Fonts/Supplemental/{name}", size)
    except OSError:
        return ImageFont.load_default(size)


def axes(answer: dict) -> str:
    """The moves as the app would list them: closer or back, the height, and always the angle."""
    framing = {"closer": "get closer", "back": "step back"}.get(answer.get("framing", ""), "")
    best = answer.get("camera_best", "")
    height = {"raise": f"raise to {best}", "lower": f"lower to {best}"}.get(answer.get("height", ""), f"keep at {best}")
    angle = {"up": "tilt up", "down": "tilt down", "level": "hold level"}.get(answer.get("angle", ""), "")
    return " · ".join(move for move in (framing, height, angle) if move)


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
            seen = " · ".join(str(tip.answer.get(key, "")) for key in ("shot", "pose", "camera_now", "camera_best"))
            header = f"{tip.scene}   round {tip.round}   {tip.seconds:.1f} s   {seen}"
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
            for line in textwrap.wrap(tip.answer.get("text", ""), 64):
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
                f"<b>{html.escape(axes(tip.answer))}</b><br>{html.escape(tip.answer.get('text', ''))}"
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
    args.korean = one_line(server_prompts()["tip_korean"]) if args.language == "ko" else None
    folders = sorted(folder for folder in PAIRS.glob("*") if (folder / "candidate.jpg").is_file())
    if args.only:
        folders = [folder for folder in folders if folder.name in args.only]
    if not folders:
        sys.exit(f"No pairs in {PAIRS}; run the candidates step first.")
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = OUT / "runs" / (f"{stamp}-{args.label}" if args.label else stamp)
    out.mkdir(parents=True, exist_ok=True)
    (out / "prompt.txt").write_text(prompt + "\n", encoding="utf-8")

    gate = asyncio.Semaphore(CONCURRENCY)

    async def one(folder: Path, number: int) -> Tip:
        async with gate:
            return await ask_tip(client, folder, prompt, number, args)

    jobs = [one(folder, number) for number in range(1, args.rounds + 1) for folder in folders]
    tips = sorted(await asyncio.gather(*jobs), key=lambda tip: (tip.scene, tip.round))
    settings = {"model": VISION_MODEL, "reasoning": args.reasoning, "max_tokens": args.max_tokens}
    record = {**settings, "prompt": prompt, "tips": [asdict(tip) for tip in tips]}
    (out / "results.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    write_sheets(out, tips)
    write_report(out, prompt, tips)

    seconds = sorted(tip.seconds for tip in tips if not tip.error)
    if seconds:
        ninetieth = seconds[int(0.9 * (len(seconds) - 1))]
        print(
            f"\n{len(seconds)}/{len(tips)} answered: median {statistics.median(seconds):.1f} s, "
            f"90th pct. {ninetieth:.1f} s, slowest {seconds[-1]:.1f} s"
        )
    if args.rounds > 1:
        # The same pair should get the same decisions every time; the sentence may differ.
        decided = ("shot", "pose", "camera_best", "angle", "framing")
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
    tips.add_argument(
        "--language", default="en", choices=["en", "ko"], help="the sentence's language, as the app sends"
    )
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
