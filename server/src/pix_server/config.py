# AI-generated with Claude Code, 2026-10-02, reviewed by Jaewan Park
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

    # The vendor's own variable name, without the PIX_ prefix. Pose requests fail with UPSTREAM_ERROR while it is unset.
    openrouter_api_key: SecretStr | None = Field(default=None, validation_alias="OPENROUTER_API_KEY")
    # An OpenRouter image model id; append @low, @medium, or @high for a model that has quality tiers.
    pose_model: str = "openai/gpt-image-2.5-flare@low"
    pose_upstream_timeout_seconds: PositiveFloat = 30
    pose_rate_limit: PositiveInt = 20
    pose_rate_window_seconds: PositiveFloat = 3600
