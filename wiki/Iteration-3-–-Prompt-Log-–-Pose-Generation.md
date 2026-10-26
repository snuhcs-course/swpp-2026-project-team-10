Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

Prompts were given in Korean to Claude Code (Claude Opus 5.5) and are translated here, shortened to the instruction; status checks are left out.

## *Pick a pose* one candidate at a time ([#114], [#108])

**The pager (10/24)**

- [Dongje, 10-24] "Generating poses now shows the four photos split on one screen, and they are too small. Can you make it a slide so each photo can be seen large, one at a time, and a swipe shows the next suggested pose? Update dev first." (A `ViewPager2` on *Pick a pose*; *Generating poses* keeps its grid. No candidate could be generated off campus, so the review images were rendered under Robolectric with stand-in photos.)
- [Dongje, 10-24] "Was *Try other poses* there before?"
- [Dongje, 10-24] "Can the branch name be changed later? I want to open the PR from a branch named after the feature and rename it just before merging." (A rename on GitHub keeps the open PR.)
- [Dongje, 10-24] "Commit it and open the PR. This will go into Iteration 3, so keep that in mind for the documents." / "Keep the documents only locally for now, and open the PR for the code only."

**Documents (10/26)**

- [Dongje, 10-26] "The Iteration 3 plan is merged into the wiki. Update dev and check, starting from PR #108, whether anything has to be fixed against the documents. If not, put the changes into the documents." (The code already matched FR-4.6; the screen in R&S 6.3, Figure 2's wireframe, Design 2.6.3, and the Testing Documentation's state-based partitions were brought to the pager.)

[#108]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/108
[#114]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/114
