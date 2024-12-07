# Keep the androidx.graphics library
-keep class androidx.graphics.** { *; }

# Keep the native library for image processing
-keep class com.bizzkoot.parkinglocator.libimage_processing_util_jni { *; }

# Keep the native library for video processing
-keep class com.bizzkoot.parkinglocator.libvideo_processing_util_jni { *; }

# Keep the native library for audio processing
-keep class com.bizzkoot.parkinglocator.libaudio_processing_util_jni { *; }

# If you have other libraries that need to be kept, add them here