# AI-generated with Claude Code, 2026-10-26, reviewed by Jaewan Park and Dongje Park
"""Environment configuration for the Pix server."""

from typing import Literal

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
    # As long as the app's own limit for a run (Design 2.6.3); the image model takes 15-20 s, and more on slow days.
    pose_upstream_timeout_seconds: PositiveFloat = 60
    pose_rate_limit: PositiveInt = 20
    pose_rate_window_seconds: PositiveFloat = 3600
    # Pose requests over all clients together; it bounds what the image API can cost within the window.
    pose_global_limit: PositiveInt = 500
    pose_global_window_seconds: PositiveFloat = 86400
    # An OpenRouter model that takes an image and answers in text; it suggests the four poses for a scene when the user
    # wrote no description (Design Documentation 2.6.3).
    suggestion_model: str = "google/gemini-3.1-flash-lite"
    # The suggestions come first within the 30 s a set may take (NFR-4); after this the default poses are used.
    suggestion_timeout_seconds: PositiveFloat = 8
    # Suggestion requests, counted apart from pose requests in windows of the same lengths.
    suggestion_rate_limit: PositiveInt = 10
    suggestion_global_limit: PositiveInt = 500
    # An OpenRouter model that takes images and answers in text; it says how to hold the phone for the chosen candidate
    # (Design Documentation 2.6.3).
    tip_model: str = "google/gemini-3.1-flash-lite"
    # A little thinking caught more of the prompt's rules on 2026-10-25 for 0.4 s more; "medium" was slower and less
    # consistent.
    tip_reasoning: Literal["minimal", "low", "medium", "high"] = "low"
    # The app asks when the user takes a candidate and shows the tip on Camera; it took 2-3.5 s on 2026-10-25.
    tip_timeout_seconds: PositiveFloat = 10
    # Tip requests, counted apart from poses and suggestions in windows of the same lengths.
    tip_rate_limit: PositiveInt = 20
    tip_global_limit: PositiveInt = 1000

    # Wrong room codes one client address may send within the window; then its joins are refused until the oldest
    # leaves the window, so codes cannot be guessed. High, because phones on one Wi-Fi or carrier address share it,
    # and a room can be taken only in the seconds before the friend joins it.
    join_failure_limit: PositiveInt = 100
    join_failure_window_seconds: PositiveFloat = 600
