# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
# Image of the Pix server (Design 2.8, Hosting). Build from server/:
#   docker build --target test .                     runs pytest; the build fails if a test fails
#   docker build --target runtime -t pix-server .    the image that deploy.sh starts
# deploy.sh and the server-image workflow build both, in this order, so an image whose tests fail is never started.

FROM python:3.11-slim AS deps
# The same uv version as the CI workflows.
COPY --from=ghcr.io/astral-sh/uv:0.12.21 /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never
WORKDIR /app
# Only the dependency list is copied here, so a code change does not install the dependencies again. The cache
# mounts keep uv's downloads on the build machine, so a changed dependency list downloads only what is new.
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev --no-install-project

FROM deps AS test
# The test tools come before the code for the same reason.
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-install-project
COPY src ./src
COPY tests ./tests
# A test checks that it lists every setting.
COPY .env.example ./
# The code is tested from src/ as it is, without building the package.
RUN PYTHONPATH=src .venv/bin/python -m pytest -q

FROM deps AS build
COPY README.md ./
COPY src ./src
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev --no-editable

FROM python:3.11-slim AS runtime
ENV PYTHONUNBUFFERED=1 \
    PATH=/app/.venv/bin:$PATH
RUN useradd --no-create-home --uid 10001 pix
WORKDIR /app
COPY --from=build /app/.venv ./.venv
USER pix
EXPOSE 8000
# deploy.sh waits for this check before it sends traffic to a new copy.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --start-interval=1s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2)"]
# Two workers share rooms through the Redis session store (Design AD-18). The count is explicit, because the
# course servers report 38 CPUs for the 8 cores we get.
# --forwarded-allow-ips "*" is safe only because port 8000 is not published: nginx is the one client, and it
# replaces X-Forwarded-For with the phone's address. 35 s lets a pose request in flight finish on shutdown.
CMD ["uvicorn", "pix_server.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2", \
     "--ws-max-size", "65536", "--proxy-headers", "--forwarded-allow-ips", "*", "--timeout-graceful-shutdown", "35"]
