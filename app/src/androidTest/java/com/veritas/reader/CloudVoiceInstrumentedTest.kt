package com.veritas.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Intent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CloudVoiceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun unavailableCloudCanContinueWithPhoneAndStopWithoutChangingTheSavedConnection() {
        org.junit.Assume.assumeFalse(PlaybackStateStore.isPlaying)
        val repository = DocumentRepository(context)
        val previousCloud = CloudVoiceStore.load(context)
        val previousVoice = repository.loadVoiceSettings()
        val prefs = context.getSharedPreferences("vern_cloud_voice",0)
        val oldProfile = prefs.getString("profile_CUSTOM",null)
        val oldChars = prefs.getLong("chars_CUSTOM",0)
        val oldRequests = prefs.getLong("requests_CUSTOM",0)
        val doc = repository.createDocument("Cloud fallback test fixture",
            ("This is a local test passage for checking phone voice fallback after an unavailable local endpoint. ").repeat(12),"TXT")
        val config = CloudVoiceConfig(enabled = true,provider = CloudVoiceProvider.CUSTOM,
            endpoint = "https://127.0.0.1:1/v1/audio/speech",model = "test",voice = "test",
            wifiOnly = false,fallbackToPhone = true,timeoutSeconds = 5)
        try {
            repository.saveVoiceSettings(VoiceSettings())
            CloudVoiceStore.save(context,config)
            instrumentation.runOnMainSync {
                context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                sendPlaybackIntent(context,PlaybackActions.ACTION_PLAY,doc.id,0)
            }
            val deadline = android.os.SystemClock.elapsedRealtime()+25_000
            while(android.os.SystemClock.elapsedRealtime()<deadline &&
                !(PlaybackStateStore.isPlaying && !PlaybackStateStore.isPreparing &&
                    PlaybackStateStore.statusMessage == context.getString(R.string.cloud_fallback_active))) {
                android.os.SystemClock.sleep(100)
            }
            assertTrue("Phone fallback never began",PlaybackStateStore.isPlaying && !PlaybackStateStore.isPreparing)
            assertEquals(context.getString(R.string.cloud_fallback_active),PlaybackStateStore.statusMessage)
            assertEquals(config,CloudVoiceStore.load(context))
            instrumentation.runOnMainSync { sendPlaybackIntent(context,PlaybackActions.ACTION_STOP) }
            val stopDeadline = android.os.SystemClock.elapsedRealtime()+5_000
            while(PlaybackStateStore.isPlaying && android.os.SystemClock.elapsedRealtime()<stopDeadline) android.os.SystemClock.sleep(50)
            assertFalse(PlaybackStateStore.isPlaying)
        } finally {
            instrumentation.runOnMainSync { context.stopService(Intent(context,PlaybackService::class.java)); PlaybackStateStore.reset() }
            CloudVoiceStore.save(context,previousCloud)
            prefs.edit().apply { if(oldProfile == null) remove("profile_CUSTOM") else putString("profile_CUSTOM",oldProfile) }
                .putLong("chars_CUSTOM",oldChars).putLong("requests_CUSTOM",oldRequests).commit()
            repository.saveVoiceSettings(previousVoice)
            repository.deleteDocument(doc.id)
        }
    }

    @Test fun credentialsAreDeviceEncryptedAndSeparateFromAiCredentials() {
        val name = "tts_test_${System.nanoTime()}"
        val fakeKey = "test-only-credential-not-a-provider-key"
        try {
            ApiCredentialStore.save(context,fakeKey,name)
            assertEquals(fakeKey,ApiCredentialStore.get(context,name))
            val ciphertext = context.getSharedPreferences("veritas_reader_secrets",0).getString(name,"").orEmpty()
            assertTrue(ciphertext.isNotBlank())
            assertFalse(ciphertext.contains(fakeKey))
            assertEquals("",ApiCredentialStore.get(context,name+"_other"))
            context.getSharedPreferences("veritas_reader_secrets",0).edit().putString(name+"_other",ciphertext).commit()
            assertEquals("Copied ciphertext must not authenticate in a different namespace","",ApiCredentialStore.get(context,name+"_other"))
        } finally { ApiCredentialStore.save(context,"",name); ApiCredentialStore.save(context,"",name+"_other") }
        assertEquals("",ApiCredentialStore.get(context,name))
    }

    @Test fun previewCredentialsCannotReplaceTheAccountUsedByReading() {
        org.junit.Assume.assumeFalse(PlaybackStateStore.isPlaying)
        val prefs = context.getSharedPreferences("vern_cloud_voice",0)
        val oldConfig = prefs.getString("config",null)
        val oldProfile = prefs.getString("profile_CUSTOM",null)
        val active = CloudVoiceConfig(enabled = true,provider = CloudVoiceProvider.CUSTOM,
            endpoint = "https://preview-${System.nanoTime()}.example.invalid/v1/audio/speech",model = "fixture-model",voice = "original")
        try {
            ApiCredentialStore.save(context,"fake-original-key",active.credentialName)
            CloudVoiceStore.save(context,active)
            val previewConfig = active.copy(voice = "preview")
            val preview = CloudVoiceConnection.prepare(context,previewConfig," fake-preview-key ",activate = false)
            assertEquals("fake-preview-key",preview.credential)
            assertFalse(preview.toString().contains("fake-preview-key"))
            assertEquals("fake-original-key",ApiCredentialStore.get(context,active.credentialName))
            assertEquals(active,CloudVoiceStore.load(context))
            assertThrows(CloudSpeechException::class.java) {
                CloudVoiceConnection.prepare(context,previewConfig,"invalid\r\nheader",activate = true)
            }
            assertEquals("fake-original-key",ApiCredentialStore.get(context,active.credentialName))
            assertEquals(active,CloudVoiceStore.load(context))
            CloudVoiceConnection.prepare(context,previewConfig,"fake-preview-key",activate = true)
            assertEquals("fake-preview-key",ApiCredentialStore.get(context,active.credentialName))
            assertEquals(previewConfig,CloudVoiceStore.load(context))
        } finally {
            ApiCredentialStore.save(context,"",active.credentialName)
            prefs.edit().apply {
                if(oldConfig == null) remove("config") else putString("config",oldConfig)
                if(oldProfile == null) remove("profile_CUSTOM") else putString("profile_CUSTOM",oldProfile)
            }.commit()
        }
    }

    @Test fun decoderRejectedAudioCannotPoisonTheSavedCache() {
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val finished = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val bad = File(CloudAudioCache.directory(context.cacheDir),"native-invalid-${System.nanoTime()}.audio").apply {
            writeText("This is a test error body, not playable audio.")
        }
        lateinit var player: CloudVoicePlayer
        try {
            instrumentation.runOnMainSync {
                player = CloudVoicePlayer(context,scope) { _, _ -> bad }
                player.play(CloudVoiceConfig(cacheAudio = true),"Test only",1f,1f,
                    { finished.countDown() },{ finished.countDown() },{ errors.incrementAndGet(); finished.countDown() })
            }
            assertTrue("Decoder did not finish",finished.await(10,TimeUnit.SECONDS))
            assertEquals(1,errors.get())
            assertFalse("Rejected audio was retained",bad.exists())
        } finally {
            instrumentation.runOnMainSync { player.stop() }
            scope.cancel(); bad.delete()
        }
    }

    @Test fun bufferedAudioPlaysOnceAndStopSuppressesLateCallbacks() {
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val calls = AtomicInteger(0)
        lateinit var player: CloudVoicePlayer
        var initialized = false
        val fixture = silentWave()
        try {
            instrumentation.runOnMainSync {
                player = CloudVoicePlayer(context,scope) { _, _ -> calls.incrementAndGet(); fixture }
                initialized = true
                player.play(CloudVoiceConfig(cacheAudio = true),"Test only",1f,1f,{ started.countDown() },{ finished.countDown() },{ errors.incrementAndGet(); finished.countDown() })
            }
            assertTrue("Native MediaPlayer did not start",started.await(10,TimeUnit.SECONDS))
            assertTrue("Native MediaPlayer did not complete",finished.await(10,TimeUnit.SECONDS))
            assertEquals(0,errors.get())
            assertEquals(1,calls.get())

            val pending = CompletableDeferred<File>()
            val fetching = CountDownLatch(1)
            val lateCallbacks = AtomicInteger(0)
            instrumentation.runOnMainSync {
                player = CloudVoicePlayer(context,scope) { _, _ -> fetching.countDown(); pending.await() }
                player.play(CloudVoiceConfig(),"Cancelled passage",1f,1f,
                    { lateCallbacks.incrementAndGet() },{ lateCallbacks.incrementAndGet() },{ lateCallbacks.incrementAndGet() })
            }
            assertTrue(fetching.await(5,TimeUnit.SECONDS))
            instrumentation.runOnMainSync { player.stop() }
            pending.complete(fixture)
            instrumentation.waitForIdleSync()
            assertEquals(0,lateCallbacks.get())
        } finally {
            instrumentation.runOnMainSync { if(initialized) player.stop() }
            scope.cancel(); fixture.delete()
        }
    }

    private fun silentWave(): File {
        val samples = 8000
        val bytes = ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples*2)
        return File(context.cacheDir,"cloud-native-test-${System.nanoTime()}.wav").apply { writeBytes(bytes.array()) }
    }
}
