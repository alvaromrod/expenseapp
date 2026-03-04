# ProGuard rules for ExpenseApp

# Add project-specific ProGuard rules here.
# By default, the rules in this file are appended to the default ProGuard
# rules file specified in build.gradle.kts.

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.** { *; }

# Keep Hilt / Dagger
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends android.app.Application
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *

# Keep Compose
-keep class androidx.compose.ui.platform.** { *; }
