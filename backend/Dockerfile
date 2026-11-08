# syntax=docker/dockerfile:1
# DrawGuidance sunucusu (v33): Spring Boot, Java 21. v40: üretim için sertleştirildi.
#   Kaynaktan:      docker build -t drawguidance-server:40 .
#   Hazır jar ile:  ./gradlew bootJar && docker build --target prebuilt -t drawguidance-server:40 .
# Çalışma katmanı: yalnız JRE (JDK/derleyici yok), root olmayan kullanıcı (10001), setuid/setgid bitleri silinmiş,
# uygulama dosyaları salt okunur; salt okunur kök dosya sistemiyle çalışır (yazılabilir tek yer /tmp: tmpfs).

ARG BUILD_IMAGE=gradle:8.14.3-jdk21
ARG JRE_IMAGE=eclipse-temurin:21.0.12.1_1-jre-noble

FROM ${BUILD_IMAGE} AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle --no-daemon -q dependencies > /dev/null || true
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle --no-daemon -q bootJar -x test

# v40 MongoDB kablo sıkıştırmasının yerel kitaplıkları (zstd-jni, snappy-java) normalde çalışırken /tmp'ye açılıp
# oradan yüklenir; Swarm'ın tmpfs bağlamaları "noexec" olduğundan bu yükleme başarısız olur. Kitaplıklar derlemede
# jar'dan çıkarılıp /app/native'e (salt okunur) konur ve JAVA_OPTS'taki yollarla oradan yüklenir.
FROM ${BUILD_IMAGE} AS natives-base
ARG TARGETARCH
COPY <<'EOF' /usr/local/bin/extract-natives
#!/bin/sh
set -eu
case "${TARGETARCH:-amd64}" in
  amd64) Z=linux/amd64; S=Linux/x86_64 ;;
  arm64) Z=linux/aarch64; S=Linux/aarch64 ;;
  *) echo "Desteklenmeyen mimari: ${TARGETARCH}" >&2; exit 1 ;;
esac
mkdir -p /x /native && cd /x
jar xf /app.jar BOOT-INF/lib/
cd BOOT-INF/lib
jar xf zstd-jni-*.jar "$Z/"
jar xf snappy-java-*.jar "org/xerial/snappy/native/$S/"
cp "$Z"/libzstd-jni-*.so /native/libzstd-jni.so
cp "org/xerial/snappy/native/$S/libsnappyjava.so" /native/libsnappyjava.so
EOF
RUN chmod 0755 /usr/local/bin/extract-natives

FROM natives-base AS natives-prebuilt
COPY build/libs/drawguidance-server.jar /app.jar
RUN extract-natives

FROM natives-base AS natives-built
COPY --from=build /src/build/libs/drawguidance-server.jar /app.jar
RUN extract-natives

FROM ${JRE_IMAGE} AS runtime-base
# Ayrıcalık yükseltmesine yarayabilecek setuid/setgid bitleri silinir (Swarm "no-new-privileges" desteklemiyor);
# girişi olmayan sistem kullanıcısı
RUN find / -xdev -perm /6000 -type f -exec chmod a-s {} + 2>/dev/null || true; \
    groupadd --system --gid 10001 dg && \
    useradd --system --uid 10001 --gid dg --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin dg && \
    mkdir -p /app && chown root:root /app && chmod 0755 /app
WORKDIR /app
EXPOSE 4747
# JVM kapsayıcı belleğine göre: yığın sınırın %75'i, düşük gecikmeli ZGC (kuşaklı), bellek bitince süreç kapanır
# (Swarm yeniden başlatır), /tmp/hsperfdata yazılmaz; geçici dosyalar (Tomcat) /tmp'de; sıkıştırma kitaplıkları /app/native'den
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+UseZGC -XX:+ZGenerational -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData -Djava.io.tmpdir=/tmp -Dfile.encoding=UTF-8 -DZstdNativePath=/app/native/libzstd-jni.so -Dorg.xerial.snappy.lib.path=/app/native -Dorg.xerial.snappy.lib.name=libsnappyjava.so" \
    SPRING_PROFILES_ACTIVE=prod \
    LANG=C.UTF-8
LABEL org.opencontainers.image.title="drawguidance-server" \
      org.opencontainers.image.description="DrawGuidance API (Spring Boot + MongoDB)" \
      org.opencontainers.image.source="https://github.com/ahmetkar/DrawGuidance"
# Canlılık: veritabanına bağlı değil (veritabanı kesintisinde kopyalar yeniden başlatılmaz)
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS -o /dev/null http://127.0.0.1:4747/actuator/health/liveness || exit 1
USER 10001:10001
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

FROM runtime-base AS prebuilt
COPY --chown=root:root --chmod=0444 build/libs/drawguidance-server.jar /app/app.jar
COPY --from=natives-prebuilt --chown=root:root --chmod=0555 /native /app/native

FROM runtime-base AS runtime
COPY --from=build --chown=root:root --chmod=0444 /src/build/libs/drawguidance-server.jar /app/app.jar
COPY --from=natives-built --chown=root:root --chmod=0555 /native /app/native
