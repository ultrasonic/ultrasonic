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
