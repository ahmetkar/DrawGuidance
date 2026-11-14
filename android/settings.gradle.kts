pluginManagement {
    repositories {
        google()
        // İsteğe bağlı Maven Central aynası (ör. DG_MAVEN_MIRROR=https://maven-central.storage-download.googleapis.com/maven2/)
        System.getenv("DG_MAVEN_MIRROR")?.let { maven { url = uri(it) } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // v39 Google Play Billing, Google Mobile Ads ve UMP kütüphanelerinin çevrimdışı deposu (varsa; google()'dan önce)
        val gmavenV39 = file("/mnt/project-files/drawguidance/gmaven-v39")
        if (gmavenV39.exists()) maven { url = uri(gmavenV39) }
        // v45 Play içi güncelleme (app-update-ktx 2.1.0) ve bağımlılıklarının çevrimdışı deposu (varsa)
        val gmavenV45 = file("/mnt/project-files/drawguidance/gmaven-v45")
        if (gmavenV45.exists()) maven { url = uri(gmavenV45) }
        google()
        System.getenv("DG_MAVEN_MIRROR")?.let { maven { url = uri(it) } }
        mavenCentral()
        // v49 freeRASP (Talsec) çalışma zamanı koruması deposu
        maven { url = uri("https://europe-west3-maven.pkg.dev/talsec-artifact-repository/common") }
        maven { url = uri("https://europe-west3-maven.pkg.dev/talsec-artifact-repository/freerasp") }
    }
}

rootProject.name = "DrawGuidance"
include(":app")
include(":core:model")
include(":core:content")
