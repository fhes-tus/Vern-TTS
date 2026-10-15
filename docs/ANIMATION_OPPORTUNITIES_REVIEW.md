# Final animation opportunity review — 9 October 2026

**Subsequent user-authorized implementation:** the user explicitly requested both opportunities, playback startup feedback and a release build after this search-only review. Those changes are recorded separately in [playback feedback and release check](PLAYBACK_FEEDBACK_RELEASE_CHECK.md). The discovery report below records the original observations and gates.

Search only, following the user's pasted **Finding Animation Opportunities** checklist. No animation source changes were made for this review. The app uses Android Compose/Material 3 and its shared `VeritasMotion` scheme; no web animation package or hover behavior is applicable. Frequency estimates below are design assumptions, not telemetry. This review identifies missing transitions, rather than judging existing animations.

## Part 1 — Opportunities

| # | Location | Today | Purpose | Frequency | Suggested motion |
| --- | --- | --- | --- | --- | --- |
| 1 | `app/src/main/java/com/veritas/reader/ui/screens/VoiceStudioUi.kt:639` | Downloading switches directly to the Installed check and label. | State indication | Rare per voice installation | Fade only the status content from alpha 0 to 1 with `VeritasMotion.effectsFast()` — `spring(dampingRatio = 1f, stiffness = 3800f)`. Keep its slot and neighboring controls stationary; accept a visible transition only within a 200 ms budget. Reduced motion uses the same gentle opacity change, with no scale or displacement. Installed state and delete action become usable immediately. |
| 2 | `app/src/main/java/com/veritas/reader/ui/screens/HomeTodayReadingCard.kt:79` | The fixed progress-ring center switches from a percentage to a checkmark immediately. | State indication | Rare: a daily goal threshold | Crossfade only the center glyph, alpha 1→0 and 0→1, using the same `effectsFast()` spring and a 200 ms budget. Keep the 64 dp ring, time text and layout stationary. Animate the threshold change while visible; do not replay a celebration on every Home visit. Reduced motion retains only this fade. |

**Timing vocabulary:** the repository's shared effects tokens are physical springs, not fixed-duration CSS curves. Their exact parameters are at `ui/VeritasMotion.kt:85–86`; settling duration depends on displacement, velocity and visibility threshold. The 200 ms figures above are acceptance ceilings, not invented existing duration tokens or measured results. Use the shared token and verify the proposed transition on device before implementation acceptance. Do not add a parallel easing system to force the checklist's web examples onto Compose.

**Gate results:** both candidates pass frequency (rare), purpose (State indication), speed (small opacity-only transitions within the stated ceiling), and function (status glyphs change without moving readable data). No bounce, delayed interaction, hold-to-confirm, stagger or repeating effect is proposed. Voice installation must remain a verified file/model state; animation cannot disguise a failed download. Neither proposal has been implemented or perceptually tested.

## Part 2 — Rejected candidates

- `ui/screens/LibraryBottomNavBarUi.kt:41` — additional route entrances, tab bounce or glass pulses. **Frequency gate:** core navigation is repeated throughout the day; extra choreography adds friction.
- `ui/screens/ReaderTextFormattingHelper.kt:187` and `ui/screens/ReaderPlayerPanelUi.kt:501` — moving highlights or animated scrubbing values. **Function gate:** users are reading text or directly controlling position; decorative movement makes these targets less stable.
- `ui/screens/StudyHubOverview.kt:50` — stagger every study collection card on entry. **Frequency/function gates:** this is a frequently revisited working dashboard; all collections should be immediately available.
- `ui/screens/LibraryNotesTab.kt:909` — animated travel between pinned/unpinned groups in the two-column Notes layout. **Function gate:** it would move neighboring note text while users are choosing material. No new list choreography is justified.
- `ui/screens/LibraryStudyTab.kt:287` — slide or animated-height entrance for the AI generation banner. **Function gate:** resizing the lazy-list header would move the study material underneath it; preserving reading position takes priority.

All hunt classes were checked: feedback gaps have Material indications and a shared press helper (`ui/VeritasMotion.kt:155`); teleporting status yielded the two candidates; menus/dialogs use Material components (`LibraryHeaderControls.kt:95,118`); group entrances were rejected above; reader gesture panels already declare anchored drag, velocity and spring/decay behavior (`ReaderPlayerPanelUi.kt:126`, `DocPlayerPanelUi.kt:153`); onboarding already has a dedicated celebration surface (`OnboardingCelebrationUi.kt:131`). Those existing mechanisms are evidence that no additional seam needs a suggestion, not a review of their quality.

## Part 3 — Verdict

Vern needs very little additional motion for this pass. Its shared motion vocabulary and standard components cover most interaction seams; daily reading benefits from restraint. The voice installation status fade has the highest leverage because it makes the successful end of a long operation easier to notice. The goal checkmark is optional. Suggested future handoff: `improve-animations plan fade verified voice installation status using VeritasMotion.effectsFast, a stationary slot, and reduced-motion opacity handling`. This is a handoff description only; no such skill was invoked or implementation started.
