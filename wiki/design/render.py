# AI-generated with Claude Code, 2026-10-27, reviewed by Jaewan Park
"""Render the drawings in wiki/design/ to the images the wiki pages show.

Each drawing has one image in wiki/images/, named after it:

- An `<svg data-image="<name>.png">` in an HTML file here, such as the Design's architecture figure in
  `design_architecture.html`, becomes `<name>.png` at twice its viewBox width over white (`rsvg-convert`).
- Each wireframe flow of R&S section 6, `wireframes/<name>.dc.html`, becomes `<name>.jpg`, rendered in headless Chrome
  at the size its `$preview` declares. A flow is a fixed-size HTML page whose elements are placed with `left` and `top`
  in px. Keep the `support.js` line and the script block at its end, which come from the Claude design canvas format.
  The two arrows between the phones start in Flow 4 and end in Flow 5, so both files have them.
- A screen is drawn as the app shows it on a Galaxy S22: a card of 168 x 336 px whose top 18 px hold the screen's name,
  then the app's 360 x 705 dp at 165/360 px per dp, in Manrope (DM Mono for the room code) with the colors of
  `android/app/src/main/res/values/colors.xml` and the app's own icons as inline SVG. In a camera image, a grey figure
  is the real person and a white outline the pose guide.

Change the drawing, never the image alone, and render it in the same PR:

    python3 wiki/design/render.py                         every image
    python3 wiki/design/render.py flow2_pose_generation   the images named

Needs `rsvg-convert` (librsvg) for the figures, Google Chrome (or CHROME set to its path) for the wireframes, and
Pillow (`uv run --with pillow wiki/design/render.py`). Rendering is deterministic, so an unchanged drawing gives the
same file.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Callable
from pathlib import Path

from PIL import Image

DESIGN = Path(__file__).resolve().parent
IMAGES = DESIGN.parent / "images"
# Twice the page's size: a screen is drawn at its real proportions, so its smallest text (11 sp, 5 px on a card) needs it.
WIREFRAME_SCALE = 2
WIREFRAME_JPEG_QUALITY = 92
# Headless Chrome now and then hangs on a page; another try then works.
CHROME_TIMEOUT_S = 120
CHROME_TRIES = 3
SVG = re.compile(r'<svg\b[^>]*\bdata-image="([^"]+)"[^>]*>.*?</svg>', re.DOTALL)
VIEWBOX = re.compile(r'viewBox="[\d.]+ [\d.]+ ([\d.]+) [\d.]+"')
CHROME_PATHS = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
]


def tool(name: str, candidates: list[str | None]) -> str:
    for candidate in candidates:
        if candidate and (Path(candidate).exists() or shutil.which(candidate)):
            return candidate
    sys.exit(f"{name} not found")


def render_figure(svg: str, target: Path) -> None:
    width = 2 * float(VIEWBOX.search(svg).group(1))
    rsvg = tool("rsvg-convert", ["rsvg-convert"])
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / "figure.svg"
        source.write_text(svg, encoding="utf-8")
        subprocess.run([rsvg, "-w", str(round(width)), "-b", "white", str(source), "-o", str(target)], check=True)


def render_wireframe(source: Path, target: Path) -> None:
    match = re.search(r"data-props='([^']*)'", source.read_text(encoding="utf-8"))
    if not match:
        sys.exit(f"{source.name}: no data-props with $preview")
    preview = json.loads(match.group(1))["$preview"]
    chrome = tool("Chrome (set CHROME to its path)", [os.environ.get("CHROME"), *CHROME_PATHS])
    with tempfile.TemporaryDirectory() as tmp:
        png = Path(tmp) / "flow.png"
        command = [
            chrome,
            "--headless",
            "--hide-scrollbars",
            f"--force-device-scale-factor={WIREFRAME_SCALE}",
            f"--window-size={preview['width']},{preview['height']}",
            # Time for the web fonts to load before the screenshot.
            "--virtual-time-budget=10000",
            f"--screenshot={png}",
            source.as_uri(),
        ]
        for attempt in range(1, CHROME_TRIES + 1):
            try:
                subprocess.run(command, check=True, capture_output=True, timeout=CHROME_TIMEOUT_S)
                break
            except subprocess.TimeoutExpired:
                if attempt == CHROME_TRIES:
                    sys.exit(f"{source.name}: Chrome did not finish within {CHROME_TIMEOUT_S} s, {CHROME_TRIES} times")
        Image.open(png).convert("RGB").save(target, "JPEG", quality=WIREFRAME_JPEG_QUALITY)


def drawings() -> dict[str, tuple[str, Callable[[Path], None]]]:
    """Every image the folder makes: its name, its file name, and how to render it to a path."""
    found: dict[str, tuple[str, Callable[[Path], None]]] = {}
    for page in sorted(DESIGN.glob("*.html")):
        for match in SVG.finditer(page.read_text(encoding="utf-8")):
            image, svg = match.group(1), match.group(0)
            found[Path(image).stem] = (image, lambda target, svg=svg: render_figure(svg, target))
    for flow in sorted((DESIGN / "wireframes").glob("*.dc.html")):
        name = flow.name.removesuffix(".dc.html")
        found[name] = (f"{name}.jpg", lambda target, flow=flow: render_wireframe(flow, target))
    return found


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("names", nargs="*", help="image names without the extension; every image when left out")
    parser.add_argument("--out", type=Path, default=IMAGES, help="output folder (default wiki/images)")
    args = parser.parse_args()
    known = drawings()
    unknown = [name for name in args.names if name not in known]
    if unknown:
        sys.exit(f"unknown: {', '.join(unknown)}; known: {', '.join(known)}")
    args.out.mkdir(parents=True, exist_ok=True)
    for name in args.names or known:
        image, render = known[name]
        target = args.out / image
        render(target)
        print(os.path.relpath(target))


if __name__ == "__main__":
    main()
