# R8 rules of the release build, on top of proguard-android-optimize.txt and the libraries' own rules.
# The classes named in the manifest (activities, receivers, services, widget provider) are kept by AGP. Nothing
# uses reflection on the app's classes.

# WorkManager's Room database is created by reflection (Room.databaseBuilder): without this, its constructor is
# removed and the app crashes at start ("NoSuchMethodException: WorkDatabase_Impl.<init>").
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Line numbers in the stack traces of the Play Console's crash reports, with the source file names hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
