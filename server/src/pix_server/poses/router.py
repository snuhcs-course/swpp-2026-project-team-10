"""REST endpoints for pose generation (Design Documentation 2.5.3 and Android's PixApi.kt)."""

import asyncio
import base64
import time
from typing import Annotated

from fastapi import APIRouter, Form, Request, UploadFile
from pydantic import BaseModel

from pix_server.errors import ApiError
from pix_server.poses.generator import MAX_SCENE_BYTES, PoseGenerator, check_scene
from pix_server.poses.limiter import RateLimiter
from pix_server.poses.templates import TEMPLATES

router = APIRouter(prefix="/api/v1")

DISCONNECT_POLL_SECONDS = 0.25


class PoseTemplateResponse(BaseModel):
    id: str
    label: str


class PoseResponse(BaseModel):
    templateId: str
    image: str
    elapsedMs: int


@router.get("/pose-templates")
async def pose_templates() -> list[PoseTemplateResponse]:
    return [PoseTemplateResponse(id=template.id, label=template.label) for template in TEMPLATES.values()]


@router.post("/poses")
async def create_pose(
    request: Request,
    image: UploadFile,
    template_id: Annotated[str, Form(alias="templateId")],
    seed: Annotated[int, Form()],
) -> PoseResponse:
    started = time.perf_counter()
    template = TEMPLATES.get(template_id)
    if template is None:
        raise ApiError(400, "UNKNOWN_TEMPLATE", "No pose template with this id")
    scene = await image.read(MAX_SCENE_BYTES + 1)
    check_scene(scene)

    # Only requests that reach the image API count, because the limit exists to control its cost.
    limiter: RateLimiter = request.app.state.pose_limiter
    if not limiter.allow(request.client.host if request.client else ""):
        raise ApiError(429, "RATE_LIMITED", "Too many pose requests from this device; try again later")

    generator: PoseGenerator = request.app.state.pose_generator
    call = asyncio.create_task(generator.generate(scene, template, seed))
    try:
        while not call.done():
            # A phone that cancels closes its connection (Design Documentation 2.6.3). Cancelling the call closes
            # ours to the image API, which does not bill a generation it could not deliver.
            if await request.is_disconnected():
                raise ApiError(499, "CLIENT_CLOSED_REQUEST", "The request was cancelled")
            await asyncio.wait({call}, timeout=DISCONNECT_POLL_SECONDS)
        candidate = call.result()
    finally:
        call.cancel()
    return PoseResponse(
        templateId=template.id,
        image=base64.b64encode(candidate).decode(),
        elapsedMs=round((time.perf_counter() - started) * 1000),
    )
