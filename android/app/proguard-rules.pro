# Release builds shrink and obfuscate; CI only assembles debug, so nothing
# here is exercised yet. Written now because the failure mode is a release
# APK that installs, launches, and then cannot parse a single response.

# kotlinx.serialization generates a companion serializer per @Serializable
# class and looks it up reflectively. R8 cannot see that link.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.joinasr.app.** {
    *** Companion;
}
-keepclasseswithmembers class io.joinasr.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class io.joinasr.app.**
-keep, allowobfuscation, allowoptimization class <1>

# OkHttp ships rules of its own; these silence the two optional dependencies
# it references and does not need on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Crashlytics: a stack trace from a release build is only useful with the
# file and line still in it. The plugin uploads the mapping file; these keep
# what it maps back to.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
