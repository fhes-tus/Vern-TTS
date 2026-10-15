# Final local release — 10 October 2026

The user authorized building the release, updating the original project and cleaning generated worktree files. Signed **2.6.1 / build 41** APKs were assembled from the final source, including system-following Midnight, the heavy neutral text-only Vern header, prompt sidebar collapse, the selected book/earbuds launcher recolored to Midnight Light indigo blue (#4338CA), shared Home hero graphics and the earlier fixes.

| ABI | MiB | SHA-256 |
| --- | ---: | --- |
| universal | 70.02 | `3a3f1c7ffa25fbcaba9634cad6419455b199e029f17c934026127b62652d279b` |
| armeabi-v7a | 44.21 | `98f38f5cd5da4c784582251702fd509e1ea0b9d7fd5013f46be1b29fd1b8f191` |
| arm64-v8a | 52.07 | `54323109a4baf41b7550cb3d71f8f6578cfd26ca8f8994aecf6e16f42906a8e3` |

Package: `com.veritas.reader`. All signatures match the previous release certificate (`3b143a77a70253305a496595432461b018ffcb3792e82a96905bd10243424db8`). All APKs pass 16 KiB ZIP alignment, and their launcher foreground pixels match the final blue source asset. Required JNI/ONNX libraries also pass ELF load alignment: source hashes match Gradle's merged inputs, APK hashes match the stripped outputs, and every allocated runtime section (code/data/symbols and addresses) remains unchanged by stripping. Raw source/APK hashes differ because Gradle removes non-runtime debug symbols. Both unused C/C++ entry points remain excluded. R8 mapping/configuration/seeds/usage and generated baseline profiles are retained in `output_apks` alongside checksums and metadata.

Release assembly and its required tasks passed. Six theme catalog tests pass; phone checks pass the four-palette neutral 32sp/900 header, compact/large-font/reduced-motion hero, and menu close/reopen with immediate Home interaction. Evidence is retained under `docs/verification/2026-10-10`. The first menu test batch had a paused-clock disposal fixture issue; the focused corrected regression passes. Existing real-model/human listening and recipient acceptance limits remain documented in `FUTURE_UI_PLAN.md`.

No publication or production app installation was performed. The original project is `C:/Users/wwwfe/Downloads/Veritas Reader Android`; it receives the final source and release files, with overwritten files backed up. Important source, resources, tests, documentation, signing/local build configuration and Git metadata are preserved during cleanup.
