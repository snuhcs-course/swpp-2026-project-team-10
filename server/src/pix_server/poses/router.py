# AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
"""REST endpoints for pose generation (Design Documentation 2.5.3 and Android's PixApi.kt)."""

import asyncio
import base64
import time
from typing import Annotated

from fastapi import APIRouter, Form, Request, UploadFile
from pydantic import BaseModel

from pix_server.errors import ApiError
from pix_server.poses.generator import PoseGenerator, check_scene
from pix_server.poses.limiter import Kind, PoseLimiter
from pix_server.poses.prompts import DEFAULT_POSES, checked_pose, pose_prompt
from pix_server.poses.suggester import PoseSuggester

router = APIRouter(prefix="/api/v1")

DISCONNECT_POLL_SECONDS = 0.25


class PoseTemplateResponse(BaseModel):
    id: str
    label: str


class PoseSuggestionsResponse(BaseModel):
    poses: list[str]


class PoseResponse(BaseModel):
    templateId: str | None  # None for a description; the app tells its four requests apart by itself
    image: str
    elapsedMs: int


async def count(request: Request, kind: Kind) -> None:
    """Counts a request that is about to reach a model, or refuses it with 429. Only such requests count, because the
    limits exist to control what the models cost."""
    limiter: PoseLimiter = request.app.state.pose_limiter
    refused = await limiter.refuse(request.client.host if request.client else "", kind)
    if refused == "client":
        raise ApiError(429, "RATE_LIMITED", "Too many pose requests from this device; try again later")
    if refused == "global":
        raise ApiError(429, "RATE_LIMITED", "Pix has made as many poses as it can for today; try again later")


@router.get("/pose-templates")
async def pose_templates() -> list[PoseTemplateResponse]:
    """The default poses, for apps from before Iteration 2, which ask for them by id; the current app does not call
    this."""
    return [PoseTemplateResponse(id=pose.id, label=pose.label) for pose in DEFAULT_POSES.values()]


@router.post("/pose-suggestions")
async def suggest_poses(
    request: Request,
    image: UploadFile,
    # Left out, or empty, for suggested poses; FastAPI reads an empty form field as left out.
    description: Annotated[str | None, Form()] = None,
) -> PoseSuggestionsResponse:
    """Four poses for the scene, which the app then sends as the descriptions of its four POST /poses: four poses
    suggested for the scene, or, with a description, the description and three variations of it."""
    if description is not None:
        checked_pose(description)
    scene = await image.read()
    check_scene(scene)
    await count(request, "suggestions")
    suggester: PoseSuggester = request.app.state.pose_suggester
    if description is None:
        return PoseSuggestionsResponse(poses=await suggester.suggest(scene))
    return PoseSuggestionsResponse(poses=await suggester.vary(scene, description))


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
        prompt = pose_prompt(description)
    elif (default := DEFAULT_POSES.get(template_id)) is not None:
        # An app from before Iteration 2; its template is a default pose, sent with the same prompt.
        prompt = pose_prompt(default.pose)
    else:
        raise ApiError(400, "UNKNOWN_TEMPLATE", "No pose template with this id")
    scene = await image.read()
    check_scene(scene)
    await count(request, "poses")

    generator: PoseGenerator = request.app.state.pose_generator
    call = asyncio.create_task(generator.generate(scene, prompt, seed))
    try:
        while not call.done():
            # A phone that cancels closes its connection (Design Documentation 2.6.3). Cancelling the call closes
            # ours to the image API, which does not bill a generation it could not deliver.
            if await request.is_disconnected():
                raise ApiError(499, "CLIENT_CLOSED_REQUEST", "The request was cancelled")
            await asyncio.wait({call}, timeout=DISCONNECT_POLL_SECONDS)
        # A refusal keeps the generator's own message: the image service does not say whether it refused the photo or
        # the text, and every pose of the current app, suggested ones included, arrives as a description (US-5).
        candidate = call.result()
    finally:
        call.cancel()
    return PoseResponse(
        templateId=template_id,
        image=base64.b64encode(candidate).decode(),
        elapsedMs=round((time.perf_counter() - started) * 1000),
    )
