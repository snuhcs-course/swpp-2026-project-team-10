# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Connection to the account database, and the migrations that every worker runs when it starts."""

from alembic import command
from alembic.config import Config
from sqlalchemy import Connection, event, text
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine

MIGRATIONS = "pix_server.db:migrations"
# Any fixed number; PostgreSQL's advisory locks are named by integers.
MIGRATION_LOCK = 7_301_865_312


def open_database(url: str) -> AsyncEngine:
    """PostgreSQL through asyncpg on the servers (`postgresql+asyncpg://…`), or a SQLite file (`sqlite+aiosqlite://…`)."""
    engine = create_async_engine(url, pool_pre_ping=True)
    if engine.dialect.name == "sqlite":

        @event.listens_for(engine.sync_engine, "connect")
        def enforce_foreign_keys(connection, _record) -> None:
            # SQLite ignores foreign keys unless each connection asks for them.
            cursor = connection.cursor()
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.close()

    return engine


async def migrate(engine: AsyncEngine) -> None:
    """Brings the schema to the newest revision. Both workers of both copies start at once during a deploy, so on
    PostgreSQL one of them migrates while the others wait for the lock and then find nothing to do. The old copy
    keeps serving meanwhile, so a revision must not break the code of the one before it."""
    async with engine.begin() as connection:
        if connection.dialect.name == "postgresql":
            await connection.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": MIGRATION_LOCK})
        await connection.run_sync(_upgrade)


def _upgrade(connection: Connection) -> None:
    config = Config()
    config.set_main_option("script_location", MIGRATIONS)
    # env.py runs the migrations on this connection, inside the transaction that holds the lock.
    config.attributes["connection"] = connection
    command.upgrade(config, "head")
