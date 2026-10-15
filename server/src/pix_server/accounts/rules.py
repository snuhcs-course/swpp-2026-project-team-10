# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""The rules for Pix IDs and passwords (R&S FR-5.2, FR-5.3, FR-5.11). Each check returns the broken rule, which
the API sends as the error message, or None."""

import re

PIX_ID_MIN, PIX_ID_MAX = 4, 20
PIX_ID_CHARACTERS = re.compile(r"[a-z0-9._]+")
PASSWORD_MIN, PASSWORD_MAX = 8, 64
DISPLAY_NAME_MAX = 30


def normalize_pix_id(text: str) -> str:
    """What a person typed to find a Pix ID: a Pix ID has no capitals or spaces, so they cannot be meant."""
    return text.strip().lower()


def pix_id_problem(pix_id: str) -> str | None:
    if len(pix_id) < PIX_ID_MIN:
        return f"A Pix ID needs at least {PIX_ID_MIN} characters"
    if len(pix_id) > PIX_ID_MAX:
        return f"A Pix ID has at most {PIX_ID_MAX} characters"
    if not PIX_ID_CHARACTERS.fullmatch(pix_id):
        return 'A Pix ID uses only lowercase letters, digits, "." and "_"'
    return None


def password_problem(password: str, pix_id: str) -> str | None:
    if len(password) < PASSWORD_MIN:
        return f"A password needs at least {PASSWORD_MIN} characters"
    if len(password) > PASSWORD_MAX:
        return f"A password has at most {PASSWORD_MAX} characters"
    if not any(char.isalpha() for char in password) or not any(char.isdigit() for char in password):
        return "A password needs at least one letter and one digit"
    if password.lower() == pix_id.lower():
        return "A password must not be the same as the Pix ID"
    return None
