# v40 R8 (yayın derlemesi küçültme/karartma) kuralları.
# Room, Retrofit, OkHttp, kotlinx.serialization, Koin, WorkManager, Firebase, Play Billing, AdMob ve Credentials
# kendi tüketici kurallarını AAR/JAR içinde getirir; uygulama kodunda yansıma (reflection) ya da JNI yok.
# Aşağıdakiler bu kuralların üstüne eklenen, uygulamaya özgü güvenceler.

# Çökme kayıtlarında (diagnostics) satır numaraları; dosya adları gizlenir. Okumak için:
# app/build/outputs/mapping/release/mapping.txt (her yayınla saklanmalı; Play Console'a da yüklenir)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: @Serializable sınıfların üretilmiş serileştiricileri (tüketici kuralına ek güvence;
# sunucu DTO'ları adlarıyla JSON'a çevrilir, alan adları @SerialName/alan adından derlemede sabitlenir)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class com.karapp.drawguidance.** {
    *** Companion;
    static *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.karapp.drawguidance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit arayüzleri (R8 tam kipinde genel türler ve suspend dönüş türleri korunur)
-keep,allowobfuscation,allowshrinking interface com.karapp.drawguidance.data.remote.** { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Credential Manager, Play Services sağlayıcısını sınıf adıyla yükler
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# v45 iyileştirmeler açık (build.gradle.kts: proguard-android-optimize.txt). v42'deki -dontoptimize etkisizdi:
# v40'tan beri kullanılan proguard-android.txt iyileştirmeyi zaten kapatıyordu. Android 10'daki Ayarlar çökmesi
# R8 tam kipiyle (v40, AGP varsayılanı) geldi ve tam kipin kapatılmasıyla (v42) gitti; tam kip kapalı kalır.
# Burada -dontoptimize olmamalı (V45R8ConfigTest denetler).
