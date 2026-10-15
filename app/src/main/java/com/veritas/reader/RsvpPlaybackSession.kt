package com.veritas.reader

import android.content.Context

internal interface RsvpTransport {
    fun enter()
    fun play(sentence: Int, offset: Int, rate: Float)
    fun accepts(position: SpeechPosition): Boolean
    fun pause()
    fun close()
}

/** RSVP borrows the existing player. It creates no second TTS instance or audio output. */
internal class RsvpPlaybackSession(context: Context, private val documentId: String?) : RsvpTransport {
    private val context = context.applicationContext
    private val memory = if (PlaybackStateStore.activeDocumentId != documentId)
        documentId?.let { DocumentRepository(this.context).loadDocVoiceMemory(it) } else null
    private val originalRate = memory?.first ?: PlaybackStateStore.rate
    private val originalPitch = memory?.second ?: PlaybackStateStore.pitch
    private val originalQueue = PlaybackStateStore.autoPlayQueue
    private var requested = false
    private var closed = false
    private var previousUtterance: String? = null
    override fun enter() { sendPlaybackIntent(context, PlaybackActions.ACTION_PAUSE) }
    override fun play(sentence: Int, offset: Int, rate: Float) {
        if (closed || documentId == null) return
        previousUtterance = PlaybackStateStore.speechPosition?.utteranceId
        requested = true
        PlaybackStateStore.autoPlayQueue = false
        sendPlaybackIntent(context, PlaybackActions.ACTION_PLAY, documentId, sentence,
            rate = rate, pitch = originalPitch, charOffset = offset, transientSettings = true)
    }
    override fun accepts(position: SpeechPosition): Boolean = requested && !closed &&
        position.documentId == documentId && position.utteranceId != previousUtterance
    override fun pause() {
        if (!closed && requested && (PlaybackStateStore.activeDocumentId == documentId || PlaybackStateStore.isPreparingFor(documentId)))
            sendPlaybackIntent(context, PlaybackActions.ACTION_PAUSE, documentId)
    }
    override fun close() {
        if (closed) return
        if (requested) sendPlaybackIntent(context, PlaybackActions.ACTION_PAUSE, documentId,
            rate = originalRate, pitch = originalPitch, endTransientSettings = true)
        closed = true
        if (PlaybackStateStore.activeDocumentId == documentId) {
            PlaybackStateStore.rate = originalRate
            PlaybackStateStore.pitch = originalPitch
        }
        PlaybackStateStore.autoPlayQueue = originalQueue
    }
}
