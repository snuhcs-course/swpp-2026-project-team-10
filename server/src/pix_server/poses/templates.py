"""Pose templates and the prompt that wraps them (Design Documentation 2.6.3)."""

from dataclasses import dataclass


@dataclass(frozen=True)
class PoseTemplate:
    id: str
    label: str
    # Sent to the image API only; the app sees the id and label.
    description: str


TEMPLATES: dict[str, PoseTemplate] = {
    template.id: template
    for template in (
        PoseTemplate("hands_on_hips", "Hands on hips", "standing with both hands on the hips"),
        PoseTemplate("wave", "Wave", "waving at the camera with one raised hand"),
        PoseTemplate("walking", "Walking", "walking toward the camera in mid-stride"),
        PoseTemplate("arms_crossed", "Arms crossed", "standing with the arms crossed over the chest"),
    )
}

# The model may re-place the person, but the camera framing is pinned so the candidate still matches the live view.
PROMPT = (
    "Keep the same person (face, hair, clothing). Keep the background, camera position, framing, and lighting "
    "exactly as they are: do not zoom, crop, or shift the scene. Recompose the shot like a skilled photographer "
    "would: judge whether the person is too far, too close, or poorly placed, and if so move them to a better "
    "spot and distance in this scene. Change the person's pose to: {pose}. Show the full body, standing on the "
    "ground at a natural scale. Photorealistic."
)
