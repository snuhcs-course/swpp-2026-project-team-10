# AI-generated with Claude Code, 2026-10-02, reviewed by Jaewan Park
"""Client messages from Design Documentation 2.5.2 and Android's SignalMessage.kt."""

import json
from typing import Literal, Self

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator


class ProtocolError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class Message(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class RoomCreate(Message):
    type: Literal["room.create"]


class RoomJoin(Message):
    type: Literal["room.join"]
    code: str = Field(min_length=6, max_length=6, pattern=r"^[0-9]{6}$")


class Leave(Message):
    type: Literal["leave"]


class SessionDescription(Message):
    type: Literal["offer", "answer"]
    sdp: str = Field(min_length=1)


class IceCandidate(Message):
    sdpMid: str | None = None
    sdpMLineIndex: int = Field(ge=0)
    # An empty candidate is WebRTC's end-of-candidates marker.
    candidate: str


class Signal(Message):
    type: Literal["signal"]
    sdp: SessionDescription | None = None
    candidate: IceCandidate | None = None

    @model_validator(mode="after")
    def one_payload(self) -> Self:
        if (self.sdp is None) == (self.candidate is None):
            raise ValueError("A signal must contain exactly one SDP or ICE candidate")
        return self


ClientMessage = RoomCreate | RoomJoin | Leave | Signal
MESSAGE_TYPES: dict[str, type[ClientMessage]] = {
    "room.create": RoomCreate,
    "room.join": RoomJoin,
    "leave": Leave,
    "signal": Signal,
}


def parse_message(text: str) -> ClientMessage:
    try:
        data = json.loads(text)
    except (ValueError, RecursionError) as exc:
        raise ProtocolError("INVALID_MESSAGE", "Expected a JSON object with a string type") from exc
    if not isinstance(data, dict) or not isinstance(data.get("type"), str):
        raise ProtocolError("INVALID_MESSAGE", "Expected a JSON object with a string type")
    message_type = data["type"]
    model = MESSAGE_TYPES.get(message_type)
    if model is None:
        raise ProtocolError("UNKNOWN_TYPE", f"Unknown message type: {message_type[:100]}")
    try:
        return model.model_validate(data)
    except ValidationError as exc:
        # Do not reflect payloads (including SDP) into errors or logs.
        raise ProtocolError("INVALID_MESSAGE", f"Invalid {message_type} message") from exc
