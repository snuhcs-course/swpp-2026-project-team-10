# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Sign-up, sign-in, the caller's own account, and device tokens (Design Documentation 2.5.3, R&S F5).

A user signs up with a Pix ID and a password, or with Google; either kind of account can add the other way to sign
in later. Every sign-in starts a session for that phone and answers with its access and refresh tokens (`auth.py`).
"""

from datetime import timedelta
from typing import Literal

from fastapi import APIRouter, Request
from pydantic import BaseModel, Field
from sqlalchemy import delete, select, update
from sqlalchemy.exc import IntegrityError

from pix_server.accounts.auth import Database, SignedIn, Tokens, hash_token, new_refresh_token, now_of
from pix_server.accounts.google import GoogleAccount, GoogleUnavailable, GoogleVerifier, InvalidGoogleToken
from pix_server.accounts.passwords import Passwords
from pix_server.accounts.rules import DISPLAY_NAME_MAX, normalize_pix_id, password_problem, pix_id_problem
from pix_server.accounts.schemas import AccountOut, AuthOut, DisplayName, TokensOut
from pix_server.db.models import AuthSession, Device, User, new_id
from pix_server.errors import ApiError

router = APIRouter(prefix="/api/v1")

# The name of a Google account that shares none.
FALLBACK_NAME = "Pix user"


class SignUpRequest(BaseModel):
    pixId: str
    password: str
    displayName: DisplayName


class LogInRequest(BaseModel):
    pixId: str
    password: str


class GoogleRequest(BaseModel):
    idToken: str = Field(min_length=1)


class RefreshRequest(BaseModel):
    refreshToken: str = Field(min_length=1)


class PasswordRequest(BaseModel):
    password: str


class AccountUpdate(BaseModel):
    pixId: str | None = None
    displayName: DisplayName | None = None


class DeviceRequest(BaseModel):
    fcmToken: str = Field(min_length=1, max_length=4096)
    platform: Literal["android"]


def pix_id_taken() -> ApiError:
    return ApiError(409, "PIX_ID_TAKEN", "This Pix ID is taken")


def google_in_use() -> ApiError:
    return ApiError(409, "GOOGLE_IN_USE", "This Google account is already used by another Pix account")


def check_pix_id(pix_id: str) -> None:
    problem = pix_id_problem(pix_id)
    if problem is not None:
        raise ApiError(400, "PIX_ID_INVALID", problem)


def session_ttl(request: Request) -> timedelta:
    return timedelta(days=request.app.state.settings.session_ttl_days)


def issue(request: Request, user_id: str, session_id: str, refresh_token: str) -> TokensOut:
    tokens: Tokens = request.app.state.tokens
    return TokensOut(
        accessToken=tokens.issue(user_id, session_id), refreshToken=refresh_token, expiresIn=tokens.ttl_seconds
    )


async def signed_in(request: Request, db: Database, user: User) -> AuthOut:
    """Starts a session for this phone, saves it with whatever the sign-in changed, and answers with its tokens.
    The user's expired sessions are removed on the way."""
    now = now_of(request.app)
    await db.execute(delete(AuthSession).where(AuthSession.user_id == user.id, AuthSession.expires_at <= now))
    refresh_token = new_refresh_token()
    session = AuthSession(
        id=new_id("ses"),
        user_id=user.id,
        refresh_hash=hash_token(refresh_token),
        created_at=now,
        expires_at=now + session_ttl(request),
    )
    db.add(session)
    await db.commit()
    tokens = issue(request, user.id, session.id, refresh_token)
    return AuthOut(**tokens.model_dump(), user=AccountOut.mine(user))


def invalid_refresh_token() -> ApiError:
    return ApiError(401, "INVALID_REFRESH_TOKEN", "This sign-in has ended; sign in again")


async def google_account(request: Request, id_token: str) -> GoogleAccount:
    google: GoogleVerifier = request.app.state.google
    try:
        return await google.verify(id_token)
    except InvalidGoogleToken as exc:
        raise ApiError(401, "INVALID_TOKEN", "This Google sign-in is not valid; sign in again") from exc
    except GoogleUnavailable as exc:
        raise ApiError(503, "UNAVAILABLE", "Google sign-in is unavailable; try again later") from exc


# --- sign-up and sign-in -------------------------------------------------------------------------------------------


@router.post("/auth/signup", status_code=201)
async def sign_up(body: SignUpRequest, request: Request, db: Database) -> AuthOut:
    check_pix_id(body.pixId)
    problem = password_problem(body.password, body.pixId)
    if problem is not None:
        raise ApiError(400, "PASSWORD_INVALID", problem)
    passwords: Passwords = request.app.state.passwords
    user = User(
        id=new_id("u"),
        pix_id=body.pixId,
        password_hash=await passwords.hash(body.password),
        display_name=body.displayName,
        created_at=now_of(request.app),
    )
    db.add(user)
    try:
        await db.flush()
    except IntegrityError as exc:
        raise pix_id_taken() from exc
    return await signed_in(request, db, user)


@router.post("/auth/login")
async def log_in(body: LogInRequest, request: Request, db: Database) -> AuthOut:
    user = await db.scalar(select(User).where(User.pix_id == normalize_pix_id(body.pixId)))
    passwords: Passwords = request.app.state.passwords
    # One answer for an unknown Pix ID and a wrong password, so the answer does not tell which Pix IDs exist.
    if not await passwords.verify(user.password_hash if user is not None else None, body.password):
        raise ApiError(401, "INVALID_CREDENTIALS", "The Pix ID or the password is wrong")
    return await signed_in(request, db, user)


@router.post("/auth/google")
async def sign_in_with_google(body: GoogleRequest, request: Request, db: Database) -> AuthOut:
    """`404 NOT_REGISTERED` for a Google account no Pix account uses: the app then asks whether the person already
    has a Pix account, and either signs in with it and adds Google (`PUT /me/google`) or signs up with Google."""
    account = await google_account(request, body.idToken)
    user = await db.scalar(select(User).where(User.google_sub == account.subject))
    if user is None:
        raise ApiError(404, "NOT_REGISTERED", "No Pix account uses this Google account")
    return await signed_in(request, db, user)


@router.post("/auth/google/signup", status_code=201)
async def sign_up_with_google(body: GoogleRequest, request: Request, db: Database) -> AuthOut:
    """A new account without a Pix ID, which the person chooses next (`PUT /me`)."""
    account = await google_account(request, body.idToken)
    name = (account.name or "").strip()[:DISPLAY_NAME_MAX].strip() or FALLBACK_NAME
    user = User(id=new_id("u"), google_sub=account.subject, display_name=name, created_at=now_of(request.app))
    db.add(user)
    try:
        await db.flush()
    except IntegrityError as exc:
        raise google_in_use() from exc
    return await signed_in(request, db, user)


@router.post("/auth/refresh")
async def refresh(body: RefreshRequest, request: Request, db: Database) -> TokensOut:
    """Exchanges a refresh token for a new access token and a new refresh token; the old refresh token stops
    working. One that was already exchanged and comes back may have been stolen, so it ends the session: the phone
    and whoever holds a copy both have to sign in again."""
    now = now_of(request.app)
    presented = hash_token(body.refreshToken)
    session = await db.scalar(select(AuthSession).where(AuthSession.refresh_hash == presented))
    if session is None:
        await db.execute(delete(AuthSession).where(AuthSession.previous_refresh_hash == presented))
        await db.commit()
        raise invalid_refresh_token()
    if session.expires_at <= now:
        await db.delete(session)
        await db.commit()
        raise invalid_refresh_token()
    refresh_token = new_refresh_token()
    # Only if the token is still the current one, so two refreshes with one token cannot both succeed.
    rotated = await db.execute(
        update(AuthSession)
        .where(AuthSession.id == session.id, AuthSession.refresh_hash == presented)
        .values(
            previous_refresh_hash=presented,
            refresh_hash=hash_token(refresh_token),
            expires_at=now + session_ttl(request),
        )
    )
    await db.commit()
    if rotated.rowcount != 1:
        raise invalid_refresh_token()
    return issue(request, session.user_id, session.id, refresh_token)


@router.delete("/auth/session", status_code=204)
async def sign_out(caller: SignedIn, db: Database) -> None:
    """Ends this phone's session: its refresh token stops working, and its device token is deleted with it, so the
    phone gets no more pushes. The access token is not stored and works until it expires, minutes later."""
    await db.execute(delete(AuthSession).where(AuthSession.id == caller.session_id))
    await db.commit()


# --- the caller's account ------------------------------------------------------------------------------------------


@router.get("/me")
async def me(caller: SignedIn) -> AccountOut:
    return AccountOut.mine(caller.user)


@router.put("/me")
async def update_me(body: AccountUpdate, caller: SignedIn, db: Database) -> AccountOut:
    """Sets the Pix ID, the display name, or both. A password account signs in with its new Pix ID afterwards."""
    user = caller.user
    if body.pixId is not None and body.pixId != user.pix_id:
        check_pix_id(body.pixId)
        user.pix_id = body.pixId
    if body.displayName is not None:
        user.display_name = body.displayName
    try:
        await db.commit()
    except IntegrityError as exc:
        raise pix_id_taken() from exc
    return AccountOut.mine(user)


@router.put("/me/google")
async def add_google(body: GoogleRequest, request: Request, caller: SignedIn, db: Database) -> AccountOut:
    """Lets the account sign in with this Google account too. Adding the same one again changes nothing."""
    account = await google_account(request, body.idToken)
    user = caller.user
    if user.google_sub == account.subject:
        return AccountOut.mine(user)
    if user.google_sub is not None:
        raise ApiError(409, "GOOGLE_ALREADY_ADDED", "This Pix account already signs in with another Google account")
    user.google_sub = account.subject
    try:
        await db.commit()
    except IntegrityError as exc:
        raise google_in_use() from exc
    return AccountOut.mine(user)


@router.put("/me/password")
async def add_password(body: PasswordRequest, request: Request, caller: SignedIn, db: Database) -> AccountOut:
    """Lets an account made with Google sign in with its Pix ID and this password too. The Pix ID is the login name,
    so it comes first. Changing a password the account already has is not offered."""
    user = caller.user
    if user.password_hash is not None:
        raise ApiError(409, "PASSWORD_ALREADY_SET", "This Pix account already has a password")
    if user.pix_id is None:
        raise ApiError(403, "PIX_ID_REQUIRED", "Choose a Pix ID first")
    problem = password_problem(body.password, user.pix_id)
    if problem is not None:
        raise ApiError(400, "PASSWORD_INVALID", problem)
    passwords: Passwords = request.app.state.passwords
    user.password_hash = await passwords.hash(body.password)
    await db.commit()
    return AccountOut.mine(user)


@router.post("/devices", status_code=204)
async def register_device(body: DeviceRequest, request: Request, caller: SignedIn, db: Database) -> None:
    """Registers this phone's Firebase token under the caller and this session, or moves it there if the phone was
    signed in to another account. A session keeps one device: a rotated token replaces the old one."""
    if await db.get(AuthSession, caller.session_id) is None:
        # Signed out, with an access token that has not expired yet.
        raise ApiError(401, "UNAUTHENTICATED", "Sign in to use this")
    device = await db.scalar(select(Device).where(Device.fcm_token == body.fcmToken))
    if device is None:
        device = Device(id=new_id("d"), fcm_token=body.fcmToken)
        db.add(device)
    device.user_id = caller.user.id
    device.session_id = caller.session_id
    device.platform = body.platform
    device.updated_at = now_of(request.app)
    await db.flush()
    await db.execute(delete(Device).where(Device.session_id == caller.session_id, Device.id != device.id))
    await db.commit()
