Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]] · [[Iteration 1 history|Iteration-1-–-CLAUDE-md-History]] · [[Iteration 2 history|Iteration-2-–-CLAUDE-md-History]]

This page continues the [[Iteration 1 history|Iteration-1-–-CLAUDE-md-History]] and [[Iteration 2 history|Iteration-2-–-CLAUDE-md-History]].

**Revised version (10/26)**

```diff
## Project instructions

+- Images drawn from source are made from `wiki/design/`: the Design's architecture figure, `wiki/images/design_target_architecture.png`, from the SVG in `wiki/design/design_architecture.html`, and the R&S wireframes (section 6), `wiki/images/flow*.jpg`, from one file per flow in `wiki/design/wireframes/`. Change the drawing there, never the image alone, and render it with `python3 wiki/design/render.py <name>`, whose docstring lists the tools it needs. A PR that changes a screen also changes its wireframe and renders its image, in the same PR.
-- The architecture figure of the design page, `wiki/images/design_target_architecture.png`, is drawn as SVG in `wiki/images/design_architecture.html`. Change the drawing there and render the PNG again as that file's header comment says; do not edit the PNG alone.
```

**Revised version (11/3)**

```diff
## Project instructions

+- Images drawn from source are made from `wiki/design/`: the Design's architecture figure, `wiki/images/design_target_architecture.png`, from the SVG in `wiki/design/design_architecture.html`, the R&S wireframes (section 6), `wiki/images/flow*.jpg`, from one file per flow in `wiki/design/wireframes/`, and the first-use tutorial's slides, `android/app/src/main/res/drawable*-nodpi/tutorial_*.webp`, from `wiki/design/tutorial/`. Change the drawing there, never the image alone, and render it with `python3 wiki/design/render.py <name>`, whose docstring lists the tools it needs. A PR that changes a screen also changes its wireframe and renders its image, in the same PR, and its slide if the tutorial shows that screen.
-- Images drawn from source are made from `wiki/design/`: the Design's architecture figure, `wiki/images/design_target_architecture.png`, from the SVG in `wiki/design/design_architecture.html`, and the R&S wireframes (section 6), `wiki/images/flow*.jpg`, from one file per flow in `wiki/design/wireframes/`. Change the drawing there, never the image alone, and render it with `python3 wiki/design/render.py <name>`, whose docstring lists the tools it needs. A PR that changes a screen also changes its wireframe and renders its image, in the same PR.
```

**Revised version (11/6)**

```diff
## Project instructions

+- Images drawn from source are made from `wiki/design/`: the Design's architecture figure, `wiki/images/design_target_architecture.png`, from the SVG in `wiki/design/design_architecture.html`, and the R&S wireframes (section 6), `wiki/images/flow*.jpg`, from one file per flow in `wiki/design/wireframes/`; the tutorial has no images, since its tips are drawn over the live screens (`TutorialFragment`). Change the drawing there, never the image alone, and render it with `python3 wiki/design/render.py <name>`, whose docstring lists the tools it needs. A PR that changes a screen also changes its wireframe and renders its image, in the same PR, and checks the tutorial's tips if one points at a changed control.
-- Images drawn from source are made from `wiki/design/`: the Design's architecture figure, `wiki/images/design_target_architecture.png`, from the SVG in `wiki/design/design_architecture.html`, the R&S wireframes (section 6), `wiki/images/flow*.jpg`, from one file per flow in `wiki/design/wireframes/`, and the first-use tutorial's slides, `android/app/src/main/res/drawable*-nodpi/tutorial_*.webp`, from `wiki/design/tutorial/`. Change the drawing there, never the image alone, and render it with `python3 wiki/design/render.py <name>`, whose docstring lists the tools it needs. A PR that changes a screen also changes its wireframe and renders its image, in the same PR, and its slide if the tutorial shows that screen.
```

**Revised version (11/6)**

```diff
## Project instructions

+- When updating these documents, use concise, simple technical English: short sentences with one idea each, each fact stated once, references at the end of a sentence, and no filler. Also add one short line to the page's revision-history table, with the next minor version, what changed, and the section numbers.

+- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page's plan in the same PR as the code: how the change is tested (the tests, frameworks, fakes, commands, devices, and the integration script or NFR it covers) in sections 1 and 2.1, with an integration script's full steps in Appendix A. Each test class lists its partitions in its header comment, not on the page. Results (test counts, coverage against the targets, measured numbers, failures with their reason), the run records of Appendix B, and the Test Log are filled in once, at the close of each iteration, not in each PR. A plan item submitted in an earlier iteration is never rewritten silently: a changed goal is added next to the original with the reason; within the iteration that wrote it, the item is simply replaced.
-- The [testing documentation](wiki/Testing-Documentation.md) is the single record of how Pix is tested. Before writing or changing tests, read its rules (section 1.3), the frameworks, fakes, and commands (2.1), and the partitions and techniques (2.1.6), and follow them. Every task that builds a feature or adds or changes tests updates that page's plan in the same PR as the code: how the change is tested (the tests and their partitions, frameworks, fakes, commands, devices, and the integration script or NFR it covers) in sections 1 and 2.1, with a revision-history row with the next minor version (1.1, 1.2, …, 1.10). Results (test counts, coverage against the targets, measured numbers, failures with their reason) and the Test Log are filled in once, at the close of each iteration, not in each PR. A submitted plan item is never rewritten silently; a changed goal is added next to the original with the reason.
```
