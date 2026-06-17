# Shared ProGuard/R8 rules for both Android and Desktop.
# Desktop-specific and library-specific rules live in proguard-desktop.pro
# and proguard-android.pro respectively.

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# kotlinx.serialization — keep serializer infrastructure for R8 full mode
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,SourceFile,LineNumberTable

# Ktor plugins loaded via ServiceLoader
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { <init>(); }

# LiteRT LM — on-device inference SDK with JNI native bridge.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Gson — pulled in transitively by litertlm. Uses heavy runtime reflection
# (Field.getType, Class.getGenericSuperclass, Unsafe.allocateInstance) to map
# JSON to classes. The generic-type attributes must survive; without Signature,
# Gson can't resolve List<Foo> -> Foo.
-keep class com.google.gson.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.JsonAdapter *;
}
-dontwarn com.google.gson.**
-dontwarn sun.misc.Unsafe

# Apache SSHD — pulled in by feat/remote-ssh. Uses javax.management and
# javax.security.auth.login classes that only exist on the JVM, not on Android.
-dontwarn javax.management.**
-dontwarn javax.security.auth.login.**

# Compose — suppress warnings about Android-only classes on desktop
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**
-dontwarn androidx.annotation.**
