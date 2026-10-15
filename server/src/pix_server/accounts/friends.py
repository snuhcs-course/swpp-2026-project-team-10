# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Exact-match lookup, friend requests, and the friend list with presence (Design Documentation 2.5.3, R&S
FR-5.5–5.10). Only users with a Pix ID take part: nobody can be found or added before choosing one."""

from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Request, Response
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import and_, delete, or_, select
from sqlalchemy.dialects import postgresql, sqlite
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from pix_server.accounts.auth import Caller, Database, SignedIn, millis, now_of
from pix_server.accounts.rules import normalize_pix_id
from pix_server.accounts.schemas import FriendOut, UserOut
from pix_server.db.models import FriendRequest, Friendship, User, new_id
from pix_server.errors import ApiError
from pix_server.store import SessionStore

router = APIRouter(prefix="/api/v1")


async def member(caller: SignedIn) -> Caller:
    if caller.user.pix_id is None:
        raise ApiError(403, "PIX_ID_REQUIRED", "Choose a Pix ID first")
    return caller


Member = Annotated[Caller, Depends(member)]


class Lookup(BaseModel):
    user: UserOut
    # Decides the button: Add, "This is you", "Friends", "Requested", or Accept (FR-5.8).
    relation: Literal["NONE", "SELF", "FRIEND", "REQUESTED", "INCOMING"]
    # The pending request between the two, when relation is REQUESTED or INCOMING.
    requestId: str | None = None


class IncomingRequest(BaseModel):
    model_config = ConfigDict(serialize_by_alias=True)

    id: str
    sender: UserOut = Field(serialization_alias="from")
    createdAt: int


class OutgoingRequest(BaseModel):
    id: str
    to: UserOut
    createdAt: int


class FriendRequests(BaseModel):
    incoming: list[IncomingRequest]
    outgoing: list[OutgoingRequest]


class NewFriendRequest(BaseModel):
    userId: str


class NewFriend(BaseModel):
    friend: FriendOut


class Friends(BaseModel):
    friends: list[FriendOut]


def ordered(first: str, second: str) -> tuple[str, str]:
    return (first, second) if first < second else (second, first)


def between(first: str, second: str):
    """Friend requests in either direction between two users."""
    return or_(
        and_(FriendRequest.from_user_id == first, FriendRequest.to_user_id == second),
        and_(FriendRequest.from_user_id == second, FriendRequest.to_user_id == first),
    )


async def are_friends(db: AsyncSession, first: str, second: str) -> bool:
    return await db.get(Friendship, ordered(first, second)) is not None


async def pending(db: AsyncSession, sender: str, receiver: str) -> FriendRequest | None:
    return await db.scalar(
        select(FriendRequest).where(FriendRequest.from_user_id == sender, FriendRequest.to_user_id == receiver)
    )


async def take_request(db: AsyncSession, *conditions) -> FriendRequest | None:
    """Deletes the request that matches and returns what it was, in one statement. Accepting, declining, and
    withdrawing all take the request this way, so of two at the same moment only one finds it."""
    row = (
        await db.execute(
            delete(FriendRequest)
            .where(*conditions)
            .returning(FriendRequest.id, FriendRequest.from_user_id, FriendRequest.to_user_id)
        )
    ).first()
    return FriendRequest(id=row.id, from_user_id=row.from_user_id, to_user_id=row.to_user_id) if row else None


async def befriend(request: Request, db: AsyncSession, me: str, other: User) -> NewFriend:
    """Makes the two friends and removes the requests between them. The friendship is inserted unless it exists, in
    one statement, so two requests that accept at the same moment both succeed."""
    dialect = postgresql if db.get_bind().dialect.name == "postgresql" else sqlite
    user_a, user_b = ordered(me, other.id)
    await db.execute(
        dialect.insert(Friendship)
        .values(user_a=user_a, user_b=user_b, created_at=now_of(request.app))
        .on_conflict_do_nothing()
    )
    await db.execute(delete(FriendRequest).where(between(me, other.id)))
    await db.commit()
    return NewFriend(friend=(await with_presence(request, [other]))[0])


async def with_presence(request: Request, users: list[User]) -> list[FriendOut]:
    store: SessionStore = request.app.state.store
    presence = await store.presence([user.id for user in users])
    return [FriendOut.of(user, online=presence[user.id][0], inSession=presence[user.id][1]) for user in users]


class LookupRequest(BaseModel):
    pixId: str


@router.post("/users/lookup")
async def find_user(body: LookupRequest, caller: Member, db: Database) -> Lookup:
    """Exact match only, with no partial search or listing (FR-5.5, NFR-15). The Pix ID travels in the body, because
    nginx's error log prints a failed request's whole request line, query included."""
    user = await db.scalar(select(User).where(User.pix_id == normalize_pix_id(body.pixId)))
    if user is None:
        raise ApiError(404, "NOT_FOUND", "No user with this Pix ID")
    me = caller.user.id
    if user.id == me:
        return Lookup(user=UserOut.of(user), relation="SELF")
    if await are_friends(db, me, user.id):
        return Lookup(user=UserOut.of(user), relation="FRIEND")
    sent = await pending(db, me, user.id)
    if sent is not None:
        return Lookup(user=UserOut.of(user), relation="REQUESTED", requestId=sent.id)
    received = await pending(db, user.id, me)
    if received is not None:
        return Lookup(user=UserOut.of(user), relation="INCOMING", requestId=received.id)
    return Lookup(user=UserOut.of(user), relation="NONE")


@router.get("/friend-requests")
async def friend_requests(caller: Member, db: Database) -> FriendRequests:
    """Pending requests the caller received and sent, newest first."""
    me = caller.user.id
    received = await db.execute(
        select(FriendRequest, User)
        .join(User, User.id == FriendRequest.from_user_id)
        .where(FriendRequest.to_user_id == me)
        .order_by(FriendRequest.created_at.desc(), FriendRequest.id)
    )
    sent = await db.execute(
        select(FriendRequest, User)
        .join(User, User.id == FriendRequest.to_user_id)
        .where(FriendRequest.from_user_id == me)
        .order_by(FriendRequest.created_at.desc(), FriendRequest.id)
    )
    return FriendRequests(
        incoming=[
            IncomingRequest(id=item.id, sender=UserOut.of(user), createdAt=millis(item.created_at))
            for item, user in received
        ],
        outgoing=[
            OutgoingRequest(id=item.id, to=UserOut.of(user), createdAt=millis(item.created_at)) for item, user in sent
        ],
    )


@router.post("/friend-requests", status_code=201)
async def send_friend_request(
    body: NewFriendRequest, request: Request, response: Response, caller: Member, db: Database
) -> OutgoingRequest | NewFriend:
    """Sends a request (FR-5.6). If the other user has already asked the caller, this accepts their request
    instead, and the answer is the new friend with `200`."""
    me = caller.user.id
    if body.userId == me:
        raise ApiError(400, "INVALID_REQUEST", "You cannot add yourself")
    other = await db.get(User, body.userId)
    if other is None or other.pix_id is None:
        raise ApiError(404, "NOT_FOUND", "No user with this id")
    if await are_friends(db, me, other.id):
        raise ApiError(409, "ALREADY_FRIENDS", "You are already friends")
    if await pending(db, me, other.id) is not None:
        raise ApiError(409, "ALREADY_REQUESTED", "You have already sent a request to this user")
    other_id = other.id
    # Taking their request, rather than reading it, keeps a withdrawal at the same moment from being overridden.
    if await take_request(db, FriendRequest.from_user_id == other_id, FriendRequest.to_user_id == me) is not None:
        response.status_code = 200
        return await befriend(request, db, me, other)
    user_a, user_b = ordered(me, other_id)
    sent = FriendRequest(
        id=new_id("fr"),
        from_user_id=me,
        to_user_id=other_id,
        user_a=user_a,
        user_b=user_b,
        created_at=now_of(request.app),
    )
    db.add(sent)
    try:
        await db.commit()
    except IntegrityError as exc:
        # A request between the two was saved after the checks above, so the pair is taken. If it is theirs, both
        # asked at once: theirs is accepted, as if it had been there first.
        await db.rollback()
        other = await db.get(User, other_id)
        if await take_request(db, FriendRequest.from_user_id == other_id, FriendRequest.to_user_id == me) is not None:
            response.status_code = 200
            return await befriend(request, db, me, other)
        raise ApiError(409, "ALREADY_REQUESTED", "You have already sent a request to this user") from exc
    return OutgoingRequest(id=sent.id, to=UserOut.of(other), createdAt=millis(sent.created_at))


def no_such_request() -> ApiError:
    return ApiError(404, "NOT_FOUND", "No pending friend request with this id")


@router.post("/friend-requests/{request_id}/accept")
async def accept_friend_request(request_id: str, request: Request, caller: Member, db: Database) -> NewFriend:
    me = caller.user.id
    taken = await take_request(db, FriendRequest.id == request_id, FriendRequest.to_user_id == me)
    if taken is None:
        raise no_such_request()
    return await befriend(request, db, me, await db.get(User, taken.from_user_id))


@router.post("/friend-requests/{request_id}/decline", status_code=204)
async def decline_friend_request(request_id: str, caller: Member, db: Database) -> None:
    """The request is deleted, so the sender can ask again later (US-8)."""
    if await take_request(db, FriendRequest.id == request_id, FriendRequest.to_user_id == caller.user.id) is None:
        raise no_such_request()
    await db.commit()


@router.delete("/friend-requests/{request_id}", status_code=204)
async def cancel_friend_request(request_id: str, caller: Member, db: Database) -> None:
    """The sender withdraws a request that has not been answered."""
    if await take_request(db, FriendRequest.id == request_id, FriendRequest.from_user_id == caller.user.id) is None:
        raise no_such_request()
    await db.commit()


@router.get("/friends")
async def friends(request: Request, caller: Member, db: Database) -> Friends:
    """Each friend with presence: `online` while their phone holds a signed-in signaling socket, `inSession` while
    that socket is in a session (FR-5.9, FR-6.9)."""
    me = caller.user.id
    users = await db.scalars(
        select(User)
        .join(
            Friendship,
            or_(
                and_(Friendship.user_a == me, Friendship.user_b == User.id),
                and_(Friendship.user_b == me, Friendship.user_a == User.id),
            ),
        )
        .order_by(User.display_name, User.pix_id)
    )
    return Friends(friends=await with_presence(request, list(users)))


@router.delete("/friends/{user_id}", status_code=204)
async def remove_friend(user_id: str, caller: Member, db: Database) -> None:
    """Ends the friendship for both users (FR-5.10). Removing someone who is not a friend changes nothing."""
    me = caller.user.id
    user_a, user_b = ordered(me, user_id)
    await db.execute(delete(Friendship).where(Friendship.user_a == user_a, Friendship.user_b == user_b))
    await db.execute(delete(FriendRequest).where(between(me, user_id)))
    await db.commit()
