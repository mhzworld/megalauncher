# kotlinx.serialization: keep generated serializers for the persisted config/cache models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.megalauncher.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.megalauncher.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.megalauncher.data.**$$serializer { *; }

# Called via reflection in ShizukuHelper.
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(...);
}
