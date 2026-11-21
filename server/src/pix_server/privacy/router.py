# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""The privacy policy as public web pages (R&S FR-10.7, Design 2.5.3): `/privacy` in Korean, the policy's own language,
and `/privacy/en` in English. The app shows the same pages in Settings and on its sign-up agreement, and the store links
to `/privacy`, so the policy has one text: `policy.ko.md` and `policy.en.md` here, which the wiki's *Privacy Policy*
pages copy (`tests/test_privacy.py`).

A page lays the text out as a document: the Pix logo (`pix-logo.svg`, a copy of `branding/pix-horizontal.svg`) and the
language switch, the title with the opening paragraph, the effective date and the contact, the contents, and one part
per numbered section. On a narrow screen each table row becomes a block of labeled lines. The page needs no JavaScript,
which the app's WebView keeps off, and loads nothing from elsewhere.

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
<title>$title</title>
<link rel="alternate" hreflang="ko" href="/privacy">
<link rel="alternate" hreflang="en" href="/privacy/en">
<style>
:root { color-scheme: light dark; --ground: #f4f4f2; --surface: #ffffff; --head: #f8f8f7; --line: #e2e3e6;
  --text: #16171a; --muted: #4f535b; --faint: #6b6f77; --accent: #f2c14e; --on-accent: #16171a;
  --accent-soft: rgba(242, 193, 78, 0.18); --link: #855500; }
@media (prefers-color-scheme: dark) {
  :root { --ground: #0b0b0d; --surface: #16171a; --head: #1c1d21; --line: rgba(255, 255, 255, 0.12);
    --text: #f4f4f2; --muted: #b8bbc0; --faint: #8a8e94; --accent-soft: rgba(242, 193, 78, 0.16); --link: #f2c14e; }
}
* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; scroll-behavior: smooth; }
body { margin: 0; background: var(--ground); color: var(--text); font-size: 16px; line-height: 1.65;
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif;
  overflow-wrap: anywhere; }
html[lang="ko"] body { word-break: keep-all; }
a { color: var(--link); text-underline-offset: 3px; }
.bar { background: var(--surface); border-bottom: 1px solid var(--line); }
.bar-in, main, .foot-in { max-width: 760px; margin: 0 auto; padding-left: 20px; padding-right: 20px; }
.bar-in { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 60px; }
.logo { display: block; height: 26px; width: auto; color: var(--text); }
.lang { display: flex; padding: 3px; border: 1px solid var(--line); border-radius: 999px; font-size: 14px;
  line-height: 1.4; white-space: nowrap; }
.lang a, .lang span { padding: 5px 12px; border-radius: 999px; text-decoration: none; }
.lang a { color: var(--muted); }
.lang span { background: var(--accent); color: var(--on-accent); font-weight: 600; }
main { padding-top: 40px; padding-bottom: 56px; }
.hero h1 { margin: 0 0 14px; font-size: 32px; line-height: 1.25; letter-spacing: -0.015em; }
.lead { margin: 0 0 22px; color: var(--muted); font-size: 17px; }
.meta { display: flex; flex-wrap: wrap; gap: 6px 28px; margin: 0; padding-top: 16px; border-top: 1px solid var(--line);
  font-size: 14px; }
.meta div { display: flex; gap: 10px; }
.meta dt { color: var(--faint); }
.meta dd { margin: 0; font-weight: 600; }
.toc { margin: 36px 0 4px; padding: 18px 20px 14px; background: var(--surface); border: 1px solid var(--line);
  border-radius: 16px; }
.toc h2 { margin: 0 0 6px; color: var(--faint); font-size: 13px; font-weight: 600; letter-spacing: 0.06em;
  text-transform: uppercase; }
.toc ol { margin: 0; padding: 0; list-style: none; }
.toc a { display: flex; align-items: baseline; gap: 12px; padding: 7px 0; color: var(--text); text-decoration: none; }
.toc a:hover { color: var(--link); }
.num { display: inline-block; flex: none; min-width: 26px; padding: 2px 6px; border-radius: 8px; background:
  var(--accent-soft); color: var(--link); font-size: 13px; font-weight: 700; line-height: 1.6; text-align: center;
  font-variant-numeric: tabular-nums; }
section { padding-top: 40px; scroll-margin-top: 8px; }
section h2 { display: flex; align-items: baseline; gap: 12px; margin: 0 0 14px; font-size: 21px; line-height: 1.4;
  letter-spacing: -0.01em; }
section h2 .num { position: relative; top: -2px; background: var(--accent); color: var(--on-accent); }
section p, section ul { margin: 0 0 14px; }
section ul { padding-left: 22px; }
section li { margin: 7px 0; padding-left: 2px; }
section li::marker { color: var(--faint); }
strong { font-weight: 650; }
code { padding: 1px 6px; border: 1px solid var(--line); border-radius: 6px; background: var(--head);
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 0.88em; }
.table { margin: 16px 0 20px; overflow: hidden; background: var(--surface); border: 1px solid var(--line);
  border-radius: 14px; }
table { width: 100%; border-collapse: collapse; font-size: 14.5px; line-height: 1.55; }
th { padding: 10px 14px; background: var(--head); border-bottom: 1px solid var(--line); color: var(--muted);
  font-size: 12.5px; font-weight: 600; letter-spacing: 0.02em; text-align: left; }
td { padding: 12px 14px; border-top: 1px solid var(--line); vertical-align: top; }
tbody tr:first-child td { border-top: 0; }
td:first-child { font-weight: 600; }
footer { border-top: 1px solid var(--line); color: var(--faint); font-size: 13px; }
.foot-in { padding-top: 20px; padding-bottom: 28px; }
@media (max-width: 640px) {
  .bar-in, main, .foot-in { padding-left: 16px; padding-right: 16px; }
  main { padding-top: 30px; }
  .hero h1 { font-size: 27px; }
  .lead { font-size: 16px; }
  .toc { padding: 16px 16px 12px; }
  section h2 { font-size: 19px; }
  .table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  .table table, .table tbody, .table tr, .table td { display: block; }
  .table tr { padding: 14px 16px; border-top: 1px solid var(--line); }
  .table tbody tr:first-child { border-top: 0; }
  .table td { padding: 0; border: 0; }
  .table td + td { margin-top: 8px; }
  .table td:first-child { font-size: 15.5px; }
  .table td + td::before { content: attr(data-label); display: block; color: var(--faint); font-size: 12.5px;
    font-weight: 600; letter-spacing: 0.02em; }
}
@media print {
  .lang, .toc { display: none; }
  .bar, .table { background: none; }
}
</style>
</head>
<body>
<div class="bar"><div class="bar-in">
$logo
<nav class="lang" aria-label="$language_nav">$switch</nav>
</div></div>
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
    (the paragraph as its lead, the list as the effective date and the contact), each `## n. Name` starts a section
    with its number apart, the contents link to them, and every table cell carries its column's name, which a narrow
    screen shows as the line's label. The roles keep the tables tables for a screen reader when CSS lays them out as
    blocks."""

    def __init__(self, md: markdown.Markdown, title: str, contents: str) -> None:
        super().__init__(md)
        self.title = title
        self.contents = contents

    def run(self, root: ET.Element) -> None:
        blocks = list(root)
        for block in blocks:
            root.remove(block)
        hero = ET.SubElement(root, "header", {"class": "hero"})
        ET.SubElement(hero, "h1").text = self.title
        toc = ET.SubElement(root, "nav", {"class": "toc", "aria-labelledby": "contents"})
        ET.SubElement(toc, "h2", {"id": "contents"}).text = self.contents
        toc_list = ET.SubElement(toc, "ol")
        section: ET.Element | None = None
        for block in blocks:
            if block.tag == "h2":
                section = self.section(root, toc_list, "".join(block.itertext()).strip())
            elif section is None:
                hero.append(meta(block) if block.tag == "ul" else lead(block))
            elif block.tag == "table":
                labeled(block)
                ET.SubElement(section, "div", {"class": "table"}).append(block)
            else:
                section.append(block)
        if section is None:
            raise ValueError("The privacy policy has no numbered sections")

    @staticmethod
    def section(root: ET.Element, toc_list: ET.Element, heading_text: str) -> ET.Element:
        match = SECTION_HEADING.fullmatch(heading_text)
        if match is None:
            raise ValueError(f"A section's heading must start with its number: {heading_text!r}")
        number, name = match.groups()
        section = ET.SubElement(root, "section", {"id": f"s{number}", "aria-labelledby": f"s{number}-title"})
        heading = ET.SubElement(section, "h2", {"id": f"s{number}-title"})
        ET.SubElement(heading, "span", {"class": "num"}).text = number
        heading[-1].tail = name
        link = ET.SubElement(ET.SubElement(toc_list, "li"), "a", {"href": f"#s{number}"})
        ET.SubElement(link, "span", {"class": "num"}).text = number
        link[-1].tail = name
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


def labeled(table: ET.Element) -> None:
    columns = ["".join(cell.itertext()).strip() for cell in table.iter("th")]
    for part, role in (("thead", "rowgroup"), ("tbody", "rowgroup")):
        for group in table.findall(part):
            group.set("role", role)
    for row in table.iter("tr"):
        row.set("role", "row")
        for cell in row.findall("th"):
            cell.set("role", "columnheader")
        for column, cell in zip(columns, row.findall("td"), strict=False):
            cell.set("role", "cell")
            cell.set("data-label", column)


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
