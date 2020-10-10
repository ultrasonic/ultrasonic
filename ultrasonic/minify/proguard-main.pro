-dontobfuscate

### Don't remove subsonic api serializers/entities
-keep class org.moire.ultrasonic.api.subsonic.response.** { *; }
-keep class org.moire.ultrasonic.api.subsonic.models.** { *; }
