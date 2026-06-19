# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Art Hung\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-keepattributes InnerClasses

# Keep source/line info so Play Console can de-obfuscate crash & ANR stacks.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Custom Views inflated from layout XML (referenced by FQCN) ---
# R8 cannot see XML references; keep the (Context, AttributeSet) constructors.
-keep public class net.toload.main.hd.candidate.** { public <init>(android.content.Context, android.util.AttributeSet); }
-keep public class net.toload.main.hd.keyboard.** { public <init>(android.content.Context, android.util.AttributeSet); }

# --- Custom Preference inflated from preferences XML ---
-keep public class net.toload.main.hd.ui.view.SegmentedHanPreference {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# --- In-app billing AIDL ---
-keep class com.android.vending.billing.** { *; }

# --- Generic View constructor safety net (any other XML-inflated views) ---
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
