# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
# Jsoup: dipendenze opzionali non presenti su Android
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
