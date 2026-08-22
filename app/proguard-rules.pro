# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt

# Matrix SDK
-keep class org.matrix.** { *; }
-dontwarn org.matrix.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepnames class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# JNA Rules for Matrix SDK Crypto
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keepnames class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** {
    public *;
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclassmembers,allowobfuscation class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Glide/Coil
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# WebRTC
-keep class org.webrtc.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-dontwarn sun.misc.**
-repackageclasses
