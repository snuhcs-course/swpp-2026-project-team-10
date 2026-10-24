Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]] · [[Iteration 1 history|Iteration-1-–-CLAUDE-md-History]]

This page continues the [[Iteration 1 history|Iteration-1-–-CLAUDE-md-History]].

**Revised version (10/14)**

```diff
## Project instructions

+- The two architecture figures of the design page, `wiki/images/design_architecture.png` and `wiki/images/design_target_architecture.png`, are drawn as SVG in `wiki/images/design_architecture.html`. Change the drawing there and render the PNG again as that file's header comment says; do not edit a PNG alone.
```

**Revised version (10/14)**

```diff
## Project instructions

-- Always refer to the [design documentation](wiki/Design-Documentation.md) and [requirements and specifications](wiki/Requirements-and-Specifications.md) for detailed information on the project specifications and design considerations.
+- Always refer to the [design documentation](wiki/Design-Documentation.md), [requirements and specifications](wiki/Requirements-and-Specifications.md), and [testing documentation](wiki/Testing-Documentation.md) for detailed information on the project specifications, design considerations, and how the project is tested.

+- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page in the same PR as the code: say how the change was tested (which tests, commands, devices, and the integration script or NFR it covers) and what the results were (test counts, coverage against the targets, measured numbers, failures with their reason), in the Results part of the current iteration, and add a revision-history row with the next minor version (1.1, 1.2, …, 1.10). A submitted plan item is never rewritten silently; a changed goal is added next to the original with the reason.
```

**Revised version (10/15)**

```diff
## Project instructions

+- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page in the same PR as the code: say how the change was tested (which tests, commands, devices, and the integration script or NFR it covers) and what the results were (test counts, coverage against the targets, measured numbers, failures with their reason), in the Results part of the current iteration, and add a revision-history row with the next minor version (1.1, 1.2, …, 1.10). A submitted plan item is never rewritten silently; a changed goal is added next to the original with the reason.
-- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page's plan in the same PR as the code: how the change is tested (the tests and their partitions, frameworks, fakes, commands, devices, and the integration script or NFR it covers) in sections 1 and 2.1, with a revision-history row with the next minor version (1.1, 1.2, …, 1.10). Results (test counts, coverage against the targets, measured numbers, failures with their reason) and the Test Log are filled in once, at the close of each iteration, not in each PR. A submitted plan item is never rewritten silently; a changed goal is added next to the original with the reason.
```
