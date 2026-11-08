// Geliştirme aracı: Android'in içerik çekirdeğindeki hazır içeriği (kütüphane şekilleri, modların ağları) iOS
// uygulamasına gömülecek veri dosyalarına aktarır. Uygulamanın kendisi Kotlin içermez; yalnız bu dosyaları okur.
plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}
kotlin { jvmToolchain(21) }
val android = rootDir.resolve("../../../android")
sourceSets["main"].kotlin.srcDir(android.resolve("core/model/src/main/kotlin"))
sourceSets["main"].kotlin.srcDir(android.resolve("core/content/src/main/kotlin"))
sourceSets["main"].resources.srcDir(android.resolve("core/content/src/main/resources"))
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }
application { mainClass.set("ContentExportKt") }
tasks.named<JavaExec>("run") { maxHeapSize = "2g" }
