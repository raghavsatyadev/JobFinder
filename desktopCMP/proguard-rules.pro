# Desktop release: ProGuard shrinks only (no obfuscation or optimization, see build.gradle.kts), on
# top of Compose's default rules. Unlike R8 on Android, it does not read the libraries' own rules,
# so the libraries that load classes by name, through ServiceLoader or from native code are kept
# whole here.

# --- Our code: Koin, kotlinx.serialization and Room reach parts of it by name. ---
-keep class io.github.raghavsatyadev.jobfinder.** { *; }

# --- Native code calls back into these (JNI, JNA). ---
-keep class org.cef.** { *; }
-keep class me.friwi.jcefmaven.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }

# --- Shrinking drops interfaces that default methods call through (VerifyError in JobSupport). ---
-keep class kotlinx.coroutines.** { *; }

# --- Loaded by name or through ServiceLoader. ---
-keep class io.netty.** { *; }
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keep class ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }
-keep class javax.jmdns.** { *; }
-keep class com.vladsch.flexmark.** { *; }
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class org.apache.commons.logging.** { *; }
-keep class com.cronutils.** { *; }
-keep class com.google.gson.** { *; }
-keep class dev.whyoleg.cryptography.** { *; }
-keep class androidx.room3.** { *; }
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep class org.koin.** { *; }
-keep class com.charleskorn.kaml.** { *; }

# --- kotlinx.serialization: serializers are found through the companion's serializer(). ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }

# --- Optional parts of libraries that the app never reaches (other OSes, unused codecs). ---
-dontwarn io.netty.**
-dontwarn org.apache.commons.**
-dontwarn com.jogamp.**
-dontwarn jogamp.**
-dontwarn ch.qos.logback.**
-dontwarn org.apache.pdfbox.**
-dontwarn dev.whyoleg.cryptography.**
-dontwarn com.cronutils.**
-dontwarn org.jsoup.**
-dontwarn org.cef.**
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**
