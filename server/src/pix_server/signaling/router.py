"""WebSocket transport with one ordered writer and a bounded queue per phone."""

import asyncio
from contextlib import suppress

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from starlette.websockets import WebSocketState

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
            registry.handle(peer, parse_message(text), text)
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
    await websocket.accept()
    peer = registry.connect()
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
    finally:
        registry.disconnect(peer)
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        if (
            websocket.application_state == WebSocketState.CONNECTED
            and websocket.client_state == WebSocketState.CONNECTED
        ):
            with suppress(WebSocketDisconnect, OSError):
                await websocket.close(code=peer.close_code)
