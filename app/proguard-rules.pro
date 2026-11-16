# LibreMobile Proguard & R8 Optimizations

# Keep data models and serialization
-keep class com.libremobile.core.model.** { *; }
-keep class com.libremobile.core.engine.** { *; }
-keep class com.libremobile.core.ml.** { *; }

# Google ML Kit ProGuard rules
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }

# Optimization
-repackageclasses 'com.libremobile.opt'
-allowaccessmodification
-dontobfuscate
