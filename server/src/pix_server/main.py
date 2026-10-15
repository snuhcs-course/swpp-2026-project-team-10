# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""ASGI entry point: uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000."""

import asyncio
import logging
import secrets
import time
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

import httpx2
from argon2 import PasswordHasher
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from redis.asyncio import Redis
from redis.exceptions import RedisError
from sqlalchemy.exc import InterfaceError, OperationalError
from sqlalchemy.ext.asyncio import async_sessionmaker
from starlette.middleware.body_limit import RequestBodyLimitMiddleware

from pix_server.accounts.auth import Tokens
from pix_server.accounts.friends import router as friends_router
from pix_server.accounts.google import GoogleTokenVerifier, GoogleVerifier
from pix_server.accounts.passwords import Passwords
from pix_server.accounts.router import router as accounts_router
from pix_server.config import Settings
from pix_server.db import migrate, open_database
from pix_server.errors import (
    ApiError,
    handle_api_error,
    handle_database_error,
    handle_store_error,
    handle_validation_error,
)
from pix_server.poses.generator import PoseGenerator
from pix_server.poses.limiter import PoseLimiter
from pix_server.poses.router import router as poses_router
from pix_server.signaling.registry import SessionRegistry
from pix_server.signaling.router import router as signaling_router
from pix_server.store import SessionStore, open_redis

# Starlette keeps an upload of up to 1 MiB in memory and spools a larger one to a temporary file. Refusing bigger
# requests before they are parsed means a scene photo never reaches the disk (FR-4.9) and an upload cannot fill it.
MAX_REQUEST_BYTES = 1024 * 1024
# nginx and the container's health check must not wait long for a store that does not answer.
HEALTH_TIMEOUT_SECONDS = 2

logger = logging.getLogger(__name__)


class HideQuery(logging.Filter):
    """Logs a request's path without its query, which can hold a Pix ID (`GET /users?pixId=`, NFR-13)."""

    def filter(self, record: logging.LogRecord) -> bool:
        # Uvicorn's access record: client, method, path with query, HTTP version, status.
        if isinstance(record.args, tuple) and len(record.args) == 5:
            client, method, path, version, status = record.args
            record.args = (client, method, str(path).partition("?")[0], version, status)
        return True


logging.getLogger("uvicorn.access").addFilter(HideQuery())


def jwt_secret(settings: Settings) -> str:
    if settings.jwt_secret is not None and settings.jwt_secret.get_secret_value():
        return settings.jwt_secret.get_secret_value()
    logger.warning("PIX_JWT_SECRET is not set: access tokens end with this process, so run one worker only")
    return secrets.token_hex(32)


def create_app(
    settings: Settings | None = None,
    *,
    clock: Callable[[], float] = time.time,
    image_api: httpx2.AsyncBaseTransport | None = None,
    redis: Callable[[], Redis] | None = None,
    google: GoogleVerifier | None = None,
    password_hasher: PasswordHasher | None = None,
) -> FastAPI:
    """`image_api` replaces the network connection to the image API, `redis` opens the session store, `google`
    verifies Google ID tokens, and `password_hasher` sets the cost of argon2id; tests pass fakes or cheaper ones.
    `clock` is wall-clock time, because every worker compares it with times other workers stored."""
    config = settings if settings is not None else Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        database = open_database(config.database_url)
        connection = redis() if redis is not None else open_redis(config)
        store = SessionStore(connection, config, clock)
        registry = SessionRegistry(config, store)
        app.state.settings = config
        app.state.clock = clock
        app.state.store = store
        app.state.signaling = registry
        app.state.pose_limiter = PoseLimiter(store, config)
        app.state.passwords = Passwords(password_hasher or PasswordHasher())
        app.state.tokens = Tokens(jwt_secret(config), config.access_token_minutes * 60, clock)
        try:
            await migrate(database)
            app.state.database = async_sessionmaker(database, expire_on_commit=False)
            # The generator enforces its own total deadline, so the client sets none.
            async with (
                httpx2.AsyncClient(transport=image_api, timeout=None) as http,
                httpx2.AsyncClient() as google_http,
            ):
                app.state.pose_generator = PoseGenerator(config, http)
                app.state.google = google or GoogleTokenVerifier(config.google_client_id, google_http, clock)
                await registry.start()
                try:
                    yield
                finally:
                    await registry.stop()
        finally:
            await connection.aclose()
            await database.dispose()

    app = FastAPI(title="Pix server", version="0.1.0", lifespan=lifespan)
    app.include_router(signaling_router)
    app.include_router(poses_router)
    app.include_router(accounts_router)
    app.include_router(friends_router)
    app.add_middleware(RequestBodyLimitMiddleware, max_body_size=MAX_REQUEST_BYTES)
    app.add_exception_handler(ApiError, handle_api_error)
    app.add_exception_handler(RequestValidationError, handle_validation_error)
    app.add_exception_handler(RedisError, handle_store_error)
    for error in (OperationalError, InterfaceError, OSError):
        app.add_exception_handler(error, handle_database_error)

    @app.get("/health")
    async def health() -> JSONResponse:
        """Unhealthy without the session store, so a copy that cannot reach Redis gets no traffic (Design 2.7)."""
        store: SessionStore = app.state.store
        try:
            async with asyncio.timeout(HEALTH_TIMEOUT_SECONDS):
                await store.ping()
        except (RedisError, TimeoutError, OSError):
            return JSONResponse({"status": "unavailable"}, status_code=503)
        return JSONResponse({"status": "ok"})

    @app.get("/internal/stats", include_in_schema=False)
    async def stats() -> dict[str, int]:
        """Open rooms and sockets over all workers, read by deploy.sh inside the container; nginx does not forward
        /internal."""
        registry: SessionRegistry = app.state.signaling
        sessions, connections = await registry.stats()
        return {"sessions": sessions, "connections": connections}

    return app


app = create_app()
