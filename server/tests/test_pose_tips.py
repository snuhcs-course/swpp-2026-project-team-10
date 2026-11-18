# AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
# POST /pose-tips partitions (Testing Documentation 2.1.6). Photos: a scene and a candidate within the contract, and
# either one not a JPEG, too large, carrying EXIF, or missing. Pose: one in Korean with extra whitespace, of 100
# characters and of 101, blank, or missing. The requests: one for the scene and one for the candidate, at once, the
# same in every language; one of them slow, which is asked again, and both in time, which are asked once. The model's
# answers: usable; JSON in a code fence; unusable, for the scene or for the candidate (not JSON, not an object, a
# field missing, a value outside the schema, a box of three numbers, outside the photo, upside down, without width, or
# not of numbers, an empty message); a head that is not a box. The move: the head and the whole person both 1.2 times
# bigger or smaller, both a little bigger or smaller, a tighter or a wider shot of the same height, a wider shot of
# the same person; a big move by both measures and by one alone; the zoom for a close shot; the two measures apart and
# a head missing from either photo, which name no move. The phone: each part of the person the camera is level with,
# for a person standing, seated, and on the ground, against the scene's, lower, higher, or the same, with each tilt,
# said also when it is the scene's hold. The frame: the person on the left, on the right, in the middle, and a little
# off it; the room above the head, almost none, plenty, at each edge of the usual, and without a head. A level line:
# leaning in the scene, in both photos, level, and none. The same distance, which is said too. All of them at once, in
# their order, and only the distance and the phone. Upstream: a refusal (403), a failing status, an unreadable body,
# no answer in time, no connection, no key. The phone: staying connected, or dropping the request while the model
# works. Limits: each window full, counted apart from poses and suggestions; refused input not counted; the session
# store down. Privacy: neither the pose nor the answer nor the upstream body is logged. Language (FR-10.2): `ko`,
# `en`, left out, and another value.
import asyncio
import base64
import json

import httpx2
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from pix_server.main import create_app
from pix_server.poses import tips
from pix_server.poses.prompts import DESCRIPTION_MAX_LENGTH, PROMPTS

POSE = "Wave at the camera with one hand"
# The scene: the whole person standing, small, in the middle, the camera level with their chest. The candidate: down to
# the knees and tall in the photo, with a head as much bigger, the camera level with their waist and tilted up.
NOW = {
    "box": [300, 350, 750, 650],
    "head": [300, 450, 370, 550],
    "shot": "full body",
    "posture": "standing",
    "level": "chest",
    "tilt": "level",
    "horizon": "none",
}
WANT = {
    "box": [80, 250, 980, 750],
    "head": [80, 400, 250, 600],
    "shot": "knees up",
    "posture": "standing",
    "level": "waist",
    "tilt": "up",
    "horizon": "none",
}
# As a test writes the model's two answers: `now` is the scene's and `want` the candidate's.
ANSWER = {"now": NOW, "want": WANT}
TEXT = "Move much closer.\nHold the phone at your waist, tilted up a little."
SAME = "Keep the same distance."
KO_SAME = "지금 거리를 유지하세요."
# How the phone is held for `photo()` as it is: level at the chest.
CHEST = "Hold the phone at your chest, not tilted."
KO_CHEST = "폰은 가슴 높이에서 수평으로 드세요."
GUIDE = "Move until the person fits the guide's outline, then take it."


@pytest.fixture(autouse=True)
def model_answers(image_api):
    image_api.tips(ANSWER)


@pytest.fixture
def photos(make_image):
    """A 9:16 scene, as Camera may take it, and a 3:4 candidate, so the two are told apart in the payload."""
    return make_image(size=(576, 1024)), make_image(size=(768, 1024))


def ask(client, photos, pose=POSE, files=None, language=None):
    scene, candidate = photos
    data = {"pose": pose} if pose is not None else {}
    if language is not None:
        data["language"] = language
    if files is None:
        files = {"scene": ("scene.jpg", scene, "image/jpeg"), "candidate": ("candidate.jpg", candidate, "image/jpeg")}
    return client.post("/api/v1/pose-tips", files=files, data=data)


def assert_error(response, status, code):
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"error"} and body["error"]["code"] == code
    return body["error"]["message"]


def image_url(image: bytes) -> dict:
    return {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(image).decode()}}


def said(response) -> list[str]:
    """The tip's sentences, one on each line."""
    assert response.status_code == 200
    return response.json()["text"].split("\n")


def texts_of(payload: dict) -> list[str]:
    return [part["text"] for part in payload["messages"][0]["content"] if part["type"] == "text"]


def test_tip_comes_from_the_model_for_the_scene_and_the_candidate(client, image_api, photos, settings):
    response = ask(client, photos)

    assert response.status_code == 200
    assert response.json() == {"text": TEXT, "framing": "closer", "hold": "waist", "height": "lower", "angle": "up"}
    # A request for each photo, so the description of one cannot lean on the other's.
    assert len(image_api.tip_requests) == 2
    for request in image_api.tip_requests:
        assert str(request.url) == "https://openrouter.ai/api/v1/chat/completions"
        assert request.headers["Authorization"] == "Bearer test-key"
    assert image_api.suggestion_requests == [] and image_api.requests == []
    for payload in image_api.tip_payloads:
        assert payload["model"] == settings.tip_model
        assert payload["reasoning"] == {"effort": settings.tip_reasoning}
        assert payload["response_format"]["json_schema"]["strict"] is True
        assert len(payload["messages"]) == 1
    scene_payload, candidate_payload = image_api.tip_payloads
    scene, candidate = photos
    # The same description of each photo, in the same order.
    described = ["box", "head", "shot", "posture", "level", "tilt", "horizon"]
    assert scene_payload["response_format"]["json_schema"]["name"] == "tip_scene"
    assert list(scene_payload["response_format"]["json_schema"]["schema"]["properties"]) == described
    assert scene_payload["messages"][0]["content"] == [
        {"type": "text", "text": PROMPTS.tip},
        {"type": "text", "text": "The photo:"},
        image_url(scene),
    ]
    # The candidate is told what it is, and the pose comes with it.
    assert candidate_payload["response_format"]["json_schema"]["name"] == "tip_candidate"
    assert list(candidate_payload["response_format"]["json_schema"]["schema"]["properties"]) == described
    assert candidate_payload["messages"][0]["content"] == [
        {"type": "text", "text": PROMPTS.tip},
        {"type": "text", "text": PROMPTS.tip_candidate},
        {"type": "text", "text": f'The photo, made for the pose "{POSE}":'},
        image_url(candidate),
    ]


def test_tip_is_written_in_the_screens_language_from_the_same_requests(client, image_api, photos):
    assert said(ask(client, photos, language="ko")) == [
        "훨씬 가까이 다가가세요.",
        "폰은 허리 높이에서 살짝 위로 기울여 드세요.",
    ]
    korean = image_api.tip_payloads
    for language in ("en", None):
        image_api.tip_requests.clear()
        assert ask(client, photos, language=language).json()["text"] == TEXT
        # The model writes no word of the tip, so it is asked the same whatever the language.
        assert image_api.tip_payloads == korean


def test_language_the_app_does_not_have_is_refused_before_a_call(client, image_api, photos):
    assert_error(ask(client, photos, language="ja"), 400, "INVALID_REQUEST")
    assert image_api.tip_requests == []


# ---- What the server makes of the two descriptions ---------------------------------------------------------------


BODY_SHARE = {"full body": 1.0, "knees up": 0.75, "waist up": 0.5, "chest up": 0.3}


def photo(
    top=300,
    bottom=750,
    left=350,
    right=650,
    shot="full body",
    posture="standing",
    level="chest",
    tilt="level",
    head=None,
    head_top=None,
    horizon="none",
) -> dict:
    """A photo's description. `head` is the head's height in the box's units; left out, it is the share of the whole
    person that a head is, so the two measures of the person's size agree. `head_top` is where the head starts, for
    the room above it; left out, it leaves the usual room, a tenth of the photo's height, whatever the box."""
    if head is None:
        head = round((bottom - top) / BODY_SHARE[shot] * 0.15)
    start = 100 if head_top is None else head_top
    middle = (left + right) // 2
    return {
        "box": [top, left, bottom, right],
        "head": [start, middle - 30, start + head, middle + 30],
        "shot": shot,
        "posture": posture,
        "level": level,
        "tilt": tilt,
        "horizon": horizon,
    }


def also(sentence: str | None) -> list[str]:
    """A sentence that comes after the distance and the phone, when it is said."""
    return [sentence] if sentence else []


def tip_for(client, image_api, photos, now, want, language=None):
    image_api.tips({"now": now, "want": want})
    return ask(client, photos, language=language)


@pytest.mark.parametrize(
    ("now", "want", "framing", "sentence"),
    [
        # The same shot, the person and their head clearly bigger: 450 to 540 of the photo's 1000 is 1.2 times.
        (photo(300, 750), photo(230, 770), "closer", "Move a little closer."),
        # Under 1.18 times, either way: a box drawn a little differently moves nobody.
        (photo(300, 750), photo(280, 800), "keep", SAME),
        (photo(280, 800), photo(300, 750), "keep", SAME),
        (photo(230, 770), photo(300, 750), "back", "Step back a little."),
        # A tighter shot of the same height in the photo shows a bigger person: knees up is three quarters of them.
        (photo(100, 900, shot="full body"), photo(100, 900, shot="knees up"), "closer", "Move a little closer."),
        # A wider shot with a box as much taller: the same person, the same distance.
        (photo(100, 700, shot="knees up"), photo(100, 900, shot="full body"), "keep", SAME),
        # One and a half times by both measures is a big move; the same the other way.
        (photo(300, 600), photo(200, 650), "closer", "Move much closer."),
        (photo(200, 650), photo(300, 600), "back", "Step back a lot."),
        # A big move by the head alone is said as a move, not as a big one.
        (photo(300, 700, head=60), photo(250, 730, head=90), "closer", "Move a little closer."),
    ],
    ids=[
        "bigger",
        "a little bigger",
        "a little smaller",
        "smaller",
        "tighter shot",
        "wider shot, taller",
        "much bigger",
        "much smaller",
        "much bigger by one measure",
    ],
)
def test_move_is_said_when_the_head_and_the_whole_person_agree(client, image_api, photos, now, want, framing, sentence):
    response = tip_for(client, image_api, photos, now, want)
    assert response.json()["framing"] == framing
    assert said(response) == [sentence, CHEST]


@pytest.mark.parametrize(
    ("shot", "little", "much", "korean"),
    [
        ("full body", "Move a little closer.", "Move much closer.", "조금 다가가세요."),
        ("knees up", "Move a little closer.", "Move much closer.", "조금 다가가세요."),
        # A close shot is reached with the zoom: a phone held that close bends the face.
        ("waist up", "Zoom in a little.", "Zoom in a lot.", "줌을 조금 당기세요."),
        ("chest up", "Zoom in a little.", "Zoom in a lot.", "줌을 조금 당기세요."),
    ],
)
def test_move_closer_is_the_zooms_for_a_close_shot(client, image_api, photos, shot, little, much, korean):
    # The same shot in both photos, so only the person's size says which way; the head keeps clear of the top.
    small, bigger, big = (photo(top, bottom, shot=shot) for top, bottom in ((300, 600), (280, 640), (200, 800)))
    assert said(tip_for(client, image_api, photos, small, bigger)) == [little, CHEST]
    assert said(tip_for(client, image_api, photos, small, bigger, language="ko")) == [korean, KO_CHEST]
    assert said(tip_for(client, image_api, photos, small, big)) == [much, CHEST]


@pytest.mark.parametrize(
    ("now", "want"),
    [
        # The head 1.2 times bigger under a hat, the person as tall: one measure is wrong, and nobody is moved by it.
        (photo(300, 750, head=70), photo(300, 750, head=84)),
        # The person 1.2 times taller, as when a shot is misread, the head as big.
        (photo(300, 750, head=70), photo(230, 770, head=70)),
        # One bigger and the other smaller.
        (photo(300, 750, head=84), photo(230, 770, head=70)),
        # Just under the ratio by one measure and over it by the other.
        (photo(100, 600, head=100), photo(100, 700, head=117)),
        # A head the model could not box, in either photo: one measure alone is not enough.
        (photo(300, 750) | {"head": [0, 0, 0, 0]}, photo(230, 770)),
        (photo(300, 750), photo(230, 770) | {"head": "hidden"}),
    ],
    ids=["head alone bigger", "body alone bigger", "apart", "one just under", "no head in the scene", "no head wanted"],
)
def test_no_move_is_said_when_the_two_measures_do_not_agree(client, image_api, photos, now, want):
    response = tip_for(client, image_api, photos, now, want)
    assert response.json()["framing"] == "unsure"
    # The guide's outline is what to go by, so that the distance is not left out where it may have changed.
    assert said(response) == [GUIDE, CHEST]
    assert said(tip_for(client, image_api, photos, now, want, language="ko")) == [
        "사람이 가이드 선에 꼭 맞게 거리를 맞춰 찍으세요.",
        KO_CHEST,
    ]
    # Beside another sentence too, first, where the move would stand.
    assert said(tip_for(client, image_api, photos, now, want | {"tilt": "up"})) == [
        GUIDE,
        "Hold the phone at your chest, tilted up a little.",
    ]


def test_move_is_said_just_over_the_ratio_by_both_measures(client, image_api, photos):
    response = tip_for(client, image_api, photos, photo(100, 600, head=100), photo(100, 700, head=119))
    assert (response.json()["framing"], said(response)) == ("closer", ["Move a little closer.", CHEST])


@pytest.mark.parametrize(
    ("posture", "holds"),
    [
        ("standing", ["crouching", "waist", "chest", "eye", "above head"]),
        # A person on a seat is about a level lower than one standing, and one on the ground lower still.
        ("seated", ["crouching", "crouching", "waist", "chest", "eye"]),
        ("on the ground", ["crouching", "crouching", "crouching", "waist", "chest"]),
    ],
)
def test_phone_is_held_level_with_the_part_of_the_person_the_camera_shows(client, image_api, photos, posture, holds):
    for level, hold in zip(["knees", "waist", "chest", "face", "above"], holds, strict=True):
        response = tip_for(client, image_api, photos, photo(), photo(posture=posture, level=level))
        assert response.json()["hold"] == hold, level


@pytest.mark.parametrize(
    ("now", "want", "hold", "height", "phone", "korean"),
    [
        (
            ("standing", "chest"),
            ("standing", "waist", "up"),
            "waist",
            "lower",
            "Hold the phone at your waist, tilted up a little.",
            "폰은 허리 높이에서 살짝 위로 기울여 드세요.",
        ),
        (
            ("standing", "face"),
            ("standing", "face", "down"),
            "eye",
            "keep",
            "Hold the phone at eye level, tilted down a little.",
            "폰은 눈높이에서 살짝 아래로 기울여 드세요.",
        ),
        (
            ("standing", "waist"),
            ("standing", "above", "down"),
            "above head",
            "raise",
            "Hold the phone above your head, tilted down a little.",
            "폰은 머리 위로 들어 살짝 아래로 기울여 드세요.",
        ),
        (
            ("standing", "face"),
            ("on the ground", "chest", "level"),
            "crouching",
            "lower",
            "Hold the phone low at a crouch, not tilted.",
            "폰은 쪼그려 앉아 수평으로 드세요.",
        ),
        # Held as the scene was taken, also level at the chest or the eyes, where a standing photographer holds it
        # anyway: said all the same, so that every tip says how to hold the phone (decided 2026-11-18).
        (
            ("standing", "chest"),
            ("standing", "chest", "level"),
            "chest",
            "keep",
            "Hold the phone at your chest, not tilted.",
            "폰은 가슴 높이에서 수평으로 드세요.",
        ),
        (
            ("standing", "face"),
            ("standing", "face", "level"),
            "eye",
            "keep",
            "Hold the phone at eye level, not tilted.",
            "폰은 눈높이에서 수평으로 드세요.",
        ),
        (
            ("standing", "waist", "up"),
            ("standing", "waist", "up"),
            "waist",
            "keep",
            "Hold the phone at your waist, tilted up a little.",
            "폰은 허리 높이에서 살짝 위로 기울여 드세요.",
        ),
        # The same part of a person who sat down: the phone goes down with them, to the chest.
        (
            ("standing", "face"),
            ("seated", "face", "level"),
            "chest",
            "lower",
            "Hold the phone at your chest, not tilted.",
            "폰은 가슴 높이에서 수평으로 드세요.",
        ),
    ],
)
def test_phone_is_always_said_held_as_the_candidate_shows(
    client, image_api, photos, now, want, hold, height, phone, korean
):
    described = (
        photo(posture=now[0], level=now[1], tilt=now[2] if len(now) > 2 else "level"),
        photo(posture=want[0], level=want[1], tilt=want[2]),
    )
    response = tip_for(client, image_api, photos, *described)
    body = response.json()
    assert (body["hold"], body["height"], body["angle"]) == (hold, height, want[2])
    assert said(response) == [SAME, phone]
    assert said(tip_for(client, image_api, photos, *described, language="ko")) == [KO_SAME, korean]


@pytest.mark.parametrize(
    ("left", "right", "sentence", "korean"),
    [
        (50, 350, "Put the person on the left of the frame.", "사람을 화면 왼쪽에 두세요."),
        (650, 950, "Put the person on the right of the frame.", "사람을 화면 오른쪽에 두세요."),
        # In the middle, or a little off it: where a photographer puts a person unasked.
        (350, 650, None, None),
        (300, 560, None, None),
    ],
    ids=["left", "right", "middle", "a little off the middle"],
)
def test_side_of_the_frame_is_said_when_the_candidate_has_the_person_off_the_middle(
    client, image_api, photos, left, right, sentence, korean
):
    # Wherever the person was in the scene: the tip says how the candidate is framed.
    for scene in (photo(), photo(left=left, right=right)):
        want = photo(left=left, right=right)
        assert said(tip_for(client, image_api, photos, scene, want)) == [SAME, CHEST, *also(sentence)]
        assert said(tip_for(client, image_api, photos, scene, want, language="ko")) == [
            KO_SAME,
            KO_CHEST,
            *also(korean),
        ]


@pytest.mark.parametrize(
    ("head_top", "sentence", "korean"),
    [
        (20, "Leave almost no room above the head.", "머리 위는 거의 남기지 마세요."),
        (59, "Leave almost no room above the head.", "머리 위는 거의 남기지 마세요."),
        # From six hundredths of the photo's height to a quarter of it: the usual room, which is not said.
        (60, None, None),
        (250, None, None),
        (251, "Leave plenty of room above the head.", "머리 위를 넉넉히 비우세요."),
        (400, "Leave plenty of room above the head.", "머리 위를 넉넉히 비우세요."),
    ],
)
def test_room_above_the_head_is_said_when_it_is_almost_none_or_plenty(
    client, image_api, photos, head_top, sentence, korean
):
    # The scene's head is where the candidate's is, so nothing else is said.
    now, want = photo(head_top=head_top), photo(head_top=head_top)
    assert said(tip_for(client, image_api, photos, now, want)) == [SAME, CHEST, *also(sentence)]
    assert said(tip_for(client, image_api, photos, now, want, language="ko")) == [KO_SAME, KO_CHEST, *also(korean)]
    # The room is the candidate's: the scene's says nothing of it.
    assert said(tip_for(client, image_api, photos, photo(head_top=head_top), photo(head_top=100))) == [SAME, CHEST]


@pytest.mark.parametrize(
    ("scene_line", "wanted_line", "advised"),
    [
        ("tilted", "level", True),
        ("tilted", "none", True),
        # A candidate that leans too may lean on purpose; a level line needs no advice, nor does none.
        ("tilted", "tilted", False),
        ("level", "level", False),
        ("level", "tilted", False),
        ("none", "none", False),
    ],
)
def test_phone_is_held_upright_when_a_level_line_leans_in_the_scene_alone(
    client, image_api, photos, scene_line, wanted_line, advised
):
    now, want = photo(horizon=scene_line), photo(horizon=wanted_line)
    upright = "Hold the phone upright so the lines behind the person are level." if advised else None
    korean = "배경의 가로선이 기울지 않게 폰을 바로 세우세요." if advised else None
    assert said(tip_for(client, image_api, photos, now, want)) == [SAME, CHEST, *also(upright)]
    assert said(tip_for(client, image_api, photos, now, want, language="ko")) == [KO_SAME, KO_CHEST, *also(korean)]


def test_sentences_come_in_the_order_a_photographer_acts_on_them(client, image_api, photos):
    now = photo(300, 600, horizon="tilted")
    want = photo(100, 700, left=650, right=950, posture="on the ground", level="chest", tilt="up", head_top=20)
    response = tip_for(client, image_api, photos, now, want)
    assert said(response) == [
        "Move much closer.",
        "Hold the phone low at a crouch, tilted up a little.",
        "Put the person on the right of the frame.",
        "Leave almost no room above the head.",
        "Hold the phone upright so the lines behind the person are level.",
    ]
    assert said(tip_for(client, image_api, photos, now, want, language="ko")) == [
        "훨씬 가까이 다가가세요.",
        "폰은 쪼그려 앉아 살짝 위로 기울여 드세요.",
        "사람을 화면 오른쪽에 두세요.",
        "머리 위는 거의 남기지 마세요.",
        "배경의 가로선이 기울지 않게 폰을 바로 세우세요.",
    ]


def test_the_distance_and_the_phone_are_said_when_nothing_differs(client, image_api, photos):
    response = tip_for(client, image_api, photos, photo(), photo())
    text = f"{SAME}\n{CHEST}"
    assert response.json() == {"text": text, "framing": "keep", "hold": "chest", "height": "keep", "angle": "level"}


# ---- The model's answers ---------------------------------------------------------------------------------------------


def candidate_of(fields: dict) -> dict:
    """The candidate's description as the model answers it."""
    return fields["want"] | {key: value for key, value in fields.items() if key not in ("now", "want")}


def test_answers_in_code_fences_are_read(client, image_api, photos):
    scene, candidate = json.dumps(NOW), json.dumps(candidate_of(ANSWER))
    image_api.tip_answers(f"```json\n{scene}\n```", f"```json\n{candidate}\n```")
    assert ask(client, photos).json()["text"] == TEXT


def without(fields: dict, key: str) -> dict:
    return {name: value for name, value in fields.items() if name != key}


@pytest.mark.parametrize(
    "fields",
    [
        ANSWER | {"want": without(WANT, "tilt")},
        ANSWER | {"want": without(WANT, "box")},
        ANSWER | {"want": WANT | {"level": "ankles"}},
        ANSWER | {"want": WANT | {"posture": "flying"}},
        ANSWER | {"want": WANT | {"tilt": "sideways"}},
        ANSWER | {"want": WANT | {"horizon": "wavy"}},
        ANSWER | {"now": NOW | {"shot": "head only"}},
        ANSWER | {"now": without(NOW, "posture")},
        ANSWER | {"now": NOW | {"box": [300, 350, 750]}},
        ANSWER | {"now": NOW | {"box": [300, 350, 750, 1200]}},
        ANSWER | {"now": NOW | {"box": [750, 350, 300, 650]}},
        ANSWER | {"now": NOW | {"box": [300, 650, 750, 650]}},
        ANSWER | {"now": NOW | {"box": ["300", 350, 750, 650]}},
        ANSWER | {"now": NOW | {"box": [True, 350, 750, 650]}},
        ANSWER | {"now": "the whole person"},
        ANSWER | {"want": "the same person, closer"},
    ],
    ids=[
        "tilt missing",
        "box missing",
        "unknown part",
        "unknown posture",
        "unknown tilt",
        "unknown line",
        "unknown shot",
        "posture missing",
        "box of three",
        "box outside the photo",
        "box upside down",
        "box without width",
        "box of text",
        "box of a boolean",
        "scene not described",
        "candidate not described",
    ],
)
def test_unusable_description_is_an_upstream_error(client, image_api, photos, fields):
    image_api.tips(fields)
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("content", ["Hold the phone at your waist.", "[1, 2, 3]", "", None])
@pytest.mark.parametrize("which", ["scene", "candidate"])
def test_answer_that_is_not_a_description_is_an_upstream_error(client, image_api, photos, which, content):
    scene, candidate = json.dumps(NOW), json.dumps(candidate_of(ANSWER))
    image_api.tip_answers(content if which == "scene" else scene, content if which == "candidate" else candidate)
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


def test_head_that_is_not_a_box_leaves_only_its_measures_out(client, image_api, photos):
    # The person may be seen from behind or cut at the neck: the guide's outline for the distance, no room above the
    # head, and the rest as it is.
    for head in ([0, 0, 0, 0], [300, 450, 1200, 550], None):
        image_api.tips(ANSWER | {"want": WANT | {"head": head}})
        response = ask(client, photos)
        assert response.json()["framing"] == "unsure", head
        assert said(response) == [GUIDE, "Hold the phone at your waist, tilted up a little."]


def test_declined_photos_are_rejected(client, image_api, photos):
    image_api.tip_fails(403, "Content moderation")
    assert_error(ask(client, photos), 422, "REJECTED")


@pytest.mark.parametrize("status", [400, 429, 500, 503])
def test_failing_model_is_an_upstream_error(client, image_api, photos, status):
    image_api.tip_fails(status)
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("body", [{"choices": []}, {"choices": [{}]}, ["not", "an", "object"]])
def test_unreadable_body_is_an_upstream_error(client, image_api, photos, body):
    image_api.tip_status = 200
    image_api.tip_bodies = {"tip_scene": body, "tip_candidate": body}
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


def test_slow_model_times_out(client, image_api, photos, settings):
    settings.tip_timeout_seconds = 0.05
    image_api.tip_delay = 5
    response = ask(client, photos)
    assert_error(response, 504, "UPSTREAM_TIMEOUT")
    assert response.elapsed.total_seconds() < 2


async def test_dropped_request_cancels_the_model_call(live_server, image_api, photos):
    # The phone drops the request when the user takes another candidate before the tip arrives.
    image_api.tip_delay = 30
    scene, candidate = photos
    async with httpx2.AsyncClient(base_url=live_server) as phone:
        with pytest.raises(httpx2.ReadTimeout):
            await phone.post(
                "/api/v1/pose-tips",
                files={
                    "scene": ("scene.jpg", scene, "image/jpeg"),
                    "candidate": ("candidate.jpg", candidate, "image/jpeg"),
                },
                data={"pose": POSE},
                timeout=0.2,
            )
    assert len(image_api.tip_requests) == 2  # both were with the model
    async with asyncio.timeout(2):
        await image_api.cancelled.wait()


def test_the_two_requests_are_made_at_once(client, image_api, photos, settings):
    # One after the other they would take 0.8 s and time out.
    image_api.tip_delay = 0.4
    settings.tip_timeout_seconds = 0.7
    assert ask(client, photos).status_code == 200


@pytest.mark.parametrize("slow", ["tip_scene", "tip_candidate"])
def test_slow_answer_is_asked_again_and_the_first_answer_wins(client, image_api, photos, monkeypatch, slow):
    # One of the two descriptions does not come: without a second request the whole tip would time out.
    monkeypatch.setattr(tips, "HEDGE_SECONDS", 0.05)
    image_api.tip_delays = {slow: [30]}
    response = ask(client, photos)
    assert response.status_code == 200 and response.json()["text"] == TEXT
    names = [payload["response_format"]["json_schema"]["name"] for payload in image_api.tip_payloads]
    assert sorted(names) == sorted(["tip_scene", "tip_candidate", slow])
    # The request that never answered is dropped once its second try has.
    assert image_api.cancelled.is_set()


@pytest.mark.parametrize(
    ("delays", "statuses"),
    [
        # The first request fails late, just before its second try answers.
        ([0.15, 0.2], [503, 200]),
        # The second try fails at once, and the first request answers after it.
        ([0.3, 0.0], [200, 429]),
    ],
    ids=["the first request fails", "the second try fails"],
)
def test_failure_of_one_of_the_two_tries_waits_for_the_other(client, image_api, photos, monkeypatch, delays, statuses):
    # A second try is there to get an answer: a failure that happens to come first must not cost the one on its way.
    monkeypatch.setattr(tips, "HEDGE_SECONDS", 0.05)
    image_api.tip_delays = {"tip_scene": list(delays)}
    image_api.tip_statuses = {"tip_scene": list(statuses)}
    response = ask(client, photos)
    assert response.status_code == 200 and response.json()["text"] == TEXT
    assert len(image_api.tip_requests) == 3


def test_both_tries_failing_is_an_upstream_error(client, image_api, photos, monkeypatch):
    monkeypatch.setattr(tips, "HEDGE_SECONDS", 0.05)
    image_api.tip_delays = {"tip_scene": [0.15, 0.05]}
    image_api.tip_statuses = {"tip_scene": [503, 503]}
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")
    assert len(image_api.tip_requests) == 3


def test_failure_before_a_second_try_is_the_answer(client, image_api, photos, monkeypatch):
    # Not slow, so not asked again: the endpoint fails as before instead of retrying every upstream error.
    monkeypatch.setattr(tips, "HEDGE_SECONDS", 0.3)
    image_api.tip_statuses = {"tip_scene": [503]}
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")
    assert len(image_api.tip_requests) == 2


def test_answers_in_time_are_asked_once(client, image_api, photos, monkeypatch):
    monkeypatch.setattr(tips, "HEDGE_SECONDS", 0.3)
    image_api.tip_delay = 0.05
    assert ask(client, photos).status_code == 200
    assert len(image_api.tip_requests) == 2


def test_request_that_stays_connected_is_not_cancelled(client, image_api, photos):
    image_api.tip_delay = 0.6
    assert ask(client, photos).status_code == 200
    assert not image_api.cancelled.is_set()


def test_unreachable_model_is_an_upstream_error(settings, photos, open_store, clock):
    def unreachable(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ConnectError("no route to host", request=request)

    app = create_app(settings, clock=clock, image_api=httpx2.MockTransport(unreachable), redis=open_store)
    with TestClient(app) as client:
        assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")


@pytest.mark.parametrize("key", [None, SecretStr("")])
def test_missing_api_key_is_an_upstream_error_without_a_call(client, image_api, photos, settings, key):
    settings.openrouter_api_key = key
    assert_error(ask(client, photos), 502, "UPSTREAM_ERROR")
    assert image_api.tip_requests == []


def test_photos_outside_the_contract_are_invalid(client, image_api, photos, make_image):
    scene, candidate = photos
    for image in (make_image("PNG"), make_image(size=(768, 1025)), make_image(exif=True), b"not an image"):
        assert_error(ask(client, (image, candidate)), 400, "INVALID_IMAGE")
        assert_error(ask(client, (scene, image)), 400, "INVALID_IMAGE")
    only_scene = {"scene": ("scene.jpg", scene, "image/jpeg")}
    assert_error(ask(client, photos, files=only_scene), 400, "INVALID_REQUEST")
    only_candidate = {"candidate": ("candidate.jpg", candidate, "image/jpeg")}
    assert_error(ask(client, photos, files=only_candidate), 400, "INVALID_REQUEST")
    assert image_api.tip_requests == []


def test_pose_is_folded_like_a_description(client, image_api, photos):
    assert ask(client, photos, pose="  벽에 기대서\n  카메라 보며 웃기  ").status_code == 200
    scene_payload, candidate_payload = image_api.tip_payloads
    assert texts_of(candidate_payload)[-1] == 'The photo, made for the pose "벽에 기대서 카메라 보며 웃기":'
    # The scene was taken before any pose: its request does not hold one.
    assert "벽에" not in json.dumps(scene_payload, ensure_ascii=False)


def test_pose_of_the_longest_length_is_accepted(client, photos):
    assert ask(client, photos, pose="a" * DESCRIPTION_MAX_LENGTH).status_code == 200


@pytest.mark.parametrize("pose", [" \n ", "a" * (DESCRIPTION_MAX_LENGTH + 1), None])
def test_invalid_pose_is_refused_before_a_call_and_not_counted(client, image_api, photos, settings, pose):
    message = assert_error(ask(client, photos, pose=pose), 400, "INVALID_REQUEST")
    assert "pose" in message
    for _ in range(settings.tip_rate_limit):
        assert ask(client, photos).status_code == 200
    assert len(image_api.tip_requests) == 2 * settings.tip_rate_limit  # two requests a tip


def test_tips_are_limited_apart_from_poses_and_suggestions(client, image_api, photos, settings, clock, make_image):
    for _ in range(settings.tip_rate_limit):
        assert ask(client, photos).status_code == 200
    assert_error(ask(client, photos), 429, "RATE_LIMITED")
    assert len(image_api.tip_requests) == 2 * settings.tip_rate_limit  # two requests a tip
    # Poses and suggestions have windows of their own.
    files = {"image": ("scene.jpg", make_image(), "image/jpeg")}
    assert client.post("/api/v1/poses", files=files, data={"seed": "1", "description": "Wave"}).status_code == 200
    assert client.post("/api/v1/pose-suggestions", files=files).status_code == 200

    clock.advance(settings.pose_rate_window_seconds)
    assert ask(client, photos).status_code == 200


def test_global_tip_limit_refuses_every_client(client, photos, settings, clock):
    settings.tip_global_limit = 2
    for _ in range(2):
        assert ask(client, photos).status_code == 200
    assert "today" in assert_error(ask(client, photos), 429, "DAILY_LIMITED")
    clock.advance(settings.pose_global_window_seconds)
    assert ask(client, photos).status_code == 200


def test_tips_are_refused_while_the_session_store_is_down(client, image_api, photos, outage):
    outage(True)
    assert_error(ask(client, photos), 503, "UNAVAILABLE")
    assert image_api.tip_requests == []
    outage(False)
    assert ask(client, photos).status_code == 200


@pytest.mark.parametrize("status", [200, 500])
def test_pose_answer_and_upstream_body_are_not_logged(client, image_api, photos, caplog, status):
    if status == 200:
        # An answer that cannot be used, with what it says of the person and the place.
        image_api.tips(ANSWER | {"now": "SECRET-TIP: by the red door"})
    else:
        image_api.tip_fails(status, "invalid prompt: SECRET-PROMPT data:image/jpeg;base64,SECRET-PHOTO")
    assert_error(ask(client, photos, pose="SECRET-POSE by the red door"), 502, "UPSTREAM_ERROR")
    assert "could not be used" in caplog.text or "OpenRouter answered" in caplog.text
    assert "SECRET" not in caplog.text
    assert "red door" not in caplog.text
