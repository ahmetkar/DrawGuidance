plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}
kotlin { jvmToolchain(21) }
sourceSets["main"].kotlin.srcDir("../../../android/core/model/src/main/kotlin")
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }
application { mainClass.set("FixGenKt") }
