# AI-generated with Claude Code, 2026-10-02, reviewed by Jaewan Park
"""ASGI entry point: uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000."""

import asyncio
import time
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager, suppress

import httpx2
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from starlette.middleware.body_limit import RequestBodyLimitMiddleware

from pix_server.config import Settings
from pix_server.errors import ApiError, handle_api_error, handle_validation_error
from pix_server.poses.generator import PoseGenerator
from pix_server.poses.limiter import RateLimiter
from pix_server.poses.router import router as poses_router
from pix_server.signaling.registry import SessionRegistry
from pix_server.signaling.router import router as signaling_router

# Starlette keeps an upload of up to 1 MiB in memory and spools a larger one to a temporary file. Refusing bigger
# requests before they are parsed means a scene photo never reaches the disk (FR-4.9) and an upload cannot fill it.
MAX_REQUEST_BYTES = 1024 * 1024


def create_app(
    settings: Settings | None = None,
    *,
    clock: Callable[[], float] = time.monotonic,
    image_api: httpx2.AsyncBaseTransport | None = None,
) -> FastAPI:
    """`image_api` replaces the network connection to the image API; tests pass a fake."""
    config = settings if settings is not None else Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        registry = SessionRegistry(config, clock)
        app.state.signaling = registry
        app.state.pose_limiter = RateLimiter(config.pose_rate_limit, config.pose_rate_window_seconds, clock)

        async def cleanup() -> None:
            while True:
                await asyncio.sleep(config.cleanup_interval_seconds)
                registry.expire()

        # The generator enforces its own total deadline, so the client sets none.
        async with httpx2.AsyncClient(transport=image_api, timeout=None) as http:
            app.state.pose_generator = PoseGenerator(config, http)
            cleanup_task = asyncio.create_task(cleanup())
            try:
                yield
            finally:
                cleanup_task.cancel()
                with suppress(asyncio.CancelledError):
                    await cleanup_task
                registry.shutdown()

    app = FastAPI(title="Pix server", version="0.1.0", lifespan=lifespan)
    app.include_router(signaling_router)
    app.include_router(poses_router)
    app.add_middleware(RequestBodyLimitMiddleware, max_body_size=MAX_REQUEST_BYTES)
    app.add_exception_handler(ApiError, handle_api_error)
    app.add_exception_handler(RequestValidationError, handle_validation_error)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    return app


app = create_app()
