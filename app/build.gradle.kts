import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            // Credentials live ONLY in untracked local.properties — never hardcode
            // fallbacks here; this file is public on GitHub. Empty string (not error())
            // keeps debug builds working on machines without release credentials.
            storeFile = file("../keystore/veritas-reader-test-release.jks")
            storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = "veritasreader"
            keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    namespace = "com.veritas.reader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.veritas.reader"
        minSdk = 28
        targetSdk = 36
        versionCode = 39
        versionName = "2.5.1"

        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    splits {
        abi {
            val isBundleTask = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
            isEnable = !isBundleTask
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            // PDFBox pulls Bouncy Castle in for PDF certificate and encryption handling.
            // That drags along the post-quantum Picnic parameter tables — lowmcL5 alone is
            // 749 KB of .properties data — which nothing on the PDF path ever reads. R8
            // cannot strip them because they are jar resources, not classes.
            excludes += setOf("org/bouncycastle/pqc/**")
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha18")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-mlkit-language-id:17.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.apache.commons:commons-compress:1.26.1")
    constraints {
        implementation("org.bouncycastle:bcpkix-jdk15to18:1.84") {
            because("Keep PDFBox Android's certificate/parser dependency line current for lint and security review.")
        }
        implementation("org.bouncycastle:bcprov-jdk15to18:1.84") {
            because("Keep PDFBox Android's crypto provider dependency line current for lint and security review.")
        }
        implementation("org.bouncycastle:bcutil-jdk15to18:1.84") {
            because("Keep PDFBox Android's Bouncy Castle utility dependency line current for lint and security review.")
        }
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.0")
}
