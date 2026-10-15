# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""Environment configuration for the Pix server."""

from pydantic import Field, PositiveFloat, PositiveInt, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="PIX_", env_file=".env", env_file_encoding="utf-8", extra="ignore")

    room_ttl_seconds: PositiveFloat = 600
    empty_session_ttl_seconds: PositiveFloat = 60
    cleanup_interval_seconds: PositiveFloat = 1
    max_message_bytes: PositiveInt = 65_536
    outbound_queue_size: PositiveInt = 128
    send_timeout_seconds: PositiveFloat = 5

    # The session store (Design Documentation 2.7). Unset keeps the state inside the process, for one worker on a laptop.
    redis_url: str | None = None
    # A worker that has not renewed its heartbeat for this long has lost its sockets.
    worker_ttl_seconds: PositiveFloat = 15

    # The account database (Design Documentation 2.7). The default is a SQLite file in the working directory, for a
    # laptop; the Compose file sets PostgreSQL.
    database_url: str = "sqlite+aiosqlite:///pix.db"
    # The web client ID that the app passes to Google sign-in as its server client ID; it is the audience of the ID
    # tokens. Google sign-in answers UNAVAILABLE while it is unset.
    google_client_id: str | None = None
    # Signs the access tokens (JWT, HS256); every worker of both copies needs the same one. Unset, each process makes
    # its own, which suits one worker on a laptop.
    jwt_secret: SecretStr | None = None
    access_token_minutes: PositiveFloat = 15
    # How long a sign-in lasts without a refresh; every refresh starts it again.
    session_ttl_days: PositiveFloat = 30

    # The vendor's own variable name, without the PIX_ prefix. Pose requests fail with UPSTREAM_ERROR while it is unset.
    openrouter_api_key: SecretStr | None = Field(default=None, validation_alias="OPENROUTER_API_KEY")
    # An OpenRouter image model id; append @low, @medium, or @high for a model that has quality tiers.
    pose_model: str = "openai/gpt-image-2.5-flare@low"
    pose_upstream_timeout_seconds: PositiveFloat = 30
    pose_rate_limit: PositiveInt = 20
    pose_rate_window_seconds: PositiveFloat = 3600
