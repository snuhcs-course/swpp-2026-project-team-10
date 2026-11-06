# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
"""Try the person check on labeled scene photos (FR-4.13, Design Documentation 2.6.3).

It does not import pix_server. Run it through uv from server/ with OPENROUTER_API_KEY in server/.env:

    uv run --env-file .env scripts/try_person_check.py --with-person people/ --without-person empty/
    uv run --env-file .env scripts/try_person_check.py --with-person a.jpg b.jpg --rounds 2 --cutoff 0.4
    uv run --env-file .env scripts/try_person_check.py --without-person empty/ --model typesafe/jev-1.13

Every scene (a JPEG or PNG file, or a folder of them) is prepared the way the app sends it (upright, long side at most
1024 px, JPEG 85, no EXIF) and goes to the decision model through OpenRouter's Decisions API with the person check's
question, once per round, in the request the server sends. The question is the [person] table of the server's
prompts.toml, so a run tries what the server asks; --prompts takes a copy with other wording. The server refuses a
scene whose probability is below its cutoff, PIX_PERSON_CUTOFF. The run prints each scene's probabilities, then, at
--cutoff, how many scenes with a person would be refused and how many without one would go through, the time, and the
cost. results.json goes to scripts/out/person/<timestamp>[-label]/. Every call is billed to the OpenRouter key, about
$0.0001 a scene with Jev 1.14 in November 2026.
"""

import argparse
import asyncio
import base64
import io
import json
import os
import statistics
import sys
import time
import tomllib
from dataclasses import asdict, dataclass, field
from datetime import datetime
from pathlib import Path

import httpx2
from PIL import Image, ImageOps

API_URL = "https://openrouter.ai/api/alpha/decisions"
PROMPTS_FILE = Path(__file__).resolve().parents[1] / "src" / "pix_server" / "poses" / "prompts.toml"
QUESTION = "person"  # the name the answer comes back under, as the server asks it
SCENE_LONG_SIDE = 1024
SCENE_JPEG_QUALITY = 85
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}


@dataclass
class Scene:
    path: Path
    person: bool  # the label: the scene shows a person
    data: str = ""  # the JPEG as the app sends it, base64


@dataclass
class Result:
    scene: str
    person: bool
    round: int
    status: int | None = None
    probability: float | None = None
    served_model: str | None = None
    cost: float | None = None
    seconds: float = 0.0
    error: str | None = None


@dataclass
class Summary:
    refused_people: list[str] = field(default_factory=list)
    passed_empty: list[str] = field(default_factory=list)


def load_question(path: Path) -> dict[str, dict]:
    """The [person] table of a prompts file, as the server sends it: one noul question, its text on one line."""
    with path.open("rb") as file:
        table = tomllib.load(file)["person"]
    return {QUESTION: {"type": "noul", "instructions": " ".join(table["question"].split())}}


def find_scenes(paths: list[Path], person: bool) -> list[Scene]:
    scenes = []
    for path in paths:
        if path.is_dir():
            files = sorted(file for file in path.iterdir() if file.suffix.lower() in IMAGE_SUFFIXES)
        elif path.is_file():
            files = [path]
        else:
            sys.exit(f"{path} is neither a file nor a folder.")
        scenes += [Scene(file, person) for file in files]
    return scenes


def prepare(path: Path) -> str:
    """The scene as the app sends it, base64: upright, long side at most 1024 px, JPEG 85, no EXIF."""
    with Image.open(path) as image:
        scene = ImageOps.exif_transpose(image).convert("RGB")
    scene.thumbnail((SCENE_LONG_SIDE, SCENE_LONG_SIDE))
    buffer = io.BytesIO()
    scene.save(buffer, "JPEG", quality=SCENE_JPEG_QUALITY)
    return base64.b64encode(buffer.getvalue()).decode()


async def ask(
    client: httpx2.AsyncClient,
    limit: asyncio.Semaphore,
    question: dict[str, dict],
    model: str,
    scene: Scene,
    number: int,
    timeout: float,
) -> Result:
    result = Result(scene.path.name, scene.person, number)
    photo = {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + scene.data}}
    body = {"model": model, "state": {"photo": photo}, "questions": question}
    async with limit:
        start = time.perf_counter()
        try:
            async with asyncio.timeout(timeout):
                response = await client.post(API_URL, json=body)
        except TimeoutError:
            result.error = f"no response within {timeout:g} s"
        except httpx2.HTTPError as error:
            result.error = f"{type(error).__name__}: {error}"
        else:
            result.status = response.status_code
            try:
                data = response.json()
                if response.status_code != 200 or "error" in data:
                    result.error = json.dumps(data.get("error") or data, ensure_ascii=False)[:300]
                else:
                    result.probability = float(data["answers"][QUESTION]["noul"])
                    result.served_model = data.get("model")
                    result.cost = (data.get("usage") or {}).get("cost")
            except (ValueError, KeyError, TypeError) as error:
                result.error = f"unreadable response: {error!r} {response.text[:200]}"
        result.seconds = time.perf_counter() - start
    return result


def summarize(scenes: list[Scene], results: list[Result], cutoff: float) -> str:
    by_scene: dict[str, list[Result]] = {}
    for result in results:
        by_scene.setdefault(result.scene, []).append(result)
    lines = []
    summary = Summary()
    for scene in sorted(scenes, key=lambda scene: (not scene.person, scene.path.name)):
        mine = by_scene[scene.path.name]
        answered = [result.probability for result in mine if result.probability is not None]
        label = "person" if scene.person else "none"
        if not answered:
            lines.append(f"{label:7} {scene.path.name:28} no answer: {mine[0].error}")
            continue
        spread = f"{min(answered):.2f}" + (f"-{max(answered):.2f}" if max(answered) != min(answered) else "")
        # A scene counts as refused when any round refused it, and as passed when any round let it through.
        if scene.person and min(answered) < cutoff:
            summary.refused_people.append(scene.path.name)
        if not scene.person and max(answered) >= cutoff:
            summary.passed_empty.append(scene.path.name)
        lines.append(f"{label:7} {scene.path.name:28} {spread}")
    people = sum(scene.person for scene in scenes)
    empty = len(scenes) - people
    seconds = [result.seconds for result in results if result.probability is not None]
    failed = [result for result in results if result.probability is None]
    lines += [
        "",
        f"At cutoff {cutoff:g}: {len(summary.refused_people)} of {people} scenes with a person refused"
        + (f" ({', '.join(summary.refused_people)})" if summary.refused_people else "")
        + f"; {len(summary.passed_empty)} of {empty} scenes without one let through"
        + (f" ({', '.join(summary.passed_empty)})" if summary.passed_empty else "")
        + ".",
    ]
    if seconds:
        lines.append(f"Time per call: {statistics.median(seconds):.2f} s median, {max(seconds):.2f} s at most.")
    if failed:
        lines.append(f"{len(failed)} calls without an answer (the server lets such a scene through).")
    return "\n".join(lines)


async def run(args: argparse.Namespace) -> None:
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        sys.exit("Set OPENROUTER_API_KEY first.")
    scenes = find_scenes(args.with_person, True) + find_scenes(args.without_person, False)
    if not scenes:
        sys.exit("Name scenes with --with-person or --without-person.")
    names = [scene.path.name for scene in scenes]
    if len(set(names)) != len(names):
        sys.exit("Two scenes have the same file name; rename one.")
    for scene in scenes:
        scene.data = prepare(scene.path)
    question = load_question(args.prompts)
    name = datetime.now().strftime("%Y%m%d-%H%M%S") + (f"-{args.label}" if args.label else "")
    out: Path = args.out or Path(__file__).parent / "out" / "person" / name
    out.mkdir(parents=True, exist_ok=True)

    print(f"{len(scenes)} scenes x {args.rounds} rounds with {args.model} -> {out}")
    async with httpx2.AsyncClient(headers={"Authorization": f"Bearer {key}"}, timeout=httpx2.Timeout(None)) as client:
        limit = asyncio.Semaphore(args.concurrency)
        calls = [
            ask(client, limit, question, args.model, scene, number, args.timeout)
            for number in range(1, args.rounds + 1)
            for scene in scenes
        ]
        results = await asyncio.gather(*calls)

    summary = summarize(scenes, results, args.cutoff)
    record = {"model": args.model, "question": question, "results": [asdict(result) for result in results]}
    (out / "results.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    total = sum(result.cost or 0 for result in results)
    print(f"\n{summary}\n\nTotal cost ${total:.4f}. Results: {out / 'results.json'}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--with-person", nargs="+", type=Path, default=[], metavar="PATH", help="scenes with a person")
    parser.add_argument("--without-person", nargs="+", type=Path, default=[], metavar="PATH", help="scenes without")
    parser.add_argument("--model", default="typesafe/jev-1.14", help="decision model (default: the server's)")
    parser.add_argument("--prompts", type=Path, default=PROMPTS_FILE, help="prompts file with the [person] question")
    parser.add_argument("--rounds", type=int, default=1, help="calls per scene, to see the spread")
    parser.add_argument("--cutoff", type=float, default=0.5, help="refused below this (default: the server's)")
    parser.add_argument("--concurrency", type=int, default=8, help="calls in flight at once")
    parser.add_argument("--timeout", type=float, default=30.0, help="give up on a call after this many seconds")
    parser.add_argument("--label", help="added to the output directory's name")
    parser.add_argument("--out", type=Path, help="output directory (default: scripts/out/person/<timestamp>[-label])")
    asyncio.run(run(parser.parse_args()))


if __name__ == "__main__":
    main()
