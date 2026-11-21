# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""The privacy policy's public pages (R&S FR-10.7, Design 2.5.3).

Partitions:
- language: none (Korean, the one that applies); `ko`; `en`; another one (404)
- what a page holds: the policy's title as its heading, its tables, the language switch with its own language marked;
  the contents linking to every numbered section; each table cell labeled with its column, and a table of more than
  three columns marked to show as blocks on a phone; the logo inline; nothing
  loaded from elsewhere and no script; no sign-in
- a policy laid out another way (a section without its number, a line before the sections that is not "Term: value"):
  refused when the pages are made
- the text: the same as the wiki's copy in each language, and the logo the same as `branding/`'s (only where those
  are checked out beside the server)
"""

import re
from pathlib import Path

import markdown
import pytest

from pix_server.privacy.router import LANGUAGES, PolicyLayoutExtension, policy_text

REPOSITORY = Path(__file__).resolve().parents[2]
WIKI = REPOSITORY / "wiki"
WIKI_PAGES = {"ko": "Privacy-Policy.md", "en": "Privacy-Policy-–-English.md"}


def page(client, path):
    response = client.get(path)
    assert response.status_code == 200, response.text
    assert response.headers["content-type"].startswith("text/html")
    return response.text


def test_privacy_without_a_language_is_the_korean_policy(client):
    text = page(client, "/privacy")
    assert '<html lang="ko">' in text
    assert "<h1>개인정보 처리방침</h1>" in text
    assert "<title>개인정보 처리방침 | Pix</title>" in text
    assert text.count("<table>") == 3
    assert '<span aria-current="page">한국어</span><a href="/privacy/en" hreflang="en" lang="en">English</a>' in text
    assert page(client, "/privacy/ko") == text


def test_privacy_in_english(client):
    text = page(client, "/privacy/en")
    assert '<html lang="en">' in text
    assert "<h1>Privacy Policy</h1>" in text
    assert text.count("<table>") == 3
    assert '<a href="/privacy" hreflang="ko" lang="ko">한국어</a><span aria-current="page">English</span>' in text


@pytest.mark.parametrize("path", ["/privacy", "/privacy/en"])
def test_a_page_has_contents_labeled_tables_and_the_logo_and_loads_nothing_else(client, path):
    text = page(client, path)
    sections = re.findall(r'<section aria-labelledby="s(\d+)-title" id="s\1">', text)
    assert sections == [str(n) for n in range(1, 10)]
    for number in sections:
        assert f'<a href="#s{number}">{number}. ' in text
        assert f'<h2 id="s{number}-title">{number}. ' in text
    assert text.count('<div class="table">') == 2 and text.count('<div class="table wide">') == 1
    cells = re.findall(r"<td( [^>]*)?>", text)
    assert cells and all('data-label="' in (attributes or "") for attributes in cells)
    assert (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -14 533 270" class="logo" role="img" aria-label="Pix">'
        in text
    )
    assert 'fill="currentColor"' in text and "#16171A" not in text
    assert "<script" not in text
    assert not re.search(r'(src|href)="(https?:)?//', text)


@pytest.mark.parametrize("path", ["/privacy/fr", "/privacy/en-US"])
def test_privacy_in_another_language_is_not_found(client, path):
    assert client.get(path).status_code == 404


def test_privacy_needs_no_sign_in(client):
    assert client.get("/privacy", headers={"Authorization": "Bearer not-a-jwt"}).status_code == 200


@pytest.mark.parametrize(
    ("policy", "problem"),
    [
        ("Intro.\n\n## Personal information\n\nText.\n", "must start with its number"),
        ("Intro.\n\n- Effective November 21, 2026\n\n## 1. Information\n\nText.\n", "must read 'Term: value'"),
        ("Intro.\n\n- Effective: November 21, 2026\n", "no numbered sections"),
    ],
)
def test_a_policy_laid_out_another_way_is_refused(policy, problem):
    with pytest.raises(ValueError, match=problem):
        markdown.markdown(policy, extensions=["tables", PolicyLayoutExtension("Privacy Policy", "Contents")])


def test_the_logo_is_the_brandings():
    """The page's logo copies `branding/`'s, which the image's test stage does not have."""
    branding = REPOSITORY / "branding" / "pix-horizontal.svg"
    if not branding.exists():
        pytest.skip("branding/ is not checked out beside the server")
    copy = REPOSITORY / "server" / "src" / "pix_server" / "privacy" / "pix-logo.svg"
    assert copy.read_bytes() == branding.read_bytes(), "Copy branding/pix-horizontal.svg to privacy/pix-logo.svg"


@pytest.mark.parametrize("language", list(LANGUAGES))
def test_the_wiki_holds_the_same_policy(language):
    """The wiki's page is a line about it, then the same text. The image's test stage has no wiki, so it skips."""
    wiki_page = WIKI / WIKI_PAGES[language]
    if not wiki_page.exists():
        pytest.skip("the wiki is not checked out beside the server")
    intro, _, copy = wiki_page.read_text(encoding="utf-8").partition("\n\n")
    assert intro.startswith("*The privacy policy of Pix")
    assert copy == policy_text(language), f"Copy server/src/pix_server/privacy/ to wiki/{WIKI_PAGES[language]}"
