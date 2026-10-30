# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""Invitations to shoot together (Design Documentation 2.5.3, 2.6.6; R&S FR-6.1–6.4, FR-6.9).

Inviting a friend opens the session at once, with only the photographer's place: the answer carries that place's
rejoin token, with which the photographer's phone takes it over signaling, and accepting gives the friend the
subject's. Both join with `session.join`, and the photographer hears `peer.joined` as with a room code (AD-8). The row
records what the two decided, and the room ends with it: a decline or a cancel removes it, and the sweeper expires a
room nobody joined at the invitation's expiry, which tells the photographer. Only friends invite each other, and only
the friend answers; anyone with the link can see who is inviting.
"""

import math
import re
from datetime import timedelta
from string import Template
from typing import Literal

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import delete, update
from sqlalchemy.orm import InstrumentedAttribute

from pix_server.accounts.auth import Database, SignedIn, millis, now_of
from pix_server.accounts.friends import Member, are_friends
from pix_server.accounts.schemas import UserOut
from pix_server.db.models import Invitation, User, new_id
from pix_server.errors import ApiError
from pix_server.invitations.push import push_to_user
from pix_server.signaling.registry import SessionRegistry
from pix_server.store import SessionStore

router = APIRouter(prefix="/api/v1")
# The page an invite link opens, outside /api/v1.
pages = APIRouter()

# Rows are deleted this long after they were created (Design 2.7).
KEEP = timedelta(days=1)
INVITATION_ID = re.compile(r"inv_[0-9A-HJKMNP-TV-Z]{26}")

Status = Literal["PENDING", "ACCEPTED", "DECLINED", "CANCELLED", "EXPIRED"]


class NewInvitation(BaseModel):
    friendId: str


class SentInvitation(BaseModel):
    id: str
    to: UserOut
    status: Status
    expiresAt: int
    # For the share sheet when the push does not arrive (FR-6.1).
    link: str
    # The photographer's place in the session, taken with `session.join`.
    sessionId: str
    rejoinToken: str
    iceServers: list[dict]


class OpenedInvitation(BaseModel):
    model_config = ConfigDict(serialize_by_alias=True)

    id: str
    sender: UserOut = Field(serialization_alias="from")
    status: Status
    expiresAt: int


class AcceptedInvitation(BaseModel):
    """The friend's place in the session, taken with `session.join`."""

    sessionId: str
    rejoinToken: str
    iceServers: list[dict]


def not_found() -> ApiError:
    return ApiError(404, "NOT_FOUND", "No invitation with this id")


def gone() -> ApiError:
    return ApiError(410, "INVITATION_GONE", "This invitation is no longer available")


def status_of(invitation: Invitation, request: Request) -> Status:
    """A pending invitation past its expiry has expired; nothing is written when it does."""
    if invitation.status == "PENDING" and invitation.expires_at <= now_of(request.app):
        return "EXPIRED"
    return invitation.status


async def answer(
    request: Request, db: Database, invitation_id: str, status: str, party: InstrumentedAttribute, caller: str
) -> str:
    """Moves a pending invitation that has not expired to `status` in one statement, so of two answers at the same
    moment only one counts, and returns its session. `party` must name the caller: only the friend accepts or
    declines, and only the photographer cancels. Someone else's invitation is NOT_FOUND, and one already answered or
    expired is INVITATION_GONE."""
    taken = (
        await db.execute(
            update(Invitation)
            .where(
                Invitation.id == invitation_id,
                party == caller,
                Invitation.status == "PENDING",
                Invitation.expires_at > now_of(request.app),
            )
            .values(status=status)
            .returning(Invitation.session_id)
        )
    ).first()
    if taken is not None:
        return taken.session_id
    found = await db.get(Invitation, invitation_id)
    if found is None or getattr(found, party.key) != caller:
        raise not_found()
    raise gone()


@router.post("/invitations", status_code=201)
async def invite(
    body: NewInvitation, request: Request, background: BackgroundTasks, caller: Member, db: Database
) -> SentInvitation:
    """Invites a friend (FR-6.1) and pushes to the friend's phones after answering. A friend in a session, a waiting
    room included, is BUSY (FR-6.9); anyone but a friend is NOT_FRIENDS, whether or not the user exists (NFR-15)."""
    me = caller.user
    friend = await db.get(User, body.friendId)
    if friend is None or not await are_friends(db, me.id, friend.id):
        raise ApiError(403, "NOT_FRIENDS", "You can invite only your friends")
    store: SessionStore = request.app.state.store
    if (await store.presence([friend.id]))[friend.id][1]:
        raise ApiError(409, "BUSY", f"{friend.display_name} is in another session")
    settings = request.app.state.settings
    registry: SessionRegistry = request.app.state.signaling
    now = now_of(request.app)
    expires_at = now + timedelta(seconds=settings.invitation_ttl_seconds)
    invitation = Invitation(
        id=new_id("inv"),
        from_user_id=me.id,
        to_user_id=friend.id,
        status="PENDING",
        expires_at=expires_at,
        created_at=now,
    )
    # The room opens before the commit, so a session store that fails answers 503 with nothing saved. A commit that
    # fails leaves a room nobody can join, which expires with the invitation.
    invitation.session_id, token = await registry.invite(invitation.id, expires_at.timestamp())
    await db.execute(delete(Invitation).where(Invitation.created_at < now - KEEP))
    db.add(invitation)
    await db.commit()
    # Asked after the commit: Cloudflare can take seconds, and the database holds nothing for it meanwhile.
    ice_servers = await registry.ice_servers()
    push = {
        "type": "invitation",
        "invitationId": invitation.id,
        # "from" is a key FCM keeps for itself.
        "fromName": me.display_name,
        "expiresAt": str(millis(expires_at)),
    }
    background.add_task(push_to_user, request.app, friend.id, push, math.ceil(settings.invitation_ttl_seconds))
    return SentInvitation(
        id=invitation.id,
        to=UserOut.of(friend),
        status="PENDING",
        expiresAt=millis(expires_at),
        link=f"{str(request.base_url).rstrip('/')}/i/{invitation.id}",
        sessionId=invitation.session_id,
        rejoinToken=token,
        iceServers=ice_servers,
    )


@router.get("/invitations/{invitation_id}")
async def open_invitation(invitation_id: str, request: Request, db: Database) -> OpenedInvitation:
    """What the friend's Invitation screen shows, opened from a push or a link (FR-6.4). No sign-in, so a link opens on
    any phone; it shows only who is inviting."""
    invitation = await db.get(Invitation, invitation_id)
    if invitation is None:
        raise not_found()
    sender = await db.get(User, invitation.from_user_id)
    return OpenedInvitation(
        id=invitation.id,
        sender=UserOut.of(sender),
        status=status_of(invitation, request),
        expiresAt=millis(invitation.expires_at),
    )


@router.post("/invitations/{invitation_id}/accept")
async def accept_invitation(invitation_id: str, request: Request, caller: SignedIn, db: Database) -> AcceptedInvitation:
    """The friend accepts: their place in the session, to take within `invitation_join_seconds`. The session store is
    asked before the commit, so an invitation whose room has ended (the photographer left) is not marked accepted."""
    session_id = await answer(request, db, invitation_id, "ACCEPTED", Invitation.to_user_id, caller.user.id)
    registry: SessionRegistry = request.app.state.signaling
    deadline = request.app.state.clock() + request.app.state.settings.invitation_join_seconds
    token = await registry.accept_invitation(session_id, deadline)
    if token is None:
        raise gone()
    await db.commit()
    return AcceptedInvitation(sessionId=session_id, rejoinToken=token, iceServers=await registry.ice_servers())


@router.post("/invitations/{invitation_id}/decline", status_code=204)
async def decline_invitation(invitation_id: str, request: Request, caller: SignedIn, db: Database) -> None:
    """The friend declines (FR-6.3): the room ends, and the photographer hears DECLINED."""
    session_id = await answer(request, db, invitation_id, "DECLINED", Invitation.to_user_id, caller.user.id)
    registry: SessionRegistry = request.app.state.signaling
    await registry.end_invitation(session_id, "DECLINED")
    await db.commit()


@router.post("/invitations/{invitation_id}/cancel", status_code=204)
async def cancel_invitation(invitation_id: str, request: Request, caller: SignedIn, db: Database) -> None:
    """The photographer cancels (FR-6.2): the room ends, and the friend's Invitation screen says that the invitation is
    no longer available (FR-6.4)."""
    session_id = await answer(request, db, invitation_id, "CANCELLED", Invitation.from_user_id, caller.user.id)
    registry: SessionRegistry = request.app.state.signaling
    await registry.end_invitation(session_id)
    await db.commit()


LINK_PAGE = Template("""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pix</title>
<style>
body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #0b0b0d; color: #f4f4f2; font-family: system-ui, sans-serif; text-align: center; }
main { padding: 32px 24px; max-width: 360px; }
h1 { font-size: 28px; margin: 0 0 12px; }
p { line-height: 1.5; margin: 0 0 24px; }
a { display: block; padding: 14px; border-radius: 12px; background: #f2c14e; color: #141413; font-weight: 700;
  text-decoration: none; }
.note { margin: 24px 0 0; font-size: 14px; color: #8a8e94; }
</style>
</head>
<body>
<main>
<h1>Pix</h1>
<p>친구가 같이 찍자고 초대했어요.<br>A friend invites you to shoot together.</p>
<a href="intent://invite/$id#Intent;scheme=pix;package=com.lastpenguin.pix;end">Pix에서 열기 · Open in Pix</a>
<p class="note">Pix가 없다면 친구에게 앱을 받아 설치하세요.<br>No Pix on this phone? Get the app from your friend.</p>
</main>
</body>
</html>
""")


@pages.get("/i/{invitation_id}", response_class=HTMLResponse)
async def invite_link(invitation_id: str) -> HTMLResponse:
    """The page an invite link opens (Design 2.6.6): a button that opens the Invitation screen in Pix through its
    `pix://invite/<id>` scheme, since a verified App Link that opens Pix directly needs HTTPS (AD-11). The page reads
    nothing and names nobody, because a link can travel further than the friend."""
    if not INVITATION_ID.fullmatch(invitation_id):
        raise HTTPException(404)
    return HTMLResponse(LINK_PAGE.substitute(id=invitation_id))
