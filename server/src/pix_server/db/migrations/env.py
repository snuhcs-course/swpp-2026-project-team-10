# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Alembic environment. The server passes its own connection (`pix_server.db.migrate`); the `alembic` command line,
run from server/ with alembic.ini, connects to PIX_DATABASE_URL, for example to write a new revision with
`uv run alembic revision --autogenerate -m "…"`."""

import asyncio

from alembic import context
from sqlalchemy import Connection

from pix_server.config import Settings
from pix_server.db.engine import open_database
from pix_server.db.models import Base


def run(connection: Connection) -> None:
    # SQLite cannot alter a table in place, so its migrations copy the table instead.
    context.configure(
        connection=connection,
        target_metadata=Base.metadata,
        render_as_batch=connection.dialect.name == "sqlite",
    )
    with context.begin_transaction():
        context.run_migrations()


async def run_from_settings() -> None:
    engine = open_database(Settings().database_url)
    try:
        async with engine.begin() as connection:
            await connection.run_sync(run)
    finally:
        await engine.dispose()


if context.is_offline_mode():
    raise SystemExit("Offline (--sql) migrations are not supported; run them against a database")
given = context.config.attributes.get("connection")
if given is not None:
    run(given)
else:
    asyncio.run(run_from_settings())
