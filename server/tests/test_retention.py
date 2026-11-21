# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""Deleting what the server keeps only for a while (privacy policy section 3, Design 2.7), for accounts nobody uses.

Partitions:
- invitations: made more than a day ago (deleted); made within the day (kept)
- sign-in sessions: expired, with their device and spent refresh token (all deleted); still valid (kept)
- spent refresh tokens of a valid session: spent more than one session lifetime ago (deleted); within it (kept)
- when: one pass, given the time; a worker's start, before it serves a request
"""

from datetime import UTC, datetime, timedelta

import httpx2
from fastapi.testclient import TestClient
from sqlalchemy import select

from pix_server.db.models import AuthSession, Device, Invitation, SpentRefreshToken
from pix_server.main import create_app
from pix_server.retention import prune

TTL = timedelta(days=30)


def ids(client, model, column):
    async def read():
        async with client.app.state.database() as db:
            return set((await db.execute(select(column))).scalars())

    return client.portal.call(read)


def add(client, *rows):
    async def write():
        async with client.app.state.database() as db:
            for row in rows:
                db.add(row)
                await db.flush()
            await db.commit()

    client.portal.call(write)


def old_and_new_rows(client, sign_up, now):
    """An invitation and a spent token on each side of their periods, and an expired session with what it holds."""
    _, me = sign_up("test1", "Test Account 1")
    _, friend = sign_up("test2", "Test Account 2")
    live = next(iter(ids(client, AuthSession, AuthSession.id)))

    def invitation(name, made):
        return Invitation(
            id=name,
            from_user_id=me["id"],
            to_user_id=friend["id"],
            status="PENDING",
            session_id="s_" + name,
            expires_at=made + timedelta(minutes=1),
            created_at=made,
        )

    add(
        client,
        invitation("inv_old", now - timedelta(days=1, minutes=1)),
        invitation("inv_new", now - timedelta(hours=23)),
        AuthSession(
            id="ses_expired",
            user_id=me["id"],
            refresh_hash="hash-expired",
            created_at=now - TTL - timedelta(days=1),
            expires_at=now - timedelta(seconds=1),
        ),
        Device(
            id="dev_expired",
            user_id=me["id"],
            session_id="ses_expired",
            fcm_token="token-expired",
            platform="android",
            updated_at=now - TTL,
        ),
        SpentRefreshToken(token_hash="spent-expired", session_id="ses_expired", spent_at=now - timedelta(days=2)),
        SpentRefreshToken(token_hash="spent-old", session_id=live, spent_at=now - TTL - timedelta(minutes=1)),
        SpentRefreshToken(token_hash="spent-new", session_id=live, spent_at=now - timedelta(days=1)),
    )
    return live


def assert_only_what_is_within_its_period_is_left(client, live):
    assert ids(client, Invitation, Invitation.id) == {"inv_new"}
    assert "ses_expired" not in ids(client, AuthSession, AuthSession.id)
    assert live in ids(client, AuthSession, AuthSession.id)
    assert ids(client, Device, Device.id) == set()
    assert ids(client, SpentRefreshToken, SpentRefreshToken.token_hash) == {"spent-new"}


def test_a_pass_deletes_what_is_past_its_period_and_keeps_the_rest(client, sign_up, clock):
    now = datetime.fromtimestamp(clock(), UTC)
    live = old_and_new_rows(client, sign_up, now)

    async def run():
        async with client.app.state.database() as db:
            await prune(db, now, TTL)

    client.portal.call(run)

    assert_only_what_is_within_its_period_is_left(client, live)


def test_a_worker_deletes_them_when_it_starts(client, sign_up, clock, settings, open_store, google):
    now = datetime.fromtimestamp(clock(), UTC)
    live = old_and_new_rows(client, sign_up, now)

    # A second worker on the same database, as after a deploy or a restart.
    app = create_app(
        settings,
        clock=clock,
        image_api=httpx2.MockTransport(lambda request: httpx2.Response(500)),
        turn_api=httpx2.MockTransport(lambda request: httpx2.Response(500)),
        fcm_api=httpx2.MockTransport(lambda request: httpx2.Response(500)),
        redis=open_store,
        google=google,
    )
    with TestClient(app):
        assert_only_what_is_within_its_period_is_left(client, live)
