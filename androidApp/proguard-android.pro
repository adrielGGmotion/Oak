# Android-specific R8 rules for Oak.
# Shared rules live in ../composeApp/proguard-rules.pro.

# ---- Ktor (narrowed to packages used on Android) ----
# Android uses ktor-client-android (java.net.HttpURLConnection), not CIO.
# Only the client, HTTP, serialization, and IO packages are needed.
-keep class io.ktor.client.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.utils.io.** { *; }
-dontwarn io.ktor.**

# ---- Okio (Ktor byte handling) ----
-keep class okio.** { *; }
-dontwarn okio.**

# ---- Coil (3.x ships consumer rules; keep Compose integration) ----
-keep class coil3.compose.** { *; }
-dontwarn coil3.**

# ---- BouncyCastle (transitive dep of sshd-core for SSH key crypto) ----
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---- Koin (consumer rules cover core; keep Compose integration) ----
-keep class org.koin.compose.** { *; }
-keep class org.koin.core.component.** { *; }
-dontwarn org.koin.**

# ---- App classes: Koin resolves ViewModels by KClass reified generics ----
-keep class com.oak.app.ui.chat.ChatViewModel { <init>(...); }
-keep class com.oak.app.ui.settings.SettingsViewModel { <init>(...); }
-keep class com.oak.app.ui.settings.SandboxViewModel { <init>(...); }
-keep class com.oak.app.ui.sandbox.SandboxSessionViewModel { <init>(...); }
-keep class com.oak.app.ui.sandbox.SandboxPackagesViewModel { <init>(...); }
-keep class com.oak.app.ui.sandbox.SandboxFileBrowserViewModel { <init>(...); }

# ---- kotlinx.serialization: keep @Serializable data classes by package ----

# com.oak.app.data — domain models (Conversation, Memory, Task, Sms, Email, etc.)
-if @kotlinx.serialization.Serializable class com.oak.app.data.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.oak.app.data.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.oak.app.data.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.oak.app.data.**$$serializer { *; }

# com.oak.app.network — API DTOs (OpenAI, Anthropic, Gemini, Tool, Sponsors)
-if @kotlinx.serialization.Serializable class com.oak.app.network.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.oak.app.network.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.oak.app.network.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.oak.app.network.**$$serializer { *; }

# com.oak.app.mcp — MCP server config and protocol models
-if @kotlinx.serialization.Serializable class com.oak.app.mcp.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.oak.app.mcp.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.oak.app.mcp.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.oak.app.mcp.**$$serializer { *; }

# com.oak.app.ssh — SSH server config
-if @kotlinx.serialization.Serializable class com.oak.app.ssh.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.oak.app.ssh.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.oak.app.ssh.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.oak.app.ssh.**$$serializer { *; }

# com.oak.app.OakScreen — sealed route class used by Navigation Compose
-if @kotlinx.serialization.Serializable class com.oak.app.OakScreen
-keepclassmembers class com.oak.app.OakScreen {
    static com.oak.app.OakScreen$Chat Chat;
}
-keep class com.oak.app.OakScreen$$serializer { *; }

# ---- Compose: strip debug source info (release only) ----
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformationMarkerStart(...);
    void sourceInformationMarkerEnd(...);
}

# ---- Kotlin null check optimization (AGP 9.0+) ----
-processkotlinnullchecks

# ---- multiplatform-settings — JVM backend uses java.util.prefs reflection ----
-keep class com.russhwolf.settings.** { *; }
-dontwarn com.russhwolf.settings.**
