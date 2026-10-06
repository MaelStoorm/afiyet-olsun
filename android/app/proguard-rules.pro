# Uygulamanın kendi kodu çok küçük; olduğu gibi kalsın.
# Oyun (JavaScript) Java köprülerini adlarıyla çağırır, bu yüzden adları değişmemeli.
-keep class com.afiyetolsun.oyun.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
