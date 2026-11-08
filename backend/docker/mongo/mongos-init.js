// mongos: ilk kullanıcı (localhost istisnası; yapılandırma sunucularına yazılır), parçalar, uygulama kullanıcısı,
// parçalama. users koleksiyonu firebaseUid (benzersiz) anahtarıyla parçalanır (sunucu da açılışta aynısını denetler);
// katalog koleksiyonları küçüktür, parçalanmaz.
const env = process.env;
const admin = db.getSiblingDB('admin');
try { admin.createUser({ user: env.MONGO_ROOT_USER, pwd: env.MONGO_ROOT_PASSWORD, roles: ['root'] }); print('Yönetici oluşturuldu'); } catch (e) { }
admin.auth(env.MONGO_ROOT_USER, env.MONGO_ROOT_PASSWORD);

const existing = admin.runCommand({ listShards: 1 }).shards.map(s => s._id);
for (const spec of (env.SHARDS || '').split(';').map(s => s.trim()).filter(Boolean)) {
  const id = spec.split('/')[0];
  if (existing.includes(id)) continue;
  for (let i = 0; i < 120; i++) {
    try { sh.addShard(spec); print(`Parça eklendi: ${spec}`); break; } catch (e) { sleep(1000); }
  }
}

const dbName = env.MONGO_APP_DB;
const app = db.getSiblingDB(dbName);
if (!app.getUser(env.MONGO_APP_USER)) {
  app.createUser({ user: env.MONGO_APP_USER, pwd: env.MONGO_APP_PASSWORD,
    roles: [{ role: 'readWrite', db: dbName }, { role: 'clusterManager', db: 'admin' }] });  // v40 dbAdmin kaldırıldı (gereksiz)
  print('Uygulama kullanıcısı oluşturuldu');
}

try { sh.enableSharding(dbName); } catch (e) { }
// Benzersiz dizin parçalı koleksiyonda yalnız parça anahtarıyla başlıyorsa uygulanabilir: anahtar firebaseUid
const keys = { users: { firebaseUid: 1 } };
for (const [coll, key] of Object.entries(keys)) {
  try { sh.shardCollection(`${dbName}.${coll}`, key, true); } catch (e) { if (!String(e).includes('already')) print(`${coll}: ${e}`); }
}
print('Parçalı küme hazır: ' + JSON.stringify(admin.runCommand({ listShards: 1 }).shards.map(s => s._id)));
