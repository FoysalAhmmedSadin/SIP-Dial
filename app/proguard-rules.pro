# Linphone SDK
-keep class org.linphone.** { *; }
-keep class org.oze.** { *; }
-dontwarn org.linphone.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
