# EntreTV - Reglas R8 optimizadas (full mode)
-dontwarn org.slf4j.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.entretv.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.entretv.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Decodificadores opcionales: DefaultRenderersFactory los crea por reflexión
# y la librería nativa de FFmpeg llama métodos Java por nombre.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn androidx.media3.decoder.ffmpeg.**
-dontwarn io.antmedia.rtmp_client.**
