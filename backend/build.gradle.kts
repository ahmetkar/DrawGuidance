plugins {
    java
    // v40 güvenlik: 3.3.5 → 3.5.16 (3.x'in son sürümü; Tomcat, Spring Framework/Security ve Jackson CVE düzeltmeleri)
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.karapp.drawguidance"
version = "44.2.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

// v40 güvenlik: Boot'un yönettiği sürümlerden daha yeni yamalar (bilinen CVE'ler; bkz. belgeler/v40-guvenlik.md)
extra["tomcat.version"] = "10.1.61"
extra["logback.version"] = "1.5.38"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Firebase ID jetonu doğrulaması (Google securetoken JWKS; imza, iss, aud, süre)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // MongoDB kablo sıkıştırması (zstd, snappy; zlib sürücüde yerleşik)
    implementation("com.github.luben:zstd-jni:1.5.7-24")
    implementation("org.xerial.snappy:snappy-java:1.1.10.12")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:junit-jupiter")
    // Testlerde yerel RSA anahtarıyla jeton üretmek için
    testImplementation("com.nimbusds:nimbus-jose-jwt")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters"); options.encoding = "UTF-8" }

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

tasks.bootJar { archiveFileName.set("drawguidance-server.jar") }
tasks.jar { enabled = false }
