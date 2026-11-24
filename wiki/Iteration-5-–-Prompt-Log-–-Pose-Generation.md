Back to [[AI Collaboration Report – Iteration 5|AI-Collaboration-Report-–-Iteration-5]]

## Auto and the options on *Generate poses* ([#274])

- [Dongje, 11-24] "For #274: is there a UI where everything still happens on one screen, as now, but that stresses 'automatic'? The drafts above look too much like AI." (The agent drew three drafts in the app's colors, without sparkle icons or "AI suggests" lines, with Auto drawn as a setting's value: Auto as the empty field's value, an Auto / Type switch, and option chips with Auto first. It recommended the first.)
- [Dongje, 11-24] "Can 1 and 3 be combined? 1 says Auto is the best choice, and options like 3 are good too. The options should be concrete, like a V sign." (One draft: the field says Auto, and chips of concrete poses under it only write into the field, so the sync, the filter, and the history stay as they are. An example list for each situation.)
- [Dongje, 11-24] "Change the line under Auto to 'Automatically makes the four poses that suit this photo's background best'. Replace 'leaning on a wall' and 'jump' with 'arms up' and 'thumbs up'. Take 'Same person and place' off the photo's card, and put only 'up to 30 s' where it fits." (The agent drew it and pointed out that a request stops at 60 s, not 30.)
- [Dongje, 11-24] "Just remove the 30 s, and review it yourself." (The review found that a tapped chip moved, since the field was higher while it said Auto; it fixed the height. It proposed no situation tag for Auto, no second listing of a description that is a chip, and chips matched by the parts between commas.)
- [Dongje, 11-24] "Go with adding one after another, replace 'jump' with 'arms up', and start building. Also make a tap on the photo at the top left show it large, so the user can check it." (Built test-first: `PoseOptions`, the form, both phones' screens, and `ScenePhotoFragment`. The large photo is also a step of [#296].)
- [Dongje, 11-24] "While it says Auto, hide the common poses and the descriptions used before, and show them only once the field is tapped. Change the line to 'Tap the field to describe the pose you want' and make it stand out a little." (What a tap opens stays open when the keyboard closes, or the chips the keyboard covered would go with it; *Back to Auto* folds it. The line is in a stronger style with a pencil in the accent.)

## The scene photo straight to *Generate poses* ([#296])

- [Dongje, 11-24] "#296 overlaps this work, so let's build what is left of #296 here too. For the chips, run a generation once each." (The agent ran one image for each of 20 chips through a local server: 19 gave the pose with the scene kept, and "turn and look" on a landmark turned the view around instead. The run cost 0.27 dollars, 0.02 over the limit the user had set.)
- [Dongje, 11-24] "Give me the draft first: how it differs from now." (A before-and-after picture: no step that asks to use the photo, the photo held for the moment the phone looks for a person, then shrinking into *Generate poses*; without a person the camera stays and says so; Back on *Generate poses* shoots again; four steps in the flow's bar.)
- [Dongje, 11-24] "Fix the character count in the Korean wireframe, and build it as drawn." (`CHECKING` in place of the review, `sceneArriving`, the panel for no person, `showArriving`; the renderer takes a count listed for Korean. A Camera test without a way on threw inside a collector and still passed, which a later test class reported; those tests now hold the check or attach navigation.)

[#274]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/274
[#296]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/296
