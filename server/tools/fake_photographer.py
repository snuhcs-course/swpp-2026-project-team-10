# AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han and Sungmin Jo
"""A photographer phone made of Python, for testing the subject's side with one phone or emulator.
It creates a room and prints the code. When the subject joins, it offers a test-pattern video and the two data
channels, exchanges `hello`, sends `camera.capabilities`, echoes every zoom, brightness, and flash request as
`camera.state`, applies `guide.move` to its guide, sends the guide image from `--guide` as the app would send it
(`mask-png`, protocol 3: the image's alpha, or its gray values when it has none, at most 720 px tall and 1280 px
wide) with its `guide.state`, then moves the guide back and forth for `--seconds` the
way a drag would (steps every 50 ms, a final value every 2 s), and ends the session. It is a development tool, not
part of the server.
    pip install websockets aiortc pillow
    python tools/fake_photographer.py --guide person.png [--seconds 20] [--clear] [--server ws://127.0.0.1:8000/ws]
"""

import argparse
import asyncio
import base64
import io
import json
import math
import time
import uuid
import zlib

import websockets
from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription, VideoStreamTrack
from aiortc.sdp import candidate_from_sdp
from av import VideoFrame
from PIL import Image, ImageDraw

T0 = time.monotonic()
CHUNK_BYTES = 9 * 1024


def log(*parts):
    print(f"[{time.monotonic() - T0:6.2f}s]", *parts, flush=True)


class Codec:
    def __init__(self):
        self.seq = {}

    def encode(self, kind, body):
        self.seq[kind] = self.seq.get(kind, 0) + 1
        return json.dumps({"v": 2, "t": kind, "seq": self.seq[kind], "ts": int(time.time() * 1000), "b": body})


class PatternVideo(VideoStreamTrack):
    """A 3:4 frame with a bar that moves down, so the subject can see that video is live."""

    def __init__(self):
        super().__init__()
        self.n = 0

    async def recv(self):
        pts, time_base = await self.next_timestamp()
        image = Image.new("RGB", (480, 640), (40, 44, 52))
        draw = ImageDraw.Draw(image)
        y = (self.n * 6) % 640
        draw.rectangle((0, y, 480, y + 30), fill=(220, 180, 60))
        draw.text((16, 16), f"fake photographer  frame {self.n}", fill=(255, 255, 255))
        self.n += 1
        frame = VideoFrame.from_image(image)
        frame.pts, frame.time_base = pts, time_base
        return frame


def guide_state(guide_id, cx=0.5, cy=0.5, height=0.7, opacity=0.5, style="OUTLINE"):
    return {
        "guideId": guide_id,
        "cx": cx,
        "cy": cy,
        "height": height,
        "opacity": opacity,
        "style": style,
        "visible": True,
    }


async def main(args):
    codec = Codec()
    pc = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    channels = {}
    state = {
        "hello": None,
        "open": 0,
        "done": asyncio.Event(),
        "zoom": 1.0,
        "exposure": 0,
        "flash": "OFF",
        "moved": None,
    }

    def camera_state(by, final):
        return {
            "zoom": state["zoom"],
            "exposure": state["exposure"],
            "flash": state["flash"],
            "aspect": "R4_3",
            "by": by,
            "final": final,
        }

    guide_id = str(uuid.uuid4())
    guide_bytes, guide_size = mask_png(args.guide)

    def send(channel, kind, body):
        channels[channel].send(codec.encode(kind, body))

    def on_message(text):
        env = json.loads(text)
        kind, body = env["t"], env["b"]
        if kind == "hello":
            state["hello"] = body
            log(f"hello from {body['name']} ({body['role']}), haveGuideId={body.get('haveGuideId')}")
        elif kind == "ping":
            send("realtime", "pong", {"ts": body["ts"]})
        elif kind == "camera.zoom.set":
            state["zoom"] = max(1.0, min(4.0, body["ratio"]))
            log(f"camera.zoom.set ratio={body['ratio']} final={body.get('final')}")
            send(
                "reliable" if body.get("final") else "realtime",
                "camera.state",
                camera_state("SUBJECT", bool(body.get("final"))),
            )
        elif kind == "camera.exposure.set":
            state["exposure"] = max(-4, min(4, int(body["index"])))
            log(f"camera.exposure.set index={body['index']} final={body.get('final')}")
            send(
                "reliable" if body.get("final") else "realtime",
                "camera.state",
                camera_state("SUBJECT", bool(body.get("final"))),
            )
        elif kind == "camera.flash.set":
            state["flash"] = body["mode"]
            log(f"camera.flash.set mode={body['mode']}")
            send("reliable", "camera.state", camera_state("SUBJECT", True))
        elif kind == "guide.move" and body.get("guideId") != guide_id:
            log(f"guide.move for guide {body.get('guideId')} dropped: this phone shows {guide_id}")
        elif kind == "guide.move":
            # Applied as the photographer would, and echoed as the subject's; the guide animation stops for it.
            state["moved"] = {"cx": body["cx"], "cy": body["cy"], "height": body["height"]}
            final = bool(body.get("final"))
            log(f"guide.move cx={body['cx']:.3f} cy={body['cy']:.3f} height={body['height']:.3f} final={final}")
            moved = guide_state(guide_id, cx=body["cx"], cy=body["cy"], height=body["height"])
            send("reliable" if final else "realtime", "guide.state", {"state": moved, "final": final, "by": "SUBJECT"})
        elif kind == "session.leave":
            log("subject left:", body["reason"])
            state["done"].set()
        elif kind != "pong":
            log("message", kind, json.dumps(body)[:120])

    def make_channel(label, **options):
        channel = pc.createDataChannel(label, **options)
        channels[label] = channel

        @channel.on("open")
        def on_open():
            state["open"] += 1
            log("data channel open:", label)
            if state["open"] == 2:
                send(
                    "reliable",
                    "hello",
                    {"protocol": 3, "appVersion": "fake", "role": "PHOTOGRAPHER", "name": "fake-photographer"},
                )

        channel.on("message", on_message)

    async with websockets.connect(args.server) as ws:
        await ws.send(json.dumps({"type": "room.create"}))

        async def pump():
            async for raw in ws:
                msg = json.loads(raw)
                kind = msg.get("type")
                if kind == "room.created":
                    log(f"room code {msg['code']}  (session {msg['sessionId']})")
                    print(f"\n    JOIN WITH CODE  {msg['code'][:3]} {msg['code'][3:]}\n", flush=True)
                elif kind == "peer.joined":
                    log("subject joined, offering")
                    pc.addTrack(PatternVideo())
                    make_channel("realtime", ordered=False, maxRetransmits=0)
                    make_channel("reliable")
                    await pc.setLocalDescription(await pc.createOffer())
                    offer = {"type": "offer", "sdp": pc.localDescription.sdp}
                    await ws.send(json.dumps({"type": "signal", "sdp": offer}))
                elif kind == "signal" and msg.get("sdp"):
                    await pc.setRemoteDescription(RTCSessionDescription(msg["sdp"]["sdp"], msg["sdp"]["type"]))
                    log("answer received")
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
            for _ in range(1200):
                if state["hello"] and state["open"] == 2:
                    break
                await asyncio.sleep(0.1)
            else:
                log("no subject connected")
                return
            send(
                "reliable",
                "camera.capabilities",
                {
                    "capabilities": {
                        "minZoom": 1.0,
                        "maxZoom": 4.0,
                        "zoomStops": [1.0, 2.0, 3.0],
                        "exposureRange": [-4, 4],
                        "exposureStep": 0.5,
                        "hasFlash": True,
                    }
                },
            )
            send("reliable", "camera.state", camera_state("PHOTOGRAPHER", True))

            if state["hello"].get("haveGuideId") == guide_id:
                log("subject already has the guide image")
            else:
                chunks = [guide_bytes[i : i + CHUNK_BYTES] for i in range(0, len(guide_bytes), CHUNK_BYTES)]
                send(
                    "reliable",
                    "guide.image.begin",
                    {
                        "guideId": guide_id,
                        "format": "mask-png",
                        "width": guide_size[0],
                        "height": guide_size[1],
                        "bytes": len(guide_bytes),
                        "chunks": len(chunks),
                        "aspect": guide_size[0] / guide_size[1],
                    },
                )
                for index, chunk in enumerate(chunks):
                    send(
                        "reliable",
                        "guide.image.chunk",
                        {"guideId": guide_id, "index": index, "data": base64.b64encode(chunk).decode()},
                    )
                send("reliable", "guide.image.end", {"guideId": guide_id, "crc32": zlib.crc32(guide_bytes)})
                log(f"guide image sent: {len(guide_bytes)} B in {len(chunks)} chunks ({guide_size[0]}x{guide_size[1]})")
            send("reliable", "guide.state", {"state": guide_state(guide_id), "final": True})
            log("guide.state final sent")

            started = time.monotonic()
            step = 0
            while time.monotonic() - started < args.seconds and not state["done"].is_set():
                if state["moved"]:
                    # The subject took the guide; a moving animation would fight its hand.
                    await asyncio.sleep(0.05)
                    continue
                t = time.monotonic() - started
                cx = 0.5 + 0.25 * math.sin(t * 1.5)
                height = 0.7 + 0.15 * math.sin(t * 0.7)
                style = "CUTOUT" if int(t) % 8 >= 4 else "OUTLINE"
                final = step % 40 == 39
                send(
                    "reliable" if final else "realtime",
                    "guide.state",
                    {"state": guide_state(guide_id, cx=cx, height=height, style=style), "final": final},
                )
                if final:
                    log(f"guide.state final cx={cx:.3f} height={height:.3f} style={style}")
                step += 1
                await asyncio.sleep(0.05)
            if args.clear and not state["done"].is_set():
                send("reliable", "guide.clear", {})
                log("guide.clear sent")
                await asyncio.sleep(2)
            if not state["done"].is_set():
                send("reliable", "session.leave", {"reason": "LEFT"})
                await asyncio.sleep(0.3)
                await ws.send(json.dumps({"type": "leave"}))

        await scenario()
        pump_task.cancel()
    await pc.close()
    log("done")


def mask_png(path):
    """The guide image as the app sends it (Design 2.5.1): an 8-bit grayscale PNG of the mask, within the app's limits.

    A cutout with alpha gives its alpha; an image without one is taken as a mask already, by its gray values.
    """
    with Image.open(path) as image:
        mask = image.getchannel("A") if "A" in image.getbands() else image.convert("L")
    mask.thumbnail((1280, 720))
    out = io.BytesIO()
    mask.save(out, "PNG")
    return out.getvalue(), mask.size


parser = argparse.ArgumentParser()
parser.add_argument("--guide", required=True, help="a cutout with alpha, or a grayscale mask; sent as mask-png")
parser.add_argument("--seconds", type=float, default=20)
parser.add_argument("--clear", action="store_true", help="send guide.clear at the end")
parser.add_argument("--server", default="ws://127.0.0.1:8000/ws")
asyncio.run(main(parser.parse_args()))
