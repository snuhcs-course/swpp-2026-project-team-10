"""Environment configuration for the single-process signaling hub."""

from pydantic import PositiveFloat, PositiveInt
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="PIX_", env_file=".env", env_file_encoding="utf-8", extra="ignore")

    room_ttl_seconds: PositiveFloat = 600
    empty_session_ttl_seconds: PositiveFloat = 60
    cleanup_interval_seconds: PositiveFloat = 1
    max_message_bytes: PositiveInt = 65_536
    outbound_queue_size: PositiveInt = 128
    send_timeout_seconds: PositiveFloat = 5
