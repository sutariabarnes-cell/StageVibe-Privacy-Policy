# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Shrink unused library classes (e.g., unused Material Extended Icons) without obfuscating names
-dontobfuscate
-dontoptimize

# Preserve all application models, Firestore POJOs, Credential Manager providers, and UI classes
-keep class com.example.** { *; }
-keepnames class com.google.firebase.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keepnames class com.google.android.recaptcha.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Keep Kotlin metadata and annotations for reflection/serialization safety
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn okio.**
-dontwarn io.grpc.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
