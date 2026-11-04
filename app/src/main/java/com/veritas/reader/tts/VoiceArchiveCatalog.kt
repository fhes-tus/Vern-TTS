package com.veritas.reader.tts

/** Official release asset identities verified 4 November 2026. Update size and digest together. */
internal object VoiceArchiveCatalog {
    data class Asset(val bytes: Long,val sha256: String)
    private val assets = mapOf(
        "kokoro-int8-multi-lang-v1_0.tar.bz2" to Asset(132353410L,"72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653"),
        "vits-piper-en_GB-alan-medium.tar.bz2" to Asset(67220121L,"a48d4017da0f77668b27bed63fe6e04dd64c6397e1fadad4f460efb0ef7c9012"),
        "vits-piper-en_GB-northern_english_male-medium.tar.bz2" to Asset(67210490L,"2bb2c1e709f58c11f17c693b3b38f500e110e7f54f2651774ec48b8d41f12c55"),
        "vits-piper-en_GB-southern_english_male-medium.tar.bz2" to Asset(80299212L,"f5525a6fd9a2fc0ab930ea70da6e3bcf207922a9f701acb0324ba2f59e4ca9ba"),
        "vits-piper-en_GB-jenny_dioco-medium.tar.bz2" to Asset(67225842L,"a0888024569bafbefc05a4b48ddf8419d8dbbf3205f4af37cf7c6f1a87cc20c5"),
        "vits-piper-en_GB-alba-medium.tar.bz2" to Asset(67212349L,"fcd45962906933eec4431d3688f7d74aaac8713c87c6717f91fd3b23463aa1a1"),
        "vits-piper-en_GB-cori-medium.tar.bz2" to Asset(67257412L,"49c9a5361bbdd95d7ca9687c4de11e5908481f65e7c7c368960df79949fdac2b"),
        "vits-piper-en_US-ryan-medium.tar.bz2" to Asset(67213100L,"c546af78b6395b4e7c4ce1ed899438b64426a362f5d4ec5fecd090ded9ad7505"),
        "vits-piper-en_US-joe-medium.tar.bz2" to Asset(67169394L,"261b903a1fb2ce621e2b5b14233cd41223c3b9f9590f896c11daee4df22d9bd3"),
        "vits-piper-en_US-hfc_male-medium.tar.bz2" to Asset(67214049L,"76388f84acfca8ba5c0ed1636a26ada14c598abd52e76f110d4756fe326fc5f2"),
        "vits-piper-en_US-amy-medium.tar.bz2" to Asset(67223746L,"9a5d1fc497f85e8022b785bff5f8105203b1e33099ee6265203efc70b0cb0264"),
        "vits-piper-en_US-lessac-medium.tar.bz2" to Asset(67230653L,"9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e"),
        "vits-piper-en_US-hfc_female-medium.tar.bz2" to Asset(67228166L,"3fffdceb0c65bd9415a085d09c3cb88cc82f9d74a6ca453f8ce7fc5eaee81ff8"),
        "kitten-nano-en-v0_8-fp32.tar.bz2" to Asset(63815222L,"16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e")
    )
    fun asset(url: String): Asset? = assets[url.substringAfterLast('/')]
}
