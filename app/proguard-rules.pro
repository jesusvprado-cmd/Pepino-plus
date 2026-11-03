-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# VPN WireGuard: la parte nativa llama a estas clases por su nombre.
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# Decodificador de audio FFmpeg (se carga por reflexión).
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
