# CLAUDE.md

## Project instructions

- Always refer to the [design documentation](wiki/Design-Documentation.md), [requirements and specifications](wiki/Requirements-and-Specifications.md), and [testing documentation](wiki/Testing-Documentation.md) for detailed information on the project specifications, design considerations, and how the project is tested.
- These documents may be updated over time, so check them regularly for the latest information. If any text referencing these documents is outdated, update it to reflect the current state of the documentation.
- The `wiki/` folder is the source of the GitHub wiki. Edit the pages there, through a pull request like any other change; the `wiki-sync` workflow mirrors the folder to the wiki on every push to `dev`, deletions included, and overwrites anything edited on GitHub directly. Every file in `wiki/` becomes a wiki page or asset, so keep notes and READMEs out of it.
- The two architecture figures of the design page, `wiki/images/design_architecture.png` and `wiki/images/design_target_architecture.png`, are drawn as SVG in `wiki/images/design_architecture.html`. Change the drawing there and render the PNG again as that file's header comment says; do not edit a PNG alone.
- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page in the same PR as the code: say how the change was tested (which tests, commands, devices, and the integration script or NFR it covers) and what the results were (test counts, coverage against the targets, measured numbers, failures with their reason), in the Results part of the current iteration, and add a revision-history row with the next minor version (1.1, 1.2, …, 1.10). A submitted plan item is never rewritten silently; a changed goal is added next to the original with the reason.
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
