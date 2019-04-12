# keep annotations
-keepattributes *Annotation*

# keep classes, interfaces and members with explicit @Keep annotation
-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class *
-keepclasseswithmembers class * {
  @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
  @androidx.annotation.Keep <methods>;
}

# Application classes that will be serialized/deserialized over Gson
-keep class org.acestream.sdk.controller.api.response.** { *; }