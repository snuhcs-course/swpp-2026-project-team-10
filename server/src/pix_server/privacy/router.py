# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""The privacy policy as public web pages (R&S FR-10.7, Design 2.5.3): `/privacy` in Korean, the policy's own language,
and `/privacy/en` in English. The app shows the same pages in Settings and on its sign-up agreement, and the store links
to `/privacy`, so the policy has one text: `policy.ko.md` and `policy.en.md` here, which the wiki's *Privacy Policy*
pages copy (`tests/test_privacy.py`).

A page lays the text out as a plain document: the Pix logo (`pix-logo.svg`, a copy of `branding/pix-horizontal.svg`)
and the language switch, the title with the effective date and the contact, the opening paragraph, the contents, and
one part per numbered section, set in the text's own colors with thin rules. A table keeps its columns' widths and, on
a narrow screen, scrolls sideways in its own box. The page needs no JavaScript, which the app's WebView keeps off, and
loads nothing from elsewhere.

Both pages are rendered once, when the module is imported, so a file that cannot be read, or a policy laid out in
another way, fails the tests and the image's build instead of a request. They name nobody and read nothing from the
request.
"""

import re
import xml.etree.ElementTree as ET
from importlib import resources
from string import Template
from typing import NamedTuple

import markdown
from fastapi import APIRouter, HTTPException
from fastapi.responses import HTMLResponse
from markdown.extensions import Extension
from markdown.treeprocessors import Treeprocessor

router = APIRouter()

PAGE = Template("""<!doctype html>
<html lang="$lang">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>$title | Pix</title>
<link rel="alternate" hreflang="ko" href="/privacy">
<link rel="alternate" hreflang="en" href="/privacy/en">
<style>
:root { color-scheme: light dark; --ground: #ffffff; --text: #1b1c1f; --muted: #5c6068; --faint: #80848b;
  --line: #e4e5e8; --code: #f4f4f5; }
@media (prefers-color-scheme: dark) {
  :root { --ground: #0b0b0d; --text: #ececec; --muted: #a9acb2; --faint: #84888f; --line: #26272b; --code: #17181b; }
}
* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; }
body { margin: 0; background: var(--ground); color: var(--text); font-size: 15px; line-height: 1.7;
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif;
  overflow-wrap: anywhere; }
html[lang="ko"] body { word-break: keep-all; }
a { color: inherit; text-underline-offset: 3px; }
.top { border-bottom: 1px solid var(--line); }
.top-in, main, .foot-in { max-width: 720px; margin: 0 auto; padding-left: 24px; padding-right: 24px; }
.top-in { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 56px; }
.logo { display: block; height: 28px; width: auto; color: var(--text); }
.lang { font-size: 13px; white-space: nowrap; }
.lang a { color: var(--muted); text-decoration: none; }
.lang a:hover { text-decoration: underline; }
.lang span { font-weight: 600; }
.lang > * + *::before { content: "|"; margin: 0 10px; color: var(--line); font-weight: 400; }
main { padding-top: 48px; padding-bottom: 64px; }
h1 { margin: 0 0 10px; font-size: 25px; line-height: 1.3; letter-spacing: -0.01em; }
.meta { display: flex; flex-wrap: wrap; gap: 2px 20px; margin: 0 0 28px; color: var(--muted); font-size: 13px; }
.meta div { display: flex; gap: 6px; }
.meta dd { margin: 0; }
.lead { margin: 0 0 32px; }
.toc { padding: 20px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.toc h2 { margin: 0 0 6px; font-size: 14px; }
.toc ol { margin: 0; padding: 0; list-style: none; color: var(--muted); font-size: 14px; line-height: 2; }
.toc a { text-decoration: none; }
.toc a:hover { color: var(--text); text-decoration: underline; }
section { padding-top: 40px; }
section h2 { margin: 0 0 12px; font-size: 17px; line-height: 1.45; }
section p, section ul { margin: 0 0 14px; }
section ul { padding-left: 20px; }
section li { margin: 6px 0; }
code { padding: 1px 5px; border-radius: 4px; background: var(--code);
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 0.88em; }
.table { margin: 16px 0 20px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; line-height: 1.6; }
th { min-width: 9em; padding: 0 16px 10px 0; border-bottom: 1px solid var(--text); font-size: 12.5px; font-weight: 600;
  text-align: left; vertical-align: bottom; }
td { min-width: 9em; padding: 12px 16px 12px 0; border-bottom: 1px solid var(--line); vertical-align: top; }
th:first-child, td:first-child { min-width: 12em; }
th:last-child, td:last-child { padding-right: 0; }
footer { border-top: 1px solid var(--line); color: var(--faint); font-size: 13px; }
.foot-in { padding-top: 20px; padding-bottom: 32px; }
@media (max-width: 640px) {
  .top-in, main, .foot-in { padding-left: 20px; padding-right: 20px; }
  main { padding-top: 32px; }
  body { font-size: 14px; }
  h1 { font-size: 22px; }
  section h2 { font-size: 16px; }
}
@media print {
  .lang, .toc { display: none; }
}
</style>
</head>
<body>
<header class="top"><div class="top-in">
$logo
<nav class="lang" aria-label="$language_nav">$switch</nav>
</div></header>
<main>
$content
</main>
<footer><div class="foot-in">© 2026 Last Penguin</div></footer>
</body>
</html>
""")


class Language(NamedTuple):
    """A page's language: its file, its name and address in the switch, and the page's own words."""

    file_name: str
    name: str
    href: str
    contents: str
    language_nav: str


LANGUAGES = {
    "ko": Language("policy.ko.md", "한국어", "/privacy", contents="목차", language_nav="언어"),
    "en": Language("policy.en.md", "English", "/privacy/en", contents="Contents", language_nav="Language"),
}

SECTION_HEADING = re.compile(r"(\d+)\.\s+(.+)")


class PolicyLayout(Treeprocessor):
    """Puts the policy's Markdown in the page's parts: what comes before the first section becomes the title block
    (the title, the list as the effective date and the contact, then the paragraph), each `## n. Name` starts a
    section, the contents link to them, and each table goes in a box of its own, which scrolls sideways when the table
    is wider than the screen."""

    def __init__(self, md: markdown.Markdown, title: str, contents: str) -> None:
        super().__init__(md)
        self.title = title
        self.contents = contents

    def run(self, root: ET.Element) -> None:
        blocks = list(root)
        for block in blocks:
            root.remove(block)
        hero = ET.SubElement(root, "div", {"class": "hero"})
        ET.SubElement(hero, "h1").text = self.title
        leads: list[ET.Element] = []
        toc = ET.SubElement(root, "nav", {"class": "toc", "aria-labelledby": "contents"})
        ET.SubElement(toc, "h2", {"id": "contents"}).text = self.contents
        toc_list = ET.SubElement(toc, "ol")
        section: ET.Element | None = None
        for block in blocks:
            if block.tag == "h2":
                section = self.section(root, toc_list, "".join(block.itertext()).strip())
            elif section is None:
                if block.tag == "ul":
                    hero.append(meta(block))
                else:
                    leads.append(lead(block))
            elif block.tag == "table":
                ET.SubElement(section, "div", {"class": "table"}).append(block)
            else:
                section.append(block)
        if section is None:
            raise ValueError("The privacy policy has no numbered sections")
        hero.extend(leads)

    @staticmethod
    def section(root: ET.Element, toc_list: ET.Element, heading_text: str) -> ET.Element:
        match = SECTION_HEADING.fullmatch(heading_text)
        if match is None:
            raise ValueError(f"A section's heading must start with its number: {heading_text!r}")
        number, name = match.groups()
        section = ET.SubElement(root, "section", {"id": f"s{number}", "aria-labelledby": f"s{number}-title"})
        ET.SubElement(section, "h2", {"id": f"s{number}-title"}).text = heading_text
        ET.SubElement(ET.SubElement(toc_list, "li"), "a", {"href": f"#s{number}"}).text = heading_text
        return section


def lead(paragraph: ET.Element) -> ET.Element:
    if paragraph.tag != "p":
        raise ValueError(f"Only paragraphs and one list may come before the first section, not <{paragraph.tag}>")
    paragraph.set("class", "lead")
    return paragraph


def meta(items: ET.Element) -> ET.Element:
    """`- Effective: date` and the like, as a list of terms."""
    terms = ET.Element("dl", {"class": "meta"})
    for item in items.findall("li"):
        term, separator, value = "".join(item.itertext()).partition(": ")
        if not separator:
            raise ValueError(f"A line before the first section must read 'Term: value': {term!r}")
        row = ET.SubElement(terms, "div")
        ET.SubElement(row, "dt").text = term
        ET.SubElement(row, "dd").text = value
    return terms


class PolicyLayoutExtension(Extension):
    def __init__(self, title: str, contents: str) -> None:
        super().__init__()
        self.title = title
        self.contents = contents

    def extendMarkdown(self, md: markdown.Markdown) -> None:  # noqa: N802 - Python-Markdown's name
        # After the inline patterns (20), so the text holds no placeholders, and before `prettify` (10).
        md.treeprocessors.register(PolicyLayout(md, self.title, self.contents), "pix_policy_layout", 15)


def logo() -> str:
    """The horizontal logo, inline, with its ink in the text's color, so one copy serves light and dark."""
    svg = resources.files(__package__).joinpath("pix-logo.svg").read_text(encoding="utf-8").strip()
    title = re.compile(r'<title id="t">[^<]*</title>')
    size = 'width="533" height="270" role="img" aria-labelledby="t"'
    if not (title.search(svg) and size in svg and 'fill="#16171A"' in svg):
        raise ValueError("pix-logo.svg is no longer the copy of branding/pix-horizontal.svg this page expects")
    svg = title.sub("", svg).replace(size, 'class="logo" role="img" aria-label="Pix"')
    return svg.replace('fill="#16171A"', 'fill="currentColor"')


def policy_text(language: str) -> str:
    """The policy's Markdown in [language], as the wiki copies it."""
    return resources.files(__package__).joinpath(LANGUAGES[language].file_name).read_text(encoding="utf-8")


def language_switch(language: str) -> str:
    """Both languages in the same order on each page, the page's own one marked as the current."""
    links = []
    for code, other in LANGUAGES.items():
        if code == language:
            links.append(f'<span aria-current="page">{other.name}</span>')
        else:
            links.append(f'<a href="{other.href}" hreflang="{code}" lang="{code}">{other.name}</a>')
    return "".join(links)


def render(language: str) -> str:
    """The page of [language]. The policy's first line, its title in bold as on the wiki, becomes the page's heading."""
    words = LANGUAGES[language]
    first, _, rest = policy_text(language).partition("\n")
    if not (first.startswith("**") and first.endswith("**")):
        raise ValueError(f"The {language} privacy policy must start with its title in bold")
    title = first.strip("*")
    content = markdown.markdown(
        rest,
        extensions=["tables", PolicyLayoutExtension(title, words.contents)],
        output_format="html",
    )
    return PAGE.substitute(
        lang=language,
        title=title,
        logo=LOGO,
        language_nav=words.language_nav,
        switch=language_switch(language),
        content=content,
    )


LOGO = logo()
PAGES = {language: render(language) for language in LANGUAGES}


@router.get("/privacy", response_class=HTMLResponse)
async def privacy_policy() -> HTMLResponse:
    """The Korean policy, the one that applies: the address the store links to."""
    return HTMLResponse(PAGES["ko"])


@router.get("/privacy/{language}", response_class=HTMLResponse)
async def privacy_policy_in(language: str) -> HTMLResponse:
    """The policy in `ko` or `en`, which the app asks for in its screens' language."""
    page = PAGES.get(language)
    if page is None:
        raise HTTPException(404)
    return HTMLResponse(page)
