# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""`.env.example` against `Settings`: deploy.sh reads the example to tell which settings a server must set, so it has
to list every setting, with the right defaults.

Partitions: a setting with a default (commented, with that default); a setting a server must provide (uncommented,
empty); a setting set elsewhere or optional (commented); a name the example lists that Settings lacks.
"""

import logging
import os
import re
from pathlib import Path

import pytest
from pydantic import SecretStr

from pix_server.config import Settings
from pix_server.main import warn_about_missing_settings

EXAMPLE = Path(__file__).resolve().parents[1] / ".env.example"
LINE = re.compile(r"^(#\s*)?([A-Z][A-Z0-9_]*)=(.*)$")
# Settings a server must provide: the example leaves them empty.
REQUIRED = {"OPENROUTER_API_KEY", "PIX_GOOGLE_CLIENT_ID", "PIX_TURN_KEY_ID", "PIX_TURN_API_TOKEN"}
# Optional settings set elsewhere on a server, with no default worth showing.
SET_ELSEWHERE = {"PIX_REDIS_URL", "PIX_DATABASE_URL", "PIX_JWT_SECRET", "PIX_FCM_CREDENTIALS_FILE"}


@pytest.fixture(autouse=True)
def clean_environment(monkeypatch):
    """Settings read the environment, so a developer's own PIX_ variables or API key must not leak in."""
    for name in list(os.environ):
        if name.upper().startswith("PIX_") or name.upper() == "OPENROUTER_API_KEY":
            monkeypatch.delenv(name)


def env_name(field: str) -> str:
    alias = Settings.model_fields[field].validation_alias
    return alias if isinstance(alias, str) else f"PIX_{field.upper()}"


def example() -> dict[str, tuple[bool, str]]:
    """Each name the example lists: whether it is commented, and its value."""
    entries = {}
    for line in EXAMPLE.read_text().splitlines():
        match = LINE.match(line)
        if match:
            entries[match.group(2)] = (match.group(1) is not None, match.group(3))
    return entries


def test_example_lists_every_setting_and_nothing_else():
    assert set(example()) == {env_name(field) for field in Settings.model_fields}


def test_example_leaves_empty_exactly_the_settings_a_server_must_provide():
    uncommented = {name for name, (commented, _) in example().items() if not commented}
    assert uncommented == REQUIRED
    assert all(example()[name][1] == "" for name in REQUIRED)
    assert all(
        Settings.model_fields[field].default is None for field in Settings.model_fields if env_name(field) in REQUIRED
    )


def test_commented_defaults_match_the_code():
    defaults = Settings(_env_file=None)
    for field in Settings.model_fields:
        name = env_name(field)
        if name in REQUIRED or name in SET_ELSEWHERE:
            continue
        commented, value = example()[name]
        assert commented, name
        assert str(getattr(defaults, field)) in {value, value + ".0"}, name


def test_startup_warns_about_each_unset_setting_a_server_must_provide(caplog):
    with caplog.at_level(logging.WARNING, logger="pix_server.main"):
        warn_about_missing_settings(Settings(_env_file=None))
    assert "OPENROUTER_API_KEY" in caplog.text
    assert "PIX_GOOGLE_CLIENT_ID" in caplog.text
    assert "PIX_TURN_KEY_ID" in caplog.text
    assert "PIX_FCM_CREDENTIALS_FILE" in caplog.text
    caplog.clear()
    configured = Settings(
        _env_file=None,
        openrouter_api_key=SecretStr("key"),
        google_client_id="client",
        turn_key_id="turn-key",
        turn_api_token=SecretStr("turn-token"),
        fcm_credentials_file="/run/pix-secrets/fcm-sender.json",
    )
    with caplog.at_level(logging.WARNING, logger="pix_server.main"):
        warn_about_missing_settings(configured)
    assert caplog.text == ""
