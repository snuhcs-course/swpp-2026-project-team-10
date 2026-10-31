# AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park and Dongje Park
"""The prompts for the image, suggestion, and tip models and the default poses, read from prompts.toml (Design
Documentation 2.6.3)."""

import tomllib
from dataclasses import dataclass
from pathlib import Path

from pix_server.errors import ApiError

PROMPTS_FILE = Path(__file__).with_name("prompts.toml")
PLACEHOLDER = "{pose}"
DESCRIPTION_PLACEHOLDER = "{description}"  # in the variation prompt
DESCRIPTION_MAX_LENGTH = 100  # the app's field stops at the same length (FR-4.8)
POSE_COUNT = 4  # candidates per set, so poses per suggestion (FR-4.3)


@dataclass(frozen=True)
class DefaultPose:
    id: str
    label: str
    # Sent to the image API only; an app from before Iteration 2 sees the id and label.
    pose: str


@dataclass(frozen=True)
class SelfiePrompts:
    """The prompts for a scene photo that is a selfie (FR-1.14), and the poses that stand in for the suggestions."""

    pose: str
    suggestion: str
    variation: str
    default_poses: tuple[str, ...]


@dataclass(frozen=True)
class Prompts:
    pose: str
    suggestion: str
    variation: str
    tip: str
    # Added after `tip` for a phone whose screens are in Korean (FR-10.2).
    tip_korean: str
    default_poses: dict[str, DefaultPose]
    selfie: SelfiePrompts


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

    def with_placeholder(table: dict, key: str, where: str, placeholder: str) -> str:
        prompt = text(table, key, where)
        if prompt.count(placeholder) != 1:
            raise ValueError(f"{path.name}: {where}{key} must hold {placeholder} exactly once")
        return prompt

    with path.open("rb") as file:
        data = tomllib.load(file)
    unknown = set(data) - {
        "pose_prompt",
        "suggestion_prompt",
        "variation_prompt",
        "tip_prompt",
        "tip_korean",
        "default_poses",
        "selfie",
    }
    if unknown:
        raise ValueError(f"{path.name}: unknown keys {', '.join(sorted(unknown))}")
    pose = with_placeholder(data, "pose_prompt", "", PLACEHOLDER)
    suggestion = text(data, "suggestion_prompt", "")
    variation = with_placeholder(data, "variation_prompt", "", DESCRIPTION_PLACEHOLDER)
    tip = text(data, "tip_prompt", "")
    tip_korean = text(data, "tip_korean", "")
    entries = data.get("default_poses")
    if not isinstance(entries, list) or len(entries) != POSE_COUNT:
        raise ValueError(f"{path.name}: exactly {POSE_COUNT} [[default_poses]] are needed")
    poses: dict[str, DefaultPose] = {}
    for number, entry in enumerate(entries, 1):
        where = f"default_poses #{number}: "
        if not isinstance(entry, dict):
            raise ValueError(f"{path.name}: {where}must be a table with id, label, and pose")
        unknown = set(entry) - {"id", "label", "pose"}
        if unknown:
            raise ValueError(f"{path.name}: {where}unknown keys {', '.join(sorted(unknown))}")
        default = DefaultPose(text(entry, "id", where), text(entry, "label", where), text(entry, "pose", where))
        if default.id in poses:
            raise ValueError(f"{path.name}: {where}id {default.id!r} is used twice")
        if len(default.pose) > DESCRIPTION_MAX_LENGTH:
            raise ValueError(f"{path.name}: {where}pose must be at most {DESCRIPTION_MAX_LENGTH} characters")
        poses[default.id] = default
    return Prompts(pose, suggestion, variation, tip, tip_korean, poses, load_selfie(data.get("selfie"), path))


def load_selfie(table: object, path: Path) -> SelfiePrompts:
    """The [selfie] table of the prompts file: the three prompts and POSE_COUNT default poses, each a different string
    of at most DESCRIPTION_MAX_LENGTH characters. Raises ValueError like load_prompts."""
    if not isinstance(table, dict):
        raise ValueError(f"{path.name}: a [selfie] table is needed")
    unknown = set(table) - {"pose_prompt", "suggestion_prompt", "variation_prompt", "default_poses"}
    if unknown:
        raise ValueError(f"{path.name}: selfie: unknown keys {', '.join(sorted(unknown))}")

    def prompt(key: str, placeholder: str | None = None) -> str:
        value = table.get(key)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{path.name}: selfie.{key} must be a non-empty string")
        if placeholder is not None and value.count(placeholder) != 1:
            raise ValueError(f"{path.name}: selfie.{key} must hold {placeholder} exactly once")
        return one_line(value)

    pose = prompt("pose_prompt", PLACEHOLDER)
    suggestion = prompt("suggestion_prompt")
    variation = prompt("variation_prompt", DESCRIPTION_PLACEHOLDER)
    entries = table.get("default_poses")
    if not isinstance(entries, list) or len(entries) != POSE_COUNT:
        raise ValueError(f"{path.name}: selfie.default_poses must be a list of {POSE_COUNT} poses")
    defaults: list[str] = []
    for number, entry in enumerate(entries, 1):
        where = f"{path.name}: selfie.default_poses #{number}"
        default = one_line(entry) if isinstance(entry, str) else ""
        if not default:
            raise ValueError(f"{where} must be a non-empty string")
        if len(default) > DESCRIPTION_MAX_LENGTH:
            raise ValueError(f"{where} must be at most {DESCRIPTION_MAX_LENGTH} characters")
        if default.casefold() in {kept.casefold() for kept in defaults}:
            raise ValueError(f"{where} repeats another pose")
        defaults.append(default)
    return SelfiePrompts(pose, suggestion, variation, tuple(defaults))


PROMPTS = load_prompts()
DEFAULT_POSES = PROMPTS.default_poses


def checked_pose(pose: str, field: str = "description") -> str:
    """A pose as a request sent it, a description, a suggestion, or a default pose, the way the prompts take it: runs
    of whitespace, line breaks included, become one space, and a pose that is blank or longer than
    DESCRIPTION_MAX_LENGTH characters is refused, naming the request's `field`."""
    text = one_line(pose)
    if not text:
        raise ApiError(400, "INVALID_REQUEST", f"{field}: must not be blank")
    if len(text) > DESCRIPTION_MAX_LENGTH:
        raise ApiError(400, "INVALID_REQUEST", f"{field}: at most {DESCRIPTION_MAX_LENGTH} characters")
    return text


def pose_prompt(pose: str, selfie: bool = False) -> str:
    """The image prompt for a pose (checked_pose), the selfie one for a selfie's scene (FR-1.14)."""
    template = PROMPTS.selfie.pose if selfie else PROMPTS.pose
    return template.replace(PLACEHOLDER, checked_pose(pose))


def variation_prompt(description: str, selfie: bool = False) -> str:
    """The suggestion model's prompt for three variations of a description (checked_pose), for a selfie's scene or
    another."""
    template = PROMPTS.selfie.variation if selfie else PROMPTS.variation
    return template.replace(DESCRIPTION_PLACEHOLDER, checked_pose(description))
