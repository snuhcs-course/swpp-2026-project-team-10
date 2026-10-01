from dataclasses import dataclass

import pytest
from fastapi.testclient import TestClient

from pix_server.config import Settings
from pix_server.main import create_app


@dataclass
class Clock:
    now: float = 1_000

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


@pytest.fixture
def clock():
    return Clock()


@pytest.fixture
def settings():
    return Settings(
        _env_file=None,
        room_ttl_seconds=600,
        empty_session_ttl_seconds=60,
        cleanup_interval_seconds=0.01,
        max_message_bytes=65_536,
        outbound_queue_size=128,
        send_timeout_seconds=5,
    )


@pytest.fixture
def client(settings, clock):
    with TestClient(create_app(settings, clock=clock)) as client:
        yield client
