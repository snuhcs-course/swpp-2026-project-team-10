**Task:** reference selection and segmentation, P9 ([#5]) · **Member:** 박동제 · **Tool:** Claude Code

Prompt log for [[AI Collaboration Iteration 1 · Dongje|AI-Collaboration-Iteration-1-Dongje]]. Each entry is the instruction as Claude restated it before acting, in English.

## Implementation

- [Dongje, 10-04] "Explain exactly what issue #5 asks me to build. Then create a branch and implement it with me one commit at a time, checking that each step works before moving on."
- [Dongje, 10-04] "Until #6 is merged, connect *Use this guide* to a temporary fake repository."

**Result:** seven commits on `feat/5-reference-guide`, from segmentation to the photo picker and the screens.

## Arm and leg lines

- [Dongje, 10-04] "It only traces the outer silhouette now. Can it also trace the inner outline of the arms?"
- [Dongje, 10-04] "Only arms and legs. If a limb isn't visible, leave it out."
- [Dongje, 10-05] "Find reference photos yourself and test with them."
- [Dongje, 10-05] "Put the arm and leg lines on hold until the next iteration, and go back to the version we had finished."

**Result:** a prototype on the local branch `feat/5-limb-lines` (`5581861`), parked for Iteration 2.

## Merge and pull request

- [Dongje, 10-05] "Merge `dev` into the branch and run the build and the tests."
- [Dongje, 10-05] "I checked it on the phone. Push and open the PR, and stress that `InterimGuideRepository` must be reverted after #6 is merged."

**Result:** [#28], squash-merged as `414bb79`.

[#5]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/5
[#28]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/28
