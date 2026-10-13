-dontwarn org.jetbrains.annotations.**

# ViewModels 由 di/AppGraph.kt 用反射按构造函数创建，R8 不能裁剪或改名
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Glance widget providers are instantiated reflectively by the system
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetProvider

# kotlinx-serialization generated serializers
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}
