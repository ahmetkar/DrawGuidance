#!/bin/bash
# DrawGuidance MongoDB kopya kümesi üyesi (v30).
# Anahtar dosyasıyla (üyeler arası kimlik doğrulama) başlar. RS_INIT=1 olan üye ilk açılışta kümeyi kurar
# (localhost istisnasıyla) ve kullanıcıları oluşturur; sonraki açılışlarda yalnız denetler.
# v40: parolalar Docker sırrından okunabilir (MONGO_ROOT_PASSWORD_FILE, MONGO_APP_PASSWORD_FILE,
# MONGO_BACKUP_PASSWORD_FILE); anahtar dosyasının yolu MONGO_KEYFILE (Swarm: /run/secrets/mongo_keyfile);
# MONGO_DROP_ROOT=1 ise mongod root değil mongodb (999) kullanıcısıyla çalışır.
set -euo pipefail

# *_FILE → değişken (değer günlüğe yazılmaz)
for name in MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD MONGO_BACKUP_PASSWORD; do
  file_var="${name}_FILE"
  if [ -n "${!file_var:-}" ]; then
    if [ -n "${!name:-}" ]; then echo "$name ve $file_var birlikte verilmiş" >&2; exit 1; fi
    if [ ! -r "${!file_var}" ]; then echo "$file_var okunamıyor: ${!file_var}" >&2; exit 1; fi
    value="$(cat "${!file_var}")"
    export "$name=$value"
    unset "$file_var" value
  fi
done

KEYFILE="${MONGO_KEYFILE:-/keys/mongo-keyfile}"
for _ in $(seq 1 60); do [ -s "$KEYFILE" ] && break; sleep 1; done

# v40 root yetkisini bırak: veri klasörleri mongodb kullanıcısına verilir, betik gosu ile yeniden çalışır
if [ "${MONGO_DROP_ROOT:-0}" = "1" ] && [ "$(id -u)" = "0" ]; then
  chown -R mongodb:mongodb /data/db /data/configdb
  exec gosu mongodb bash "$0" "$@"
fi

rm -f /tmp/ready
ARGS=(--replSet "${RS_NAME:?}" --bind_ip_all --port 27017 --keyFile "$KEYFILE" --wiredTigerCacheSizeGB "${MONGO_CACHE_GB:-1}")
[ "${CONFIGSVR:-0}" = "1" ] && ARGS+=(--configsvr)
[ "${SHARDSVR:-0}" = "1" ] && ARGS+=(--shardsvr)
mongod "${ARGS[@]}" &
PID=$!
trap 'kill -TERM $PID; wait $PID' TERM INT
until mongosh --quiet --port 27017 --eval 'db.adminCommand({ping: 1}).ok' >/dev/null 2>&1; do sleep 1; done
if [ "${RS_INIT:-0}" = "1" ]; then
  mongosh --quiet --port 27017 /scripts/rs-init.js
fi
touch /tmp/ready
wait $PID
