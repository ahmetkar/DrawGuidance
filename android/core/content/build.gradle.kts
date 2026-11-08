// Saf Kotlin içerik modülü: vektör çizim dili (birim kutuda şekiller), şekil/desen kütüphaneleri, formül dizgisi
// ve el yazısı tanıma. Android'e bağımlı değildir; önizleme testleri Java2D ile PNG üretir.
plugins {
    alias(libs.plugins.kotlin.jvm)
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

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    // -PpreviewDir=<klasör>: önizleme testleri PNG'leri buraya yazar
    project.findProperty("previewDir")?.let { systemProperty("previewDir", it.toString()) }
    // v33 -PcatalogOut=<dosya>: sunucunun katalog tohum dosyası (catalog.json.gz) üretilir
    project.findProperty("catalogOut")?.let { systemProperty("catalogOut", it.toString()) }
    // v37 -PretrainNets=1: kütüphane şablonları ve modların ağları (ART/NOTES/COMIC) yeniden üretilir;
    // -PtrainTextNet=1: not alma modunun yazı ağı (NOTES_TEXT) yeniden eğitilir (bkz. testlerdeki ModeNetRetrain, TextNetTraining)
    project.findProperty("retrainNets")?.let { systemProperty("retrainNets", it.toString()) }
    project.findProperty("trainTextNet")?.let { systemProperty("trainTextNet", it.toString()) }
    // v38 -PtrainNets38=1: 4+ elemanlı şekil ağları (ART/NOTES/COMIC/KIDS) ve yazı ağının "yazı mı" kapısı yeniden
    // eğitilir (bkz. testlerdeki ModeNetsV38Training, TextGateTraining)
    project.findProperty("trainNets38")?.let { systemProperty("trainNets38", it.toString()) }
    maxHeapSize = if (project.hasProperty("trainTextNet") || project.hasProperty("retrainNets") || project.hasProperty("trainNets38")) "3g" else "1g"
    // v24 ekran kartı testi: Mesa EGL ekransız (pencere/ekran gerekmez)
    environment("EGL_PLATFORM", "surfaceless")
}

dependencies {
    // v24 glTF/GLB okuma-yazma (JSON ağacı; serileştirme eklentisi gerekmez)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    // v24 ekran kartı çizicisinin testi: masaüstü EGL/GLES (Mesa, ekransız) üzerinden
    val lwjgl = "3.3.4"
    testImplementation("org.lwjgl:lwjgl:$lwjgl")
    testImplementation("org.lwjgl:lwjgl-egl:$lwjgl")
    testImplementation("org.lwjgl:lwjgl-opengles:$lwjgl")
    testRuntimeOnly("org.lwjgl:lwjgl:$lwjgl:natives-linux")
    testRuntimeOnly("org.lwjgl:lwjgl-opengles:$lwjgl:natives-linux")
}
