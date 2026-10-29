# Poshan Photo Print ProGuard / R8 Rules

# Keep ML Kit Text Recognition
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.android.gms.vision.** { *; }

# Keep Coil Image Loader
-keep class coil.** { *; }

# Keep Model Classes
-keep class com.poshan.photoprint.core.model.** { *; }

# AndroidX / Compose
-dontwarn androidx.**
