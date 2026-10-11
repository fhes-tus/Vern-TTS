"""Compare Marcus int8, int8 at lower latent noise, and fp16 using identical text.

Model archives and the isolated sherpa-onnx installation live in ignored .checks.
This diagnostic does not alter the app's model catalog, settings or DSP.
"""
import json
import sys
import tarfile
import time
import wave
import ctypes
import argparse
from pathlib import Path

import numpy as np

root = Path(__file__).resolve().parents[1] / ".checks"
parser = argparse.ArgumentParser()
parser.add_argument("--variant", choices=("all", "int8", "default", "fp16"), default="all")
parser.add_argument("--runtime", choices=("1.13.4", "1.13.8"), default="1.13.8")
args = parser.parse_args()
runtime_directory = "tts-python" if args.runtime == "1.13.8" else "tts-python-1.13.4"
sys.path.insert(0, str(root / runtime_directory))
# Windows can load its old system32 ONNX DLL first. Preload only our isolated matching DLL.
runtime_dll = root / "tts-python" / "onnxruntime" / "capi" / "onnxruntime.dll"
if sys.platform == "win32" and runtime_dll.is_file():
    ctypes.WinDLL(str(runtime_dll))
import sherpa_onnx

models = root / "tts-models"
output = root / "audio-analysis"
output.mkdir(parents=True, exist_ok=True)
text = "It was a quiet morning in October. The students opened their books and began to read. A good story can take us somewhere new, while a familiar voice makes each sentence easy to follow."
report = {"runtime": sherpa_onnx.__version__, "text": text, "variants": []}
for kind, noise, label in (("int8", .667, "marcus-int8-default"), ("int8", .25, "marcus-int8-low-noise"), ("fp16", .667, "marcus-fp16-default")):
    if args.variant == "default" and (kind != "int8" or noise != .667):
        continue
    if args.variant not in ("all", "default") and kind != args.variant:
        continue
    if args.runtime != "1.13.8":
        label += f"-runtime-{args.runtime}"
    directory = models / kind
    directory.mkdir(exist_ok=True)
    if not list(directory.rglob("*.onnx")):
        with tarfile.open(models / f"marcus-{kind}.tar.bz2") as archive:
            archive.extractall(directory, filter="data")
    model = next(directory.rglob("*.onnx"))
    tokens = next(directory.rglob("tokens.txt"))
    espeak = next(path for path in directory.rglob("espeak-ng-data") if path.is_dir())
    config = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(model), tokens=str(tokens), data_dir=str(espeak),
                                               noise_scale=noise, noise_scale_w=.8, length_scale=1),
        num_threads=2, provider="cpu"), max_num_sentences=1)
    started = time.monotonic()
    try:
        engine = sherpa_onnx.OfflineTts(config)
    except Exception as error:
        result = {"label": label, "error": str(error), "model_bytes": model.stat().st_size}
        report["variants"].append(result)
        print(json.dumps(result), flush=True)
        (output / f"model-comparison-{args.variant}-{args.runtime}.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        continue
    generated = engine.generate(text, sid=0, speed=1)
    samples = np.asarray(generated.samples)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    filename = output / f"{label}.wav"
    with wave.open(str(filename), "wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(generated.sample_rate)
        wav.writeframes(pcm.tobytes())
    result = {"label": label, "noise_scale": noise, "sample_rate": generated.sample_rate,
              "duration_seconds": len(samples)/generated.sample_rate, "peak": float(np.max(abs(samples))),
              "rms": float(np.sqrt(np.mean(samples**2))), "synthesis_seconds": time.monotonic()-started,
              "model_bytes": model.stat().st_size}
    report["variants"].append(result)
    print(json.dumps(result), flush=True)
    del engine
    (output / f"model-comparison-{args.variant}-{args.runtime}.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
