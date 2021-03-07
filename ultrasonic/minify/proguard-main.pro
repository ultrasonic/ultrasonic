-dontobfuscate

### Don't remove subsonic api serializers/entities
-keep class org.moire.ultrasonic.api.subsonic.response.** { *; }
-keep class org.moire.ultrasonic.api.subsonic.models.** { *; }

## Don't remove NowPlayingFragment
-keep class org.moire.ultrasonic.fragment.NowPlayingFragment { *; }
