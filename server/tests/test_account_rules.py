# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""The Pix ID and password rules at their edges (R&S FR-5.2, FR-5.3, FR-5.11).

Partitions:
- Pix ID length: 3 (too short), 4, 20, 21 (too long); characters: a–z, 0–9, ".", "_" valid; a capital, a space,
  a hyphen, a Korean letter invalid
- password length: 7, 8, 64, 65; letters and digits: both, letters only, digits only, non-ASCII letters with a digit;
  the Pix ID itself in any case
- lookup text: capitals and surrounding spaces
"""

import pytest

from pix_server.accounts.rules import normalize_pix_id, password_problem, pix_id_problem


@pytest.mark.parametrize("pix_id", ["abcd", "a" * 20, "dongje.p", "jun_hyeong", "user.2026", "____"])
def test_pix_id_of_four_to_twenty_allowed_characters_is_valid(pix_id):
    assert pix_id_problem(pix_id) is None


@pytest.mark.parametrize(
    "pix_id, rule",
    [
        ("", "at least 4"),
        ("abc", "at least 4"),
        ("a" * 21, "at most 20"),
        ("Dongje", "lowercase"),
        ("dong je", "lowercase"),
        ("dong-je", "lowercase"),
        ("동제박박", "lowercase"),
    ],
)
def test_pix_id_breaking_a_rule_names_it(pix_id, rule):
    assert rule in pix_id_problem(pix_id)


@pytest.mark.parametrize("password", ["abcdef12", "a1" * 32, "비밀번호는1234", "correct horse 9"])
def test_password_of_eight_to_sixty_four_with_a_letter_and_a_digit_is_valid(password):
    assert password_problem(password, "dongje.p") is None


@pytest.mark.parametrize(
    "password, rule",
    [
        ("abcde12", "at least 8"),
        ("a1" * 32 + "b", "at most 64"),
        ("abcdefgh", "one letter and one digit"),
        ("12345678", "one letter and one digit"),
        ("!!!!????", "one letter and one digit"),
        ("Dongje.P2", "Pix ID"),
    ],
)
def test_password_breaking_a_rule_names_it(password, rule):
    assert rule in password_problem(password, "dongje.p2")


def test_lookup_text_drops_capitals_and_surrounding_spaces():
    assert normalize_pix_id("  Dongje.P\n") == "dongje.p"
