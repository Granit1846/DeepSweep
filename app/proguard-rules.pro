# Saves are kotlinx.serialization classes: keep their generated serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.skeorz.lab.deepsweep.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.skeorz.lab.deepsweep.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.skeorz.lab.deepsweep.**$$serializer { *; }
