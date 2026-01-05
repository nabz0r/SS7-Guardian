# ==============================================================================
# SS7 Guardian - ProGuard/R8 Configuration
# ==============================================================================
# This file contains ProGuard rules for release builds.
# These rules ensure that obfuscation doesn't break critical functionality.
#
# For more details, see:
#   https://developer.android.com/build/shrink-code
# ==============================================================================

# ------------------------------------------------------------------------------
# General Android Rules
# ------------------------------------------------------------------------------

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# Hide original source file name in stack traces
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# Kotlin Specific Rules
# ------------------------------------------------------------------------------

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Coroutines debugging
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ------------------------------------------------------------------------------
# Room Database Rules
# ------------------------------------------------------------------------------

# Keep Room entities (they use reflection for column mapping)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    *;
}

# Keep DAOs
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Dao class * {
    *;
}

# Keep our specific entities
-keep class com.ss7guardian.data.entity.** { *; }
-keep class com.ss7guardian.data.dao.** { *; }

# ------------------------------------------------------------------------------
# SS7 Guardian Specific Rules
# ------------------------------------------------------------------------------

# Keep enum classes (used for event types, network types)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep callback interfaces (used by monitors)
-keep interface com.ss7guardian.monitor.**$*Callback {
    *;
}

# Keep data classes used in StateFlow (for proper serialization)
-keep class com.ss7guardian.monitor.CellMonitor$CellObservation { *; }
-keep class com.ss7guardian.monitor.NetworkMonitor$NetworkChange { *; }
-keep class com.ss7guardian.monitor.SmsMonitor$SuspiciousSms { *; }

# ------------------------------------------------------------------------------
# Android Telephony API Rules
# ------------------------------------------------------------------------------

# Keep telephony classes accessed via reflection
-keep class android.telephony.** { *; }
-keep class android.telephony.SmsMessage { *; }

# Keep TelephonyCallback (Android 12+)
-keep class * extends android.telephony.TelephonyCallback { *; }

# ------------------------------------------------------------------------------
# Debugging - Remove for production
# ------------------------------------------------------------------------------

# Uncomment to keep all classes (useful for debugging ProGuard issues)
# -dontobfuscate
# -dontoptimize

# Keep logging statements in debug builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
