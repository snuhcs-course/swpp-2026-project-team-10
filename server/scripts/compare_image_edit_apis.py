"""Compare image-editing models on OpenRouter for pose generation (Design Documentation 2.6.3).

It does not import pix_server. Run it through uv from server/ with OPENROUTER_API_KEY set:

    uv run scripts/compare_image_edit_apis.py scene1.jpg scene2.jpg
    uv run scripts/compare_image_edit_apis.py scene.jpg --models openai/gpt-image-2@low qwen/qwen-image-3
    uv run scripts/compare_image_edit_apis.py scene.jpg --pose-text "벽에 기대서 한 손은 주머니에"
    uv run scripts/compare_image_edit_apis.py scene.jpg --prompt "Same person and place. New pose: {pose}."
    uv run scripts/compare_image_edit_apis.py --list-models

Each scene is prepared the way the app sends it (upright, long side 1024 px, JPEG 85, no EXIF). For every
scene and model the poses are requested in parallel, like the app's four POST /poses calls. The images,
results.json, and report.html (originals next to the candidates, for judging identity and background
preservation) go to scripts/out/<timestamp>/. Every returned image is billed to the OpenRouter key.
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
import statistics
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from urllib.parse import quote

import httpx2
from PIL import Image, ImageOps

API_URL = "https://openrouter.ai/api/v1/images"
SITE = "https://openrouter.ai"

# The models that answered within the latency budget when screened on 2026-10-03; `--list-models` shows
# everything that can edit. `@quality` picks a quality tier where the model has one.
DEFAULT_MODELS = [
    "google/gemini-3.1-flash-image",
    "bytedance-seed/seedream-5-0-flash",
    "openai/gpt-image-2.5-flare@low",
    "google/gemini-3.1-flash-lite-image",
]

# Wraps each template's pose description. Unlike the pose-only wording in Design Documentation 2.6.3, it lets the
# model re-place the person, and pins the camera framing so the candidate still lines up with the live view.
PROMPT = (
    "Keep the same person (face, hair, clothing). Keep the background, camera position, framing, and lighting "
    "exactly as they are: do not zoom, crop, or shift the scene. Recompose the shot like a skilled photographer "
    "would: judge whether the person is too far, too close, or poorly placed, and if so move them to a better "
    "spot and distance in this scene. Change the person's pose to: {pose}. Show the full body, standing on the "
    "ground at a natural scale. Photorealistic."
)
POSES = {
    "hands_on_hips": "standing with both hands on the hips",
    "wave": "waving at the camera with one raised hand",
    "walking": "walking toward the camera in mid-stride",
    "arms_crossed": "standing with the arms crossed over the chest",
}

SCENE_LONG_SIDE = 1024
SCENE_JPEG_QUALITY = 85
UPSTREAM_BUDGET = 25.0  # the server stops waiting for the image API after this (Design Documentation 2.8)
RESOLUTIONS = ["1K", "1.5K", "2K", "4K"]  # first match wins: candidates only need to match the 1024 px scene


@dataclass
class Scene:
    name: str
    data_url: str
    ratio: float


@dataclass
class Model:
    label: str
    id: str
    quality: str | None
    supported: dict


@dataclass
class Result:
    scene: str
    model: str
    pose: str
    params: dict
    seconds: float = 0.0
    status: int | None = None
    error: str | None = None
    cost: float | None = None
    file: str | None = None
    size: str | None = None

    @property
    def in_budget(self) -> bool:
        return self.error is None and self.seconds <= UPSTREAM_BUDGET


def prepare_scene(path: Path) -> Image.Image:
    with Image.open(path) as image:
        scene = ImageOps.exif_transpose(image).convert("RGB")
    scene.thumbnail((SCENE_LONG_SIDE, SCENE_LONG_SIDE))
    return scene


def request_params(model: Model, ratio: float) -> dict:
    """Pick the optional request fields this model accepts; the catalog lists them per model."""
    supported = model.supported
    params: dict = {}
    ratios = {}
    for value in supported.get("aspect_ratio", {}).get("values", []):
        width, _, height = value.partition(":")
        if height:
            ratios[value] = float(width) / float(height)
    if ratios:
        params["aspect_ratio"] = min(ratios, key=lambda value: abs(math.log(ratios[value] / ratio)))
    tiers = supported.get("resolution", {}).get("values", [])
    if resolution := next((tier for tier in RESOLUTIONS if tier in tiers), None):
        params["resolution"] = resolution
    if "jpeg" in supported.get("output_format", {}).get("values", []):
        params["output_format"] = "jpeg"
    if "seed" in supported:
        params["seed"] = random.randrange(2**31)
    if model.quality:
        params["quality"] = model.quality
    return params


def parse_model(spec: str, catalog: dict[str, dict]) -> Model:
    model_id, _, quality = spec.partition("@")
    supported = catalog.get(model_id)
    if supported is None:
        sys.exit(f"Unknown model {model_id!r}; --list-models shows the valid ids.")
    if "input_references" not in supported:
        sys.exit(f"{model_id} does not accept an input image.")
    qualities = supported.get("quality", {}).get("values", [])
    if quality and quality not in qualities:
        sys.exit(f"{model_id} has no quality {quality!r}; it accepts: {', '.join(qualities) or 'none'}.")
    return Model(spec, model_id, quality or None, supported)


async def edit(
    client: httpx2.AsyncClient, scene: Scene, model: Model, pose: str, prompt: str, out: Path, timeout: float
) -> Result:
    result = Result(scene.name, model.label, pose, request_params(model, scene.ratio))
    payload = {
        "model": model.id,
        "prompt": prompt,
        "input_references": [{"type": "image_url", "image_url": {"url": scene.data_url}}],
        **result.params,
    }
    start = time.perf_counter()
    try:
        async with asyncio.timeout(timeout):
            response = await client.post(API_URL, json=payload)
    except TimeoutError:
        result.error = f"no response within {timeout:g} s"
    except httpx2.HTTPError as error:
        result.error = f"{type(error).__name__}: {error}"
    else:
        result.status = response.status_code
        try:
            body = response.json()
            if response.status_code != 200 or not body.get("data"):
                result.error = json.dumps(body.get("error") or body, ensure_ascii=False)[:500]
            else:
                data = base64.b64decode(body["data"][0]["b64_json"])
                with Image.open(io.BytesIO(data)) as image:
                    result.size = f"{image.width}x{image.height}"
                    extension = {"JPEG": "jpg"}.get(image.format, image.format.lower())
                file = out / scene.name / model.label.replace("/", "__") / f"{pose}.{extension}"
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(data)
                result.file = file.relative_to(out).as_posix()
                result.cost = (body.get("usage") or {}).get("cost")
        except (ValueError, KeyError, AttributeError, OSError) as error:
            result.error = f"unreadable response: {error!r} {response.text[:200]}"
    result.seconds = time.perf_counter() - start
    outcome = result.error or f"{result.size}" + ("" if result.cost is None else f"  ${result.cost:.3f}")
    print(f"{scene.name}  {model.label}  {pose}  {result.seconds:5.1f} s  {outcome}")
    return result


def table(rows: list[dict[str, str]]) -> str:
    widths = {key: max(len(key), *(len(row[key]) for row in rows)) for key in rows[0]}
    lines = [{key: key for key in widths}, *rows]
    return "\n".join("  ".join(line[key].ljust(width) for key, width in widths.items()).rstrip() for line in lines)


def summarize(results: list[Result]) -> list[dict[str, str]]:
    rows = []
    for model in dict.fromkeys(result.model for result in results):
        mine = [result for result in results if result.model == model]
        seconds = [result.seconds for result in mine if result.error is None]
        costs = [result.cost for result in mine if result.cost is not None]
        rows.append(
            {
                "model": model,
                "images": f"{len(seconds)}/{len(mine)}",
                f"within {UPSTREAM_BUDGET:g} s": f"{sum(result.in_budget for result in mine)}/{len(mine)}",
                "median": f"{statistics.median(seconds):.1f} s" if seconds else "-",
                "slowest": f"{max(seconds):.1f} s" if seconds else "-",
                "$/image": f"{statistics.mean(costs):.3f}" if costs else "-",
            }
        )
    return rows


def write_report(
    out: Path, prompt: str, poses: dict[str, str], results: list[Result], summary: list[dict[str, str]]
) -> None:
    def row(cells: list[str], tag: str = "td") -> str:
        return "<tr>" + "".join(f"<{tag}>{cell}</{tag}>" for cell in cells) + "</tr>"

    def cell(result: Result) -> str:
        if result.file is None:
            return f'<span class="bad">{html.escape(result.error or "")}</span><br>{result.seconds:.1f} s'
        timing = f"{result.seconds:.1f} s" if result.in_budget else f'<span class="bad">{result.seconds:.1f} s</span>'
        cost = "" if result.cost is None else f" · ${result.cost:.3f}"
        return f'<a href="{quote(result.file)}"><img src="{quote(result.file)}" loading="lazy"></a>{timing}{cost}'

    by_key = {(result.scene, result.model, result.pose): result for result in results}
    parts = [
        '<!doctype html><meta charset="utf-8"><title>Image edit comparison</title>',
        "<style>:root{color-scheme:light dark}body{font:14px/1.4 system-ui,sans-serif;margin:24px}"
        "table{border-collapse:collapse;margin:12px 0 32px}th,td{border:1px solid #8886;padding:6px 8px;"
        "text-align:left;vertical-align:top}img{width:220px;display:block}.bad{color:#d33}</style>",
        f"<h1>Image edit comparison</h1><p>Prompt: {html.escape(prompt)}</p><ul>",
        *(f"<li><b>{pose}</b>: {html.escape(text)}</li>" for pose, text in poses.items()),
        f"</ul><p>Red: failed, or slower than the server's {UPSTREAM_BUDGET:g} s upstream timeout.</p><table>",
        row(list(summary[0]), "th"),
        *(row([html.escape(value) for value in line.values()]) for line in summary),
        "</table>",
    ]
    for scene in dict.fromkeys(result.scene for result in results):
        original = f'<img src="{quote(scene)}/scene.jpg">'
        parts += [f"<h2>{html.escape(scene)}</h2><table>", row(["model", "scene", *poses], "th")]
        for model in dict.fromkeys(result.model for result in results):
            cells = [cell(by_key[scene, model, pose]) for pose in poses]
            parts.append(row([html.escape(model), original, *cells]))
        parts.append("</table>")
    (out / "report.html").write_text("\n".join(parts), encoding="utf-8")


async def list_models(client: httpx2.AsyncClient) -> None:
    catalog = (await client.get(f"{SITE}/api/v1/images/models")).raise_for_status().json()["data"]
    models = [model for model in catalog if "input_references" in model["supported_parameters"]]
    responses = await asyncio.gather(*(client.get(SITE + model["endpoints"]) for model in models))
    rows = []
    for model, response in zip(models, responses):
        prices = []
        for line in (response.json().get("endpoints") or [{}])[0].get("pricing", []):
            if line["billable"] == "output_image":
                per_token = line["unit"] == "token"
                amount = f"${line['cost_usd'] * (1_000_000 if per_token else 1):g}/{'M tokens' if per_token else line['unit']}"
                prices.append(amount + (f" ({line['variant']})" if line.get("variant") else ""))
        supported = model["supported_parameters"]
        rows.append(
            {
                "model": model["id"],
                "seed": "yes" if "seed" in supported else "no",
                "quality": "|".join(supported.get("quality", {}).get("values", [])) or "-",
                "output price": ", ".join(prices) or "-",
            }
        )
    print(table(rows))


async def run(args: argparse.Namespace) -> None:
    timeout = httpx2.Timeout(None, connect=10.0)
    if args.list_models:
        async with httpx2.AsyncClient(timeout=timeout) as client:
            return await list_models(client)

    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        sys.exit("Set OPENROUTER_API_KEY first.")
    templates = args.poses if args.poses is not None else ([] if args.pose_text else list(POSES))
    poses = {pose: POSES[pose] for pose in templates}
    poses |= {f"text-{number}": text for number, text in enumerate(args.pose_text, 1)}
    out: Path = args.out or Path(__file__).parent / "out" / datetime.now().strftime("%Y%m%d-%H%M%S")

    async with httpx2.AsyncClient(headers={"Authorization": f"Bearer {key}"}, timeout=timeout) as client:
        catalog = (await client.get(f"{SITE}/api/v1/images/models")).raise_for_status().json()["data"]
        supported = {model["id"]: model["supported_parameters"] for model in catalog}
        models = [parse_model(spec, supported) for spec in args.models]

        scenes = []
        for number, path in enumerate(args.scenes, 1):
            image = prepare_scene(path)
            name = f"{number:02d}-{path.stem}"
            (out / name).mkdir(parents=True, exist_ok=True)
            image.save(out / name / "scene.jpg", "JPEG", quality=SCENE_JPEG_QUALITY)
            encoded = base64.b64encode((out / name / "scene.jpg").read_bytes()).decode()
            scenes.append(Scene(name, f"data:image/jpeg;base64,{encoded}", image.width / image.height))

        print(f"{len(scenes)} scenes x {len(models)} models x {len(poses)} poses -> {out}")
        gate = asyncio.Semaphore(args.concurrency)

        async def run_set(scene: Scene, model: Model) -> list[Result]:
            async with gate:
                prompts = {pose: args.prompt.replace("{pose}", text) for pose, text in poses.items()}
                requests = (
                    edit(client, scene, model, pose, prompt, out, args.timeout) for pose, prompt in prompts.items()
                )
                return await asyncio.gather(*requests)

        sets = await asyncio.gather(*(run_set(scene, model) for scene in scenes for model in models))

    results = [result for results in sets for result in results]
    summary = summarize(results)
    record = {"prompt": args.prompt, "poses": poses, "results": [asdict(result) for result in results]}
    (out / "results.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    write_report(out, args.prompt, poses, results, summary)
    total = sum(result.cost or 0 for result in results)
    print(f"\n{table(summary)}\n\nTotal cost ${total:.3f}. Report: {out / 'report.html'}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("scenes", nargs="*", type=Path, help="scene photos with the subject in frame")
    parser.add_argument("--models", nargs="+", default=DEFAULT_MODELS, metavar="ID[@QUALITY]")
    parser.add_argument("--poses", nargs="+", choices=list(POSES), help="pose templates (default: all four)")
    parser.add_argument("--pose-text", action="append", default=[], metavar="TEXT", help="free-text pose, repeatable")
    parser.add_argument("--prompt", default=PROMPT, metavar="TEMPLATE", help="prompt to try; {pose} becomes the pose")
    parser.add_argument("--out", type=Path, help="output directory (default: scripts/out/<timestamp>)")
    parser.add_argument("--timeout", type=float, default=60.0, help="give up on a request after this many seconds")
    parser.add_argument("--concurrency", type=int, default=1, help="scene x model sets in flight; 1 is one user")
    parser.add_argument("--list-models", action="store_true", help="list the models that can edit, with prices")
    args = parser.parse_args()
    if not args.scenes and not args.list_models:
        parser.error("give at least one scene photo, or --list-models")
    if "{pose}" not in args.prompt:
        parser.error("--prompt needs a {pose} placeholder")
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
