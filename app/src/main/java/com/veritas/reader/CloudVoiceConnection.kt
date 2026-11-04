package com.veritas.reader

import android.content.Context

internal class PreparedCloudVoice(val config: CloudVoiceConfig, val credential: String) {
    override fun toString(): String = "PreparedCloudVoice(redacted)"
}

internal object CloudVoiceConnection {
    /** Preview never replaces the account used by reading. Activation is the explicit commit point. */
    fun prepare(context: Context, config: CloudVoiceConfig, enteredKey: String, activate: Boolean): PreparedCloudVoice {
        config.checked()
        val previous = ApiCredentialStore.get(context,config.credentialName)
        val credential = CloudCredentialRules.normalize(enteredKey.takeIf { it.isNotBlank() } ?: previous,
            config.provider != CloudVoiceProvider.CUSTOM)
        if(activate) {
            var replaced = false
            try {
                if(enteredKey.isNotBlank()) {
                    ApiCredentialStore.save(context,credential,config.credentialName)
                    replaced = true
                }
                CloudVoiceStore.save(context,config)
            } catch(_: Exception) {
                if(replaced) runCatching { ApiCredentialStore.save(context,previous,config.credentialName) }
                throw CloudSpeechException(R.string.cloud_error_config)
            }
        } else CloudVoiceStore.saveProfile(context,config)
        return PreparedCloudVoice(config,credential)
    }
}
