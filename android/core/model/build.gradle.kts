// Saf Kotlin çekirdek modül: belge modeli (çizimler, şekil/diyagram/yazı tanımları), kayıt biçimi (ContentCodec)
// ve düzenleme geçmişi. Android'e bağımlı değildir; testleri Robolectric olmadan JVM'de saniyeler içinde çalışır.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
