# OkHttp rules
-keepattributes Signature
-keepattributes Annotation
-keepclassmembers class com.squareup.okhttp3.** { *; }
-dontwarn com.squareup.okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepattributes *Annotation*,ElementValuePosition,LineNumberTable
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# WorkManager & Foreground Services
-keep class androidx.work.** { *; }
-keep class com.example.drivesync.worker.** { *; }

# Navigation & Compose
-keep class com.example.drivesync.** { *; }
