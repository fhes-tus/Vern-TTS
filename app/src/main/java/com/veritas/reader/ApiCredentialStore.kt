package com.veritas.reader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound credentials, excluded from cloud backup and device transfer. */
internal object ApiCredentialStore {
    private const val ALIAS = "vern_ai_credentials_v1"
    private const val PREFS = "veritas_reader_secrets"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun get(context: Context): String {
        migrate(context)
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("credential", null) ?: return ""
        return runCatching {
            val parts = encoded.split(':')
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("") // A missing device key requires entering the credential again.
    }

    @Synchronized fun save(context: Context, value: String) {
        val secrets = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = secrets.edit()
        if (value.isBlank()) editor.remove("credential") else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
            editor.putString("credential", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
        check(editor.commit()) { "Could not save the API credential." }
        removeLegacy(context)
    }

    @Synchronized fun migrate(context: Context) {
        val legacy = context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE)
        if (!legacy.contains("ai_api_key") && !legacy.contains("gemini_api_key")) return
        val secrets = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = legacy.getString("ai_api_key", "").orEmpty().ifBlank { legacy.getString("gemini_api_key", "").orEmpty() }
        if (!secrets.contains("credential") && value.isNotBlank()) save(context, value) else removeLegacy(context)
    }

    private fun removeLegacy(context: Context) {
        check(context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE).edit()
            .remove("ai_api_key").remove("gemini_api_key").commit()) { "Could not migrate the legacy API credential." }
    }
}
