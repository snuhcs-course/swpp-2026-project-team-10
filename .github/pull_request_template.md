<!--
Title: Conventional Commits, for example "feat: add guide overlay". It becomes the squash-merge commit message.
Branch: <type>/<issue#>-<name> from dev, and this PR targets dev.
-->

## Summary

<!-- What this PR does and why, in one to three sentences. -->

## Changes

-

## Not included

<!-- Parts of the issue left for a later PR, or "None". -->

## How to verify

<!-- Steps a reviewer can follow: build or run command, screen, device, and the expected result. Add a screenshot or a short video for UI changes. -->

## AI usage

<!-- Write "None" if no AI was used. Otherwise fill in all four lines; the prompts link points to the wiki prompt log page for this work. -->

- **Tools used:** <!-- Claude Code, ChatGPT Codex, Copilot review, ... -->
- **What AI did well:**
- **What we had to fix:**
- **Prompts:** <!-- link to the wiki prompt log -->

## Checklist

- [ ] The app builds (`./gradlew assembleDebug` in `android/`), or the server starts locally
- [ ] The shared contracts in `android/` are unchanged, or the affected owners review this PR and the Design Documentation is updated
- [ ] The "Done when" items of the issue are met
- [ ] A changed screen is also changed in its wireframe (`wiki/design/wireframes/`), and its image in `wiki/images/` is rendered again (`wiki/design/render.py`)
- [ ] Every source file AI wrote or changed starts with the `AI-generated with <tool>, <date>, reviewed by <name>` marker, dated and named for this PR

<!-- Use "Part of #n" instead if the issue stays open after this PR. -->
Closes #
