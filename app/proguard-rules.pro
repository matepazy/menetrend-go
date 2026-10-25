# Moshi's Kotlin reflection adapter constructs these API models and reads their
# property names, constructors (including default arguments), and metadata.
-keep class hu.matepazy.menetrendgo.GitHubRelease { *; }
-keep class hu.matepazy.menetrendgo.GitHubAsset { *; }

# Leaflet invokes these methods by name through WebView's NativeMap bridge.
-keepclassmembers class hu.matepazy.menetrendgo.** {
    @android.webkit.JavascriptInterface <methods>;
}

# OkHttp 4.10 probes these optional desktop TLS providers with Class.forName.
# Android uses its platform provider; these classes are intentionally absent.
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
