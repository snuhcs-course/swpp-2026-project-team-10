# AI-generated with Claude Code, 2026-10-20, reviewed by Jaewan Park
# prompts.toml partitions (Testing Documentation 2.1.6). The file as shipped; a prompt wrapped over several lines; a
# prompt without {pose}, with it twice, missing, or empty; an unknown key at the top or in a template; a template
# without a field, with an empty one, or not a table; no templates; an id used twice; a file that is not TOML. A
# description or template with braces of its own.
import tomllib

import pytest

from pix_server.poses.templates import PROMPTS_FILE, TEMPLATES, description_prompt, load_prompts, template_prompt

TEMPLATE_PROMPT = 'template_prompt = "Same person and place. Pose: {pose}."\n'
DESCRIPTION_PROMPT = 'description_prompt = "Same person and place. The user asked for: {pose}."\n'
WAVE = '[[templates]]\nid = "wave"\nlabel = "Wave"\npose = "waving"\n'


@pytest.fixture
def prompts_file(tmp_path):
    def write(text):
        path = tmp_path / "prompts.toml"
        path.write_text(text, encoding="utf-8")
        return path

    return write


def test_shipped_file_has_both_prompts_and_the_templates_in_order():
    prompts = load_prompts(PROMPTS_FILE)
    assert list(prompts.templates) == ["hands_on_hips", "wave", "walking", "arms_crossed"]
    for prompt in (prompts.template, prompts.description):
        assert prompt.count("{pose}") == 1
        assert "\n" not in prompt and "  " not in prompt
    assert prompts.templates == TEMPLATES


def test_prompt_wrapped_over_lines_is_sent_as_one_line(prompts_file):
    wrapped = 'template_prompt = """\nSame person\n  and place.\n\nPose: {pose}.\n"""\n'
    prompts = load_prompts(prompts_file(wrapped + DESCRIPTION_PROMPT + WAVE))
    assert prompts.template == "Same person and place. Pose: {pose}."


@pytest.mark.parametrize(
    ("text", "named"),
    [
        ('template_prompt = "Same person and place."\n' + DESCRIPTION_PROMPT + WAVE, "template_prompt"),
        (TEMPLATE_PROMPT + 'description_prompt = "{pose}, then {pose} again"\n' + WAVE, "description_prompt"),
        (TEMPLATE_PROMPT + WAVE, "description_prompt"),
        (TEMPLATE_PROMPT + 'description_prompt = "  "\n' + WAVE, "description_prompt"),
        (TEMPLATE_PROMPT + DESCRIPTION_PROMPT + 'descripton_prompt = "{pose}"\n' + WAVE, "descripton_prompt"),
    ],
    ids=["no placeholder", "placeholder twice", "missing", "empty", "unknown key"],
)
def test_prompt_mistake_is_refused_with_its_name(prompts_file, text, named):
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(text))


@pytest.mark.parametrize(
    ("templates", "named"),
    [
        ('[[templates]]\nid = "wave"\nlabel = "Wave"\n', "pose"),
        ('[[templates]]\nid = "wave"\nlabel = ""\npose = "waving"\n', "label"),
        (WAVE.replace('pose = "waving"', 'pose = "waving"\nposes = "waving"'), "poses"),
        (WAVE + WAVE.replace('label = "Wave"', 'label = "Wave again"'), "'wave' is used twice"),
        ('templates = ["wave"]\n', "must be a table"),
        ("", r"\[\[templates\]\]"),
    ],
    ids=["no pose", "empty label", "unknown key", "id twice", "not a table", "no templates"],
)
def test_template_mistake_is_refused(prompts_file, templates, named):
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(TEMPLATE_PROMPT + DESCRIPTION_PROMPT + templates))


def test_file_that_is_not_toml_is_refused(prompts_file):
    with pytest.raises(tomllib.TOMLDecodeError):
        load_prompts(prompts_file("template_prompt = Same person\n"))


def test_braces_in_a_description_or_template_are_sent_as_written():
    prompt = description_prompt("{pose} {0} {name}")
    assert "{pose} {0} {name}" in prompt
    assert all(template.pose in template_prompt(template) for template in TEMPLATES.values())
