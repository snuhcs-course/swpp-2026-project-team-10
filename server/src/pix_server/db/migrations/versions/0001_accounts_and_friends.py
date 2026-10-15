# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Accounts and friends: users, sessions, spent refresh tokens, devices, friend requests, and friendships (Design
Documentation 2.7).

Revision ID: 0001
Revises:
Create Date: 2026-10-15
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def timestamp(name: str) -> sa.Column:
    return sa.Column(name, sa.DateTime(timezone=True), nullable=False)


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("google_sub", sa.String(), nullable=True),
        sa.Column("pix_id", sa.String(), nullable=True),
        sa.Column("password_hash", sa.String(), nullable=True),
        sa.Column("display_name", sa.String(), nullable=False),
        timestamp("created_at"),
        sa.CheckConstraint("google_sub IS NOT NULL OR password_hash IS NOT NULL", name=op.f("ck_users_sign_in")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
        sa.UniqueConstraint("google_sub", name=op.f("uq_users_google_sub")),
        sa.UniqueConstraint("pix_id", name=op.f("uq_users_pix_id")),
    )
    op.create_table(
        "sessions",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column("refresh_hash", sa.String(), nullable=False),
        timestamp("created_at"),
        timestamp("expires_at"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=op.f("fk_sessions_user_id_users")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_sessions")),
        sa.UniqueConstraint("refresh_hash", name=op.f("uq_sessions_refresh_hash")),
    )
    op.create_index(op.f("ix_sessions_user_id"), "sessions", ["user_id"])
    op.create_table(
        "spent_refresh_tokens",
        sa.Column("token_hash", sa.String(), nullable=False),
        sa.Column("session_id", sa.String(), nullable=False),
        timestamp("spent_at"),
        sa.ForeignKeyConstraint(
            ["session_id"],
            ["sessions.id"],
            name=op.f("fk_spent_refresh_tokens_session_id_sessions"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("token_hash", name=op.f("pk_spent_refresh_tokens")),
    )
    op.create_index(op.f("ix_spent_refresh_tokens_session_id"), "spent_refresh_tokens", ["session_id"])
    op.create_table(
        "devices",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column("session_id", sa.String(), nullable=False),
        sa.Column("fcm_token", sa.String(), nullable=False),
        sa.Column("platform", sa.String(), nullable=False),
        timestamp("updated_at"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=op.f("fk_devices_user_id_users")),
        sa.ForeignKeyConstraint(
            ["session_id"], ["sessions.id"], name=op.f("fk_devices_session_id_sessions"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_devices")),
        sa.UniqueConstraint("fcm_token", name=op.f("uq_devices_fcm_token")),
    )
    op.create_index(op.f("ix_devices_user_id"), "devices", ["user_id"])
    op.create_index(op.f("ix_devices_session_id"), "devices", ["session_id"])
    op.create_table(
        "friend_requests",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("from_user_id", sa.String(), nullable=False),
        sa.Column("to_user_id", sa.String(), nullable=False),
        sa.Column("user_a", sa.String(), nullable=False),
        sa.Column("user_b", sa.String(), nullable=False),
        timestamp("created_at"),
        sa.ForeignKeyConstraint(["from_user_id"], ["users.id"], name=op.f("fk_friend_requests_from_user_id_users")),
        sa.ForeignKeyConstraint(["to_user_id"], ["users.id"], name=op.f("fk_friend_requests_to_user_id_users")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_friend_requests")),
        sa.UniqueConstraint("user_a", "user_b", name=op.f("uq_friend_requests_user_a")),
    )
    op.create_index("ix_friend_requests_to_user_id", "friend_requests", ["to_user_id"])
    op.create_table(
        "friendships",
        sa.Column("user_a", sa.String(), nullable=False),
        sa.Column("user_b", sa.String(), nullable=False),
        timestamp("created_at"),
        sa.ForeignKeyConstraint(["user_a"], ["users.id"], name=op.f("fk_friendships_user_a_users")),
        sa.ForeignKeyConstraint(["user_b"], ["users.id"], name=op.f("fk_friendships_user_b_users")),
        sa.PrimaryKeyConstraint("user_a", "user_b", name=op.f("pk_friendships")),
    )
    op.create_index("ix_friendships_user_b", "friendships", ["user_b"])


def downgrade() -> None:
    op.drop_table("friendships")
    op.drop_table("friend_requests")
    op.drop_table("devices")
    op.drop_table("spent_refresh_tokens")
    op.drop_table("sessions")
    op.drop_table("users")
