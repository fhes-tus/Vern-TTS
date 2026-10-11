"""Inspect an exported PCM16 voice WAV. Requires NumPy; plots are optional (Matplotlib).

Usage: python tools/analyze_voice_wav.py input.wav output_directory
No resampling, gain normalization, or modification of the source recording.
"""
import json
import sys
import wave
from pathlib import Path

import numpy as np


def analyze(source, output):
    output.mkdir(parents=True, exist_ok=True)
    with wave.open(str(source), "rb") as wav:
        channels, width, rate, frames, _, _ = wav.getparams()
        if width != 2:
            raise ValueError("Expected PCM16 WAV")
        pcm = np.frombuffer(wav.readframes(frames), dtype="<i2").reshape(-1, channels)
    signal = pcm.astype(np.float64).mean(axis=1) / 32768
    n = 8192
    hop = 4096
    blocks = np.lib.stride_tricks.sliding_window_view(signal, n)[::hop]
    rms = np.sqrt(np.mean(blocks ** 2, axis=1))
    power = abs(np.fft.rfft(blocks * np.hanning(n), axis=1)) ** 2
    frequencies = np.fft.rfftfreq(n, 1 / rate)
    audible = rms > 1e-4
    quiet = audible & (rms <= np.quantile(rms[audible], .2))

    def peaks(selected):
        average = power[selected].mean(axis=0)
        candidates = []
        for i in range(35, len(average) - 35):
            if frequencies[i] < 70 or average[i] <= max(average[i-1], average[i+1]):
                continue
            neighbors = np.r_[average[i-30:i-3], average[i+4:i+31]]
            prominence = 10 * np.log10(max(average[i], 1e-20) / max(np.median(neighbors), 1e-20))
            per_frame = power[selected, i]
            background = np.median(power[selected, i-30:i+31], axis=1)
            persistence = np.mean(per_frame > background * 4)
            if prominence > 8:
                candidates.append({"hz": round(float(frequencies[i]), 2), "above_neighbors_db": round(float(prominence), 2),
                                   "persistent_fraction": round(float(persistence), 3), "power": float(average[i])})
        return sorted(candidates, key=lambda item: item["power"], reverse=True)[:20]

    report = {"source": str(source), "sample_rate": rate, "channels": channels, "duration_seconds": frames / rate,
              "peak": float(np.max(abs(signal))), "rms": float(np.sqrt(np.mean(signal ** 2))),
              "dc_mean": float(signal.mean()), "clipped_samples": int(np.count_nonzero(abs(pcm.astype(np.int32)) >= 32767)),
              "non_silent_windows": int(audible.sum()), "quiet_windows": int(quiet.sum()),
              "overall_narrow_peaks": peaks(audible), "quiet_narrow_peaks": peaks(quiet)}
    (output / "analysis.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        fig, axes = plt.subplots(2, 1, figsize=(12, 7), constrained_layout=True)
        spectrum = 10 * np.log10(np.maximum(power[audible].mean(axis=0), 1e-12))
        axes[0].plot(frequencies, spectrum, linewidth=.7)
        axes[0].set(xlim=(0, rate / 2), xlabel="Frequency (Hz)", ylabel="Relative power (dB)", title="Exported voice spectrum")
        axes[1].imshow(10 * np.log10(np.maximum(power.T, 1e-12)), origin="lower", aspect="auto",
                       extent=(0, len(signal)/rate, 0, rate/2), vmin=-50, vmax=25, cmap="magma")
        axes[1].set(xlabel="Time (seconds)", ylabel="Frequency (Hz)", title="Persistent tones appear as horizontal lines")
        fig.savefig(output / "spectrum.png", dpi=140)
        plt.close(fig)
    except ImportError:
        pass


if __name__ == "__main__":
    analyze(Path(sys.argv[1]), Path(sys.argv[2]))
