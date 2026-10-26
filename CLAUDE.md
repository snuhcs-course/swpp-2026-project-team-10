# CLAUDE.md

## Project instructions

- Always refer to the [design documentation](wiki/Design-Documentation.md), [requirements and specifications](wiki/Requirements-and-Specifications.md), and [testing documentation](wiki/Testing-Documentation.md) for detailed information on the project specifications, design considerations, and how the project is tested.
- These documents may be updated over time, so check them regularly for the latest information. If any text referencing these documents is outdated, update it to reflect the current state of the documentation.
- The `wiki/` folder is the source of the GitHub wiki. Edit the pages there, through a pull request like any other change; the `wiki-sync` workflow mirrors the folder to the wiki on every push to `dev`, deletions included, and overwrites anything edited on GitHub directly. Every file in `wiki/` becomes a wiki page or asset, so keep notes and READMEs out of it.
- Images drawn from source are made from `wiki/design/`: the Design's architecture figure, `wiki/images/design_target_architecture.png`, from the SVG in `wiki/design/design_architecture.html`, and the R&S wireframes (section 6), `wiki/images/flow*.jpg`, from one file per flow in `wiki/design/wireframes/`. Change the drawing there, never the image alone, and render it with `python3 wiki/design/render.py <name>`, whose docstring lists the tools it needs. A PR that changes a screen also changes its wireframe and renders its image, in the same PR.
- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page's plan in the same PR as the code: how the change is tested (the tests and their partitions, frameworks, fakes, commands, devices, and the integration script or NFR it covers) in sections 1 and 2.1, with a revision-history row with the next minor version (1.1, 1.2, …, 1.10). Results (test counts, coverage against the targets, measured numbers, failures with their reason) and the Test Log are filled in once, at the close of each iteration, not in each PR. A submitted plan item is never rewritten silently; a changed goal is added next to the original with the reason.
- Do not make git commits or pushes to the repository on your own. Only make commits and pushes when instructed to do so by a human.
- Every source file that AI wrote or substantially changed (Kotlin, Gradle scripts, Python, workflow YAML) starts with the marker `AI-generated with <tool>, <YYYY-MM-DD>, reviewed by <name>` as its first line, in the file's line-comment syntax (`//` or `#`), before any docstring or import. Name every tool used (for example `Claude Code`, `ChatGPT Codex`, or `ChatGPT Codex and Claude Code`), the date of the last AI change, and the team member who reviewed the result. One marker line per file, not per function or class, and only in source files, never in documentation, resources, or configuration data. Add or update the marker in the same PR as the code. The reviewer is a human; never fill in a name you were not given.

## Maintaining this file

Treat `CLAUDE.md` as persistent context for future coding-agent sessions.

Maintain only these three files:
- `/CLAUDE.md` for repository-wide context
- `/android/CLAUDE.md` for Android-specific context
- `/server/CLAUDE.md` for server-specific context

Update the appropriate file only when you discover or establish durable, non-obvious information that future agents need to work correctly.

Record things such as:
- architecture and important invariants;
- non-obvious dependencies or constraints;
- build, test, lint, and run commands;
- project conventions;
- recurring pitfalls or environment requirements;
- pointers to more detailed documentation.

Do not use `CLAUDE.md` as a task log or scratchpad. Do not record temporary progress, debugging notes, speculative ideas, obvious implementation details, secrets, or machine-specific information.

Keep the files concise and current. Update or remove outdated information instead of continually appending. Avoid duplicating detailed documentation; link to it instead. Keep each Markdown paragraph and list item on one source line; do not manually wrap prose. Preserve line breaks required for Markdown structure and code blocks.

Before finishing a task, consider whether it introduced durable knowledge that belongs in one of the three `CLAUDE.md` files. If not, leave them unchanged.
