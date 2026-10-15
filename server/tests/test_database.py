# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""The account database's migrations and invariants (Design 2.7), on a new SQLite file or the PostgreSQL that
PIX_TEST_DATABASE_URL names.

Partitions:
- migrate: an empty database; one already at the newest revision (data kept); two workers starting at once
- schema: matches the models; a user with no way to sign in; a second user with the same Pix ID or Google account;
  several users without a Pix ID
- times: read back with their zone
"""

import asyncio
from datetime import UTC, datetime

import pytest
from alembic.autogenerate import compare_metadata
from alembic.migration import MigrationContext
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from pix_server.db import migrate, open_database
from pix_server.db.models import Base, User, new_id

NOW = datetime(2026, 10, 15, 4, 30, tzinfo=UTC)


@pytest.fixture
async def engine(database_url):
    engine = open_database(database_url)
    yield engine
    await engine.dispose()


def user(**fields) -> User:
    defaults = {"id": new_id("u"), "display_name": "Dongje", "created_at": NOW, "password_hash": "hash"}
    return User(**(defaults | fields))


async def save(engine, *users: User) -> None:
    table = User.__table__
    async with engine.begin() as connection:
        for item in users:
            await connection.execute(
                table.insert().values(**{column.key: getattr(item, column.key) for column in table.c})
            )


async def test_migrations_build_the_schema_of_the_models(engine):
    await migrate(engine)
    async with engine.connect() as connection:
        differences = await connection.run_sync(
            lambda sync: compare_metadata(MigrationContext.configure(sync), Base.metadata)
        )
    assert differences == []


async def test_migrating_a_current_database_again_keeps_its_data(engine):
    await migrate(engine)
    await save(engine, user(id="u_1", pix_id="dongje.p"))
    await migrate(engine)
    async with engine.connect() as connection:
        assert (await connection.execute(select(User.pix_id))).scalars().all() == ["dongje.p"]


async def test_workers_that_start_together_migrate_once(database_url):
    if not database_url.startswith("postgresql"):
        pytest.skip("Only PostgreSQL has the lock; a SQLite file serves one process on a laptop")
    engines = [open_database(database_url) for _ in range(2)]
    try:
        await asyncio.gather(*(migrate(engine) for engine in engines))
        await save(engines[0], user(pix_id="dongje.p"))
    finally:
        for engine in engines:
            await engine.dispose()


async def test_every_user_needs_a_way_to_sign_in(engine):
    await migrate(engine)
    with pytest.raises(IntegrityError):
        await save(engine, user(password_hash=None, google_sub=None))


@pytest.mark.parametrize("field, value", [("pix_id", "dongje.p"), ("google_sub", "sub-1")])
async def test_pix_ids_and_google_accounts_are_unique(engine, field, value):
    await migrate(engine)
    await save(engine, user(**{field: value}))
    with pytest.raises(IntegrityError):
        await save(engine, user(**{field: value}))


async def test_many_users_can_be_without_a_pix_id_or_google_account(engine):
    await migrate(engine)
    await save(engine, user(), user(), user(google_sub="sub-1", password_hash=None))


async def test_times_are_read_back_in_utc(engine):
    await migrate(engine)
    await save(engine, user(id="u_1"))
    async with engine.connect() as connection:
        created = (await connection.execute(select(User.created_at))).scalar_one()
    assert created == NOW
    assert created.tzinfo is not None
