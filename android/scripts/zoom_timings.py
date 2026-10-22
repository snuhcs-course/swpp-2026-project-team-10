# AI-generated with Claude Code, 2026-10-22, reviewed by Sungmin Jo
"""Turns a `PixTimings` logcat dump into the zoom numbers of NFR-19 (Testing Documentation IT-10).

Record on the photographer's phone, from `android/`:

    adb logcat -c
    # pinch for about ten seconds, cross 1x a few times, then press the shutter
    adb logcat -d -s PixTimings > zoom.log
    python3 scripts/zoom_timings.py zoom.log

A frozen frame repeats the zoom of the frame before although the fingers moved (allowing for the camera's lag from
request to picture): what a stutter looks like.
The app logs every camera frame as `zoom.frame` for 1.5 s after each zoom request, lens switch, or shutter press
(`CameraXController`). A pinch is a run of `zoom.request` marks less than 0.5 s apart. Times are milliseconds of
`elapsedRealtime`; frame intervals come from the sensor timestamps.
"""

from __future__ import annotations  # the Python 3.9 that macOS ships runs this too

import argparse
import re
import statistics
import sys
from dataclasses import dataclass

LINE = re.compile(r"PixTimings\s*:\s*(\d+) (\S+)(?: (.*))?$")
GESTURE_GAP_MS = 500
DROP_FACTOR = 1.5  # an interval this many times the median is a dropped frame
MATCH = 0.005  # a frame shows a requested native ratio when within 0.5 %
SETTLE_MS = 400  # frames of a pinch still arriving after its last request
SWITCH_WINDOW_MS = 5000  # how far after a lens switch its own marks are looked for


@dataclass
class Mark:
    ms: int
    name: str
    detail: str

    def field(self, key: str) -> str | None:
        found = re.search(rf"\b{key}=(\S+)", self.detail)
        return found.group(1) if found else None


@dataclass
class Frame:
    ms: int
    sensor_us: int
    zoom: float | None
    crop: int | None
    lens: str

    @property
    def view(self) -> object:
        """What the frame shows: its zoom ratio, or its crop where the phone reports no ratio."""
        return self.zoom if self.zoom is not None else self.crop


def number(text: str | None) -> float | None:
    try:
        return float(text) if text not in (None, "null") else None
    except ValueError:
        return None


def parse(lines: list[str]) -> list[Mark]:
    marks = []
    for line in lines:
        found = LINE.search(line.rstrip())
        if found:
            marks.append(Mark(int(found.group(1)), found.group(2), found.group(3) or ""))
    return marks


def frames_of(marks: list[Mark]) -> list[Frame]:
    frames = []
    for mark in marks:
        if mark.name != "zoom.frame":
            continue
        sensor = number(mark.field("us"))
        if sensor is None:
            continue
        crop = number(mark.field("crop"))
        frames.append(
            Frame(mark.ms, int(sensor), number(mark.field("z")), int(crop) if crop else None, mark.field("lens") or "")
        )
    return frames


def gestures_of(marks: list[Mark]) -> list[tuple[int, int, list[Mark]]]:
    gestures: list[tuple[int, int, list[Mark]]] = []
    for mark in (m for m in marks if m.name == "zoom.request"):
        if gestures and mark.ms - gestures[-1][1] <= GESTURE_GAP_MS:
            start, _, requests = gestures[-1]
            gestures[-1] = (start, mark.ms, requests + [mark])
        else:
            gestures.append((mark.ms, mark.ms, [mark]))
    return gestures


def percentile(values: list[float], share: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, round(share * (len(ordered) - 1)))]


def intervals(frames: list[Frame]) -> list[float]:
    """Frame-to-frame time in ms on one lens; a lens switch is not an interval."""
    return [
        (b.sensor_us - a.sensor_us) / 1000
        for a, b in zip(frames, frames[1:])
        if a.lens == b.lens and b.sensor_us > a.sensor_us
    ]


@dataclass
class Frozen:
    """Frames that repeat the zoom of the frame before while the zoom is moving: the visible stutter."""

    frozen: int = 0
    moving: int = 0
    longest_ms: float = 0.0

    def add(self, other: Frozen) -> None:
        self.frozen += other.frozen
        self.moving += other.moving
        self.longest_ms = max(self.longest_ms, other.longest_ms)

    def __str__(self) -> str:
        if not self.moving:
            return "—"
        return f"{self.frozen} of {self.moving} ({100 * self.frozen / self.moving:.0f} %), longest {self.longest_ms:.0f} ms"


def changes_of(marks: list[Mark]) -> list[Mark]:
    """The `zoom.apply` marks that ask the camera for a new native ratio. A held lens gets the same ratio for every
    step inside the hold band (and a pinch held at either end gets its limit): those change nothing on screen, so they
    are neither stutter nor a latency."""
    changes, last = [], {}
    for apply in (m for m in marks if m.name == "zoom.apply"):
        native, lens = number(apply.field("native")), apply.field("lens")
        if native is None:
            continue
        before = last.get(lens)
        if before is None or abs(native - before) > MATCH * before:
            changes.append(apply)
        last = {lens: native}  # a lens switch starts its lens afresh
    return changes


def frozen_of(frames: list[Frame], changes: list[Mark], lag_ms: float) -> Frozen:
    """A frame is moving when a new ratio was asked for during the frame time before it, shifted back by the camera's
    lag from request to picture, so a pinch that pauses or a held lens is not counted; it is frozen when it is moving
    and shows the same zoom as the frame before it."""
    times = [m.ms for m in changes]
    result, run = Frozen(), 0.0
    for previous, frame in zip(frames, frames[1:]):
        if previous.lens != frame.lens:
            run = 0.0
            continue
        start, end = previous.ms - lag_ms, frame.ms - lag_ms
        moved = any(start < ms <= end for ms in times)
        if not moved:
            run = 0.0
            continue
        result.moving += 1
        if frame.view == previous.view:
            result.frozen += 1
            run += (frame.sensor_us - previous.sensor_us) / 1000
            result.longest_ms = max(result.longest_ms, run)
        else:
            run = 0.0
    return result


def table(title: str, header: list[str], rows: list[list[object]]) -> str:
    out = [f"### {title}", "", "| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    out += ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join(out) + "\n"


def fmt(value: float | None) -> str:
    return "—" if value is None else f"{value:.0f}"


def pinch_rows(
    marks: list[Mark], frames: list[Frame], changes: list[Mark], lag_ms: float
) -> tuple[list[list[object]], list[float], Frozen]:
    rows, all_intervals, all_frozen = [], [], Frozen()
    applies = [m for m in marks if m.name == "zoom.apply"]
    switches = [m for m in marks if m.name == "zoom.lens"]
    for number_, (start, end, requests) in enumerate(gestures_of(marks), 1):
        seconds = max((end - start) / 1000, 0.001)
        # The picture follows the fingers a few frames late, so the frames after the last request count too.
        inside = [f for f in frames if start <= f.ms <= end + SETTLE_MS]
        gaps = intervals(inside)
        all_intervals += gaps
        median = statistics.median(gaps) if gaps else None
        drops = sum(1 for gap in gaps if median and gap > DROP_FACTOR * median)
        frozen = frozen_of(inside, changes, lag_ms)
        all_frozen.add(frozen)
        rows.append(
            [
                number_,
                f"{seconds:.1f}",
                f"{len(requests)} ({len(requests) / seconds:.0f}/s)",
                sum(1 for m in applies if start <= m.ms <= end + 300),
                sum(1 for m in switches if start <= m.ms <= end + 300),
                len(inside),
                fmt(median),
                fmt(percentile(gaps, 0.95) if gaps else None),
                fmt(max(gaps) if gaps else None),
                drops,
                frozen,
            ]
        )
    return rows, all_intervals, all_frozen


def latencies(changes: list[Mark], frames: list[Frame]) -> tuple[list[float], int]:
    """A new ratio to the first frame showing it; a request the camera replaced before showing it is not counted."""
    found, missed = [], 0
    for apply in changes:
        native, lens = number(apply.field("native")), apply.field("lens")
        if native is None:
            continue
        shown = next(
            (
                f
                for f in frames
                if f.ms >= apply.ms and f.lens == lens and f.zoom is not None and abs(f.zoom - native) <= MATCH * native
            ),
            None,
        )
        if shown and shown.ms - apply.ms < 1000:
            found.append(shown.ms - apply.ms)
        else:
            missed += 1
    return found, missed


def first_after(marks: list[Mark], start: int, until: int, name: str, detail: str | None = None) -> Mark | None:
    """The first such mark from [start] up to [until], so a mark missing for one event is not taken from a later one."""
    return next(
        (
            m
            for m in marks
            if start <= m.ms <= until and m.name == name and (detail is None or m.detail.startswith(detail))
        ),
        None,
    )


def switch_rows(marks: list[Mark], frames: list[Frame]) -> list[list[object]]:
    rows = []
    switches = [m for m in marks if m.name == "zoom.lens"]
    for i, switch in enumerate(switches):
        # Each switch's marks come before the next switch, and within a few seconds.
        until = switch.ms + SWITCH_WINDOW_MS
        if i + 1 < len(switches):
            until = min(until, switches[i + 1].ms)
        before = [f for f in frames if f.ms <= switch.ms]
        after = next(
            (f for f in frames if switch.ms < f.ms <= until and (not before or f.lens != before[-1].lens)), None
        )
        ready = first_after(marks, switch.ms, until, "camera.status", "READY")
        idle = first_after(marks, switch.ms, until, "preview.stream", "IDLE")
        streaming = first_after(marks, idle.ms, until, "preview.stream", "STREAMING") if idle else None
        still = first_after(marks, switch.ms - 50, switch.ms + 200, "preview.still", "on")
        held = still is not None
        released = first_after(marks, still.ms, until, "preview.still", "off") if still else None
        rows.append(
            [
                f"{switch.detail.removeprefix('from=')} → {after.lens if after else '?'}",
                fmt(after.ms - before[-1].ms if after and before else None),
                fmt(ready.ms - switch.ms if ready else None),
                fmt(streaming.ms - idle.ms if streaming and idle else None),
                f"yes, {released.ms - still.ms} ms" if held and released else ("yes" if held else "no"),
            ]
        )
    return rows


def capture_rows(marks: list[Mark], frames: list[Frame]) -> list[list[object]]:
    rows = []
    for start in (m for m in marks if m.name == "camera.capture.start"):
        end = next(
            (
                m
                for m in marks
                if start.ms <= m.ms <= start.ms + SWITCH_WINDOW_MS
                and m.name.startswith("camera.capture.")
                and m is not start
            ),
            None,
        )
        window = [f for f in frames if start.ms - 200 <= f.ms <= (end.ms if end else start.ms) + 300]
        gaps = intervals(window)
        rows.append(
            [
                fmt(end.ms - start.ms if end else None),
                end.name.removeprefix("camera.capture.") if end else "?",
                fmt(max(gaps) if gaps else None),
            ]
        )
    return rows


def report(marks: list[Mark]) -> str:
    frames = frames_of(marks)
    changes = changes_of(marks)
    found, missed = latencies(changes, frames)
    rows, all_intervals, frozen = pinch_rows(marks, frames, changes, statistics.median(found) if found else 0.0)
    out = []
    if all_intervals:
        median = statistics.median(all_intervals)
        drops = sum(1 for gap in all_intervals if gap > DROP_FACTOR * median)
        out.append(
            table(
                "Summary",
                [
                    "pinches",
                    "frame interval p50 / p95 / max (ms)",
                    "dropped frames",
                    "frozen frames while zooming",
                    "request → shown p50 / p95 (ms)",
                ],
                [
                    [
                        len(rows),
                        f"{median:.0f} / {percentile(all_intervals, 0.95):.0f} / {max(all_intervals):.0f}",
                        f"{drops} of {len(all_intervals)} ({100 * drops / len(all_intervals):.1f} %)",
                        frozen,
                        f"{fmt(percentile(found, 0.5) if found else None)} / {fmt(percentile(found, 0.95) if found else None)}"
                        f" ({len(found)} shown, {missed} replaced first)",
                    ]
                ],
            )
        )
    out.append(
        table(
            "Pinches",
            [
                "#",
                "s",
                "requests",
                "applied",
                "lens switches",
                "frames",
                "interval p50",
                "p95",
                "max",
                "drops",
                "frozen while zooming",
            ],
            rows,
        )
    )
    out.append(
        table(
            "Lens switches",
            ["switch", "no new frame (ms)", "until READY (ms)", "preview idle (ms)", "last frame held"],
            switch_rows(marks, frames),
        )
    )
    out.append(
        table("Shutter", ["press → result (ms)", "result", "longest frame gap (ms)"], capture_rows(marks, frames))
    )
    return "\n".join(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("log", nargs="?", help="a `adb logcat -d -s PixTimings` dump; standard input when omitted")
    args = parser.parse_args()
    if args.log:
        with open(args.log, encoding="utf-8", errors="replace") as file:
            lines = file.readlines()
    else:
        lines = sys.stdin.readlines()
    marks = parse(lines)
    if not any(m.name == "zoom.request" for m in marks):
        sys.exit("No zoom.request marks: pinch on the Camera screen of a debug build while logcat records.")
    print(report(marks))


if __name__ == "__main__":
    main()
