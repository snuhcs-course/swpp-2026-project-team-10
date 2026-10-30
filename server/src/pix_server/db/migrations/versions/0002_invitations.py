# AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
"""Invitations to shoot together (Design Documentation 2.7, Iter3-P15). A new table, so the release before it keeps
working while both copies run during a deploy.

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-29
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def timestamp(name: str) -> sa.Column:
    return sa.Column(name, sa.DateTime(timezone=True), nullable=False)


def upgrade() -> None:
    op.create_table(
        "invitations",
        sa.Column("id", sa.String(), nullable=False),
        sa.Column("from_user_id", sa.String(), nullable=False),
        sa.Column("to_user_id", sa.String(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("session_id", sa.String(), nullable=False),
        timestamp("expires_at"),
        timestamp("created_at"),
        sa.ForeignKeyConstraint(["from_user_id"], ["users.id"], name=op.f("fk_invitations_from_user_id_users")),
        sa.ForeignKeyConstraint(["to_user_id"], ["users.id"], name=op.f("fk_invitations_to_user_id_users")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_invitations")),
    )
    op.create_index("ix_invitations_created_at", "invitations", ["created_at"])
    op.create_index(op.f("ix_invitations_from_user_id"), "invitations", ["from_user_id"])
    op.create_index(op.f("ix_invitations_to_user_id"), "invitations", ["to_user_id"])


def downgrade() -> None:
    op.drop_table("invitations")
