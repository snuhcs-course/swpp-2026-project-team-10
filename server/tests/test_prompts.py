# AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
# prompts.toml partitions (Testing Documentation 2.1.6). The file as shipped; a prompt wrapped over several lines; a
# pose prompt without {pose}, with it twice, missing, or empty; a suggestion prompt missing or empty; a variation
# prompt without {description}, with it twice, missing, or empty; an unknown key at the top or in a default pose; a
# default pose without a field, with an empty one, longer than a description, or not a table; three or five default
# poses; an id used twice; a file that is not TOML. A pose or description with braces of its own.
import tomllib

import pytest

from pix_server.poses.prompts import (
    DEFAULT_POSES,
    DESCRIPTION_MAX_LENGTH,
    PROMPTS_FILE,
    load_prompts,
    pose_prompt,
    variation_prompt,
)

POSE_PROMPT = 'pose_prompt = "Same person and place. Pose: {pose}."\n'
SUGGESTION_PROMPT = 'suggestion_prompt = "Suggest four poses as JSON."\n'
VARIATION_PROMPT = 'variation_prompt = "Three variations of {description} as JSON."\n'
MODEL_PROMPTS = SUGGESTION_PROMPT + VARIATION_PROMPT


def default_pose(pose_id: str, pose: str = "waving") -> str:
    return f'[[default_poses]]\nid = "{pose_id}"\nlabel = "{pose_id.title()}"\npose = "{pose}"\n'


FOUR = "".join(default_pose(pose_id) for pose_id in ("hips", "wave", "walk", "arms"))


@pytest.fixture
def prompts_file(tmp_path):
    def write(text):
        path = tmp_path / "prompts.toml"
        path.write_text(text, encoding="utf-8")
        return path

    return write


def test_shipped_file_has_the_prompts_and_four_default_poses_in_order():
    prompts = load_prompts(PROMPTS_FILE)
    assert list(prompts.default_poses) == ["hands_on_hips", "wave", "walking", "arms_crossed"]
    assert prompts.pose.count("{pose}") == 1
    assert prompts.variation.count("{description}") == 1
    for prompt in (prompts.pose, prompts.suggestion, prompts.variation):
        assert "\n" not in prompt and "  " not in prompt
    assert '{"poses": [' in prompts.suggestion and '{"poses": [' in prompts.variation
    assert prompts.default_poses == DEFAULT_POSES


def test_prompt_wrapped_over_lines_is_sent_as_one_line(prompts_file):
    wrapped = 'pose_prompt = """\nSame person\n  and place.\n\n- Pose: {pose}.\n"""\n'
    prompts = load_prompts(prompts_file(wrapped + MODEL_PROMPTS + FOUR))
    assert prompts.pose == "Same person and place. - Pose: {pose}."


@pytest.mark.parametrize(
    ("text", "named"),
    [
        ('pose_prompt = "Same person and place."\n' + MODEL_PROMPTS + FOUR, "pose_prompt"),
        ('pose_prompt = "{pose}, then {pose} again"\n' + MODEL_PROMPTS + FOUR, "pose_prompt"),
        (MODEL_PROMPTS + FOUR, "pose_prompt"),
        ('pose_prompt = "  "\n' + MODEL_PROMPTS + FOUR, "pose_prompt"),
        (POSE_PROMPT + VARIATION_PROMPT + FOUR, "suggestion_prompt"),
        (POSE_PROMPT + 'suggestion_prompt = ""\n' + VARIATION_PROMPT + FOUR, "suggestion_prompt"),
        (POSE_PROMPT + SUGGESTION_PROMPT + FOUR, "variation_prompt"),
        (POSE_PROMPT + SUGGESTION_PROMPT + 'variation_prompt = " "\n' + FOUR, "variation_prompt"),
        (POSE_PROMPT + SUGGESTION_PROMPT + 'variation_prompt = "Three variations."\n' + FOUR, "variation_prompt"),
        (
            POSE_PROMPT + SUGGESTION_PROMPT + 'variation_prompt = "{description} or {description}"\n' + FOUR,
            "variation_prompt",
        ),
        (POSE_PROMPT + MODEL_PROMPTS + 'template_prompt = "{pose}"\n' + FOUR, "template_prompt"),
    ],
    ids=[
        "no placeholder",
        "placeholder twice",
        "missing",
        "empty",
        "no suggestion",
        "empty suggestion",
        "no variation",
        "empty variation",
        "variation without placeholder",
        "variation placeholder twice",
        "unknown key",
    ],
)
def test_prompt_mistake_is_refused_with_its_name(prompts_file, text, named):
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(text))


@pytest.mark.parametrize(
    ("poses", "named"),
    [
        ('[[default_poses]]\nid = "wave"\nlabel = "Wave"\n' + FOUR[: FOUR.rindex("[[")], "pose"),
        (FOUR.replace('label = "Hips"', 'label = ""'), "label"),
        (FOUR.replace('pose = "waving"', 'pose = "waving"\nposes = "waving"', 1), "poses"),
        (FOUR.replace('id = "arms"', 'id = "wave"'), "'wave' is used twice"),
        (FOUR.replace('pose = "waving"', f'pose = "{"a" * (DESCRIPTION_MAX_LENGTH + 1)}"', 1), "at most 100"),
        ('default_poses = ["a", "b", "c", "d"]\n', "must be a table"),
        (FOUR[: FOUR.rindex("[[")], "exactly 4"),
        (FOUR + default_pose("jump"), "exactly 4"),
        ("", "exactly 4"),
    ],
    ids=["no pose", "empty label", "unknown key", "id twice", "too long", "not a table", "three", "five", "none"],
)
def test_default_pose_mistake_is_refused(prompts_file, poses, named):
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + poses))


def test_default_pose_of_the_longest_length_is_accepted(prompts_file):
    longest = FOUR.replace('pose = "waving"', f'pose = "{"a" * DESCRIPTION_MAX_LENGTH}"', 1)
    prompts = load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + longest))
    assert len(prompts.default_poses["hips"].pose) == DESCRIPTION_MAX_LENGTH


def test_file_that_is_not_toml_is_refused(prompts_file):
    with pytest.raises(tomllib.TOMLDecodeError):
        load_prompts(prompts_file("pose_prompt = Same person\n"))


def test_braces_in_a_pose_are_sent_as_written():
    assert "{pose} {0} {name}" in pose_prompt("{pose} {0} {name}")
    assert '"{description} {pose}"' in variation_prompt("{description} {pose}")
    assert all(default.pose in pose_prompt(default.pose) for default in DEFAULT_POSES.values())
