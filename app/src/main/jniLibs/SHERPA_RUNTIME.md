# Sherpa Android runtime

The app packages the JNI and ONNX Runtime libraries for ARM64 and ARMv7 from
[Sherpa-ONNX v1.13.8](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8).
The separate C/C++ entry points left in the source folders are excluded from APKs.
Only JNI is used by the Kotlin bindings.

Official archive: `sherpa-onnx-v1.13.8-android.tar.bz2`.

Archive SHA-256: `2ff63469a71cb6009aa2e3ed5f4a670f8abdcbe4bb9ffd23776afc792a6b4f44`.

Installed file SHA-256:

| ABI | Library | SHA-256 |
| --- | --- | --- |
| arm64-v8a | libonnxruntime.so | 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1 |
| arm64-v8a | libsherpa-onnx-jni.so | 3c6492ea91ea68fd3b72b9495efdde6dd913f6b58c387e3b07389cf335f4d26f |
| armeabi-v7a | libonnxruntime.so | 7a8f17a707e52e0ac3a026be319272876d9eae61f4bb19585292772d785f7744 |
| armeabi-v7a | libsherpa-onnx-jni.so | 3d6b6daf6d23162dceb1df91ac075a42a09b60fafb5f9400cba22562f10a1dc0 |

This version includes the [Piper phoneme framing correction](https://github.com/k2-fsa/sherpa-onnx/pull/3770)
which adds a missing padding token after the start token. Voice models and download
sizes remain unchanged. The change is not proof that all background hiss is fixed.

Compatibility checks: all ten local native configuration classes match the tagged
upstream field names/types/order; all six used JNI functions are exported for both
ABIs; ELF dependencies require only ONNX Runtime and Android system libraries.
Both packaged libraries use 16 KiB ELF load alignment. Model inference on an Android
device still requires a device test; no device was connected during this update.
