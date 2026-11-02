Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

## The settings screen ([#123])

- [Sungmin, 11-02] "I'll build only the settings screen of this issue and open a PR. First pull `dev` and make a new branch, then write a plan for the settings screen. Also think about what belongs on it and tell me first. Don't implement yet; start with the plan." (`dev` pulled, `feat/123-settings` made. #123 is assigned to Junhyeong, so the plan noted that the team should agree on who takes the settings screen. The plan: the rows the issue asks for, and notifications, withdrawing the AI agreement, the Pix account, and *About Pix*; no shutter-sound switch, since Korean phones always make the sound, and no grid switch, since the grid is a Must (FR-1.9); the latency from the `ping` both phones already send.)
- [Sungmin, 11-02] "Add the extra rows, add black to the guide colors, and do the rest as you recommend." (`AppSettings` in DataStore, the gear on Camera hidden during a session and the scene photo, the guide's color as a filter and another thickness traced again from the mask, `SessionManager.latencyMs` from the pongs, and the agreement withdrawn through `GenerationViewModel`. Robolectric's Android 16 kept no per-app language, so the language goes through `AppLanguage`, with a fake in the tests. Each fix was checked by breaking it once. Flow 7 drawn, and the gear added to Flows 1, 2, and 4.)

[#123]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/123
