# Đổi tên lớp thư viện thành tên ngắn vô nghĩa, gom vào 1 gói
-repackageclasses 'a'
-allowaccessmodification
-renamesourcefileattribute ''
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn java.beans.**
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}
-keep class org.ocpsoft.prettytime.i18n.Resources* { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
# Không dừng build vì thiếu lớp phụ của thư viện
-ignorewarnings
