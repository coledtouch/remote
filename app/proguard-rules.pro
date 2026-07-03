# Protobuf lite
-keep class com.curbscript.tvremote.proto.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Glance action callbacks (referenced by class name)
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }
