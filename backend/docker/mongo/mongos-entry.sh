#!/bin/bash
# DrawGuidance parçalı küme yönlendiricisi (mongos, v30): parçaları ekler, kullanıcıları oluşturur,
# veritabanında parçalamayı açar ve koleksiyonları parçalar (her açılışta denetler; tekrar çalıştırılabilir).
set -euo pipefail
KEYFILE=/keys/mongo-keyfile
for _ in $(seq 1 60); do [ -s "$KEYFILE" ] && break; sleep 1; done
rm -f /tmp/ready
mongos --configdb "${CONFIG_DB:?}" --bind_ip_all --port 27017 --keyFile "$KEYFILE" &
PID=$!
trap 'kill -TERM $PID; wait $PID' TERM INT
until mongosh --quiet --port 27017 --eval 'db.adminCommand({ping: 1}).ok' >/dev/null 2>&1; do sleep 1; done
mongosh --quiet --port 27017 /scripts/mongos-init.js
touch /tmp/ready
wait $PID
