# AI-generated with Claude Code, 2026-10-20, reviewed by Jaewan Park
"""Pose templates and the prompts that wrap a template or a description, read from prompts.toml (Design
Documentation 2.6.3)."""

import tomllib
from dataclasses import dataclass
from pathlib import Path

from pix_server.errors import ApiError

PROMPTS_FILE = Path(__file__).with_name("prompts.toml")
PLACEHOLDER = "{pose}"
DESCRIPTION_MAX_LENGTH = 100  # the app's field stops at the same length (FR-4.8)


@dataclass(frozen=True)
class PoseTemplate:
    id: str
    label: str
    # Sent to the image API only; the app sees the id and label.
    pose: str


@dataclass(frozen=True)
class Prompts:
    template: str
    description: str
    templates: dict[str, PoseTemplate]


def one_line(text: str) -> str:
    return " ".join(text.split())


def load_prompts(path: Path = PROMPTS_FILE) -> Prompts:
    """Read the prompts file and check it, so that a mistake in it stops the server when it starts instead of
    failing pose requests. Raises ValueError naming the file and the mistake."""

    def text(table: dict, key: str, where: str) -> str:
        value = table.get(key)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{path.name}: {where}{key} must be a non-empty string")
        return one_line(value)

    def prompt(key: str) -> str:
        value = text(data, key, "")
        if value.count(PLACEHOLDER) != 1:
            raise ValueError(f"{path.name}: {key} must hold {PLACEHOLDER} exactly once")
        return value

    with path.open("rb") as file:
        data = tomllib.load(file)
    unknown = set(data) - {"template_prompt", "description_prompt", "templates"}
    if unknown:
        raise ValueError(f"{path.name}: unknown keys {', '.join(sorted(unknown))}")
    entries = data.get("templates")
    if not isinstance(entries, list) or not entries:
        raise ValueError(f"{path.name}: at least one [[templates]] is needed")
    templates: dict[str, PoseTemplate] = {}
    for number, entry in enumerate(entries, 1):
        where = f"templates #{number}: "
        if not isinstance(entry, dict):
            raise ValueError(f"{path.name}: {where}must be a table with id, label, and pose")
        unknown = set(entry) - {"id", "label", "pose"}
        if unknown:
            raise ValueError(f"{path.name}: {where}unknown keys {', '.join(sorted(unknown))}")
        template = PoseTemplate(text(entry, "id", where), text(entry, "label", where), text(entry, "pose", where))
        if template.id in templates:
            raise ValueError(f"{path.name}: {where}id {template.id!r} is used twice")
        templates[template.id] = template
    return Prompts(prompt("template_prompt"), prompt("description_prompt"), templates)


PROMPTS = load_prompts()
TEMPLATES = PROMPTS.templates


def template_prompt(template: PoseTemplate) -> str:
    return PROMPTS.template.replace(PLACEHOLDER, template.pose)


def description_prompt(description: str) -> str:
    """The prompt for a description as the request sent it. Runs of whitespace, line breaks included, become one
    space; a description that is blank or longer than DESCRIPTION_MAX_LENGTH characters is refused."""
    text = one_line(description)
    if not text:
        raise ApiError(400, "INVALID_REQUEST", "description: must not be blank")
    if len(text) > DESCRIPTION_MAX_LENGTH:
        raise ApiError(400, "INVALID_REQUEST", f"description: at most {DESCRIPTION_MAX_LENGTH} characters")
    return PROMPTS.description.replace(PLACEHOLDER, text)
