"""ASGI entry point: uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000."""

import asyncio
import time
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager, suppress

from fastapi import FastAPI

from pix_server.config import Settings
from pix_server.signaling.registry import SessionRegistry
from pix_server.signaling.router import router


def create_app(settings: Settings | None = None, *, clock: Callable[[], float] = time.monotonic) -> FastAPI:
    config = settings if settings is not None else Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        registry = SessionRegistry(config, clock)
        app.state.signaling = registry

        async def cleanup() -> None:
            while True:
                await asyncio.sleep(config.cleanup_interval_seconds)
                registry.expire()

        cleanup_task = asyncio.create_task(cleanup())
        try:
            yield
        finally:
            cleanup_task.cancel()
            with suppress(asyncio.CancelledError):
                await cleanup_task
            registry.shutdown()

    app = FastAPI(title="Pix server", version="0.1.0", lifespan=lifespan)
    app.include_router(router)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    return app


app = create_app()
