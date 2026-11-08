#!/bin/bash
# DrawGuidance v40 MongoDB yedeği (Swarm "mongo-backup" servisi): her gün BACKUP_HOUR'da (UTC) bütün kopya kümesinin
# mongodump arşivi (gzip, --oplog ile tutarlı) /backups'a yazılır; BACKUP_KEEP_DAYS günden eski arşivler silinir.
# Parola Docker sırrından okunur ve komut satırına (ps çıktısına) yazılmaz: mongodump'a tmpfs'teki --config ile verilir.
set -euo pipefail

: "${MONGO_HOSTS:?}" "${RS_NAME:?}" "${MONGO_BACKUP_USER:?}" "${MONGO_BACKUP_PASSWORD_FILE:?}"
BACKUP_HOUR="${BACKUP_HOUR:-3}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
DIR=/backups

umask 077
CONF="$(mktemp /tmp/mongodump.XXXXXX.yml)"
trap 'rm -f "$CONF"' EXIT
# YAML tek tırnak içinde: tek tırnak iki kez yazılır
printf "password: '%s'\n" "$(sed "s/'/''/g" "$MONGO_BACKUP_PASSWORD_FILE")" > "$CONF"

backup() {
  local name
  name="drawguidance-$(date -u +%Y%m%d-%H%M%S).archive.gz"
  echo "Yedek başlıyor: $name"
  if mongodump --quiet --config="$CONF" \
      --uri="mongodb://${MONGO_BACKUP_USER}@${MONGO_HOSTS}/?replicaSet=${RS_NAME}&authSource=admin&readPreference=secondaryPreferred" \
      --oplog --gzip --archive="$DIR/$name.part"; then
    mv "$DIR/$name.part" "$DIR/$name"
    echo "Yedek tamam: $name ($(du -h "$DIR/$name" | cut -f1))"
  else
    rm -f "$DIR/$name.part"
    echo "Yedek BAŞARISIZ: $name" >&2
  fi
  find "$DIR" -maxdepth 1 -name 'drawguidance-*.archive.gz' -mtime +"$BACKUP_KEEP_DAYS" -print -delete
}

[ "${BACKUP_ON_START:-0}" = "1" ] && backup
while true; do
  now=$(date -u +%s)
  next=$(date -u -d "today ${BACKUP_HOUR}:00" +%s)
  [ "$next" -le "$now" ] && next=$(date -u -d "tomorrow ${BACKUP_HOUR}:00" +%s)
  sleep $((next - now))
  backup
done
