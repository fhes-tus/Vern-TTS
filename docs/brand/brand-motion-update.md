# Brand and motion update

The big-head V from Image 1 is now the app-wide brand mark. Launcher, notification,
widget, reader and About artwork use cream (#FFF6E6) on indigo (#4540D8).
The header combines a transparent V with “ern”; brand marks are charcoal (#3C3C40) on light
surfaces and cream on dark surfaces. The drawer uses the same mark.

`tools/build_vern_v_brand.ps1` traces the selected reference, smooths the outline,
and generates Android vectors and matching PNGs. Its three audio slots remain
transparent. Run it with Windows PowerShell and `-Reference
docs/brand/icon-mode-previews/image-1-light.png` to regenerate the assets.

The hero and classics covers share Reading Insights’ 4.2-second float and tilt.
Today’s Reading keeps its original text footprint and card surface, with neutral
layered book-page artwork and a six-second left-to-right slide of the full stat display between
recorded time, weekly reading days, goal completion and total time since Monday.
A clock, seven weekly spines, a goal arc and a flowing line distinguish the four
views. The hidden sizing page uses the actual displayed typography to prevent
clipped captions with custom fonts. The canvas has an explicit height.
The page artwork fans gently over a 12-second cycle, with faint typeset lines and
a bookmark detail in neutral theme ink. Decorative motion stops with Reduce Motion; the carousel
pauses when the activity is not resumed.

The collapsed player follows a 50 ms PCM loudness envelope, smoothed in the UI.
Built-in voices use actual AudioTrack playback frames. Android TTS uses engine
audio callbacks and corrects timing with word frames where supplied. Engines that
do not expose audio data show quiet bars rather than simulated loudness.

Navigation uses official Material Symbols Rounded Home, Newsstand and Note Stack
icons, plus a sliding selection capsule. Tap areas and label sizes remain fixed.
Selection position is shared across split/combined capsules so leaving Notes
travels from the right, including transitions to Library and Study. Notes &
Annotations uses the same Bold title weight as Library and Study Hub.
Android 12+ uses an animated vector in the system splash itself (640 ms reveal,
140 ms exit), replacing the static opening icon and separate Compose entrance.
Older Android versions retain the Compose entrance. Reduced motion skips the exit
hold and the Compose entrance; the system animation obeys Android animator settings.
The notification small icon is the same big-head V with transparent audio cutouts,
rendered as a white silhouette that Android tints for its notification surface.

Validation: debug APKs built successfully; all three SpeechAmplitudeTest unit
tests and six focused device UI tests passed on Android 16. UI checks cover fixed
card bounds, goal controls, light/dark header layout with large text, drawer exit,
startup completion and reduced motion. Light/dark captures are in device-previews.

Preview APK: output_apks/Vern-2026-10-13-reading-pages-preview-arm64.apk.
The installed preview uses package com.veritas.reader.preview.
