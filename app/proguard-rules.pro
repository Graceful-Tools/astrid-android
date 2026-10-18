# The core's answers are decoded by kotlinx.serialization; keep the generated serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.gracefultools.astrid.** { *** Companion; }
-keepclasseswithmembers class com.gracefultools.astrid.** { kotlinx.serialization.KSerializer serializer(...); }
