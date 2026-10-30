# ScrollPause ProGuard rules

# Keep Room entity and DAO classes
-keep class com.scrollpause.app.data.db.** { *; }

# Keep DataStore preferences keys
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences$Key { *; }

# Keep AccessibilityService subclass
-keep class com.scrollpause.app.service.ScrollPauseAccessibilityService { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Default R8 rules handle most of Compose and Kotlin.
