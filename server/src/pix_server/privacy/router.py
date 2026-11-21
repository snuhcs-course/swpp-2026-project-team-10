# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""The privacy policy as public web pages (R&S FR-10.7, Design 2.5.3): `/privacy` in Korean, the policy's own language,
and `/privacy/en` in English. The app shows the same pages in Settings and on its sign-up screens, and the store links
to `/privacy`, so the policy has one text: `policy.ko.md` and `policy.en.md` here, which the wiki's *Privacy Policy*
pages copy (`tests/test_privacy.py`).

Both pages are rendered once, when the module is imported, so a file that cannot be read fails the tests and the
image's build instead of a request. They name nobody and read nothing from the request.
"""

from importlib import resources
from string import Template

import markdown
from fastapi import APIRouter, HTTPException
from fastapi.responses import HTMLResponse

router = APIRouter()

PAGE = Template("""<!doctype html>
<html lang="$lang">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
:root { color-scheme: light dark; --ground: #ffffff; --text: #1b1c1f; --muted: #5d6677; --line: #d9dce3;
  --link: #8a5a00; }
@media (prefers-color-scheme: dark) {
  :root { --ground: #0b0b0d; --text: #f4f4f2; --muted: #a3a7ad; --line: #2c2f35; --link: #f2c14e; }
}
body { margin: 0; background: var(--ground); color: var(--text); line-height: 1.6;
  font-family: system-ui, -apple-system, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif; }
main { max-width: 760px; margin: 0 auto; padding: 16px 16px 48px; overflow-wrap: anywhere; }
nav { text-align: right; font-size: 15px; }
a { color: var(--link); }
h1 { font-size: 26px; line-height: 1.3; margin: 16px 0 12px; }
h2 { font-size: 19px; margin: 32px 0 8px; }
table { display: block; overflow-x: auto; border-collapse: collapse; font-size: 14px; margin: 12px 0; }
th, td { border: 1px solid var(--line); padding: 8px 10px; text-align: left; vertical-align: top; min-width: 96px; }
th { color: var(--muted); font-weight: 600; }
code { font-size: 0.95em; }
</style>
</head>
<body>
<main>
<nav><a href="$other_href" hreflang="$other_lang" lang="$other_lang">$other_label</a></nav>
$content
</main>
</body>
</html>
""")

# Each language's file, title, and the link to the other language.
LANGUAGES = {
    "ko": ("policy.ko.md", "/privacy/en", "en", "English"),
    "en": ("policy.en.md", "/privacy", "ko", "한국어"),
}


def policy_text(language: str) -> str:
    """The policy's Markdown in [language], as the wiki copies it."""
    file_name = LANGUAGES[language][0]
    return resources.files(__package__).joinpath(file_name).read_text(encoding="utf-8")


def render(language: str) -> str:
    """The page of [language]. The policy's first line, its title in bold as on the wiki, becomes the page's heading."""
    _, other_href, other_lang, other_label = LANGUAGES[language]
    first, _, rest = policy_text(language).partition("\n")
    if not (first.startswith("**") and first.endswith("**")):
        raise ValueError(f"The {language} privacy policy must start with its title in bold")
    title = first.strip("*")
    content = markdown.markdown(f"# {title}\n{rest}", extensions=["tables"], output_format="html")
    return PAGE.substitute(
        lang=language,
        title=title,
        content=content,
        other_href=other_href,
        other_lang=other_lang,
        other_label=other_label,
    )


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
