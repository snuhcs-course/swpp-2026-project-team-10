"""Exercise NFR-18 against a running server using 50 sessions / 100 sockets."""

import argparse
import asyncio
import json
import time

from websockets.asyncio.client import connect


async def receive(socket):
    return json.loads(await socket.recv())


async def check_session(url: str, barrier: asyncio.Barrier, codes: set[str], number: int) -> None:
    async with asyncio.timeout(30), connect(url) as photographer, connect(url) as subject:
        await photographer.send(json.dumps({"type": "room.create"}))
        room = await receive(photographer)
        assert room["type"] == "room.created", room
        assert room["code"] not in codes, "Duplicate room code"
        codes.add(room["code"])
        await subject.send(json.dumps({"type": "room.join", "code": room["code"]}))
        assert await receive(subject) == {
            "type": "session.joined",
            "sessionId": room["sessionId"],
            "role": "SUBJECT",
            "iceServers": [],
        }
        assert await receive(photographer) == {"type": "peer.joined"}

        # Keep every pair connected until all requested sessions have joined.
        await barrier.wait()
        for sender, receiver, sdp_type in ((photographer, subject, "offer"), (subject, photographer, "answer")):
            signal = {"type": "signal", "sdp": {"type": sdp_type, "sdp": f"v=0\r\ns=load-{number}\r\n"}}
            text = json.dumps(signal)
            await sender.send(text)
            assert await receiver.recv() == text
            candidate = {
                "type": "signal",
                "candidate": {"sdpMid": "0", "sdpMLineIndex": 0, "candidate": f"candidate:{number}"},
            }
            await sender.send(json.dumps(candidate))
            assert await receive(receiver) == candidate
        await photographer.send(json.dumps({"type": "leave"}))
        assert await receive(subject) == {"type": "peer.left", "reason": "PEER_LEFT"}


async def main(url: str, sessions: int) -> None:
    barrier = asyncio.Barrier(sessions)
    codes: set[str] = set()
    start = time.monotonic()
    async with asyncio.TaskGroup() as group:
        for number in range(sessions):
            group.create_task(check_session(url, barrier, codes, number))
    print(f"Passed: {sessions} simultaneous sessions ({sessions * 2} sockets) in {time.monotonic() - start:.2f}s")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="ws://127.0.0.1:8000/ws")
    parser.add_argument("--sessions", type=int, default=50)
    args = parser.parse_args()
    if args.sessions < 1:
        parser.error("--sessions must be positive")
    asyncio.run(main(args.url, args.sessions))
