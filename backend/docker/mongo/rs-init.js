// Kopya kümesini kurar (kurulmamışsa), birincil seçilene kadar bekler ve kullanıcıları oluşturur.
const env = process.env;
const name = env.RS_NAME;
const members = (env.RS_MEMBERS || 'localhost:27017').split(',').map(s => s.trim()).filter(Boolean);
const configsvr = env.CONFIGSVR === '1';

function initiated() {
  try { rs.status(); return true; } catch (e) { return e.codeName !== 'NotYetInitialized'; }
}

if (!initiated()) {
  // Bütün üyeler yanıt verene kadar bekle
  for (const host of members) {
    for (let i = 0; i < 120; i++) {
      try { const c = new Mongo(host); c.getDB('admin').runCommand({ ping: 1 }); break; } catch (e) { sleep(1000); }
    }
  }
  const config = { _id: name, members: members.map((host, i) => ({ _id: i, host, priority: i === 0 ? 2 : 1 })) };
  if (configsvr) config.configsvr = true;
  rs.initiate(config);
  print(`Kopya kümesi kuruldu: ${name} (${members.join(', ')})`);
}

for (let i = 0; i < 120 && !db.hello().isWritablePrimary; i++) sleep(500);

if (env.CREATE_USERS === '1' && env.MONGO_ROOT_USER) {
  const admin = db.getSiblingDB('admin');
  try {
    admin.createUser({ user: env.MONGO_ROOT_USER, pwd: env.MONGO_ROOT_PASSWORD, roles: ['root'] });
    print('Yönetici kullanıcı oluşturuldu');
  } catch (e) { /* zaten var */ }
  admin.auth(env.MONGO_ROOT_USER, env.MONGO_ROOT_PASSWORD);
  if (env.MONGO_APP_USER && !configsvr && env.SHARDSVR !== '1') {
    // v40 en az yetki: yalnız uygulama veritabanında readWrite (dizin kurmak da buna dahil; dbAdmin gereksiz).
    // Var olan kullanıcının yetkisi ve parolası her açılışta ortamdakine (Docker sırrına) eşitlenir: parola
    // değiştirmek için sırrı yenileyip servisi güncellemek yeter
    const app = db.getSiblingDB(env.MONGO_APP_DB);
    const roles = [{ role: 'readWrite', db: env.MONGO_APP_DB }];
    if (!app.getUser(env.MONGO_APP_USER)) {
      app.createUser({ user: env.MONGO_APP_USER, pwd: env.MONGO_APP_PASSWORD, roles });
      print('Uygulama kullanıcısı oluşturuldu');
    } else {
      app.updateUser(env.MONGO_APP_USER, { pwd: env.MONGO_APP_PASSWORD, roles });
    }
  }
  // v40 isteğe bağlı yedek kullanıcısı (mongodump): yalnız "backup" rolü
  if (env.MONGO_BACKUP_USER && env.MONGO_BACKUP_PASSWORD && !configsvr) {
    const roles = [{ role: 'backup', db: 'admin' }];
    if (!admin.getUser(env.MONGO_BACKUP_USER)) {
      admin.createUser({ user: env.MONGO_BACKUP_USER, pwd: env.MONGO_BACKUP_PASSWORD, roles });
      print('Yedek kullanıcısı oluşturuldu');
    } else {
      admin.updateUser(env.MONGO_BACKUP_USER, { pwd: env.MONGO_BACKUP_PASSWORD, roles });
    }
  }
}
