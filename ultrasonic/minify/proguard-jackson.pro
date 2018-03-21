#### From Jackson

-keepattributes *Annotation*,EnclosingMethod,Signature
-keepnames class com.fasterxml.jackson.** {
*;
}
-keepnames interface com.fasterxml.jackson.** {
    *;
}
-dontwarn com.fasterxml.jackson.databind.**
-keep class org.codehaus.** { *; }
-keepclassmembers class * extends com.fasterxml.jackson.databind.JsonDeserializer {
    *;
}

-keepclassmembers public class * {
     @com.fasterxml.jackson.annotation.JsonCreator *;
     @com.fasterxml.jackson.annotation.JsonProperty *;
     @com.fasterxml.jackson.databind.annotation.JsonDeserialize *;
}

