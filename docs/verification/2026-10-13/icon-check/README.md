# Samsung icon check — 13 October 2026

Built and installed the signed debug package com.veritas.reader.iconcheck (launcher label Vern Preview) on Samsung SM-A175F, serial RZGL403LCNW. Version remains 2.6.1 / 41. The production package was not replaced.

- Debug assembly and ARM64 installation passed.
- launcher.png shows the fresh debug launcher icon under Samsung One UI's rounded-square mask. The full cream V and slots are visible with padding; no clipping observed.
- splash-2.png captures the cold-start splash. The full logo is centered and visible without clipping. Android reported a successful cold launch.
- This validates normal launcher and splash appearance on this device. Themed monochrome appearance and other launcher masks/devices were not tested on the phone.
- Safe-circle geometry and three simulated masks were checked separately by tools/check_vern_icons.ps1.

## Updated 52 dp preview

The user selected a 52 dp logo. Rebuilt and updated the same isolated debug package on 13 October. Safe-circle check passed at 29.195 dp maximum radius. launcher-52.png shows the updated Vern Preview entry with additional padding beside the existing production Vern icon. A successful cold launch was confirmed (TotalTime 1990 ms); the preview was left open on the phone. Final bundle/release packaging is held for the user's forthcoming additional changes.
