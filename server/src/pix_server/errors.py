# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""The single error shape of the REST API (Design Documentation 2.5.3)."""

import traceback

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from redis.exceptions import RedisError


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


def error_response(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse({"error": {"code": code, "message": message}}, status_code=status)


async def handle_api_error(request: Request, exc: ApiError) -> JSONResponse:
    return error_response(exc.status, exc.code, exc.message)


async def handle_validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
    # FastAPI's default is 422, which this API reserves for REJECTED. The submitted value is left out of the message.
    error = exc.errors()[0]
    return error_response(400, "INVALID_REQUEST", f"{error['loc'][-1]}: {error['msg']}")


UNAVAILABLE_MESSAGE = "The server is temporarily unavailable; try again"


async def handle_store_error(request: Request, exc: RedisError) -> JSONResponse:
    # The session store also holds the rate limit, so a request that cannot be counted is refused.
    return error_response(503, "UNAVAILABLE", UNAVAILABLE_MESSAGE)


DATABASE_MODULES = frozenset({"sqlalchemy", "asyncpg", "aiosqlite", "sqlite3"})


def raised_by_database(exc: BaseException) -> bool:
    """Whether the exception was raised inside the database driver. A connection that cannot be opened surfaces as a
    plain `OSError` (refused, or a name that does not resolve), which SQLAlchemy does not wrap."""
    for frame, _ in traceback.walk_tb(exc.__traceback__):
        if frame.f_globals.get("__name__", "").split(".")[0] in DATABASE_MODULES:
            return True
    return False


async def handle_database_error(request: Request, exc: Exception) -> JSONResponse:
    """The account database cannot be reached, dropped the connection, or has no connection free: SQLAlchemy's
    `OperationalError`, `InterfaceError`, or pool `TimeoutError`. Other database errors, such as a broken constraint,
    are bugs and stay a 500."""
    return error_response(503, "UNAVAILABLE", UNAVAILABLE_MESSAGE)


async def handle_os_error(request: Request, exc: OSError) -> JSONResponse:
    """An `OSError` from the database driver means the database cannot be reached; any other is a bug, so it is
    raised again and answers 500."""
    if raised_by_database(exc):
        return error_response(503, "UNAVAILABLE", UNAVAILABLE_MESSAGE)
    raise exc
