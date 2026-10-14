# AI-generated with Claude Code, 2026-10-14, reviewed by Jaewan Park
"""ASGI entry point: uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000."""

import asyncio
import time
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

import httpx2
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from redis.asyncio import Redis
from redis.exceptions import RedisError
from starlette.middleware.body_limit import RequestBodyLimitMiddleware

from pix_server.config import Settings
from pix_server.errors import ApiError, handle_api_error, handle_store_error, handle_validation_error
from pix_server.poses.generator import PoseGenerator
from pix_server.poses.limiter import RateLimiter
from pix_server.poses.router import router as poses_router
from pix_server.signaling.registry import SessionRegistry
from pix_server.signaling.router import router as signaling_router
from pix_server.store import SessionStore, open_redis

# Starlette keeps an upload of up to 1 MiB in memory and spools a larger one to a temporary file. Refusing bigger
# requests before they are parsed means a scene photo never reaches the disk (FR-4.9) and an upload cannot fill it.
MAX_REQUEST_BYTES = 1024 * 1024
# nginx and the container's health check must not wait long for a store that does not answer.
HEALTH_TIMEOUT_SECONDS = 2


def create_app(
    settings: Settings | None = None,
    *,
    clock: Callable[[], float] = time.time,
    image_api: httpx2.AsyncBaseTransport | None = None,
    redis: Callable[[], Redis] | None = None,
) -> FastAPI:
    """`image_api` replaces the network connection to the image API and `redis` opens the session store; tests
    pass fakes. `clock` is wall-clock time, because every worker compares it with times other workers stored."""
    config = settings if settings is not None else Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        connection = redis() if redis is not None else open_redis(config)
        store = SessionStore(connection, config, clock)
        registry = SessionRegistry(config, store)
        app.state.store = store
        app.state.signaling = registry
        app.state.pose_limiter = RateLimiter(store, config.pose_rate_limit, config.pose_rate_window_seconds)
        try:
            # The generator enforces its own total deadline, so the client sets none.
            async with httpx2.AsyncClient(transport=image_api, timeout=None) as http:
                app.state.pose_generator = PoseGenerator(config, http)
                await registry.start()
                try:
                    yield
                finally:
                    await registry.stop()
        finally:
            await connection.aclose()

    app = FastAPI(title="Pix server", version="0.1.0", lifespan=lifespan)
    app.include_router(signaling_router)
    app.include_router(poses_router)
    app.add_middleware(RequestBodyLimitMiddleware, max_body_size=MAX_REQUEST_BYTES)
    app.add_exception_handler(ApiError, handle_api_error)
    app.add_exception_handler(RequestValidationError, handle_validation_error)
    app.add_exception_handler(RedisError, handle_store_error)

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
