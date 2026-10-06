"""A subject phone made of Python, for testing the photographer's side with one phone or emulator.

It joins a room code through the signaling server, answers the WebRTC offer, exchanges `hello` on the data
channels, counts the video frames it receives, optionally sends `camera.zoom.set`, and reports the echoed
`camera.state` with its delay. It also receives the photographer's guide: the image chunks are reassembled and
checked against the CRC, and every `guide.state` is printed. It is a development tool, not part of the server.

    pip install websockets aiortc
    python tools/fake_subject.py <code> [--seconds 20] [--zoom 2.0] [--have-guide <id>] [--save-guide guide.webp]
                                 [--server ws://127.0.0.1:8000/ws]
"""

import argparse
import asyncio
import base64
import json
import time
import zlib

import websockets
from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription
from aiortc.sdp import candidate_from_sdp

T0 = time.monotonic()


def log(*parts):
    print(f"[{time.monotonic() - T0:6.2f}s]", *parts, flush=True)


class Codec:
    def __init__(self):
        self.seq = {}

    def encode(self, kind, body):
        self.seq[kind] = self.seq.get(kind, 0) + 1
        return json.dumps({"v": 1, "t": kind, "seq": self.seq[kind], "ts": int(time.time() * 1000), "b": body})


async def main(args):
    codec = Codec()
    pc = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    channels = {}
    state = {"hello": False, "frames": 0, "zoom_sent_at": None, "done": asyncio.Event(), "guide": None}

    async def count_frames(track):
        while True:
            try:
                frame = await track.recv()
            except Exception as exc:
                log("video track ended:", exc)
                return
            state["frames"] += 1
            if state["frames"] == 1:
                log(f"first video frame {frame.width}x{frame.height}")
            elif state["frames"] % 30 == 0:
                log(f"video frames: {state['frames']}")

    @pc.on("track")
    def on_track(track):
        log("track:", track.kind)
        if track.kind == "video":
            asyncio.ensure_future(count_frames(track))

    @pc.on("connectionstatechange")
    async def on_state():
        log("peer connection:", pc.connectionState)

    def on_message(label, text):
        env = json.loads(text)
        kind, body = env["t"], env["b"]
        if kind == "hello":
            log(f"hello from {body['name']} ({body['role']}, protocol {body['protocol']})")
            hello = {
                "protocol": 1,
                "appVersion": "fake",
                "role": "SUBJECT",
                "name": "fake-subject",
                "haveGuideId": args.have_guide,
            }
            channels["reliable"].send(codec.encode("hello", hello))
            state["hello"] = True
        elif kind == "ping":
            channels["realtime"].send(codec.encode("pong", {"ts": body["ts"]}))
        elif kind == "camera.capabilities":
            log("capabilities:", body["capabilities"])
        elif kind == "camera.state":
            sent_at = state["zoom_sent_at"]
            since = f" {1000 * (time.monotonic() - sent_at):.0f} ms after zoom.set" if sent_at else ""
            log(f"camera.state zoom={body['zoom']} by={body['by']} final={body['final']}{since}")
        elif kind == "guide.image.begin":
            log(
                f"guide.image.begin {body['guideId']} {body['format']} {body['width']}x{body['height']} "
                f"{body['bytes']} B in {body['chunks']} chunks"
            )
            state["guide"] = {"begin": body, "parts": [], "at": time.monotonic()}
        elif kind == "guide.image.chunk":
            transfer = state["guide"]
            if transfer and body["guideId"] == transfer["begin"]["guideId"] and body["index"] == len(transfer["parts"]):
                transfer["parts"].append(base64.b64decode(body["data"]))
            else:
                log(f"guide.image.chunk {body['index']} out of order, dropped")
        elif kind == "guide.image.end":
            transfer = state["guide"]
            state["guide"] = None
            if not transfer or body["guideId"] != transfer["begin"]["guideId"]:
                log("guide.image.end without a matching begin")
            else:
                data = b"".join(transfer["parts"])
                ok = len(data) == transfer["begin"]["bytes"] and zlib.crc32(data) == body["crc32"]
                took = 1000 * (time.monotonic() - transfer["at"])
                log(
                    f"guide image {transfer['begin']['guideId']}: {len(data)} B, crc {'ok' if ok else 'MISMATCH'}, "
                    f"{took:.0f} ms from begin to end"
                )
                if ok and args.save_guide:
                    with open(args.save_guide, "wb") as f:
                        f.write(data)
                    log("saved to", args.save_guide)
        elif kind == "guide.state":
            st = body["state"]
            log(
                f"guide.state {st.get('guideId')} cx={st.get('cx'):.3f} cy={st.get('cy'):.3f} "
                f"height={st.get('height'):.3f} opacity={st.get('opacity'):.2f} style={st.get('style')} "
                f"visible={st.get('visible')} final={body['final']}"
            )
        elif kind == "guide.clear":
            log("guide.clear")
        elif kind == "session.leave":
            log("photographer left:", body["reason"])
            state["done"].set()
        elif kind != "pong":
            log("message", kind, json.dumps(body)[:120])

    @pc.on("datachannel")
    def on_datachannel(channel):
        channels[channel.label] = channel
        log("data channel open:", channel.label)
        channel.on("message", lambda text: on_message(channel.label, text))

    async with websockets.connect(args.server) as ws:
        await ws.send(json.dumps({"type": "room.join", "code": args.code}))

        async def pump():
            async for raw in ws:
                msg = json.loads(raw)
                kind = msg.get("type")
                if kind == "session.joined":
                    log("joined session", msg["sessionId"])
                elif kind == "signal" and msg.get("sdp"):
                    await pc.setRemoteDescription(RTCSessionDescription(msg["sdp"]["sdp"], msg["sdp"]["type"]))
                    await pc.setLocalDescription(await pc.createAnswer())
                    answer = {"type": "answer", "sdp": pc.localDescription.sdp}
                    await ws.send(json.dumps({"type": "signal", "sdp": answer}))
                    log("answer sent")
                elif kind == "signal" and msg.get("candidate"):
                    c = msg["candidate"]
                    if c["candidate"]:
                        ice = candidate_from_sdp(c["candidate"].split(":", 1)[1])
                        ice.sdpMid, ice.sdpMLineIndex = c["sdpMid"], c["sdpMLineIndex"]
                        await pc.addIceCandidate(ice)
                elif kind == "peer.left":
                    log("peer.left", msg.get("reason"))
                    state["done"].set()
                elif kind == "error":
                    log("server error", msg)
                    state["done"].set()

        pump_task = asyncio.ensure_future(pump())

        async def scenario():
            for _ in range(200):
                if state["hello"] and "realtime" in channels:
                    break
                await asyncio.sleep(0.1)
            else:
                log("never connected")
                return
            await asyncio.sleep(2)
            if args.zoom is not None:
                state["zoom_sent_at"] = time.monotonic()
                channels["reliable"].send(codec.encode("camera.zoom.set", {"ratio": args.zoom, "final": True}))
                log(f"sent camera.zoom.set ratio={args.zoom}")
            try:
                await asyncio.wait_for(state["done"].wait(), args.seconds)
            except asyncio.TimeoutError:
                pass
            if "reliable" in channels:
                channels["reliable"].send(codec.encode("session.leave", {"reason": "LEFT"}))
                await asyncio.sleep(0.3)
            await ws.send(json.dumps({"type": "leave"}))

        await scenario()
        pump_task.cancel()
    await pc.close()
    log(f"done: {state['frames']} video frames")


parser = argparse.ArgumentParser()
parser.add_argument("code")
parser.add_argument("--seconds", type=float, default=20)
parser.add_argument("--zoom", type=float, default=None)
parser.add_argument("--have-guide", default=None, help="guide id to report in hello, to test that the image is skipped")
parser.add_argument("--save-guide", default=None, help="write the received guide image (WebP) to this file")
parser.add_argument("--server", default="ws://127.0.0.1:8000/ws")
asyncio.run(main(parser.parse_args()))
