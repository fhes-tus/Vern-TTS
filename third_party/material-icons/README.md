# Selected Material Icons Extended definitions

The Android app carries 199 selected icon definitions from
`androidx.compose.material:material-icons-extended-android:1.7.8` instead of the
complete extended dependency. The original Kotlin files live under
`app/src/main/java/androidx/compose/material/icons/` and retain their upstream
packages, copyright headers, vector paths, style, caching and auto-mirroring.
App imports and icon call sites therefore stay unchanged.

`material-icons-core` remains a dependency: it supplies `Icons`, the core icon
set and the shared vector-building helpers. Do not add the complete extended
dependency alongside these local files, which would define the same classes.

`manifest.json` records the original source archive hash, each selected entry,
destination and SHA-256. The Apache license and upstream notices are included in
the app's `assets/licenses/MaterialIconsExtended-*` files. Selection includes
all Android source sets, explicit imports, conservative wildcard matches and
dependencies of upstream deprecated/mirrored definitions.

The extraction does not redesign icons or change actions. It is not a promise
of identical build output, a measured APK-size saving or a runtime speedup.

To check the local files against the same upstream sources:

```powershell
./tools/vendor_material_icons.ps1 -ExtendedSources <extended-1.7.8-sources.jar> -CoreSources <core-1.7.8-sources.jar> -CheckOnly
```

To include another extended icon, add its normal import/use and run the command
without `-CheckOnly`. Existing differing icon files are rejected rather than
overwritten. The script does not delete old files. Review an upstream version
change separately; the manifest currently describes version 1.7.8.
