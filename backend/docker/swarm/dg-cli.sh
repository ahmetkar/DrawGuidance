#!/bin/bash
# DrawGuidance yönetim komut satırı (sunucu 54.0.0+): çalışan API kapsayıcısında `cli <komut>` çalıştırır.
# v54'ten beri her komut yönetim şifresini sorar; şifre kapsayıcının içinde gizli okunur (ekrana ve komut geçmişine
# yazılmaz). Kullanım:  ./dg-cli.sh help   |   ./dg-cli.sh surum listele   |   ./dg-cli.sh surum ekle --kod 55 --ad 1.12.1 --ileti "Yeni sürüm"
# Argümanlar olduğu gibi aktarılır (boşluklu değerler tırnak içinde verilebilir).
set -euo pipefail

# Docker yetkisi yoksa sudo ile
DOCKER=(docker)
if ! docker info >/dev/null 2>&1; then DOCKER=(sudo docker); fi

CONTAINER_ID=$("${DOCKER[@]}" ps -q -f name=drawguidance_api -f status=running | head -n 1)
if [ -z "$CONTAINER_ID" ]; then
  echo "Hata: çalışan drawguidance_api kapsayıcısı bulunamadı." >&2
  exit 1
fi

[ "$#" -eq 0 ] && set -- help

# Terminal varsa -it (şifre gizli sorulur); yoksa -i (şifre standart girdinin ilk satırından okunur)
TTY=(-i)
[ -t 0 ] && [ -t 1 ] && TTY=(-it)

exec "${DOCKER[@]}" exec "${TTY[@]}" "$CONTAINER_ID" java \
  -Xms16m -Xmx64m -XX:+UseSerialGC \
  -DZstdNativePath=/app/native/libzstd-jni.so \
  -Dorg.xerial.snappy.lib.path=/app/native \
  -Dorg.xerial.snappy.lib.name=libsnappyjava.so \
  -jar /app/app.jar cli "$@"
