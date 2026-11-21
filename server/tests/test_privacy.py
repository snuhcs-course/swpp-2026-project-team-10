# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""The privacy policy's public pages (R&S FR-10.7, Design 2.5.3).

Partitions:
- language: none (Korean, the one that applies); `ko`; `en`; another one (404)
- what a page holds: the policy's title as its heading, its tables, and a link to the other language; no sign-in
- the text: the same as the wiki's copy in each language (only where the wiki is checked out beside the server)
"""

from pathlib import Path

import pytest

from pix_server.privacy.router import LANGUAGES, policy_text

WIKI = Path(__file__).resolve().parents[2] / "wiki"
WIKI_PAGES = {"ko": "Privacy-Policy.md", "en": "Privacy-Policy-–-English.md"}


def page(client, path):
    response = client.get(path)
    assert response.status_code == 200, response.text
    assert response.headers["content-type"].startswith("text/html")
    return response.text


def test_privacy_without_a_language_is_the_korean_policy(client):
    text = page(client, "/privacy")
    assert '<html lang="ko">' in text
    assert "<h1>Pix 개인정보 처리방침</h1>" in text
    assert text.count("<table>") == 3
    assert '<a href="/privacy/en" hreflang="en" lang="en">English</a>' in text
    assert page(client, "/privacy/ko") == text


def test_privacy_in_english(client):
    text = page(client, "/privacy/en")
    assert '<html lang="en">' in text
    assert "<h1>Pix Privacy Policy</h1>" in text
    assert text.count("<table>") == 3
    assert '<a href="/privacy" hreflang="ko" lang="ko">한국어</a>' in text


@pytest.mark.parametrize("path", ["/privacy/fr", "/privacy/en-US"])
def test_privacy_in_another_language_is_not_found(client, path):
    assert client.get(path).status_code == 404


def test_privacy_needs_no_sign_in(client):
    assert client.get("/privacy", headers={"Authorization": "Bearer not-a-jwt"}).status_code == 200


@pytest.mark.parametrize("language", list(LANGUAGES))
def test_the_wiki_holds_the_same_policy(language):
    """The wiki's page is a line about it, then the same text. The image's test stage has no wiki, so it skips."""
    wiki_page = WIKI / WIKI_PAGES[language]
    if not wiki_page.exists():
        pytest.skip("the wiki is not checked out beside the server")
    intro, _, copy = wiki_page.read_text(encoding="utf-8").partition("\n\n")
    assert intro.startswith("*The privacy policy of Pix")
    assert copy == policy_text(language), f"Copy server/src/pix_server/privacy/ to wiki/{WIKI_PAGES[language]}"
