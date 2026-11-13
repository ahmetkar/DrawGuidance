import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// v33 Firebase (Google ile giriş): google-services.json varsa eklenti uygulanır (yoksa giriş kapalı derlenir)
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// v39 Play yükleme anahtarı: app/keystore/release.properties (git'e girmez; storeFile bu klasöre göre). Yoksa
// yayın sürümü imzasız derlenir (Play'e yüklemeden önce imzalanmalı).
val releaseKeys = Properties().apply {
    file("keystore/release.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    // v41 dil uygulama içinde seçilebildiği için .aab'de diller ayrı paketlere bölünmez (Play yalnız cihazın dilini
    // indirirdi; başka dile geçince metinler eksik kalırdı)
    bundle { language { enableSplit = false } }
    namespace = "com.karapp.drawguidance"
    // v18: Android 16 (API 36) ile derlenir ve onu hedefler. v39: Android 6.0'dan (API 23) itibaren kurulur (Google Play
    // Billing 8, Google Mobile Ads 24 ve UMP en az API 23 ister). Sürüme özel API'ler çalışma anında seçilir (bkz.
    // ui/platform/DeviceCaps.kt), eski sürümlerde karşılıkları kullanılır.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.karapp.drawguidance"
        minSdk = 23
        targetSdk = 36
        // v39 Play'e yükleme: her yüklemede versionCode artmalı
        versionCode = 49
        versionName = "1.7.0"

        // Emülatör/gerçek cihazda backend'e ulaşmak için:
        // - Emülatör (AVD) için host makine 10.0.2.2 üzerinden erişilir.
        // - Gerçek cihaz için bilgisayarın LAN IP adresini kullanın.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:4747/\"")
        // Apple ile giriş (Firebase'te Apple sağlayıcısı açılınca): gradle.properties'te dg.appleSignIn=false ile gizlenir
        buildConfigField("boolean", "APPLE_SIGN_IN", (project.findProperty("dg.appleSignIn")?.toString() ?: "true").toBoolean().toString())
        // v39 Google Play aboneliği (Play Console'da henüz ürün yok: sabit kimlikler)
        buildConfigField("String", "PLAY_PREMIUM_PRODUCT_ID", "\"drawguidance_premium\"")
        buildConfigField("String", "PLAY_PREMIUM_BASE_PLAN_ID", "\"premium-monthly\"")
    }

    // v33 ortak hata ayıklama anahtarı (projeyle gelir): her bilgisayarda aynı SHA-1, Firebase'deki Android istemcisi
    // bu parmak iziyle kayıtlı. Yayın (Play) imzası Play Console'da ayrıca tanımlanır.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeys.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file("keystore/" + releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // v39 gerçek AdMob uygulama ve geçiş reklamı kimlikleri (yalnız yayın derlemesinde)
            manifestPlaceholders["admobAppId"] = "ca-app-pub-4962587185426397~4108507569"
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-4962587185426397/2112899614\"")
            // v40 R8: kod küçültme/karartma ve kullanılmayan kaynakların atılması (kurallar: proguard-rules.pro)
            // v45 DEX kod iyileştirmesi açık (proguard-android-optimize.txt: satır içine alma, sınıf birleştirme, enum
            // açma, ölü kod atma). v40–v44'teki proguard-android.txt iyileştirmeyi zaten kapatıyordu (-dontoptimize);
            // Android 10'daki Ayarlar çökmesini v42'de gideren R8 tam kipinin kapatılmasıydı ve tam kip kapalı kalır
            // (gradle.properties: android.enableR8.fullMode=false).
            // -PdgNoMinify: tanılama için R8'siz yayın derlemesi
            val noMinify = project.hasProperty("dgNoMinify")
            isMinifyEnabled = !noMinify
            isShrinkResources = !noMinify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // v39 yayın sunucusu (Ayarlar'daki sunucu adresi kaldırıldı)
            buildConfigField("String", "API_BASE_URL", "\"https://drawapi.ahmetkar.com/\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            // v39 hata ayıklamada Google'ın deneme kimlikleri (gerçek reklam isteği yapılmaz)
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time, akışlar vb. Android 8.0 öncesinde de çalışsın
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                // Koin Compose kök kapsamını statik olarak önbelleğe alıyor; her test sınıfı temiz bir JVM'de çalışsın.
                it.forkEvery = 1
                // Her sınıf kendi JVM'inde: çekirdek sayısına göre birkaç JVM aynı anda koşar
                it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
                // -PdgLogs: uygulamanın günlükleri test çıktısına yazılır (hata/uyarı denetimi için)
                if (project.hasProperty("dgLogs")) it.systemProperty("robolectric.logging", "stdout")
                // v43 -PbasePackOut=<klasör>: taban paketlerin dışa aktarımı (V43BasePackExport)
                project.findProperty("basePackOut")?.let { p -> it.systemProperty("basePackOut", p.toString()) }
                // v46 -PtemplateOut=<klasör>: hazır tuval şablonlarının üretimi (V46TemplateExport)
                project.findProperty("templateOut")?.let { p -> it.systemProperty("templateOut", p.toString()) }
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(project(":core:content"))
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    // v49 freeRASP (Talsec) çalışma zamanı koruması (root, hook, kurcalama, emülatör… algılar)
    implementation("com.aheaditec.talsec.security:TalsecSecurity-Community:19.3.1")
    implementation(libs.lifecycle.runtime.compose)
    // v43 uygulama ön plandayken gelen kutusunun uzun yoklaması (ProcessLifecycleOwner)
    implementation("androidx.lifecycle:lifecycle-process:${libs.versions.lifecycle.get()}")
    // v43 Google Play uygulama içi güncelleme (güncelleme penceresinden Play akışı)
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.graphics.core)
    // v33 Google ile giriş ve çevrimdışı olay kuyruğu
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    implementation(libs.work.runtime)
    // v39 Google Play abonelik, AdMob geçiş reklamları ve kullanıcı onayı (UMP)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("androidx.test.espresso:espresso-core:3.5.0")
    debugImplementation(libs.compose.ui.test.manifest)
}
