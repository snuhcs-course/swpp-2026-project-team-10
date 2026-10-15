# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""WebSocket transport with one ordered writer and a bounded queue per phone."""

import asyncio
from contextlib import suppress

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from redis.exceptions import RedisError
from starlette.websockets import WebSocketState

from pix_server.accounts.auth import socket_user
from pix_server.signaling.messages import ProtocolError, parse_message
from pix_server.signaling.registry import Peer, SessionRegistry

router = APIRouter()


async def receive_messages(websocket: WebSocket, registry: SessionRegistry, peer: Peer) -> None:
    while not peer.closing.is_set():
        frame = await websocket.receive()
        if frame["type"] == "websocket.disconnect":
            return
        text = frame.get("text")
        if text is None:
            registry.error(peer, ProtocolError("INVALID_MESSAGE", "Use JSON text frames, not binary frames"))
            continue
        if len(text.encode("utf-8")) > registry.settings.max_message_bytes:
            peer.close_code = 1009
            return
        try:
            await registry.handle(peer, parse_message(text), text)
        except ProtocolError as exc:
            registry.error(peer, exc)


async def send_messages(websocket: WebSocket, registry: SessionRegistry, peer: Peer) -> None:
    while True:
        text = await peer.outbox.get()
        try:
            async with asyncio.timeout(registry.settings.send_timeout_seconds):
                await websocket.send_text(text)
        except TimeoutError:
            peer.close_code = 1013
            return


@router.websocket("/ws")
async def signaling(websocket: WebSocket) -> None:
    registry: SessionRegistry = websocket.app.state.signaling
    # A signed-in phone sends its Pix session token in the upgrade request (Design 2.5.2).
    user = socket_user(websocket)
    await websocket.accept()
    try:
        peer = await registry.connect(user, websocket.client.host if websocket.client else "")
    except RedisError:
        # Without the session store this server cannot hold a room; 1013 asks the phone to try again later.
        await websocket.close(code=1013)
        return
    tasks = [
        asyncio.create_task(receive_messages(websocket, registry, peer)),
        asyncio.create_task(send_messages(websocket, registry, peer)),
        asyncio.create_task(peer.closing.wait()),
    ]
    try:
        done, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in done:
            task.result()
    except (WebSocketDisconnect, OSError):
        pass
    except RedisError:
        peer.close_code = 1013
    finally:
        # Cancelled first, so they end even if this handler is cancelled while it frees its place in the store.
        for task in tasks:
            task.cancel()
        await registry.disconnect(peer)
        # asyncio.wait and not gather: a handler cancelled while it waits here must raise its canceller's own
        # CancelledError, and gather can replace it with one that carries no message.
        await asyncio.wait(tasks)
        for task in tasks:
            if not task.cancelled():
                task.exception()
        if (
            websocket.application_state == WebSocketState.CONNECTED
            and websocket.client_state == WebSocketState.CONNECTED
        ):
            with suppress(WebSocketDisconnect, OSError):
                await websocket.close(code=peer.close_code)
