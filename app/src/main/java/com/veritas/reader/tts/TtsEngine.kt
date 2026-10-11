package com.veritas.reader.tts

/**
 * Common interface for all TTS engines (System TextToSpeech, Kokoro, Piper).
 * Keeps playback and UI code agnostic of the underlying TTS synthesis implementation.
 */
interface TtsEngine {
    /**
     * Synthesize a single sentence into raw PCM 16-bit ShortArray audio samples.
     * Returns null if synthesis fails or engine is not ready.
     */
    suspend fun synthesize(sentence: String, rate: Float = 1.0f, pitch: Float = 1.0f): ShortArray?

    /**
     * Returns whether the engine is initialized and ready to synthesize audio.
     */
    fun isReady(): Boolean

    /**
     * Sample rate of the synthesized PCM audio in Hz (e.g. 22050 Hz or 24000 Hz).
     */
    val sampleRate: Int

    /**
     * Release all native and system resources.
     */
    fun shutdown()
}

/** Model construction happens with the buffer's background synthesis, not on UI/service main. */
internal class DeferredTtsEngine(factory: () -> TtsEngine) : TtsEngine {
    private val delegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, factory)
    override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? =
        delegate.value.synthesize(sentence, rate, pitch)
    override fun isReady(): Boolean = delegate.value.isReady()
    override val sampleRate: Int get() = delegate.value.sampleRate
    override fun shutdown() { if (delegate.isInitialized()) delegate.value.shutdown() }
}
