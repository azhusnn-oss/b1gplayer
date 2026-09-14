# kotlinx.serialization keeps its generated serializers by annotation.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.b1g.player.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.b1g.player.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
