# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# SLF4J — used internally by iText7, not needed at runtime on Android
-dontwarn org.slf4j.**

# iText7
-dontwarn com.itextpdf.**
-keep class com.itextpdf.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Supabase / Ktor
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# Yandex MapKit
-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# MediaPipe Tasks GenAI (Gemma on-device inference)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Protobuf annotations used by MediaPipe (compile-time only, not in APK)
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField

# javax.lang.model — annotation processing classes, not needed at runtime
-dontwarn javax.lang.model.**

# AutoValue / JavaPoet shaded inside MediaPipe (annotation processor, runtime-unused)
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**