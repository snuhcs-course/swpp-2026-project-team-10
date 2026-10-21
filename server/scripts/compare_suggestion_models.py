# AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
"""Compare vision models on OpenRouter for pose suggestions (Design Documentation 2.6.3).

It does not import pix_server. Run it through uv from server/ with OPENROUTER_API_KEY set:

    uv run scripts/compare_suggestion_models.py scene1.jpg scene2.jpg
    uv run scripts/compare_suggestion_models.py scene.jpg --models google/gemini-2.5-flash-lite openai/gpt-6-luna
    uv run scripts/compare_suggestion_models.py scene.jpg --rounds 3
    uv run scripts/compare_suggestion_models.py scene.jpg --description "벽에 기대서 카메라 보며 살짝 웃기"

Each scene is prepared the way the app sends it (upright, long side 1024 px, JPEG 85, no EXIF) and sent with the
server's suggestion prompt from src/pix_server/poses/prompts.toml to every model at once, through OpenRouter's chat
completions, once per round; with --description, the server's variation prompt for that description instead. An answer
counts as usable when the server would use all of it: four different poses of at most 100 characters, or three such
variations besides the description itself, which the server leaves out when a model repeats it. results.json and report.html (each scene with every model's poses
and times) go to scripts/out/<timestamp>/. Every call is billed to the OpenRouter key, at a fraction of a cent.
"""

import argparse
import asyncio
import base64
import html
import json
import os
import re
import statistics
import sys
import time
import tomllib
from dataclasses import asdict, dataclass, field
from datetime import datetime
from pathlib import Path
from urllib.parse import quote

import httpx2
from PIL import Image, ImageOps

API_URL = "https://openrouter.ai/api/v1/chat/completions"
MODELS_URL = "https://openrouter.ai/api/v1/models"

# Cheap and fast vision models screened on 2026-10-21; any OpenRouter model that takes an image works. The server's
# default (PIX_SUGGESTION_MODEL) is google/gemini-3.1-flash-lite.
DEFAULT_MODELS = [
    "google/gemini-2.5-flash-lite",
    "google/gemini-3.1-flash-lite",
    "google/gemini-3.5-flash-lite",
    "openai/gpt-6-luna",
    "anthropic/claude-haiku-5.5",
    "qwen/qwen3.7-flash",
    "qwen/qwen3.8-flash",
    "z-ai/glm-5.3-flash",
    "bytedance-seed/seed-2.0-mini",
    "mistralai/mistral-small-2603",
]

PROMPTS_FILE = Path(__file__).resolve().parents[1] / "src" / "pix_server" / "poses" / "prompts.toml"
SCENE_LONG_SIDE = 1024
SCENE_JPEG_QUALITY = 85
POSE_COUNT = 4
POSE_MAX_LENGTH = 100  # the server's limit for a description, which a suggestion becomes
MAX_TOKENS = 400
# Models that refused to answer with --reasoning none; they get the lowest effort from then on.
MUST_REASON: set[str] = set()
SCHEMA = {
    "type": "object",
    "properties": {"poses": {"type": "array", "items": {"type": "string"}}},
    "required": ["poses"],
    "additionalProperties": False,
}


def one_line(text: str) -> str:
    """Each run of whitespace becomes one space, as the server reads the file."""
    return " ".join(text.split())


def load_prompt(key: str) -> str:
    with PROMPTS_FILE.open("rb") as file:
        return one_line(tomllib.load(file)[key])


def usable_poses(text: str, description: str | None = None) -> list[str] | None:
    """The poses the server would use from this answer, or None when it would not use all of them; the server's
    check, in pix_server/poses. With a description, three variations, after leaving out the description itself."""
    match = re.search(r"\{.*\}", text, re.DOTALL)  # some models wrap the JSON in a code fence
    try:
        poses = json.loads(match.group(0) if match else text)["poses"]
    except (ValueError, TypeError, KeyError):
        return None
    if not isinstance(poses, list) or not all(isinstance(pose, str) for pose in poses):
        return None
    poses = [one_line(pose) for pose in poses]
    count = POSE_COUNT
    if description is not None:
        poses = [pose for pose in poses if pose.casefold() != one_line(description).casefold()]
        count = POSE_COUNT - 1
    lengths_ok = all(0 < len(pose) <= POSE_MAX_LENGTH for pose in poses)
    distinct = len({pose.casefold() for pose in poses}) == len(poses)
    return poses if len(poses) == count and lengths_ok and distinct else None


@dataclass
class Result:
    scene: str
    model: str
    round: int
    seconds: float = 0.0
    status: int | None = None
    error: str | None = None
    cost: float | None = None
    tokens: dict = field(default_factory=dict)
    answer: str | None = None
    poses: list[str] | None = None


def prepare_scene(path: Path) -> Image.Image:
    with Image.open(path) as image:
        scene = ImageOps.exif_transpose(image).convert("RGB")
    scene.thumbnail((SCENE_LONG_SIDE, SCENE_LONG_SIDE))
    return scene


def payload(model: str, supported: list[str], prompt: str, data_url: str, reasoning: str) -> dict:
    body: dict = {
        "model": model,
        "messages": [
            {
                "role": "user",
                "content": [{"type": "text", "text": prompt}, {"type": "image_url", "image_url": {"url": data_url}}],
            }
        ],
        "max_tokens": MAX_TOKENS,
        "usage": {"include": True},
    }
    if "structured_outputs" in supported:
        body["response_format"] = {
            "type": "json_schema",
            "json_schema": {"name": "poses", "strict": True, "schema": SCHEMA},
        }
    elif "response_format" in supported:
        body["response_format"] = {"type": "json_object"}
    if "reasoning" in supported:
        body["reasoning"] = {"effort": "minimal" if model in MUST_REASON else reasoning}
    if "temperature" in supported:
        body["temperature"] = 1.0  # a new set of ideas on every call, for Try other poses
    return body


async def suggest(
    client: httpx2.AsyncClient, scene: str, data_url: str, model: str, supported: list[str], args, number: int
) -> Result:
    result = Result(scene, model, number)
    start = time.perf_counter()
    try:
        async with asyncio.timeout(args.timeout):
            response = await client.post(API_URL, json=payload(model, supported, args.prompt, data_url, args.reasoning))
            if response.status_code == 400 and "Reasoning is mandatory" in response.text:
                # The server would know this from the start, so the time counts only the call that answers.
                MUST_REASON.add(model)
                start = time.perf_counter()
                response = await client.post(
                    API_URL, json=payload(model, supported, args.prompt, data_url, args.reasoning)
                )
    except TimeoutError:
        result.error = f"no response within {args.timeout:g} s"
    except httpx2.HTTPError as error:
        result.error = f"{type(error).__name__}: {error}"
    else:
        result.status = response.status_code
        try:
            body = response.json()
            if response.status_code != 200 or "error" in body:
                result.error = json.dumps(body.get("error") or body, ensure_ascii=False)[:300]
            else:
                result.answer = body["choices"][0]["message"]["content"] or ""
                usage = body.get("usage") or {}
                result.cost = usage.get("cost")
                result.tokens = {key: usage.get(key) for key in ("prompt_tokens", "completion_tokens")}
                result.poses = usable_poses(result.answer, args.description)
                if result.poses is None:
                    result.error = "unusable answer"
        except (ValueError, KeyError, IndexError, TypeError) as error:
            result.error = f"unreadable response: {error!r} {response.text[:200]}"
    result.seconds = time.perf_counter() - start
    print(f"{scene}  {model}  round {number}  {result.seconds:5.1f} s  {result.error or 'ok'}")
    return result


def table(rows: list[dict[str, str]]) -> str:
    widths = {key: max(len(key), *(len(row[key]) for row in rows)) for key in rows[0]}
    lines = [{key: key for key in widths}, *rows]
    return "\n".join("  ".join(line[key].ljust(width) for key, width in widths.items()).rstrip() for line in lines)


def summarize(results: list[Result]) -> list[dict[str, str]]:
    rows = []
    for model in dict.fromkeys(result.model for result in results):
        mine = [result for result in results if result.model == model]
        seconds = sorted(result.seconds for result in mine if result.status == 200)
        costs = [result.cost for result in mine if result.cost is not None]
        rows.append(
            {
                "model": model,
                "usable": f"{sum(result.poses is not None for result in mine)}/{len(mine)}",
                "median": f"{statistics.median(seconds):.1f} s" if seconds else "-",
                "90th pct.": f"{seconds[int(0.9 * (len(seconds) - 1))]:.1f} s" if seconds else "-",
                "slowest": f"{max(seconds):.1f} s" if seconds else "-",
                "$/call": f"{statistics.mean(costs):.5f}" if costs else "-",
            }
        )
    return rows


def write_report(out: Path, prompt: str, results: list[Result], summary: list[dict[str, str]]) -> None:
    def row(cells: list[str], tag: str = "td") -> str:
        return "<tr>" + "".join(f"<{tag}>{cell}</{tag}>" for cell in cells) + "</tr>"

    def cell(result: Result) -> str:
        timing = f"<b>{result.seconds:.1f} s</b>"
        if result.poses is None:
            shown = html.escape(result.answer or "")[:400]
            return f'{timing} <span class="bad">{html.escape(result.error or "")}</span><br><small>{shown}</small>'
        return timing + "<ol>" + "".join(f"<li>{html.escape(pose)}</li>" for pose in result.poses) + "</ol>"

    rounds = sorted({result.round for result in results})
    by_key = {(result.scene, result.model, result.round): result for result in results}
    parts = [
        '<!doctype html><meta charset="utf-8"><title>Pose suggestion comparison</title>',
        "<style>:root{color-scheme:light dark}body{font:14px/1.4 system-ui,sans-serif;margin:24px}"
        "table{border-collapse:collapse;margin:12px 0 32px}th,td{border:1px solid #8886;padding:6px 8px;"
        "text-align:left;vertical-align:top}td{min-width:260px}img{width:200px;display:block}.bad{color:#d33}"
        "ol{margin:4px 0;padding-left:18px}</style>",
        "<h1>Pose suggestion comparison</h1>",
        f"<p>Prompt: {html.escape(prompt)}</p><table>",
        row(list(summary[0]), "th"),
        *(row([html.escape(value) for value in line.values()]) for line in summary),
        "</table>",
    ]
    for scene in dict.fromkeys(result.scene for result in results):
        parts += [f'<h2>{html.escape(scene)}</h2><img src="{quote(scene)}.jpg"><table>']
        parts.append(row(["model", *(f"round {number}" for number in rounds)], "th"))
        for model in dict.fromkeys(result.model for result in results):
            parts.append(row([html.escape(model), *(cell(by_key[scene, model, number]) for number in rounds)]))
        parts.append("</table>")
    (out / "report.html").write_text("\n".join(parts), encoding="utf-8")


async def run(args: argparse.Namespace) -> None:
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        sys.exit("Set OPENROUTER_API_KEY first.")
    out: Path = args.out or Path(__file__).parent / "out" / datetime.now().strftime("%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)

    async with httpx2.AsyncClient(headers={"Authorization": f"Bearer {key}"}, timeout=httpx2.Timeout(None)) as client:
        catalog = {model["id"]: model for model in (await client.get(MODELS_URL)).raise_for_status().json()["data"]}
        for model in args.models:
            if model not in catalog or "image" not in catalog[model]["architecture"]["input_modalities"]:
                sys.exit(f"{model} is not an OpenRouter model that takes an image.")

        scenes = {}
        for number, path in enumerate(args.scenes, 1):
            name = f"{number:02d}-{path.stem}"
            prepare_scene(path).save(out / f"{name}.jpg", "JPEG", quality=SCENE_JPEG_QUALITY)
            scenes[name] = "data:image/jpeg;base64," + base64.b64encode((out / f"{name}.jpg").read_bytes()).decode()

        print(f"{len(scenes)} scenes x {len(args.models)} models x {args.rounds} rounds -> {out}")
        results: list[Result] = []
        for number in range(1, args.rounds + 1):
            for scene, data_url in scenes.items():
                # Every model at once for a scene, so they see the same moment's load.
                calls = (
                    suggest(client, scene, data_url, model, catalog[model]["supported_parameters"], args, number)
                    for model in args.models
                )
                results += await asyncio.gather(*calls)

    summary = summarize(results)
    record = {"prompt": args.prompt, "results": [asdict(result) for result in results]}
    (out / "results.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    write_report(out, args.prompt, results, summary)
    total = sum(result.cost or 0 for result in results)
    print(f"\n{table(summary)}\n\nTotal cost ${total:.4f}. Report: {out / 'report.html'}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("scenes", nargs="+", type=Path, help="scene photos with the subject in frame")
    parser.add_argument("--models", nargs="+", default=DEFAULT_MODELS, metavar="ID")
    parser.add_argument("--rounds", type=int, default=2, help="calls per scene and model, to see the spread")
    parser.add_argument("--prompt", help="suggestion or variation prompt (default: the server's)")
    parser.add_argument("--description", help="ask for three variations of this description instead of poses")
    parser.add_argument("--reasoning", default="minimal", help="reasoning effort (default: the server's)")
    parser.add_argument("--out", type=Path, help="output directory (default: scripts/out/<timestamp>)")
    parser.add_argument("--timeout", type=float, default=20.0, help="give up on a call after this many seconds")
    args = parser.parse_args()
    if args.prompt is None:
        args.prompt = load_prompt("suggestion_prompt" if args.description is None else "variation_prompt")
    if args.description is not None:
        # {description} stands for the description, as on the server.
        args.prompt = args.prompt.replace("{description}", one_line(args.description))
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
