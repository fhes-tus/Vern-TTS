# Application-specific R8 rules for Veritas Reader

# 1. Native methods and Sherpa JNI bindings
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# 2. Bouncy Castle Crypto Provider
# BC registers algorithm implementations dynamically by constructing class names.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# 3. PDFBox-Android & Harmony AWT Backport
# PDFBox relies heavily on reflection for font loading, filter streams, and color models.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.harmony.**

# 4. Jetpack PDF Viewer (alpha fragment components)
-keep class androidx.pdf.** { *; }
-dontwarn androidx.pdf.**

# 5. ML Kit Vision & Text Recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# 6. WorkManager & Background Workers
# WorkManager stores canonical class names in SQLite and instantiates via reflection.
-keep class com.veritas.reader.DocumentImportWorker { *; }
-keep class com.veritas.reader.AutoBackupWorker { *; }
-keep class com.veritas.reader.StreakReminderWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.WorkDatabase
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
# WorkSpec persists InputMerger class names. WorkManager constructs mergers by
# reflection before it constructs/runs a worker; full mode must retain the
# public zero-argument constructors as well as the classes themselves.
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
-dontwarn androidx.work.**

# 7. Jetpack Glance & App Widgets
# Glance renders RemoteViews via worker sessions and resolves ActionCallbacks reflectively.
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
-keep class * implements androidx.glance.action.ActionCallback {
    public <init>();
}
-keep class com.veritas.reader.**WidgetReceiver { *; }
-keep class com.veritas.reader.VeritasPlayerWidgetProvider { *; }
-keep class com.veritas.reader.**Callback { *; }
