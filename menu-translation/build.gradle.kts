import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.dynamic-feature") }

android {
    namespace = "com.veritas.reader.menutranslation"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

dependencies {
    implementation(project(":app"))
    implementation("com.google.mlkit:translate:17.0.3")
}
