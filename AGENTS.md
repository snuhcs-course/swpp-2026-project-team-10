# AGENTS.md

## Project instructions

- Always refer to the [design documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation) and [requirements and specifications](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications) for detailed information on the project specifications and design considerations.
- These documents may be updated over time, so check them regularly for the latest information. If any text referencing these documents is outdated, update it to reflect the current state of the documentation.
- Do not make git commits or pushes to the repository on your own. Only make commits and pushes when instructed to do so by a human.

## Maintaining this file

Treat `AGENTS.md` as persistent context for future coding-agent sessions.

Before starting work, read the applicable `AGENTS.md` files and follow their instructions.

Maintain only these three files:
- `/AGENTS.md` for repository-wide context
- `/android/AGENTS.md` for Android-specific context
- `/server/AGENTS.md` for server-specific context

Update the appropriate file only when you discover or establish durable, non-obvious information that future agents need to work correctly.

Record things such as:
- architecture and important invariants;
- non-obvious dependencies or constraints;
- build, test, lint, and run commands;
- project conventions;
- recurring pitfalls or environment requirements;
- pointers to more detailed documentation.

Do not use `AGENTS.md` as a task log or scratchpad. Do not record temporary progress, debugging notes, speculative ideas, obvious implementation details, secrets, or machine-specific information.

Keep the files concise and current. Update or remove outdated information instead of continually appending. Avoid duplicating detailed documentation; link to it instead. Keep each Markdown paragraph and list item on one source line; do not manually wrap prose. Preserve line breaks required for Markdown structure and code blocks.

Before finishing a task, consider whether it introduced durable knowledge that belongs in one of the three `AGENTS.md` files. If not, leave them unchanged.
