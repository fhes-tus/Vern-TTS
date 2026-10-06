# Pending follow-up — 3 October 2026

Implemented and verified; see the latest PROGRESS.md entry. The reported disappearing-note cause was not reproduced; thumbnail stability/fallback handling was added and tested.

Authorized on 3 October: implement this follow-up. Editor margin reduction applies only to open notes; 4 dp was an estimate. The Notes tear stretches horizontally with pointed facing edges only during separation, settling back into rounded pills. The player collapses left in its existing row; expansion smoothly raises action pills and collapse returns them.

- Closed notes: place the date and attachment badges immediately above the link footer, removing the excess gap.
- Notes search: replace the always-visible field with a search icon immediately before the overflow menu. Reuse Library's animated, theme-shaped expanding/collapsing search behavior. Preserve dynamic horizontally scrollable filters.
- Attachment thumbnails: investigate the reported disappearing note when this preference is enabled. Preserve the note, media and settings; reproduce before fixing.
- Reduce excessive open-editor text margins; 4 dp was an estimate, not a fixed requirement.
- Notes navigation detachment uses a horizontal stretching/pinching tear, with pointed facing edges only during motion and rounded resting shapes.
- Player: reduce its height by another 2 dp; initially use about 80% opacity. Real glossy/glass treatment remains deferred to the theme work.
- Player controls: match the supplied home-widget reference, with a prominent circular play/pause background and subtler circular previous/next backgrounds, adapted to the active theme.
- Add a left-pointing collapse affordance beside the cover. Collapse left in the existing player row into a compact circular waveform control, animated while playback is running; tap to expand. Expansion raises action pills, collapse returns them, using synchronized animation. Preserve playback/session state.
- Add taking a photo with the camera as a Notes attachment option.

Reference files were supplied in the user message: Screenshot_20261003_124940_One UI Home.png, Screenshot_20261003_125321_Vern TTS.png, Screenshot_20261003_125453_Vern TTS.png, 20261003_125700.jpg, and 20261003_125644.jpg (under the user's Local/Temp directory).
