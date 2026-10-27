# AI-generated with Claude Code, 2026-10-26, reviewed by Jaewan Park and Dongje Park
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
class Prompts:
    pose: str
    suggestion: str
    variation: str
    tip: str
    default_poses: dict[str, DefaultPose]


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

    with path.open("rb") as file:
        data = tomllib.load(file)
    unknown = set(data) - {"pose_prompt", "suggestion_prompt", "variation_prompt", "tip_prompt", "default_poses"}
    if unknown:
        raise ValueError(f"{path.name}: unknown keys {', '.join(sorted(unknown))}")
    pose = text(data, "pose_prompt", "")
    if pose.count(PLACEHOLDER) != 1:
        raise ValueError(f"{path.name}: pose_prompt must hold {PLACEHOLDER} exactly once")
    suggestion = text(data, "suggestion_prompt", "")
    variation = text(data, "variation_prompt", "")
    if variation.count(DESCRIPTION_PLACEHOLDER) != 1:
        raise ValueError(f"{path.name}: variation_prompt must hold {DESCRIPTION_PLACEHOLDER} exactly once")
    tip = text(data, "tip_prompt", "")
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
    return Prompts(pose, suggestion, variation, tip, poses)


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


def pose_prompt(pose: str) -> str:
    """The image prompt for a pose (checked_pose)."""
    return PROMPTS.pose.replace(PLACEHOLDER, checked_pose(pose))


def variation_prompt(description: str) -> str:
    """The suggestion model's prompt for three variations of a description (checked_pose)."""
    return PROMPTS.variation.replace(DESCRIPTION_PLACEHOLDER, checked_pose(description))
