# AI-generated with Claude Code, 2026-10-27, reviewed by Jaewan Park
"""Render the drawings in wiki/design/ to the images the wiki pages show.

Each drawing has one image in wiki/images/, named after it:

- An `<svg data-image="<name>.png">` in an HTML file here, such as the Design's architecture figure in
  `design_architecture.html`, becomes `<name>.png` at twice its viewBox width over white (`rsvg-convert`).
- Each wireframe flow of R&S section 6, `wireframes/<name>.dc.html`, becomes `<name>.jpg`, rendered in headless Chrome
  at the size its `$preview` declares, and `<name>_ko.jpg`, the same flow with its screens in Korean. A flow is a
  fixed-size HTML page whose elements are placed with `left` and `top` in px. Keep the `support.js` line and the script
  block at its end, which come from the Claude design canvas format.
  The two arrows between the phones start in Flow 4 and end in Flow 5, so both files have them.
- A screen is drawn as the app shows it on a Galaxy S22: a card of 168 x 364 px whose top 18 px hold the screen's name,
  then the app's 360 x 705 dp at 165/360 px per dp, and below it the navigation bar with its home indicator. It is in
  Manrope (DM Mono for the room code; Pretendard in the Korean image, as in the app) with the colors of
  `android/app/src/main/res/values/colors.xml` and the app's own icons as inline SVG. A camera image or a photo is a
  lighter grey gradient, so its frame shows against the app; in it, a grey figure is the real person and a white
  outline the pose guide. A spinner, which Robolectric's render leaves out, is a three-quarter ring.

The Korean image changes only the text on the screens, the part of each card that clips its content between the
screen's name and the navigation bar; the arrows, the headings, the boxes where a flow starts and ends, and a box on a
screen that `wireframes/ko.json` lists under `notes` stay in English. A screen's text that is one of the English
strings of `android/app/src/main/res/values/strings.xml`, in any case and with its arguments filled in, becomes that
string's Korean from `values-ko`. The rest, such as the screens the app does not have yet, is in ko.json under the
flow's name, which also keeps the names and Pix IDs of the flow's people as they are. A screen's text with a Latin
letter that neither gives, or an entry the flow no longer uses, stops the render, so ko.json changes with the drawing;
render the flows again after a Korean string changes. Anything as wide as its content in the app (a chip, a button, a
line of text its parent centers, or a row of them) is drawn as one element marked `data-fit` (left, center, or right:
the side its parent keeps it at), and in Korean it takes its content's width from that side. A dialog is one box
whose content flows, centered on the screen, with its buttons in a row that wraps (`flex-flow: row-reverse wrap`): as
in the app, they stand side by side when they fit and stack at the right when they do not, so the Korean dialog takes
its own height and its own buttons' places.

Change the drawing, never the image alone, and render it in the same PR:

    python3 wiki/design/render.py                         every image
    python3 wiki/design/render.py flow2_pose_generation   the images named

Needs `rsvg-convert` (librsvg) for the figures, Google Chrome (or CHROME set to its path) for the wireframes, and
Pillow (`uv run --with pillow wiki/design/render.py`). Rendering is deterministic, so an unchanged drawing gives the
same file.
"""

import argparse
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Callable
from pathlib import Path
from xml.etree import ElementTree

from PIL import Image

DESIGN = Path(__file__).resolve().parent
IMAGES = DESIGN.parent / "images"
STRINGS = DESIGN.parent.parent / "android" / "app" / "src" / "main" / "res"
KOREAN = DESIGN / "wireframes" / "ko.json"
# The app's Korean font files, res/font/pretendard_<name>.otf, by the CSS weight each one is.
PRETENDARD = {"medium": 500, "semibold": 600, "bold": 700, "extrabold": 800}
# Twice the page's size: a screen is drawn at its real proportions, so its smallest text (11 sp, 5 px) needs it.
WIREFRAME_SCALE = 2
WIREFRAME_JPEG_QUALITY = 92
# Headless Chrome now and then hangs on a page; another try then works.
CHROME_TIMEOUT_S = 120
CHROME_TRIES = 3
SVG = re.compile(r'<svg\b[^>]*\bdata-image="([^"]+)"[^>]*>.*?</svg>', re.DOTALL)
VIEWBOX = re.compile(r'viewBox="[\d.]+ [\d.]+ ([\d.]+) [\d.]+"')
# A flow's markup split into tags (a style block counts as one) and the texts between them.
MARKUP = re.compile(r"(<style\b.*?</style>|<[^>]+>)", re.DOTALL)
LATIN = re.compile(r"[A-Za-z]")
ARGUMENT = re.compile(r"%(\d+)\$([sd])")
VOID = {"br", "hr", "img", "input", "link", "meta", "wbr"}
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


def clean(text: str) -> str:
    """[text] with its runs of whitespace as one space and a typographic apostrophe as a straight one."""
    return " ".join(text.replace("\u2019", "'").split())


def android_strings(folder: str) -> tuple[dict[str, str], dict[str, list[str]]]:
    """The strings and the string arrays of one values folder, unescaped."""

    def unescape(text: str | None) -> str:
        return (text or "").replace("\\'", "'").replace('\\"', '"').replace("\\@", "@").replace("\\n", " ")

    root = ElementTree.parse(STRINGS / folder / "strings.xml").getroot()
    strings = {e.get("name"): unescape(e.text) for e in root.iter("string")}
    arrays = {e.get("name"): [unescape(item.text) for item in e.iter("item")] for e in root.iter("string-array")}
    return strings, arrays


class Korean:
    """The Korean of a flow's texts: its entries in ko.json first, then the app's strings."""

    def __init__(self, flow: str):
        entries = json.loads(KOREAN.read_text(encoding="utf-8")).get(flow, {})
        self.entries = {clean(text).casefold(): korean for text, korean in entries.get("texts", {}).items()}
        self.entries |= {clean(text).casefold(): text for text in entries.get("keep", [])}
        self.notes = {clean(text).casefold() for text in entries.get("notes", [])}
        self.written = {clean(text).casefold(): text for group in entries.values() for text in group}
        self.used: set[str] = set()
        english, english_arrays = android_strings("values")
        korean, korean_arrays = android_strings("values-ko")
        self.strings: dict[str, str] = {}
        formats = []
        for name, text in english.items():
            if ARGUMENT.search(text):
                formats.append((self.pattern(text), korean.get(name), text))
            elif name in korean:
                self.strings.setdefault(clean(text).casefold(), korean[name])
        for name, items in english_arrays.items():
            for text, translated in zip(items, korean_arrays.get(name, [])):
                self.strings.setdefault(clean(text).casefold(), translated)
        # The longest words first, so "%1$s left" does not take a text that a longer string matches.
        self.formats = sorted(formats, key=lambda f: -len(ARGUMENT.sub("", f[2])))

    @staticmethod
    def pattern(text: str) -> re.Pattern[str]:
        parts = ARGUMENT.split(text)  # words, then the number and the type of each argument
        regex = re.escape(parts[0])
        for i in range(1, len(parts), 3):
            argument = r"\d+" if parts[i + 1] == "d" else ".+?"
            regex += f"(?P<a{parts[i]}>{argument})" + re.escape(parts[i + 2])
        return re.compile(regex.replace("%%", "%"), re.IGNORECASE)

    def __call__(self, text: str) -> str | None:
        """The Korean of [text], or None when neither the app nor ko.json has it."""
        key = clean(text).casefold()
        if key in self.entries:
            self.used.add(key)
            return self.entries[key]
        if key in self.strings:
            return self.strings[key]
        for pattern, korean, english in self.formats:
            match = pattern.fullmatch(clean(text))
            if not match:
                continue
            arguments = {name: self(value) or value for name, value in match.groupdict().items()}
            if korean is None and all(arguments[name] == value for name, value in match.groupdict().items()):
                continue  # a format without words of its own, around arguments that stay English
            filled = ARGUMENT.sub(lambda m: arguments[f"a{m.group(1)}"], korean or english)
            return filled.replace("%%", "%")
        return None


def screen_texts(parts: list[str], notes: set[str]) -> tuple[list[int], set[str]]:
    """The indexes of the texts on the screens in [parts], a flow's markup split by MARKUP, and the [notes] found. A
    card is a top-level element that clips its content, and its screen the box in it that clips too; the screen's
    name above it and the navigation bar below are not screen text, nor a box on the screen that holds one of
    [notes]."""
    stack: list[tuple[int, str]] = []  # the open elements: where each starts in parts, and its tag
    boxes: dict[int, int] = {}  # a text on a screen, and the box of the screen it is in
    noted, found = set(), set()
    for i, part in enumerate(parts):
        if i % 2 == 1:  # a tag
            name = re.match(r"</?([a-zA-Z][\w-]*)", part)
            if part.startswith("</"):
                stack.pop()
            elif name and name.group(1).lower() not in VOID | {"style"} and not part.endswith("/>"):
                stack.append((i, part))  # a style block is one part, so it does not stay open
            continue
        # stack[0] is the page's root, stack[1] a card, stack[2] its screen, and stack[3] a box on the screen
        if len(stack) < 3 or "overflow: hidden" not in stack[1][1] or "overflow: hidden" not in stack[2][1]:
            continue
        box = stack[3] if len(stack) > 3 else stack[2]
        boxes[i] = box[0]
        if (key := clean(html.unescape(part)).casefold()) in notes:
            noted.add(box[0])
            found.add(key)
    return [i for i, box in boxes.items() if box not in noted], found


SCREEN_WIDTH = 165  # px, a card's screen, inside its border


def fit_to_text(match: re.Match[str]) -> str:
    """An element as wide as its content in the app, as wide as its Korean content, from the side the app keeps it at
    (data-fit: left, center, or right), and never wider than the screen's margins."""
    side, style = match.group(1), match.group(2)
    left = float(re.search(r"left: ([\d.]+)px", style).group(1))
    width = float(re.search(r"width: ([\d.]+)px", style).group(1))
    style = re.sub(r"width: [\d.]+px", f"width: max-content; max-width: {SCREEN_WIDTH - 16}px", style, count=1)
    if side == "center":
        style = (
            re.sub(r"left: [\d.]+px", f"left: {left + width / 2:.1f}px", style, count=1)
            + "; transform: translateX(-50%)"
        )
    elif side == "right":
        style = re.sub(r"left: [\d.]+px", f"left: auto; right: {SCREEN_WIDTH - left - width:.1f}px", style, count=1)
    return f'<div data-fit="{side}" style="{style.replace("white-space: nowrap", "white-space: normal")}">'


def korean_page(source: Path) -> str:
    """The flow's page with its screens in Korean and in Pretendard, the font the app uses in Korean, from the app's
    own files. Stops on a screen's text without Korean, or an entry of ko.json the flow does not use."""
    flow = source.name.removesuffix(".dc.html")
    korean = Korean(flow)
    page = source.read_text(encoding="utf-8")
    head, rest = page.split("<x-dc>", 1)
    body, tail = rest.split("</x-dc>", 1)
    parts = MARKUP.split(body)
    screens, notes = screen_texts(parts, korean.notes)
    missing = []
    for i in screens:
        text = html.unescape(parts[i])
        if not LATIN.search(text):
            continue
        translated = korean(text)
        if translated is None:
            missing.append(clean(text))
        elif translated != text.strip():
            lead, trail = text[: len(text) - len(text.lstrip())], text[len(text.rstrip()) :]
            parts[i] = lead + html.escape(translated, quote=False) + trail
    unused = sorted(korean.written[key] for key in (set(korean.entries) - korean.used) | (korean.notes - notes))
    if missing or unused:
        sys.exit(
            f"{source.name}: add to {KOREAN.name} under {flow!r}: {json.dumps(missing, ensure_ascii=False)}; "
            f"remove: {json.dumps(unused, ensure_ascii=False)}"
        )
    body = re.sub(r'<div data-fit="(\w+)" style="([^"]*)">', fit_to_text, "".join(parts))
    page = head + "<x-dc>" + body + "</x-dc>" + tail
    page = page.replace('<html lang="en">', '<html lang="ko">')
    # Korean breaks lines between words, not inside them.
    page = page.replace("</helmet>", "<style>*{word-break:keep-all;overflow-wrap:break-word}</style>\n</helmet>")
    files = {weight: (STRINGS / "font" / f"pretendard_{name}.otf").as_uri() for name, weight in PRETENDARD.items()}
    faces = "".join(
        f"@font-face{{font-family:'Pretendard';font-weight:{weight};src:url('{uri}')}}" for weight, uri in files.items()
    )
    page = page.replace("</helmet>", f"<style>{faces}</style>\n</helmet>")
    page = page.replace("'Manrope', sans-serif", "'Pretendard', sans-serif")
    return page.replace("'DM Mono', monospace", "'DM Mono', 'Pretendard', monospace")


def render_wireframe(source: Path, target: Path, korean: bool = False) -> None:
    page = korean_page(source) if korean else source.read_text(encoding="utf-8")
    match = re.search(r"data-props='([^']*)'", page)
    if not match:
        sys.exit(f"{source.name}: no data-props with $preview")
    preview = json.loads(match.group(1))["$preview"]
    chrome = tool("Chrome (set CHROME to its path)", [os.environ.get("CHROME"), *CHROME_PATHS])
    with tempfile.TemporaryDirectory() as tmp:
        png = Path(tmp) / "flow.png"
        if korean:
            source = Path(tmp) / source.name
            source.write_text(page, encoding="utf-8")
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


def drawings() -> dict[str, list[tuple[str, Callable[[Path], None]]]]:
    """Every drawing in the folder: its name, and the file name of each of its images with how to render it."""
    found: dict[str, list[tuple[str, Callable[[Path], None]]]] = {}
    for page in sorted(DESIGN.glob("*.html")):
        for match in SVG.finditer(page.read_text(encoding="utf-8")):
            image, svg = match.group(1), match.group(0)
            found[Path(image).stem] = [(image, lambda target, svg=svg: render_figure(svg, target))]
    for flow in sorted((DESIGN / "wireframes").glob("*.dc.html")):
        name = flow.name.removesuffix(".dc.html")
        found[name] = [
            (f"{name}.jpg", lambda target, flow=flow: render_wireframe(flow, target)),
            (f"{name}_ko.jpg", lambda target, flow=flow: render_wireframe(flow, target, korean=True)),
        ]
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
        for image, render in known[name]:
            target = args.out / image
            render(target)
            print(os.path.relpath(target))


if __name__ == "__main__":
    main()
