# Keep persisted settings DTOs (kotlinx.serialization) and their generated serializers.
-keepclassmembers class com.sandpause.app.data.settings.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.sandpause.app.data.settings.**$$serializer { *; }
-keepclassmembers class com.sandpause.app.data.settings.** {
    *** Companion;
}
-keepclasseswithmembers class com.sandpause.app.data.settings.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Device-admin and accessibility components are referenced from XML.
-keep class com.sandpause.app.admin.SandPauseAdminReceiver { *; }
-keep class com.sandpause.app.lock.AppGuardService { *; }
