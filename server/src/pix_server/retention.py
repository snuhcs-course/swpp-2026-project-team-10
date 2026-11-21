# AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
"""Deletes what the server keeps only for a while, so the privacy policy's periods hold also for an account nobody
uses (policy section 3, Design 2.7): invitations a day after they were made, sign-in sessions once they expire, 30
days after their last use, with their devices and spent refresh tokens, and spent refresh tokens one session lifetime
after they were spent. Requests delete some of the same rows on the way (a new invitation, a sign-in, a refresh); this
covers the rest.

Every worker runs a pass when it starts, before it serves, and then every hour. A pass gives the same result run twice
or by two workers at once, so the workers need no lock between them.
"""

import asyncio
import logging
from datetime import datetime, timedelta

from fastapi import FastAPI
from sqlalchemy import delete
from sqlalchemy.ext.asyncio import AsyncSession

from pix_server.accounts.auth import now_of
from pix_server.db.models import AuthSession, Invitation, SpentRefreshToken
from pix_server.invitations.router import KEEP

logger = logging.getLogger(__name__)

INTERVAL_SECONDS = 3600


async def prune(db: AsyncSession, now: datetime, session_ttl: timedelta) -> None:
    """One pass at [now]. A session's device and spent refresh tokens go with it (`ON DELETE CASCADE`)."""
    await db.execute(delete(Invitation).where(Invitation.created_at < now - KEEP))
    await db.execute(delete(AuthSession).where(AuthSession.expires_at <= now))
    await db.execute(delete(SpentRefreshToken).where(SpentRefreshToken.spent_at < now - session_ttl))
    await db.commit()


async def prune_now(app: FastAPI) -> None:
    """A pass at the app's time. A failure, such as the database being away, is logged, and the next pass tries
    again."""
    try:
        async with app.state.database() as db:
            await prune(db, now_of(app), timedelta(days=app.state.settings.session_ttl_days))
    except Exception as exc:
        # The rows hold no secret, but a database error's text can quote them, so only its kind is logged.
        logger.warning("Could not delete expired rows (%s); trying again in an hour", type(exc).__name__)


async def keep_pruning(app: FastAPI) -> None:
    """A pass every hour until cancelled, after the one at the start."""
    while True:
        await asyncio.sleep(INTERVAL_SECONDS)
        await prune_now(app)
