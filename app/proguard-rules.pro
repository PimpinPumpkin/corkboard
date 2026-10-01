# Cronet ships its own consumer rules inside the AAR; these are references it only uses on
# devices and builds that have them.
-dontwarn org.chromium.**

# WorkManager's database is created by Room through reflection on its no-argument constructor,
# which R8 in full mode removes unless told otherwise. Without this the app dies at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
