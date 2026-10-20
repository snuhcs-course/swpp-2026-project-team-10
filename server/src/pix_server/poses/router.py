# AI-generated with Claude Code, 2026-10-20, reviewed by Jaewan Park
"""REST endpoints for pose generation (Design Documentation 2.5.3 and Android's PixApi.kt)."""

import asyncio
import base64
import time
from typing import Annotated

from fastapi import APIRouter, Form, Request, UploadFile
from pydantic import BaseModel

from pix_server.errors import ApiError
from pix_server.poses.generator import PoseGenerator, check_scene
from pix_server.poses.limiter import PoseLimiter
from pix_server.poses.templates import TEMPLATES, description_prompt, template_prompt

router = APIRouter(prefix="/api/v1")

DISCONNECT_POLL_SECONDS = 0.25


class PoseTemplateResponse(BaseModel):
    id: str
    label: str


class PoseResponse(BaseModel):
    templateId: str | None  # None for a description; the app tells its four requests apart by itself
    image: str
    elapsedMs: int


@router.get("/pose-templates")
async def pose_templates() -> list[PoseTemplateResponse]:
    return [PoseTemplateResponse(id=template.id, label=template.label) for template in TEMPLATES.values()]


@router.post("/poses")
async def create_pose(
    request: Request,
    image: UploadFile,
    seed: Annotated[int, Form()],
    # FastAPI reads an empty form field as left out, so an empty description with a template asks for the template.
    template_id: Annotated[str | None, Form(alias="templateId")] = None,
    description: Annotated[str | None, Form()] = None,
) -> PoseResponse:
    started = time.perf_counter()
    if (template_id is None) == (description is None):
        raise ApiError(400, "INVALID_REQUEST", "templateId or description: send exactly one")
    if description is not None:
        prompt = description_prompt(description)
    elif (template := TEMPLATES.get(template_id)) is not None:
        prompt = template_prompt(template)
    else:
        raise ApiError(400, "UNKNOWN_TEMPLATE", "No pose template with this id")
    scene = await image.read()
    check_scene(scene)

    # Only requests that reach the image API count, because the limits exist to control its cost.
    limiter: PoseLimiter = request.app.state.pose_limiter
    refused = await limiter.refuse(request.client.host if request.client else "")
    if refused == "client":
        raise ApiError(429, "RATE_LIMITED", "Too many pose requests from this device; try again later")
    if refused == "global":
        raise ApiError(429, "RATE_LIMITED", "Pix has made as many poses as it can for today; try again later")

    generator: PoseGenerator = request.app.state.pose_generator
    call = asyncio.create_task(generator.generate(scene, prompt, seed))
    try:
        while not call.done():
            # A phone that cancels closes its connection (Design Documentation 2.6.3). Cancelling the call closes
            # ours to the image API, which does not bill a generation it could not deliver.
            if await request.is_disconnected():
                raise ApiError(499, "CLIENT_CLOSED_REQUEST", "The request was cancelled")
            await asyncio.wait({call}, timeout=DISCONNECT_POLL_SECONDS)
        candidate = call.result()
    except ApiError as exc:
        if exc.code == "REJECTED" and description is not None:
            # A refused description gets the message the app shows for it (US-5).
            raise ApiError(exc.status, exc.code, "Try a different description") from None
        raise
    finally:
        call.cancel()
    return PoseResponse(
        templateId=template_id,
        image=base64.b64encode(candidate).decode(),
        elapsedMs=round((time.perf_counter() - started) * 1000),
    )
