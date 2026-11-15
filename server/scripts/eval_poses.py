# AI-generated with Claude Code, 2026-11-14, reviewed by Jaewan Park
# /// script
# requires-python = ">=3.11"
# dependencies = ["anthropic>=2.1,<3", "httpx2>=2.13.1", "pillow>=12.3.0"]
# ///
"""The AI module tests of pose generation, suggestions, and tips (Testing Documentation 3.1).

It does not import pix_server: `generate` sends the frozen cases of eval/pose_cases.json to a running Pix server the way
the app does, and `judge` has Claude check every candidate, set, and tip against eval/rubrics.toml. Run it from server/
with uv, which installs the Anthropic SDK for this script alone:

    uv run scripts/eval_poses.py generate --server http://127.0.0.1:8765 --run run1
    uv run scripts/eval_poses.py generate --run landmark --only s03 --situation landmark  # one situation's prompts
    uv run --env-file .env scripts/eval_poses.py judge run1             # Batch API, half price; waits for the results
    uv run --env-file .env scripts/eval_poses.py judge run1 --now       # direct calls, for a pilot
    uv run scripts/eval_poses.py report run1 run2 run3
    uv run scripts/eval_poses.py sample run1 --size 60                  # 60 candidates for two of us to label
    uv run scripts/eval_poses.py agree run1 --labels a.json b.json      # our labels against Claude's verdicts
    uv run --env-file .env scripts/eval_poses.py judge run1 --repeat 3  # the sample judged 3 times, for consistency
    uv run scripts/eval_poses.py person --phone phone.json --jev scripts/out/person/<run>/results.json

`generate` follows the app's RemotePoseGenerator for each case twice, once with its description and once without (a
set of suggested poses): the scene prepared like the app's (upright, long side at most 1024 px, JPEG 85, no EXIF);
without a description, POST /pose-suggestions and then four POST /poses in parallel; with one, POST /poses with the
description at once and POST /pose-suggestions for three variations (10 s, the description again for any it does not
get), then the other three. Each request has the app's 60 s limit, `selfie` for a selfie, and the case's situation
(FR-4.8), or the one --situation gives every case; a selfie keeps its own. The time of a set runs
from its first request to its last candidate. Then one POST /pose-tips for one of the set's candidates, at a long side
of 768 px, in the case's language. Sets go one at a time by default, as one user's would, so the times are those of
one phone; --sets-at-once runs several, and the report says so. The server must allow the requests: start a local one
with raised limits (README, *Evaluate the AI modules*). Everything goes to scripts/out/eval/<run>/, one folder per set,
and a set that is there is never made again, so an interrupted run continues where it stopped.

`judge` sends Claude (claude-opus-5-5) one request per candidate with the scene and the candidate, one per suggested
set with its four poses, and one per tip with the scene, the candidate, and the sentence, each with its rubric and a
JSON schema; verdicts go to judgments.jsonl with the rubric's hash, and a request already judged is not sent again. It
reads ANTHROPIC_API_KEY. `report` counts the rules each candidate breaks, the requests it follows, the times, the
suggested sets whose four poses all differ, and the tips that match their pose or hold a number or unit; with several
runs it gives each run and all together, with 95 % intervals. `sample`, `agree`, and `judge --repeat` check the judge
(3.1, *Judge validation*). `person` puts the phone's person check (the app's PersonCheckEval on a device) and
the server's (try_person_check.py) together, as the app runs them. The photos go to the Pix server's models and to
Anthropic.
"""

import argparse
import asyncio
import base64
import hashlib
import html
import io
import itertools
import json
import math
import os
import random
import re
import statistics
import subprocess
import sys
import time
import tomllib
from dataclasses import dataclass, replace
from datetime import datetime
from pathlib import Path

import httpx2
from PIL import Image, ImageOps

# The server's own check of a tip's sentence, kept in step with poses/tips.py by that script.
from try_pose_tips import MEASURE

SERVER = Path(__file__).resolve().parents[1]
CASES = SERVER / "eval" / "pose_cases.json"
RUBRICS = SERVER / "eval" / "rubrics.toml"
PROMPTS = SERVER / "src" / "pix_server" / "poses" / "prompts.toml"
OUT = Path(__file__).parent / "out" / "eval"

# The app's values (RemotePoseGenerator, RemotePoseTipper, PoseImageCodec).
POSE_COUNT = 4
REQUEST_SECONDS = 60
VARIATION_SECONDS = 10
TIP_SECONDS = 15
SCENE_LONG_SIDE = 1024
TIP_CANDIDATE_LONG_SIDE = 768
JPEG_QUALITY = 85
# The candidate's ratio, as the server's `ASPECT_RATIOS` in poses/generator.py: the app's frame closest to the scene's.
ASPECT_RATIOS = {"3:4": 3 / 4, "9:16": 9 / 16, "1:1": 1.0}
MODES = ("described", "suggested")
# The server's situations (`Situation` in poses/prompts.py).
SITUATIONS = ("default", "background", "half_body", "full_body", "landmark", "selfie")

# The judge (Testing Documentation 3.1). Opus 5.5 always thinks; effort sets how much.
JUDGE_MODEL = "claude-opus-5-5"
JUDGE_EFFORT = "medium"
JUDGE_MAX_TOKENS = 8000
JUDGE_CONCURRENCY = 8
# Requests per batch, so a batch stays well under the Batch API's 256 MB with two photos per request.
BATCH_SIZE = 100
# US dollars per million tokens in November 2026; the Batch API halves them.
PRICES = {"input": 4.0, "output": 20.0, "cache_read": 0.2, "cache_write": 5.0}

# The rules every candidate must keep (3.1); `selfie_stays_selfie` only for a selfie. `follows_request` is counted on
# its own, not as a broken rule.
KEEP_RULES = ("same_person", "same_background", "whole_natural_body", "no_artifacts_or_text", "selfie_stays_selfie")
RULE_NAMES = {
    "same_person": "Same person",
    "same_background": "Same background",
    "whole_natural_body": "Whole, natural body",
    "no_artifacts_or_text": "No artifacts or text",
    "selfie_stays_selfie": "Selfie stays a selfie",
    "follows_request": "Follows the request",
}
DIGIT = re.compile(r"[0-9０-９]")


# ---------------------------------------------------------------------------------------------------------------------
# Cases, photos, and files


@dataclass(frozen=True)
class Case:
    id: str
    scene: Path  # in scenes/ beside the cases file
    selfie: bool
    language: str
    description: str
    situation: str  # `default` when the case names none, `selfie` for a selfie


def load_cases(path: Path) -> list[Case]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    scenes = path.parent / "scenes"
    cases = [
        Case(
            c["id"],
            scenes / c["file"],
            bool(c["selfie"]),
            c["language"],
            c["description"],
            c.get("situation", "selfie" if c["selfie"] else "default"),
        )
        for c in raw
    ]
    ids = [case.id for case in cases]
    if len(set(ids)) != len(ids):
        sys.exit(f"{path}: duplicate case ids")
    for case in cases:
        if case.language not in ("en", "ko") or not 0 < len(case.description) <= 100:
            sys.exit(f"{path}: case {case.id} needs language en or ko and a description of 1-100 characters")
        if not case.scene.is_file():
            sys.exit(f"{path}: case {case.id}: no scene {case.scene}")
        if case.situation not in SITUATIONS or case.selfie != (case.situation == "selfie"):
            sys.exit(f"{path}: case {case.id}: situation must be one of {', '.join(SITUATIONS)}, selfie for a selfie")
    return cases


def jpeg(image: Image.Image, long_side: int) -> bytes:
    """As the app encodes a photo: RGB, the long side at most `long_side`, JPEG 85, no EXIF."""
    image = image.convert("RGB")
    scale = long_side / max(image.size)
    if scale < 1:
        image = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=JPEG_QUALITY)
    return buffer.getvalue()


def prepared_scene(path: Path) -> bytes:
    with Image.open(path) as image:
        return jpeg(ImageOps.exif_transpose(image), SCENE_LONG_SIDE)


def aspect_ratio(size: tuple[int, int]) -> str:
    width, height = size
    return min(ASPECT_RATIOS, key=lambda name: abs(math.log(width / height / ASPECT_RATIOS[name])))


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:12]


def run_dir(name: str) -> Path:
    """A run by its name, in scripts/out/eval/, or by a path to its folder."""
    return Path(name) if "/" in name else OUT / name


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def append_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("a", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False) + "\n")


def git(*args: str) -> str:
    try:
        return subprocess.run(["git", *args], cwd=SERVER, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def flatten(text: str) -> str:
    """rubrics.toml's wrapping: runs of spaces and line breaks are one space, but a line starting "- " starts a line."""
    lines = [" ".join(part.split()) for part in re.split(r"\n\s*(?=- )", text.strip())]
    return "\n".join(lines)


def load_rubrics() -> dict:
    with RUBRICS.open("rb") as file:
        raw = tomllib.load(file)
    return {
        key: flatten(value) if isinstance(value, str) else {k: flatten(v) for k, v in value.items()}
        for key, value in raw.items()
    }


# ---------------------------------------------------------------------------------------------------------------------
# generate: the app's requests to a Pix server


def error_code(response: httpx2.Response) -> str:
    try:
        return response.json()["error"]["code"]
    except (ValueError, KeyError, TypeError):
        return f"HTTP {response.status_code}"


def situation_fields(case: Case) -> dict[str, str]:
    """The form fields that name the case's situation: `selfie`, as every app sends it for a selfie, and `situation`,
    left out for `default` as an app from before Iteration 4 leaves it out."""
    fields = {"selfie": "true"} if case.selfie else {}
    if case.situation != "default":
        fields["situation"] = case.situation
    return fields


async def suggestions(
    client: httpx2.AsyncClient, server: str, scene: bytes, case: Case, description: str | None, seconds: float
) -> tuple[list[str], dict]:
    data = {"description": description} if description is not None else {}
    data |= situation_fields(case)
    started = time.perf_counter()
    try:
        response = await client.post(
            f"{server}/api/v1/pose-suggestions",
            files={"image": ("scene.jpg", scene, "image/jpeg")},
            data=data,
            timeout=seconds,
        )
    except httpx2.TimeoutException:
        return [], {"status": None, "code": "TIMEOUT", "seconds": round(time.perf_counter() - started, 2)}
    except httpx2.HTTPError as exc:
        return [], {"status": None, "code": type(exc).__name__, "seconds": round(time.perf_counter() - started, 2)}
    record = {"status": response.status_code, "seconds": round(time.perf_counter() - started, 2)}
    if response.status_code != 200:
        return [], {**record, "code": error_code(response)}
    poses = [pose for pose in response.json().get("poses", []) if isinstance(pose, str) and pose.strip()]
    return poses, record


async def pose(
    client: httpx2.AsyncClient, server: str, scene: bytes, case: Case, text: str, seed: int, started: float
) -> tuple[bytes | None, dict]:
    data = {"description": text, "seed": str(seed)} | situation_fields(case)
    record: dict = {"pose": text, "seed": seed}
    try:
        response = await client.post(
            f"{server}/api/v1/poses",
            files={"image": ("scene.jpg", scene, "image/jpeg")},
            data=data,
            timeout=REQUEST_SECONDS,
        )
    except httpx2.TimeoutException:
        return None, {**record, "status": None, "code": "TIMEOUT", "at": round(time.perf_counter() - started, 2)}
    except httpx2.HTTPError as exc:
        return None, {
            **record,
            "status": None,
            "code": type(exc).__name__,
            "at": round(time.perf_counter() - started, 2),
        }
    record.update(status=response.status_code, at=round(time.perf_counter() - started, 2))
    if response.status_code != 200:
        return None, {**record, "code": error_code(response)}
    body = response.json()
    candidate = base64.b64decode(body["image"])
    record["server_ms"] = body.get("elapsedMs")
    return candidate, record


def check_candidate(candidate: bytes, scene_size: tuple[int, int]) -> dict:
    """The automatic checks: a JPEG the app can decode, in the ratio the server asks for."""
    try:
        with Image.open(io.BytesIO(candidate)) as image:
            image.load()
            size, image_format = image.size, image.format
    except OSError:
        return {"valid": False, "why": "not an image"}
    wanted, got = aspect_ratio(scene_size), size[0] / size[1]
    ratio_ok = abs(math.log(got / ASPECT_RATIOS[wanted])) < 0.03
    return {
        "valid": image_format == "JPEG" and ratio_ok,
        "format": image_format,
        "size": list(size),
        "ratio": wanted,
        "why": "" if image_format == "JPEG" and ratio_ok else f"{image_format} {size[0]}x{size[1]}, asked {wanted}",
    }


async def tip(
    client: httpx2.AsyncClient, server: str, scene: bytes, candidate: bytes, text: str, language: str
) -> dict:
    with Image.open(io.BytesIO(candidate)) as image:
        sent = jpeg(image, TIP_CANDIDATE_LONG_SIDE)
    started = time.perf_counter()
    try:
        response = await client.post(
            f"{server}/api/v1/pose-tips",
            files={"scene": ("scene.jpg", scene, "image/jpeg"), "candidate": ("candidate.jpg", sent, "image/jpeg")},
            data={"pose": text, "language": language},
            timeout=TIP_SECONDS,
        )
    except httpx2.HTTPError as exc:
        code = "TIMEOUT" if isinstance(exc, httpx2.TimeoutException) else type(exc).__name__
        return {"status": None, "code": code, "seconds": round(time.perf_counter() - started, 2)}
    record = {"status": response.status_code, "seconds": round(time.perf_counter() - started, 2), "language": language}
    if response.status_code != 200:
        return {**record, "code": error_code(response)}
    body = response.json()
    sentence = body.get("text", "")
    measured = bool(MEASURE.search(sentence) or DIGIT.search(sentence))
    return {**record, **{k: body.get(k) for k in ("text", "framing", "hold", "height", "angle")}, "measure": measured}


def stable_int(*parts: object) -> int:
    return int.from_bytes(hashlib.sha256("/".join(map(str, parts)).encode()).digest()[:8], "big")


async def run_set(client: httpx2.AsyncClient, server: str, case: Case, mode: str, run: str, folder: Path) -> dict:
    scene = prepared_scene(case.scene)
    with Image.open(io.BytesIO(scene)) as image:
        scene_size = image.size
    # One seed for the set gives each pose a seed of its own, in the order of the poses, as the app does.
    seeds = random.Random(stable_int(run, case.id, mode))
    started = time.perf_counter()
    tasks: list[asyncio.Task] = []
    suggestion_record: dict
    if mode == "described":
        texts = [case.description]
        tasks.append(asyncio.create_task(pose(client, server, scene, case, texts[0], seeds.randrange(2**63), started)))
        variations, suggestion_record = await suggestions(
            client, server, scene, case, case.description, VARIATION_SECONDS
        )
        variations = variations[1:][: POSE_COUNT - 1]
        suggestion_record["usable"] = len(variations)
        texts += variations + [case.description] * (POSE_COUNT - 1 - len(variations))
    else:
        texts, suggestion_record = await suggestions(client, server, scene, case, None, REQUEST_SECONDS)
        texts = texts[:POSE_COUNT]
        suggestion_record["usable"] = len(texts)
    for text in texts[len(tasks) :]:
        tasks.append(asyncio.create_task(pose(client, server, scene, case, text, seeds.randrange(2**63), started)))
    results = await asyncio.gather(*tasks)

    candidates = []
    for index, (image, record) in enumerate(results, start=1):
        if image is not None:
            (folder / f"c{index}.jpg").write_bytes(image)
            record.update(check_candidate(image, scene_size))
        candidates.append({"index": index, **record, "ok": image is not None})
    ready = [c["at"] for c in candidates if c["ok"]]
    complete = len(ready) == POSE_COUNT and len(texts) == POSE_COUNT
    record = {
        "case": case.id,
        "mode": mode,
        "selfie": case.selfie,
        "situation": case.situation,
        "language": case.language,
        "description": case.description if mode == "described" else None,
        "poses": texts,
        "suggestions": suggestion_record,
        "candidates": candidates,
        "first_seconds": min(ready) if ready else None,
        "all_seconds": max(ready) if complete else None,
    }
    # The tip of one candidate, as the user would take one: which one is fixed by the run and the set.
    good = [c for c in candidates if c["ok"]]
    if good:
        chosen = good[stable_int(run, case.id, mode, "tip") % len(good)]
        candidate = (folder / f"c{chosen['index']}.jpg").read_bytes()
        record["tip"] = {
            "candidate": chosen["index"],
            **await tip(client, server, scene, candidate, chosen["pose"], case.language),
        }
    (folder / "scene.jpg").write_bytes(scene)
    return record


async def generate(args: argparse.Namespace) -> None:
    cases = load_cases(Path(args.cases))
    if args.only:
        cases = [case for case in cases if case.id in set(args.only)]
    if args.limit:
        cases = cases[: args.limit]
    if args.situation:
        # A selfie keeps the selfie situation: the others turn a selfie into a photo someone else took.
        cases = [case if case.selfie else replace(case, situation=args.situation) for case in cases]
    out = run_dir(args.run)
    out.mkdir(parents=True, exist_ok=True)
    server = args.server.rstrip("/")
    async with httpx2.AsyncClient() as client:
        try:
            health = await client.get(f"{server}/health", timeout=5)
        except httpx2.HTTPError as exc:
            sys.exit(f"{server}/health: {exc!r}; start the server first (README, Evaluate the AI modules)")
        if health.status_code != 200:
            sys.exit(f"{server}/health answered {health.status_code}")
        meta_path = out / "run.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
        meta.setdefault("started", datetime.now().isoformat(timespec="seconds"))
        meta.update(
            server=server,
            commit=git("rev-parse", "--short", "HEAD"),
            changed=git("status", "--porcelain", "--", "src", "eval"),
            cases=f"{Path(args.cases).name} {sha(Path(args.cases))}",
            prompts=sha(PROMPTS),
            sets_at_once=args.sets_at_once,
            situation=args.situation,
            # What the server was started with, when it reads the same .env and environment as this script.
            settings={k: v for k, v in os.environ.items() if k.startswith("PIX_") and "SECRET" not in k},
        )
        meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

        todo = [
            (case, mode)
            for case in cases
            for mode in args.modes
            if not (out / "sets" / f"{case.id}-{mode}" / "set.json").exists()
        ]
        print(f"{len(todo)} sets to make in {out}")
        limit = asyncio.Semaphore(args.sets_at_once)

        async def one(case: Case, mode: str) -> None:
            async with limit:
                folder = out / "sets" / f"{case.id}-{mode}"
                folder.mkdir(parents=True, exist_ok=True)
                record = await run_set(client, server, case, mode, args.run, folder)
                (folder / "set.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
                made = sum(c["ok"] for c in record["candidates"])
                codes = sorted({c.get("code", "") for c in record["candidates"] if not c["ok"]})
                tip_text = (record.get("tip") or {}).get("text") or (record.get("tip") or {}).get("code", "-")
                all_s = record["all_seconds"]
                print(
                    f"{case.id}-{mode:<9} {made}/4 {'' if all_s is None else f'{all_s:5.1f} s'}"
                    f"{' ' + ','.join(codes) if codes else ''}  tip: {tip_text}"
                )

        await asyncio.gather(*(one(case, mode) for case, mode in todo))


# ---------------------------------------------------------------------------------------------------------------------
# judge: Claude against the rubrics


def image_block(data: bytes) -> dict:
    return {
        "type": "image",
        "source": {"type": "base64", "media_type": "image/jpeg", "data": base64.b64encode(data).decode()},
    }


def verdict_schema(rules: list[str], seen: str | None) -> dict:
    rule = {
        "type": "object",
        "properties": {"reason": {"type": "string"}, "verdict": {"type": "string", "enum": ["pass", "fail"]}},
        "required": ["reason", "verdict"],
        "additionalProperties": False,
    }
    properties = ({seen: {"type": "string"}} if seen else {}) | {name: rule for name in rules}
    return {"type": "object", "properties": properties, "required": list(properties), "additionalProperties": False}


@dataclass
class Item:
    id: str  # the judgment's key: <kind>-<case>-<d|s>-<candidate>, the Batch API's custom_id without the repetition
    kind: str  # pose, suggestions, tip
    content: list[dict]
    rules: list[str]
    seen: str | None


def items_of(out: Path, rubrics: dict) -> list[Item]:
    items = []
    for set_json in sorted((out / "sets").glob("*/set.json")):
        record = json.loads(set_json.read_text(encoding="utf-8"))
        folder = set_json.parent
        key = f"{record['case']}-{record['mode'][0]}"
        scene = (folder / "scene.jpg").read_bytes()
        for candidate in record["candidates"]:
            if not candidate["ok"] or not (folder / f"c{candidate['index']}.jpg").exists():
                continue
            asked = record["description"] if record["mode"] == "described" else candidate["pose"]
            rules = [r for r in KEEP_RULES if r != "selfie_stays_selfie" or record["selfie"]] + ["follows_request"]
            text = (
                f"The request{' (the user typed it, possibly in Korean)' if record['mode'] == 'described' else ''}:"
                f' "{asked}"\nPhoto 1 is {"a selfie" if record["selfie"] else "not a selfie"}.\n\nJudge each rule:\n'
                + "\n".join(f"- {name}: {rubrics['pose'][name]}" for name in rules)
            )
            content = [
                {"type": "text", "text": rubrics["pose"]["intro"] + "\n\nPhoto 1:"},
                image_block(scene),
                {"type": "text", "text": "Photo 2:"},
                image_block((folder / f"c{candidate['index']}.jpg").read_bytes()),
                {"type": "text", "text": text},
            ]
            items.append(Item(f"pose-{key}-{candidate['index']}", "pose", content, rules, "pose_seen"))
        # The four poses of the set: suggested ones, or the description and its three variations (the description
        # again for each the server did not give, which then counts as a pose that is not different).
        if len(record["poses"]) == POSE_COUNT:
            numbered = "\n".join(f"{i}. {p}" for i, p in enumerate(record["poses"], start=1))
            text = f"{rubrics['suggestions']['intro']}\n\n{numbered}\n\nJudge:\n- all_different: {rubrics['suggestions']['all_different']}"
            items.append(
                Item(f"suggestions-{key}", "suggestions", [{"type": "text", "text": text}], ["all_different"], None)
            )
        tip_record = record.get("tip") or {}
        if tip_record.get("text"):
            with Image.open(folder / f"c{tip_record['candidate']}.jpg") as image:
                sent = jpeg(image, TIP_CANDIDATE_LONG_SIDE)
            pose_text = record["candidates"][tip_record["candidate"] - 1]["pose"]
            text = (
                f'The pose: "{pose_text}"\nThe tip ({"Korean" if tip_record["language"] == "ko" else "English"}):'
                f' "{tip_record["text"]}"\n\nJudge:\n- matches_pose: {rubrics["tip"]["matches_pose"]}'
            )
            content = [
                {"type": "text", "text": rubrics["tip"]["intro"] + "\n\nPhoto 1:"},
                image_block(scene),
                {"type": "text", "text": "Photo 2:"},
                image_block(sent),
                {"type": "text", "text": text},
            ]
            items.append(Item(f"tip-{key}", "tip", content, ["matches_pose"], "shot_and_pose_seen"))
    return items


def request_params(item: Item, rubrics: dict) -> dict:
    return {
        "model": JUDGE_MODEL,
        "max_tokens": JUDGE_MAX_TOKENS,
        "system": [{"type": "text", "text": rubrics["system"], "cache_control": {"type": "ephemeral"}}],
        "messages": [{"role": "user", "content": item.content}],
        "output_config": {
            "effort": JUDGE_EFFORT,
            "format": {"type": "json_schema", "schema": verdict_schema(item.rules, item.seen)},
        },
    }


def cost(usage: dict, batch: bool) -> float:
    dollars = (
        usage.get("input_tokens", 0) * PRICES["input"]
        + usage.get("output_tokens", 0) * PRICES["output"]
        + (usage.get("cache_read_input_tokens") or 0) * PRICES["cache_read"]
        + (usage.get("cache_creation_input_tokens") or 0) * PRICES["cache_write"]
    ) / 1e6
    return dollars / 2 if batch else dollars


def judgment_row(item: Item, rep: int, message, batch: bool, rubric: str) -> dict:
    usage = message.usage.model_dump() if hasattr(message.usage, "model_dump") else dict(message.usage)
    row = {
        "id": item.id,
        "rep": rep,
        "kind": item.kind,
        "model": message.model,
        "rubric": rubric,
        "batch": batch,
        "stop_reason": message.stop_reason,
        "usage": {k: v for k, v in usage.items() if isinstance(v, int)},
        "cost": round(cost(usage, batch), 5),
        "at": datetime.now().isoformat(timespec="seconds"),
    }
    text = next((block.text for block in message.content if block.type == "text"), "")
    try:
        answer = json.loads(text)
        verdicts = {rule: answer[rule]["verdict"] for rule in item.rules}
    except (ValueError, KeyError, TypeError):
        return {**row, "error": f"unusable answer ({message.stop_reason})"}
    return {**row, "answer": answer, "verdicts": verdicts}


def custom_id(item_id: str, rep: int) -> str:
    return f"{item_id}-r{rep}"


def split_custom_id(value: str) -> tuple[str, int]:
    item_id, _, rep = value.rpartition("-r")
    return item_id, int(rep)


async def judge_now(todo: list[tuple[Item, int]], rubrics: dict, rubric: str, path: Path) -> None:
    import anthropic

    client = anthropic.AsyncAnthropic(max_retries=4)
    limit = asyncio.Semaphore(JUDGE_CONCURRENCY)
    spent = 0.0

    async def one(item: Item, rep: int) -> None:
        nonlocal spent
        async with limit:
            try:
                message = await client.messages.create(**request_params(item, rubrics))
            except anthropic.APIError as exc:
                print(f"{custom_id(item.id, rep)}  failed: {type(exc).__name__}: {str(exc)[:160]}")
                return
        row = judgment_row(item, rep, message, False, rubric)
        append_jsonl(path, [row])
        spent += row["cost"]
        print(f"{custom_id(item.id, rep):<28} {row.get('verdicts') or row.get('error')}  ${row['cost']:.3f}")

    await asyncio.gather(*(one(item, rep) for item, rep in todo))
    print(f"spent about ${spent:.2f}")


def judge_batch(
    todo: list[tuple[Item, int]], items: list[Item], rubrics: dict, rubric: str, out: Path, wait: bool
) -> None:
    import anthropic

    client = anthropic.Anthropic(max_retries=4)
    path, batches_path = out / "judgments.jsonl", out / "batches.json"
    batches = json.loads(batches_path.read_text(encoding="utf-8")) if batches_path.exists() else []
    pending = {cid for b in batches if not b.get("collected") for cid in b["ids"]}
    # Every item of the run, so a batch sent by an earlier call with other options is still collected.
    by_item = {item.id: item for item in items}
    fresh = [custom_id(item.id, rep) for item, rep in todo if custom_id(item.id, rep) not in pending]
    for start in range(0, len(fresh), BATCH_SIZE):
        chunk = fresh[start : start + BATCH_SIZE]
        batch = client.messages.batches.create(
            requests=[
                {"custom_id": cid, "params": request_params(by_item[split_custom_id(cid)[0]], rubrics)} for cid in chunk
            ]
        )
        batches.append(
            {"id": batch.id, "ids": chunk, "rubric": rubric, "created": datetime.now().isoformat(timespec="seconds")}
        )
        batches_path.write_text(json.dumps(batches, indent=2), encoding="utf-8")
        print(f"batch {batch.id}: {len(chunk)} requests")
    while True:
        open_batches = [b for b in batches if not b.get("collected")]
        if not open_batches:
            break
        for entry in open_batches:
            batch = client.messages.batches.retrieve(entry["id"])
            if batch.processing_status != "ended":
                continue
            rows = []
            for result in client.messages.batches.results(entry["id"]):
                item_id, rep = split_custom_id(result.custom_id)
                item = by_item.get(item_id)
                if result.result.type == "succeeded" and item is not None:
                    rows.append(judgment_row(item, rep, result.result.message, True, entry["rubric"]))
                else:
                    print(f"{result.custom_id}: {result.result.type}; it is sent again next time")
            append_jsonl(path, rows)
            entry["collected"] = True
            batches_path.write_text(json.dumps(batches, indent=2), encoding="utf-8")
            print(f"batch {entry['id']}: {len(rows)} verdicts, about ${sum(r['cost'] for r in rows):.2f}")
        if not wait or all(b.get("collected") for b in batches):
            break
        counts = ", ".join(
            f"{b['id'][-8:]}: {client.messages.batches.retrieve(b['id']).request_counts.processing} left"
            for b in batches
            if not b.get("collected")
        )
        print(f"waiting ({counts})")
        time.sleep(30)


def judge(args: argparse.Namespace) -> None:
    if not os.environ.get("ANTHROPIC_API_KEY"):
        sys.exit("ANTHROPIC_API_KEY is not set; run through `uv run --env-file .env` with it in server/.env")
    out = run_dir(args.run)
    rubrics = load_rubrics()
    rubric = sha(RUBRICS)
    every = items_of(out, rubrics)
    items = [item for item in every if not args.kinds or item.kind in args.kinds]
    reps = [0]
    if args.repeat > 1 or args.sample:
        sample = json.loads((out / "sample.json").read_text(encoding="utf-8"))
        wanted = {entry["id"] for entry in sample["items"]}
        items = [item for item in items if item.id in wanted]
        reps = list(range(args.repeat))
    path = out / "judgments.jsonl"
    done = {(row["id"], row["rep"]) for row in read_jsonl(path) if "verdicts" in row and row["rubric"] == rubric}
    todo = [(item, rep) for item in items for rep in reps if (item.id, rep) not in done]
    if args.limit:
        todo = todo[: args.limit]
    print(f"{len(todo)} judgments to make with rubric {rubric} ({len(done)} already made)")
    if not todo:
        return
    if args.now:
        asyncio.run(judge_now(todo, rubrics, rubric, path))
    else:
        judge_batch(todo, every, rubrics, rubric, out, wait=not args.no_wait)


# ---------------------------------------------------------------------------------------------------------------------
# report: the metrics of 3.1


def wilson(hits: int, total: int) -> tuple[float, float]:
    if total == 0:
        return (math.nan, math.nan)
    z, p = 1.96, hits / total
    centre = (p + z * z / (2 * total)) / (1 + z * z / total)
    half = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / (1 + z * z / total)
    return (max(0.0, centre - half), min(1.0, centre + half))


def share(hits: int, total: int) -> str:
    if total == 0:
        return "—"
    low, high = wilson(hits, total)
    return f"{100 * hits / total:5.1f} % ({hits}/{total}; 95 % {100 * low:.0f}–{100 * high:.0f} %)"


def latest_verdicts(out: Path, rep: int = 0, rubric: str | None = None) -> dict[str, dict]:
    """Each item's latest verdicts at `rep`, judged with the current rubric or the one whose hash is `rubric`."""
    rubric = rubric or sha(RUBRICS)
    verdicts = {}
    for row in read_jsonl(out / "judgments.jsonl"):
        if row.get("rep") == rep and row.get("rubric") == rubric and "verdicts" in row:
            verdicts[row["id"]] = row
    return verdicts


def load_sets(out: Path) -> list[dict]:
    return [json.loads(p.read_text(encoding="utf-8")) for p in sorted((out / "sets").glob("*/set.json"))]


def percentile(values: list[float], q: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, math.ceil(q * len(ordered)) - 1))]


def metrics(sets: list[dict], verdicts: dict[str, dict]) -> list[tuple[str, str]]:
    lines: list[tuple[str, str]] = []
    candidates = [(s, c) for s in sets for c in s["candidates"]]
    made = [(s, c) for s, c in candidates if c["ok"]]
    judged = [
        (s, c, verdicts[f"pose-{s['case']}-{s['mode'][0]}-{c['index']}"]["verdicts"])
        for s, c in made
        if f"pose-{s['case']}-{s['mode'][0]}-{c['index']}" in verdicts
    ]

    lines.append(("Pose generation", ""))
    lines.append(("  candidates made", share(len(made), len(candidates))))
    failures: dict[str, int] = {}
    for _, c in candidates:
        if not c["ok"]:
            failures[c.get("code", "?")] = failures.get(c.get("code", "?"), 0) + 1
    if failures:
        lines.append(("  failed requests", ", ".join(f"{code} {n}" for code, n in sorted(failures.items()))))
    lines.append(("  valid JPEG in the scene's ratio", share(sum(bool(c.get("valid")) for _, c in made), len(made))))
    lines.append(("  judged", f"{len(judged)} of {len(made)}"))
    for group, keep in (
        ("all", lambda s: True),
        ("described", lambda s: s["mode"] == "described"),
        ("suggested", lambda s: s["mode"] == "suggested"),
        ("selfies", lambda s: s["selfie"]),
    ):
        rows = [v for s, _, v in judged if keep(s)]
        broke = sum(any(v.get(rule) == "fail" for rule in KEEP_RULES) for v in rows)
        lines.append((f"  break any rule · {group}", share(broke, len(rows))))
    for rule in KEEP_RULES:
        rows = [v for _, _, v in judged if rule in v]
        lines.append((f"    {RULE_NAMES[rule]}", share(sum(v[rule] == "fail" for v in rows), len(rows))))
    for group, mode in (("the user's description", "described"), ("the suggested pose", "suggested")):
        rows = [v for s, _, v in judged if s["mode"] == mode]
        lines.append((f"  follow {group}", share(sum(v["follows_request"] == "pass" for v in rows), len(rows))))
    complete = [s["all_seconds"] for s in sets if s["all_seconds"] is not None]
    firsts = [s["first_seconds"] for s in sets if s["first_seconds"] is not None]
    if complete:
        lines.append(
            (
                "  all four ready, s",
                f"median {statistics.median(complete):.1f} · p90 {percentile(complete, 0.9):.1f} · max {max(complete):.1f} ({len(complete)} complete sets of {len(sets)})",
            )
        )
        lines.append(("  all four within 30 s (NFR-4)", share(sum(t <= 30 for t in complete), len(sets))))
    if firsts:
        lines.append(("  first within 20 s (NFR-4)", share(sum(t <= 20 for t in firsts), len(sets))))

    lines.append(("Pose suggestions", ""))
    for group, mode in (
        ("suggested sets, all four different", "suggested"),
        ("described sets with variations, all four different", "described"),
    ):
        rows = [
            verdicts[f"suggestions-{s['case']}-{mode[0]}"]["verdicts"]
            for s in sets
            if s["mode"] == mode and f"suggestions-{s['case']}-{mode[0]}" in verdicts
        ]
        lines.append((f"  {group}", share(sum(v["all_different"] == "pass" for v in rows), len(rows))))
    defaults = [s for s in sets if s["mode"] == "suggested" and s["suggestions"].get("status") != 200]
    lines.append(
        ("  suggestion requests that failed", share(len(defaults), sum(s["mode"] == "suggested" for s in sets)))
    )

    lines.append(("Pose tips", ""))
    tips = [s["tip"] for s in sets if s.get("tip")]
    answered = [t for t in tips if t.get("text")]
    lines.append(("  tips answered", share(len(answered), len(tips))))
    rows = [
        verdicts[f"tip-{s['case']}-{s['mode'][0]}"]["verdicts"]
        for s in sets
        if f"tip-{s['case']}-{s['mode'][0]}" in verdicts
    ]
    lines.append(("  match their pose", share(sum(v["matches_pose"] == "pass" for v in rows), len(rows))))
    lines.append(
        ("  with numbers or units (should be 0)", share(sum(bool(t.get("measure")) for t in answered), len(answered)))
    )
    return lines


def report(args: argparse.Namespace) -> None:
    runs = [run_dir(name) for name in args.runs]
    sections = []
    for out in runs:
        sections.append((out.name, metrics(load_sets(out), latest_verdicts(out))))
    if len(runs) > 1:
        sections.append(("all runs", pooled_metrics(runs)))
    text = []
    for name, lines in sections:
        text.append(f"== {name}")
        text.extend(f"{label:<52} {value}" for label, value in lines)
        text.append("")
    summary = "\n".join(text)
    print(summary)
    target = runs[0] if len(runs) == 1 else OUT / f"report-{'-'.join(r.name for r in runs)}"
    target.mkdir(parents=True, exist_ok=True)
    (target / "summary.txt").write_text(summary, encoding="utf-8")
    meta = [json.loads((r / "run.json").read_text(encoding="utf-8")) for r in runs if (r / "run.json").exists()]
    (target / "summary.json").write_text(
        json.dumps({"runs": [r.name for r in runs], "meta": meta, "sections": sections}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    write_report_html(target, runs)
    print(f"report: {target / 'report.html'}")


def pooled_metrics(runs: list[Path]) -> list[tuple[str, str]]:
    """Every run's sets and verdicts together, each case named after its run so the keys stay apart."""
    sets, verdicts = [], {}
    for out in runs:
        for s in load_sets(out):
            sets.append({**s, "case": f"{out.name}.{s['case']}"})
        for key, row in latest_verdicts(out).items():
            kind, case, rest = key.split("-", 2)
            verdicts[f"{kind}-{out.name}.{case}-{rest}"] = row
    return metrics(sets, verdicts)


def thumbnail(path: Path, long_side: int = 360) -> str:
    with Image.open(path) as image:
        return "data:image/jpeg;base64," + base64.b64encode(jpeg(image, long_side)).decode()


def write_report_html(target: Path, runs: list[Path]) -> None:
    """Every candidate with its verdicts, the failures first, to read what the judge saw."""
    rows = []
    for out in runs:
        verdicts = latest_verdicts(out)
        for s in load_sets(out):
            folder = out / "sets" / f"{s['case']}-{s['mode']}"
            key = f"{s['case']}-{s['mode'][0]}"
            cells = []
            for c in s["candidates"]:
                row = verdicts.get(f"pose-{key}-{c['index']}")
                asked = html.escape(c["pose"])
                if not c["ok"]:
                    code = html.escape(c.get("code", "?"))
                    cells.append(f"<div class=c><div class=fail>{code}</div><p>{asked}</p></div>")
                    continue
                notes = ""
                if row:
                    for rule, verdict in row["verdicts"].items():
                        reason = html.escape(row["answer"][rule]["reason"])
                        notes += f"<li class={verdict}><b>{RULE_NAMES[rule]}</b>: {reason}</li>"
                bad = bool(row) and any(v == "fail" for r, v in row["verdicts"].items() if r in KEEP_RULES)
                image = thumbnail(folder / f"c{c['index']}.jpg")
                frame = "c bad" if bad else "c"
                at = c["at"]
                cells.append(
                    f"<div class='{frame}'><img src='{image}'><p>{asked} · {at:.1f} s</p><ul>{notes}</ul></div>"
                )
            extra = ""
            sug = verdicts.get(f"suggestions-{key}")
            if sug:
                extra += f"<p class={sug['verdicts']['all_different']}>All four different: {html.escape(sug['answer']['all_different']['reason'])}</p>"
            tip_record = s.get("tip") or {}
            tip_row = verdicts.get(f"tip-{key}")
            if tip_record:
                said = tip_record.get("text") or tip_record.get("code", "")
                judged = f" — {html.escape(tip_row['answer']['matches_pose']['reason'])}" if tip_row else ""
                cls = tip_row["verdicts"]["matches_pose"] if tip_row else ""
                extra += f"<p class={cls}>Tip for {tip_record.get('candidate')}: {html.escape(said)}{judged}</p>"
            bad_set = any("c bad" in cell for cell in cells)
            title = [out.name, s["case"], s["mode"]]
            if s["selfie"]:
                title.append("selfie")
            if s["description"]:
                title.append(html.escape(s["description"]))
            if s["all_seconds"] is not None:
                title.append(f"all four {s['all_seconds']:.1f} s")
            scene = thumbnail(folder / "scene.jpg")
            section = (
                f"<section><h2>{' · '.join(title)}</h2><div class=row><div class=c><img src='{scene}'><p>scene</p></div>"
                f"{''.join(cells)}</div>{extra}</section>"
            )
            rows.append((not bad_set, f"{out.name}/{key}", section))
    rows.sort()
    page = (
        "<!doctype html><meta charset=utf-8><title>Pix AI module tests</title><style>"
        "body{font:14px/1.4 -apple-system,system-ui,sans-serif;margin:24px;background:#fff;color:#16171a}"
        "h2{font-size:15px;margin:28px 0 8px}.row{display:flex;gap:10px;align-items:flex-start}"
        ".c{width:200px}.c img{width:200px;border-radius:6px;border:3px solid transparent}.c.bad img{border-color:#e5383b}"
        ".c p{margin:4px 0;font-size:12px;color:#5f626a}ul{padding-left:14px;margin:4px 0;font-size:11px}"
        "li.fail,p.fail{color:#c2463f}li.pass{color:#3a7d44}.fail{font-weight:700}</style>"
        f"<h1>Pix AI module tests · {', '.join(r.name for r in runs)}</h1><p>Sets with a broken rule first; a red frame marks a candidate that breaks one.</p>"
        + "".join(section for _, _, section in rows)
    )
    (target / "report.html").write_text(page, encoding="utf-8")


# ---------------------------------------------------------------------------------------------------------------------
# sample, agree, and the consistency check: how far to trust the judge


def sample(args: argparse.Namespace) -> None:
    out = run_dir(args.run)
    verdicts = latest_verdicts(out, rubric=args.by_rubric)
    pose_ids = []
    for s in load_sets(out):
        for c in s["candidates"]:
            if c["ok"]:
                pose_ids.append((f"pose-{s['case']}-{s['mode'][0]}-{c['index']}", s, c))
    rng = random.Random(args.seed)
    rng.shuffle(pose_ids)
    if verdicts:
        # Half the candidates the judge failed and half it passed, when it has judged them, so that our labels test
        # both of its answers and not mostly easy passes.
        failed = [
            p
            for p in pose_ids
            if p[0] in verdicts and any(v == "fail" for r, v in verdicts[p[0]]["verdicts"].items() if r in KEEP_RULES)
        ]
        passed = [p for p in pose_ids if p[0] in verdicts and p not in failed]
        half = min(len(failed), args.size // 2)
        chosen = failed[:half] + passed[: args.size - half]
    else:
        chosen = pose_ids[: args.size]
    rng.shuffle(chosen)
    rubrics = load_rubrics()
    entries = []
    for item_id, s, c in chosen:
        folder = out / "sets" / f"{s['case']}-{s['mode']}"
        asked = s["description"] if s["mode"] == "described" else c["pose"]
        rules = [r for r in KEEP_RULES if r != "selfie_stays_selfie" or s["selfie"]] + ["follows_request"]
        entries.append(
            {
                "id": item_id,
                "request": asked,
                "selfie": s["selfie"],
                "rules": rules,
                "scene": thumbnail(folder / "scene.jpg", 640),
                "candidate": thumbnail(folder / f"c{c['index']}.jpg", 640),
            }
        )
    (out / "sample.json").write_text(
        json.dumps({"seed": args.seed, "items": [{"id": e["id"]} for e in entries]}, indent=2), encoding="utf-8"
    )
    rules_html = "".join(
        f"<dt>{RULE_NAMES[r]}</dt><dd>{html.escape(rubrics['pose'][r])}</dd>" for r in (*KEEP_RULES, "follows_request")
    )
    page = (
        LABEL_PAGE.replace("{{ITEMS}}", json.dumps(entries).replace("</", "<\\/"))
        .replace("{{NAMES}}", json.dumps(RULE_NAMES))
        .replace("{{RULES}}", rules_html)
        .replace("{{RUN}}", html.escape(out.name))
    )
    (out / "label.html").write_text(page, encoding="utf-8")
    print(
        f"{len(entries)} candidates in {out / 'label.html'}; each labeler opens it, labels every rule, and downloads the labels"
    )


LABEL_PAGE = """<!doctype html><meta charset=utf-8><title>Label poses</title><style>
body{font:15px/1.45 -apple-system,system-ui,sans-serif;margin:24px auto;max-width:1100px;padding:0 16px;color:#16171a;background:#fff}
.item{border-top:1px solid #ddd;padding:18px 0}.photos{display:flex;gap:12px}.photos img{width:48%;max-width:420px;border-radius:6px}
.rule{display:flex;gap:10px;align-items:center;margin:6px 0}.rule span{width:190px}button{font:inherit;padding:4px 14px;border:1px solid #bbb;border-radius:6px;background:#fff;cursor:pointer}
button.on.pass{background:#3a7d44;color:#fff;border-color:#3a7d44}button.on.fail{background:#c2463f;color:#fff;border-color:#c2463f}
#bar{position:sticky;top:0;background:#fff;padding:10px 0;border-bottom:1px solid #ddd;display:flex;gap:12px;align-items:center;z-index:1}
dt{font-weight:700;margin-top:8px}dd{margin:2px 0 0 0;color:#444}</style>
<h1>Label poses · {{RUN}}</h1>
<p>For each edit, answer every rule pass or fail on your own, without looking at anyone's answers. Photo 1 is the original, photo 2 the AI's edit. The rules:</p>
<details><summary>The rules in full (the same text the judge reads)</summary><dl>{{RULES}}</dl></details>
<div id=bar><label>Your name <input id=who size=12></label><span id=count></span><button id=save>Download labels</button></div>
<div id=items></div>
<script>
const ITEMS={{ITEMS}}, NAMES={{NAMES}}, KEY="pix-labels-{{RUN}}";
let labels={}; try{labels=JSON.parse(localStorage.getItem(KEY)||"{}")}catch(e){}
const who=document.getElementById("who"); try{who.value=localStorage.getItem(KEY+"-who")||""}catch(e){}
who.oninput=()=>{try{localStorage.setItem(KEY+"-who",who.value)}catch(e){}};
function count(){let need=0,done=0;for(const it of ITEMS)for(const r of it.rules){need++;if(labels[it.id]&&labels[it.id][r])done++}
document.getElementById("count").textContent=done+" / "+need+" answered"}
const box=document.getElementById("items");
ITEMS.forEach((it,i)=>{const d=document.createElement("div");d.className="item";
d.innerHTML=`<h3>${i+1}. ${it.selfie?"Selfie · ":""}Request: “${it.request.replace(/</g,"&lt;")}”</h3><div class=photos><img src="${it.scene}"><img src="${it.candidate}"></div>`;
for(const r of it.rules){const row=document.createElement("div");row.className="rule";row.innerHTML=`<span>${NAMES[r]}</span>`;
for(const v of ["pass","fail"]){const b=document.createElement("button");b.textContent=v;b.className=v+((labels[it.id]||{})[r]===v?" on":"");
b.onclick=()=>{labels[it.id]=labels[it.id]||{};labels[it.id][r]=v;row.querySelectorAll("button").forEach(x=>x.classList.toggle("on",x===b));
try{localStorage.setItem(KEY,JSON.stringify(labels))}catch(e){}count()};row.appendChild(b)}d.appendChild(row)}box.appendChild(d)});
count();
document.getElementById("save").onclick=()=>{const blob=new Blob([JSON.stringify({labeler:who.value,run:"{{RUN}}",labels},null,2)],{type:"application/json"});
const a=document.createElement("a");a.href=URL.createObjectURL(blob);a.download="labels-"+(who.value||"me")+".json";a.click()};
</script>"""


def agree(args: argparse.Namespace) -> None:
    out = run_dir(args.run)
    ids = [entry["id"] for entry in json.loads((out / "sample.json").read_text(encoding="utf-8"))["items"]]
    people = [json.loads(Path(p).read_text(encoding="utf-8")) for p in args.labels]
    claude = latest_verdicts(out)
    rules = (*KEEP_RULES, "follows_request")
    lines = [f"{len(ids)} candidates; labelers: {', '.join(p.get('labeler') or '?' for p in people)}"]

    def pairs(a, b) -> dict[str, tuple[int, int]]:
        counts = {rule: [0, 0] for rule in rules}
        for item_id in ids:
            for rule in rules:
                x, y = a(item_id, rule), b(item_id, rule)
                if x and y:
                    counts[rule][0] += x == y
                    counts[rule][1] += 1
        return counts

    def person(index: int):
        return lambda item_id, rule: people[index]["labels"].get(item_id, {}).get(rule)

    def judge_of(item_id: str, rule: str):
        return (claude.get(item_id) or {}).get("verdicts", {}).get(rule)

    def consensus(item_id: str, rule: str):
        answers = {p["labels"].get(item_id, {}).get(rule) for p in people}
        return answers.pop() if len(answers) == 1 and None not in answers else None

    def majority(item_id: str, rule: str):
        answers = [p["labels"].get(item_id, {}).get(rule) for p in people]
        for answer in ("pass", "fail"):
            if answers.count(answer) * 2 > len(people):
                return answer
        return None

    def show(title: str, counts: dict[str, tuple[int, int]]) -> None:
        hits, total = sum(c[0] for c in counts.values()), sum(c[1] for c in counts.values())
        lines.append(f"{title:<40} {share(hits, total)}")
        for rule in rules:
            if counts[rule][1]:
                lines.append(f"    {RULE_NAMES[rule]:<36} {share(*counts[rule])}")

    for first, second in itertools.combinations(range(len(people)), 2):
        show(f"person {first + 1} vs person {second + 1}", pairs(person(first), person(second)))
    show("Claude vs our consensus (target ≥ 85 %)", pairs(judge_of, consensus))
    if len(people) > 2:
        show("Claude vs our majority", pairs(judge_of, majority))
    for index in range(len(people)):
        show(f"Claude vs person {index + 1}", pairs(judge_of, person(index)))
    # The overall answer each candidate gets: does it break any rule?
    broke = lambda verdict: None if verdict is None else any(verdict.get(r) == "fail" for r in KEEP_RULES)  # noqa: E731
    hits = total = 0
    for item_id in ids:
        ours = {broke(p["labels"].get(item_id)) for p in people}
        theirs = broke((claude.get(item_id) or {}).get("verdicts"))
        if len(ours) == 1 and None not in ours and theirs is not None:
            hits += ours.pop() == theirs
            total += 1
    lines.append(f"{'breaks any rule: Claude vs consensus':<40} {share(hits, total)}")
    # Consistency: the same candidates judged again (judge --repeat).
    reps = [latest_verdicts(out, rep) for rep in range(args.repeat)]
    if args.repeat > 1 and all(reps):
        changed = total = 0
        flips = 0
        for item_id in ids:
            seen = [r[item_id]["verdicts"] for r in reps if item_id in r]
            if len(seen) < args.repeat:
                continue
            for rule in seen[0]:
                total += 1
                changed += len({v.get(rule) for v in seen}) > 1
            flips += len({broke(v) for v in seen}) > 1
        lines.append(f"{'verdicts that changed across ' + str(args.repeat) + ' judgments':<40} {share(changed, total)}")
        lines.append(f"{'candidates whose breaks-any-rule flipped':<40} {share(flips, len(ids))}")
    text = "\n".join(lines)
    print(text)
    (out / "agreement.txt").write_text(text + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------------------------------------------------
# person: the person check of the phone and the server together


def person(args: argparse.Namespace) -> None:
    """The app's person check on labeled photos: the phone's (PersonCheckEval's phone.json) and the server's (Jev's
    results.json from try_person_check.py), alone and as the app runs them, where a photo goes on only when both find
    a person and the server's check lets a photo through when it gives no answer."""
    labels = {Path(entry["file"]).name: entry for entry in json.loads(Path(args.labels).read_text(encoding="utf-8"))}
    phone = {r["scene"]: r["found"] for r in json.loads(Path(args.phone).read_text(encoding="utf-8"))["results"]}
    jev: dict[str, bool] = {}
    for result in json.loads(Path(args.jev).read_text(encoding="utf-8"))["results"]:
        probability = result.get("probability")
        jev[result["scene"]] = probability is None or probability >= args.cutoff
    missing = sorted(name for name in labels if name not in phone or name not in jev)
    if missing:
        sys.exit(f"no answer from the phone or the server for {', '.join(missing)}")
    checks = {
        "phone (ML Kit)": phone,
        "server (Jev)": jev,
        "both, as the app": {name: phone[name] and jev[name] for name in labels},
    }
    lines = [f"{len(labels)} photos; server cutoff {args.cutoff}"]
    for title, found in checks.items():
        people = [name for name, entry in labels.items() if entry["label"] == "with"]
        empty = [name for name, entry in labels.items() if entry["label"] == "without"]
        refused = [name for name in people if not found[name]]
        passed = [name for name in empty if found[name]]
        lines.append(f"{title}")
        lines.append(f"  with a person, rejected     {share(len(refused), len(people))}  {' '.join(refused)}")
        lines.append(f"  without a person, passed    {share(len(passed), len(empty))}  {' '.join(passed)}")
    categories = sorted({entry["category"] for entry in labels.values()})
    lines.append("by category, as the app (found / photos)")
    for category in categories:
        names = [name for name, entry in labels.items() if entry["category"] == category]
        found = sum(checks["both, as the app"][name] for name in names)
        lines.append(f"  {labels[names[0]]['label']:<8} {category:<12} {found}/{len(names)}")
    text = "\n".join(lines)
    print(text)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------------------------------------------------


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    g = commands.add_parser("generate", help="make the sets through a Pix server")
    g.add_argument("--server", default="http://127.0.0.1:8765")
    g.add_argument("--run", required=True, help="the run's name; its folder is scripts/out/eval/<run>/")
    g.add_argument("--cases", default=str(CASES))
    g.add_argument("--modes", nargs="+", choices=MODES, default=list(MODES))
    g.add_argument("--only", nargs="+", help="case ids")
    g.add_argument("--limit", type=int, help="only the first N cases, for a pilot")
    g.add_argument("--sets-at-once", type=int, default=1)
    g.add_argument(
        "--situation",
        choices=[name for name in SITUATIONS if name != "selfie"],
        help="send every case but the selfies with this situation instead of its own",
    )

    j = commands.add_parser("judge", help="have Claude judge a run")
    j.add_argument("run")
    j.add_argument("--now", action="store_true", help="direct calls at full price instead of the Batch API")
    j.add_argument("--no-wait", action="store_true", help="send the batches and stop; run again to collect")
    j.add_argument("--kinds", nargs="+", choices=("pose", "suggestions", "tip"))
    j.add_argument("--repeat", type=int, default=1, help="judge the sample of `sample` this many times in all")
    j.add_argument("--sample", action="store_true", help="only the sample of `sample`, to try a changed rubric on it")
    j.add_argument("--limit", type=int, help="at most N judgments, for a pilot")

    r = commands.add_parser("report", help="the metrics of one or more runs")
    r.add_argument("runs", nargs="+")

    s = commands.add_parser("sample", help="candidates for us to label, in label.html")
    s.add_argument("run")
    s.add_argument("--size", type=int, default=60)
    s.add_argument("--seed", type=int, default=3)
    s.add_argument("--by-rubric", metavar="HASH", help="split failed and passed by an earlier rubric's verdicts")

    a = commands.add_parser("agree", help="our labels against Claude's verdicts")
    a.add_argument("run")
    a.add_argument("--labels", nargs="+", required=True)
    a.add_argument("--repeat", type=int, default=3, help="also count changes across this many judgments")

    p = commands.add_parser("person", help="the phone's and the server's person checks on labeled photos")
    p.add_argument("--labels", default=str(SERVER / "eval" / "person" / "labels.json"))
    p.add_argument("--phone", required=True, help="phone.json from PersonCheckEval")
    p.add_argument("--jev", required=True, help="results.json from try_person_check.py")
    p.add_argument("--cutoff", type=float, default=0.5, help="the server's PIX_PERSON_CUTOFF")
    p.add_argument("--out", help="also write the summary here")

    args = parser.parse_args()
    if args.command == "generate":
        asyncio.run(generate(args))
    elif args.command == "judge":
        judge(args)
    elif args.command == "report":
        report(args)
    elif args.command == "sample":
        sample(args)
    elif args.command == "person":
        person(args)
    else:
        agree(args)


if __name__ == "__main__":
    main()
