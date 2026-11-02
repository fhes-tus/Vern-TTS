# Android release keep rules

Release builds enable R8 minification and resource shrinking. The authoritative
rules are in app/proguard-rules.pro; dependencies and AAPT add their own rules.

Preserve the runtime contracts that ordinary call graphs cannot describe:

- WorkManager database and generated implementation names, with the public
  WorkDatabase_Impl zero-argument constructor used by Room reflection.
- InputMerger subclass names and public zero-argument constructors. A class-only
  keep removed these constructors under strict full mode and caused imports to
  fail before their worker ran.
- Persisted worker names and their Context/WorkerParameters constructors.
- Glance callback constructors and widget/receiver entry points.
- Sherpa JNI binding fields, constructors and native methods.
- Bouncy Castle's dynamically registered algorithms and Firebase registrar
  discovery constructors.

Current PDFBox/Harmony, ML Kit and AndroidX PDF package safeguards remain.
Narrow them only with actual minified extraction/viewer coverage. Persistence
that uses explicit JSONObject/SQLite keys does not need every model field name
kept.

Use the framework-only [release checks](../tools/r8-smoke/README.md) to verify
startup, merger construction, actual TXT/PDF/OCR imports, retained documents and
Glance rendering. The probe adds no application symbols or test-runtime keeps
to the target. Debug success alone cannot establish release correctness.

Downloaded voice weights and native libraries are separate from DEX shrinking.
Keep exact release binaries, mappings and diagnostic captures locally rather
than committing generated artifacts. Local APK signing can differ from Google
Play signing; follow [update compatibility](UPDATE_COMPATIBILITY.md).
