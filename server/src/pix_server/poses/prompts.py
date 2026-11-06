# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
"""The prompts for the image, suggestion, and tip models, the description filter's and the person check's questions, and
the default poses, read from prompts.toml (Design Documentation 2.6.3)."""

import tomllib
from dataclasses import dataclass
from pathlib import Path

from pix_server.errors import ApiError

PROMPTS_FILE = Path(__file__).with_name("prompts.toml")
PLACEHOLDER = "{pose}"
DESCRIPTION_PLACEHOLDER = "{description}"  # in the variation prompt
DESCRIPTION_MAX_LENGTH = 100  # the app's field stops at the same length (FR-4.8)
POSE_COUNT = 4  # candidates per set, so poses per suggestion (FR-4.3)
# The description filter's yes/no questions, by the names its answers come back under.
FILTER_QUESTIONS = ("malicious", "injection")
# The person check's yes/no question, by the name its answer comes back under (FR-4.13).
PERSON_QUESTION = "person"
# Double quotation marks, typographic and full-width ones included. In a prompt each becomes a single one, so the user's
# words cannot close the quotation marks they are placed in.
QUOTES = str.maketrans(dict.fromkeys('"“”„‟″＂«»', "'"))


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
    # The description filter's questions, as the Decisions API takes them: {name: {type, instructions, criteria}}.
    filter_questions: dict[str, dict]
    # The person check's question, as the Decisions API takes it: {PERSON_QUESTION: {type, instructions}}.
    person_question: dict[str, dict]


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
        "filter",
        "person",
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
    selfie = load_selfie(data.get("selfie"), path)
    filter_questions = load_filter(data.get("filter"), path)
    person = load_person(data.get("person"), path)
    return Prompts(pose, suggestion, variation, tip, tip_korean, poses, selfie, filter_questions, person)


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


def load_filter(table: object, path: Path) -> dict[str, dict]:
    """The [filter] table of the prompts file: the context and the FILTER_QUESTIONS, each with its question and what yes
    and no mean, as the Decisions API takes them (each question's instructions are the context, a space, and the
    question). Raises ValueError like load_prompts."""
    if not isinstance(table, dict):
        raise ValueError(f"{path.name}: a [filter] table is needed")
    unknown = set(table) - {"context", *FILTER_QUESTIONS}
    if unknown:
        raise ValueError(f"{path.name}: filter: unknown keys {', '.join(sorted(unknown))}")

    def text(source: dict, key: str, where: str) -> str:
        value = source.get(key)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{path.name}: {where}.{key} must be a non-empty string")
        return one_line(value)

    context = text(table, "context", "filter")
    questions = {}
    for name in FILTER_QUESTIONS:
        where = f"filter.{name}"
        question = table.get(name)
        if not isinstance(question, dict):
            raise ValueError(f"{path.name}: a [{where}] table is needed")
        unknown = set(question) - {"question", "yes", "no"}
        if unknown:
            raise ValueError(f"{path.name}: {where}: unknown keys {', '.join(sorted(unknown))}")
        questions[name] = {
            "type": "noul",
            "instructions": f"{context} {text(question, 'question', where)}",
            "criteria": {"true": text(question, "yes", where), "false": text(question, "no", where)},
        }
    return questions


def load_person(table: object, path: Path) -> dict[str, dict]:
    """The [person] table of the prompts file: the yes/no question the decision model answers about the scene photo,
    which the request's `state` holds as `photo`, as the Decisions API takes it. Raises ValueError like
    load_prompts."""
    if not isinstance(table, dict):
        raise ValueError(f"{path.name}: a [person] table is needed")
    unknown = set(table) - {"question"}
    if unknown:
        raise ValueError(f"{path.name}: person: unknown keys {', '.join(sorted(unknown))}")
    question = table.get("question")
    if not isinstance(question, str) or not question.strip():
        raise ValueError(f"{path.name}: person.question must be a non-empty string")
    return {PERSON_QUESTION: {"type": "noul", "instructions": one_line(question)}}


PROMPTS = load_prompts()
DEFAULT_POSES = PROMPTS.default_poses


def checked_pose(pose: str, field: str = "description") -> str:
    """A pose as a request sent it, a description, a suggestion, or a default pose, the way the prompts take it: runs
    of whitespace, line breaks included, become one space, and double quotation marks single ones (QUOTES); a pose that
    is blank or longer than DESCRIPTION_MAX_LENGTH characters is refused, naming the request's `field`. The description
    filter judges the pose as sent, quotation marks and all, since closing them is a way to break out of a prompt."""
    text = one_line(pose)
    if not text:
        raise ApiError(400, "INVALID_REQUEST", f"{field}: must not be blank")
    if len(text) > DESCRIPTION_MAX_LENGTH:
        raise ApiError(400, "INVALID_REQUEST", f"{field}: at most {DESCRIPTION_MAX_LENGTH} characters")
    return text.translate(QUOTES)


def pose_prompt(pose: str, selfie: bool = False) -> str:
    """The image prompt for a pose (checked_pose), the selfie one for a selfie's scene (FR-1.14)."""
    template = PROMPTS.selfie.pose if selfie else PROMPTS.pose
    return template.replace(PLACEHOLDER, checked_pose(pose))


def variation_prompt(description: str, selfie: bool = False) -> str:
    """The suggestion model's prompt for three variations of a description (checked_pose), for a selfie's scene or
    another."""
    template = PROMPTS.selfie.variation if selfie else PROMPTS.variation
    return template.replace(DESCRIPTION_PLACEHOLDER, checked_pose(description))
