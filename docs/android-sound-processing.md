# Voice shaping and sound processing

Open **Voice and language** and use **Voice shaping** to compare the app's tone profile with the voice's original tone. The switch is saved and included in backups. Speed and pitch stay as selected. Existing settings keep shaping enabled.

- **Vern Studio / Kokoro:** shaping applies the existing narrow model-repair filters, brightness shelf and fixed gain. Off bypasses those stages. Compatible speakers reuse a loaded model within their existing owner; service, preview and export keep separate mutable engines.
- **Vern Lite / Piper:** there is no added tone EQ. The updated pitch and export conversion use band-limited resampling, and offline output retains float headroom until final peak protection.
- **Android / Google voices:** shaping requests a managed Android EQ session for native reading and previews. Availability and response depend on the engine and device. Turning the switch off releases the app's effect. Device-wide or headphone sound settings remain separate.

Offline preview, reading and export follow the selected shaping setting. Exports convert to their output rate before final quantization. Android live playback uses the provider's native speech path; an exported file does not reproduce a proprietary device EQ curve exactly. The app does not retrain Google's voice or gain access to Cloud SSML controls through this switch.

The new peak stage attenuates overloads; it does not force every sentence to the same loudness. It preserves clip duration and quiet endings. Its ceiling describes stored samples, and is not a certified true-peak guarantee. Turning shaping off retains offline peak protection.

These changes address avoidable aliasing, intermediate clipping and repeated model loading. A basic voice's pronunciation and expression still depend on its underlying engine/model. Kokoro can remain slower than playback on a mid-range phone; Piper or a suitable Android voice can be a better fit when continuous reading speed matters.
