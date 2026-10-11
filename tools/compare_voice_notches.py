"""Make a level-matched diagnostic A/B pair for measured tones. Does not change app DSP."""
import math
import sys
import wave
from pathlib import Path

import numpy as np

source, output = Path(sys.argv[1]), Path(sys.argv[2])
output.mkdir(parents=True, exist_ok=True)
with wave.open(str(source), "rb") as wav:
    rate, channels, width = wav.getframerate(), wav.getnchannels(), wav.getsampwidth()
    if channels != 1 or width != 2:
        raise ValueError("Expected mono PCM16")
    wav.setpos(int(rate * 15))
    original = np.frombuffer(wav.readframes(int(rate * 18)), dtype="<i2").copy()

filtered = original.astype(np.float64)
for hz in (4480.0, 8960.0):
    angle = 2 * math.pi * hz / rate
    alpha = math.sin(angle) / (2 * 80.0)
    a0 = 1 + alpha
    b0, b1, b2 = 1/a0, -2*math.cos(angle)/a0, 1/a0
    a1, a2 = b1, (1-alpha)/a0
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(filtered)):
        x0 = filtered[i]
        y0 = b0*x0 + b1*x1 + b2*x2 - a1*y1 - a2*y2
        x2, x1, y2, y1 = x1, x0, y1, y0
        filtered[i] = y0

for name, samples in (("marcus-original.wav", original), ("marcus-tone-comparison.wav", np.clip(np.rint(filtered), -32768, 32767).astype("<i2"))):
    with wave.open(str(output / name), "wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(rate)
        wav.writeframes(samples.tobytes())
print("Original RMS:", float(np.sqrt(np.mean(original.astype(np.float64)**2))))
print("Filtered RMS:", float(np.sqrt(np.mean(filtered**2))))
