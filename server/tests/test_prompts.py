# AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
# prompts.toml partitions (Testing Documentation 2.1.6). The file as shipped; a prompt wrapped over several lines; a
# pose prompt without {pose}, with it twice, missing, or empty; a suggestion prompt missing or empty; a variation
# prompt without {description}, with it twice, missing, or empty; a tip prompt or its Korean addition missing or
# empty; an unknown key at the
# top or in a default pose; a default pose without a field, with an empty one, longer than a description, or not a
# table; three or five default poses; an id used twice; a file that is not TOML. A pose or description with braces of
# its own. The [selfie] table (FR-1.14): missing or not a table; its pose prompt without {pose} or with it twice, its
# suggestion prompt missing, its variation prompt empty or without {description}; an unknown key; three or five
# default poses, one that is not a string, blank, of 100 characters and of 101, or the same as another in any case.
# The [filter] table (Design 2.6.3): as shipped, wrapped over lines; missing or not a table; its context missing or
# empty; a question's table missing or not a table, its question, yes, or no missing or empty; an unknown key in the
# table or in a question. The [person] table (FR-4.13): as shipped, wrapped over lines; missing or not a table; its
# question missing or empty; an unknown key. Double quotation marks in a pose, straight, typographic, full-width, or
# angle, and none.
import tomllib

import pytest

from pix_server.errors import ApiError
from pix_server.poses.prompts import (
    DEFAULT_POSES,
    DESCRIPTION_MAX_LENGTH,
    FILTER_QUESTIONS,
    PERSON_QUESTION,
    PROMPTS,
    PROMPTS_FILE,
    checked_pose,
    load_prompts,
    pose_prompt,
    variation_prompt,
)

POSE_PROMPT = 'pose_prompt = "Same person and place. Pose: {pose}."\n'
SUGGESTION_PROMPT = 'suggestion_prompt = "Suggest four poses as JSON."\n'
VARIATION_PROMPT = 'variation_prompt = "Three variations of {description} as JSON."\n'
TIP_PROMPT = 'tip_prompt = "How to hold the phone, as JSON."\n'
TIP_KOREAN = 'tip_korean = "The sentence in Korean."\n'
MODEL_PROMPTS = SUGGESTION_PROMPT + VARIATION_PROMPT + TIP_PROMPT + TIP_KOREAN


def selfie(
    pose='"Same selfie. Pose: {pose}."',
    suggestion='"Suggest four selfie poses as JSON."',
    variation='"Three selfie variations of {description} as JSON."',
    poses='["smile", "V sign", "chin on hand", "look away"]',
    extra="",
) -> str:
    """A [selfie] table with these values, written as TOML; None leaves a key out."""
    keys = {"pose_prompt": pose, "suggestion_prompt": suggestion, "variation_prompt": variation, "default_poses": poses}
    return "[selfie]\n" + "".join(f"{key} = {value}\n" for key, value in keys.items() if value is not None) + extra


QUESTION = 'question = "Is it harmful?"\nyes = "It asks for harm."\nno = "It is a pose."\n'


def filter_table(context='"Text a user typed to describe a pose."', malicious=QUESTION, injection=QUESTION, extra=""):
    """A [filter] table with these values, written as TOML; None leaves the context or a question's table out, and
    `extra` goes into [filter] itself."""
    text = "[filter]\n" + (f"context = {context}\n" if context is not None else "") + extra
    for name, question in (("malicious", malicious), ("injection", injection)):
        if question is not None:
            text += f"[filter.{name}]\n{question}"
    return text


def default_pose(pose_id: str, pose: str = "waving") -> str:
    return f'[[default_poses]]\nid = "{pose_id}"\nlabel = "{pose_id.title()}"\npose = "{pose}"\n'


FOUR = "".join(default_pose(pose_id) for pose_id in ("hips", "wave", "walk", "arms"))
SELFIE = selfie()
FILTER = filter_table()
PERSON = '[person]\nquestion = "Does `photo` show a person?"\n'


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
    for prompt in (prompts.pose, prompts.suggestion, prompts.variation, prompts.tip, prompts.tip_korean):
        assert "\n" not in prompt and "  " not in prompt
    assert "한 걸음" in prompts.tip_korean  # its examples of what not to write are Korean
    assert '{"poses": [' in prompts.suggestion and '{"poses": [' in prompts.variation
    assert prompts.default_poses == DEFAULT_POSES


def test_shipped_file_has_the_selfie_prompts_and_four_selfie_poses():
    shipped = load_prompts(PROMPTS_FILE).selfie
    assert shipped == PROMPTS.selfie
    assert shipped.pose.count("{pose}") == 1
    assert shipped.variation.count("{description}") == 1
    for prompt in (shipped.pose, shipped.suggestion, shipped.variation):
        assert "\n" not in prompt and "  " not in prompt
        assert "selfie" in prompt
    assert '{"poses": [' in shipped.suggestion and '{"poses": [' in shipped.variation
    assert len(shipped.default_poses) == 4
    assert all(0 < len(pose) <= DESCRIPTION_MAX_LENGTH for pose in shipped.default_poses)


def test_prompt_wrapped_over_lines_is_sent_as_one_line(prompts_file):
    wrapped = 'pose_prompt = """\nSame person\n  and place.\n\n- Pose: {pose}.\n"""\n'
    prompts = load_prompts(prompts_file(wrapped + MODEL_PROMPTS + FOUR + SELFIE + FILTER + PERSON))
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
        (POSE_PROMPT + SUGGESTION_PROMPT + TIP_PROMPT + FOUR, "variation_prompt"),
        (POSE_PROMPT + SUGGESTION_PROMPT + 'variation_prompt = " "\n' + TIP_PROMPT + FOUR, "variation_prompt"),
        (
            POSE_PROMPT + SUGGESTION_PROMPT + 'variation_prompt = "Three variations."\n' + TIP_PROMPT + FOUR,
            "variation_prompt",
        ),
        (
            POSE_PROMPT
            + SUGGESTION_PROMPT
            + 'variation_prompt = "{description} or {description}"\n'
            + TIP_PROMPT
            + FOUR,
            "variation_prompt",
        ),
        (POSE_PROMPT + SUGGESTION_PROMPT + VARIATION_PROMPT + FOUR, "tip_prompt"),
        (POSE_PROMPT + SUGGESTION_PROMPT + VARIATION_PROMPT + 'tip_prompt = ""\n' + FOUR, "tip_prompt"),
        (POSE_PROMPT + SUGGESTION_PROMPT + VARIATION_PROMPT + TIP_PROMPT + FOUR, "tip_korean"),
        (POSE_PROMPT + SUGGESTION_PROMPT + VARIATION_PROMPT + TIP_PROMPT + 'tip_korean = " "\n' + FOUR, "tip_korean"),
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
        "no tip",
        "empty tip",
        "no Korean tip",
        "empty Korean tip",
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
    prompts = load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + longest + SELFIE + FILTER + PERSON))
    assert len(prompts.default_poses["hips"].pose) == DESCRIPTION_MAX_LENGTH


@pytest.mark.parametrize(
    ("table", "named"),
    [
        ("", "a \\[selfie\\] table is needed"),
        (selfie(pose='"Same selfie."'), "selfie.pose_prompt must hold"),
        (selfie(pose='"{pose} and {pose}"'), "selfie.pose_prompt must hold"),
        (selfie(suggestion=None), "selfie.suggestion_prompt"),
        (selfie(variation='" "'), "selfie.variation_prompt"),
        (selfie(variation='"Three variations as JSON."'), "selfie.variation_prompt must hold"),
        (selfie(extra='tip_prompt = "How to hold it."\n'), "selfie: unknown keys tip_prompt"),
        (selfie(poses='["smile", "V sign", "chin"]'), "a list of 4"),
        (selfie(poses='["smile", "V sign", "chin", "away", "wink"]'), "a list of 4"),
        (selfie(poses='"smile"'), "a list of 4"),
        (selfie(poses='["smile", 2, "chin", "away"]'), "#2 must be a non-empty string"),
        (selfie(poses='["smile", "V sign", " ", "away"]'), "#3 must be a non-empty string"),
        (
            selfie(poses=f'["smile", "V sign", "chin", "{"a" * (DESCRIPTION_MAX_LENGTH + 1)}"]'),
            "#4 must be at most 100",
        ),
        (selfie(poses='["smile", "V sign", "SMILE", "away"]'), "#3 repeats another pose"),
    ],
    ids=[
        "no table",
        "pose without placeholder",
        "pose placeholder twice",
        "no suggestion",
        "empty variation",
        "variation without placeholder",
        "unknown key",
        "three poses",
        "five poses",
        "poses not a list",
        "pose not a string",
        "blank pose",
        "pose too long",
        "pose repeated",
    ],
)
def test_selfie_mistake_is_refused_with_its_name(prompts_file, table, named):
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + FOUR + table))


def test_selfie_that_is_not_a_table_is_refused(prompts_file):
    with pytest.raises(ValueError, match=r"a \[selfie\] table is needed"):
        load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + 'selfie = "x"\n' + FOUR))


def test_selfie_default_pose_of_the_longest_length_is_accepted(prompts_file):
    longest = "a" * DESCRIPTION_MAX_LENGTH
    table = selfie(poses=f'["smile", "V sign", "chin", "{longest}"]')
    prompts = load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + FOUR + table + FILTER + PERSON))
    assert prompts.selfie.default_poses[3] == longest


def test_shipped_file_has_the_two_filter_questions_as_the_decisions_api_takes_them():
    questions = load_prompts(PROMPTS_FILE).filter_questions
    assert questions == PROMPTS.filter_questions
    assert tuple(questions) == FILTER_QUESTIONS == ("malicious", "injection")
    context = "The state holds `description`, text a user typed into a photo app to describe a pose."
    for question in questions.values():
        assert question["type"] == "noul"
        assert question["instructions"].startswith(context)
        assert set(question["criteria"]) == {"true", "false"}
        for text in (question["instructions"], *question["criteria"].values()):
            assert "\n" not in text and "  " not in text
    assert "nudity" in questions["malicious"]["criteria"]["true"]
    assert "quotation mark" in questions["injection"]["criteria"]["true"]


def test_filter_question_is_the_context_and_the_question_on_one_line(prompts_file):
    table = filter_table(
        context='"""\nA user\n  typed this.\n"""', malicious='question = "Harmful?"\nyes = "Yes."\nno = "No."\n'
    )
    prompts = load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + FOUR + SELFIE + table + PERSON))
    assert prompts.filter_questions["malicious"] == {
        "type": "noul",
        "instructions": "A user typed this. Harmful?",
        "criteria": {"true": "Yes.", "false": "No."},
    }


@pytest.mark.parametrize(
    ("table", "named"),
    [
        ("", r"a \[filter\] table is needed"),
        (filter_table(context=None), "filter.context must be a non-empty string"),
        (filter_table(context='" "'), "filter.context must be a non-empty string"),
        (filter_table(injection=None), r"a \[filter.injection\] table is needed"),
        (filter_table(malicious=None, extra='malicious = "Harmful?"\n'), r"a \[filter.malicious\] table is needed"),
        (filter_table(malicious='yes = "Yes."\nno = "No."\n'), "filter.malicious.question must be"),
        (filter_table(injection='question = "Tricky?"\nyes = ""\nno = "No."\n'), "filter.injection.yes must be"),
        (filter_table(malicious='question = "Harmful?"\nyes = "Yes."\n'), "filter.malicious.no must be"),
        (filter_table(extra='violence = "Violent?"\n'), "filter: unknown keys violence"),
        (filter_table(malicious=QUESTION + 'maybe = "Unsure."\n'), "filter.malicious: unknown keys maybe"),
    ],
    ids=[
        "no table",
        "no context",
        "empty context",
        "no question table",
        "question not a table",
        "no question",
        "empty yes",
        "no no",
        "unknown key in the table",
        "unknown key in a question",
    ],
)
def test_filter_mistake_is_refused_with_its_name(prompts_file, table, named):
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + FOUR + SELFIE + table))


def test_filter_that_is_not_a_table_is_refused(prompts_file):
    with pytest.raises(ValueError, match=r"a \[filter\] table is needed"):
        load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + 'filter = "x"\n' + FOUR + SELFIE))


def test_shipped_file_has_the_person_question_as_the_decisions_api_takes_it():
    question = load_prompts(PROMPTS_FILE).person_question
    assert question == PROMPTS.person_question
    assert question == {PERSON_QUESTION: {"type": "noul", "instructions": "Does `photo` show a person?"}}


def test_person_question_wrapped_over_lines_is_sent_as_one_line(prompts_file):
    table = '[person]\nquestion = """\nDoes `photo`\n  show a person?\n"""\n'
    prompts = load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + FOUR + SELFIE + FILTER + table))
    assert prompts.person_question == {"person": {"type": "noul", "instructions": "Does `photo` show a person?"}}


@pytest.mark.parametrize(
    ("table", "named"),
    [
        ("", r"a \[person\] table is needed"),
        ('person = "Does `photo` show a person?"\n', r"a \[person\] table is needed"),
        ("[person]\n", "person.question must be a non-empty string"),
        ('[person]\nquestion = " "\n', "person.question must be a non-empty string"),
        ("[person]\nquestion = 1\n", "person.question must be a non-empty string"),
        (PERSON + 'yes = "A person is there."\n', "person: unknown keys yes"),
    ],
    ids=["no table", "not a table", "no question", "empty question", "question not a string", "unknown key"],
)
def test_person_mistake_is_refused_with_its_name(prompts_file, table, named):
    # A bare key must come before the tables, as TOML reads it into the table above otherwise.
    head, tables = (table, "") if not table.startswith("[") else ("", table)
    with pytest.raises(ValueError, match=named):
        load_prompts(prompts_file(POSE_PROMPT + MODEL_PROMPTS + head + FOUR + SELFIE + FILTER + tables))


@pytest.mark.parametrize(
    "quoted",
    ['say "cheese"', "say “cheese”", "say ＂cheese＂", "say «cheese»", "say „cheese‟", 'say "cheese″'],
    ids=["straight", "typographic", "full-width", "angle", "low and reversed", "straight and double prime"],
)
def test_double_quotation_marks_in_a_pose_reach_the_prompts_as_single_ones(quoted):
    assert checked_pose(quoted) == "say 'cheese'"
    assert "say 'cheese'" in pose_prompt(quoted)
    assert "say 'cheese'" in pose_prompt(quoted, selfie=True)
    assert "say 'cheese'" in variation_prompt(quoted)
    assert 'cheese"' not in pose_prompt(quoted)


def test_pose_without_double_quotation_marks_reaches_the_prompts_as_written():
    assert checked_pose("엄지 척, it's fine") == "엄지 척, it's fine"


def test_quotation_marks_do_not_change_the_length_a_pose_is_checked_at():
    longest = '"' * DESCRIPTION_MAX_LENGTH
    assert checked_pose(longest) == "'" * DESCRIPTION_MAX_LENGTH
    with pytest.raises(ApiError):
        checked_pose(longest + '"')


def test_file_that_is_not_toml_is_refused(prompts_file):
    with pytest.raises(tomllib.TOMLDecodeError):
        load_prompts(prompts_file("pose_prompt = Same person\n"))


def test_braces_in_a_pose_are_sent_as_written():
    assert "{pose} {0} {name}" in pose_prompt("{pose} {0} {name}")
    assert '"{description} {pose}"' in variation_prompt("{description} {pose}")
    assert all(default.pose in pose_prompt(default.pose) for default in DEFAULT_POSES.values())
    assert "{pose} {0}" in pose_prompt("{pose} {0}", selfie=True)
    assert '"{description}"' in variation_prompt("{description}", selfie=True)


def test_a_selfie_gets_the_selfie_prompts_and_another_scene_does_not():
    assert pose_prompt("wink", selfie=True) == PROMPTS.selfie.pose.replace("{pose}", "wink")
    assert pose_prompt("wink") == PROMPTS.pose.replace("{pose}", "wink")
    assert variation_prompt("wink", selfie=True) == PROMPTS.selfie.variation.replace("{description}", "wink")
    assert variation_prompt("wink") == PROMPTS.variation.replace("{description}", "wink")
