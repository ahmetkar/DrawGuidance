package com.karapp.drawguidance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.images.SavedImageEntity

@Database(
    entities = [
        CanvasEntity::class, SavedImageEntity::class, CanvasAssetEntity::class, FolderEntity::class, ShapeTemplateEntity::class,
        PaperAssetEntity::class, Model3DEntity::class, Texture3DEntity::class,
        // v33 çevrimdışı olay tablosu ve cihazın kullanıcısı
        SyncEventEntity::class, LocalUserEntity::class,
        // v34 dil ayarı
        LanguageSettingsEntity::class,
        // v36 kütüphane şekilleri (yerleşik ve sunucudan gelen)
        CatalogVectorEntity::class,
        // v37 sinir ağları (şekil düzeltme ve yazı düzeltme; yerleşik ve sunucudan gelen)
        CatalogNetEntity::class,
        // v39 abonelik planları, erişim hakları, kullanıcının aboneliği ve çizim görevinin ilerlemesi
        PlanEntity::class, FeatureEntity::class, PlanFeatureEntity::class, SubscriptionEntity::class, ChallengeProgressEntity::class,
        // v40 çocuk tuvaline girişlerin sayacı (maskotun çizim görevi önerisi)
        KidsEntryEntity::class,
        // v40 tuvalde geçirilen etkin düzenleme süresi (Ana sayfa: en çok vakit geçirdiklerin)
        CanvasTimeEntity::class,
        // v42 çizgi roman tema paketleri: paketler, varlıklar ve paket ↔ varlık bağlantısı (iki yabancı anahtar)
        com.karapp.drawguidance.data.comicassets.ThemePackEntity::class,
        com.karapp.drawguidance.data.comicassets.ComicAssetEntity::class,
        com.karapp.drawguidance.data.comicassets.ThemePackAssetEntity::class,
        // v43 Android izinleri (ilk açılışta istenir, sonucu burada)
        AppPermissionEntity::class,
        KidsLockEntity::class,
    ],
    version = 23,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun canvasDao(): CanvasDao
    abstract fun savedImageDao(): SavedImageDao
    abstract fun canvasAssetDao(): CanvasAssetDao
    abstract fun folderDao(): FolderDao
    abstract fun shapeTemplateDao(): ShapeTemplateDao
    abstract fun paperAssetDao(): PaperAssetDao
    abstract fun model3DDao(): Model3DDao
    abstract fun texture3DDao(): Texture3DDao
    abstract fun syncEventDao(): SyncEventDao
    abstract fun localUserDao(): LocalUserDao
    abstract fun languageSettingsDao(): LanguageSettingsDao
    abstract fun catalogVectorDao(): CatalogVectorDao
    abstract fun catalogNetDao(): CatalogNetDao
    abstract fun billingDao(): BillingDao
    abstract fun challengeProgressDao(): ChallengeProgressDao
    abstract fun kidsEntryDao(): KidsEntryDao
    abstract fun canvasTimeDao(): CanvasTimeDao
    abstract fun themePackDao(): com.karapp.drawguidance.data.comicassets.ThemePackDao
    abstract fun appPermissionDao(): AppPermissionDao
    /** v44 çocuk kilidi. */
    abstract fun kidsLockDao(): KidsLockDao

    companion object {
        /** v1 → v2: Resimlerim için saved_images tablosu. Mevcut tuvaller korunur. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_images` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`canvasLocalId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`uri` TEXT NOT NULL, " +
                        "`width` INTEGER NOT NULL, " +
                        "`height` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`location` TEXT NOT NULL)",
                )
            }
        }

        /**
         * v2 → v3: çizim içeriği JSON metin yerine sıkıştırılmış ikili BLOB olarak saklanır
         * (tablo yeniden oluşturulup her satır [ContentCodec] ile dönüştürülür) ve resimler için
         * canvas_assets tablosu eklenir. Mevcut tuvaller ve çizimler korunur.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `canvases_new` (`localId` TEXT NOT NULL, `remoteId` TEXT, `name` TEXT NOT NULL, " +
                        "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `backgroundColorHex` TEXT NOT NULL, " +
                        "`cloudSyncEnabled` INTEGER NOT NULL, `aiEnabled` INTEGER NOT NULL, `content` BLOB NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, " +
                        "`lastSyncedAt` INTEGER, PRIMARY KEY(`localId`))",
                )
                db.execSQL(
                    "INSERT INTO `canvases_new` (localId, remoteId, name, width, height, backgroundColorHex, cloudSyncEnabled, " +
                        "aiEnabled, content, createdAt, updatedAt, syncStatus, lastSyncedAt) " +
                        "SELECT localId, remoteId, name, width, height, backgroundColorHex, cloudSyncEnabled, aiEnabled, " +
                        "X'', createdAt, updatedAt, syncStatus, lastSyncedAt FROM `canvases`",
                )
                val update = db.compileStatement("UPDATE `canvases_new` SET content = ? WHERE localId = ?")
                db.query("SELECT localId, contentJson FROM `canvases`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val strokes = com.karapp.drawguidance.data.repository.decodeStrokes(cursor.getString(1) ?: "[]")
                        update.bindBlob(1, ContentCodec.encode(strokes))
                        update.bindString(2, cursor.getString(0))
                        update.executeUpdateDelete()
                        update.clearBindings()
                    }
                }
                db.execSQL("DROP TABLE `canvases`")
                db.execSQL("ALTER TABLE `canvases_new` RENAME TO `canvases`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `canvas_assets` (`id` TEXT NOT NULL, `canvasLocalId` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `data` BLOB NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_canvas_assets_canvasLocalId` ON `canvas_assets` (`canvasLocalId`)")
            }
        }

        /**
         * v3 → v4: cloud kaydı kaldırıldı. Senkron kolonları (remoteId, cloudSyncEnabled, syncStatus,
         * lastSyncedAt) tablodan çıkarılır; tuvaller ve çizimler aynen korunur. (Eski Android sürümlerindeki
         * SQLite DROP COLUMN desteklemediği için tablo yeniden oluşturulur.)
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `canvases_new` (`localId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `backgroundColorHex` TEXT NOT NULL, " +
                        "`aiEnabled` INTEGER NOT NULL, `content` BLOB NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`localId`))",
                )
                db.execSQL(
                    "INSERT INTO `canvases_new` (localId, name, width, height, backgroundColorHex, aiEnabled, content, createdAt, updatedAt) " +
                        "SELECT localId, name, width, height, backgroundColorHex, aiEnabled, content, createdAt, updatedAt FROM `canvases`",
                )
                db.execSQL("DROP TABLE `canvases`")
                db.execSQL("ALTER TABLE `canvases_new` RENAME TO `canvases`")
            }
        }

        /** v4 → v5: kaydedilen resimlerin dosya boyutu (eski kayıtlar 0; ilk gösterimde hesaplanır). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_images` ADD COLUMN `sizeBytes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5 → v6: tuval türü (resim / çalışma-not). Mevcut tuvaller resim türünde kalır. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `canvases` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'ART'")
            }
        }

        /** v6 → v7: açık sayfa ve kılavuz ayarları (çoklu sayfa içeriği `content` içinde saklanır). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `canvases` ADD COLUMN `currentPage` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `canvases` ADD COLUMN `guide` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7 → v8: tuval ve resim klasörleri (katmanlar içerik biçiminde, veritabanı değişmez). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `sourceCanvasId` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))",
                )
                db.execSQL("ALTER TABLE `canvases` ADD COLUMN `folderId` TEXT")
                db.execSQL("ALTER TABLE `saved_images` ADD COLUMN `folderId` TEXT")
            }
        }

        /** v8 → v9: şekil veritabanı (v21; şablonlar uygulama açılınca arka planda yazılır). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(ShapeTemplateEntity.CREATE_SQL)
                ShapeTemplateEntity.INDEX_SQL.forEach { db.execSQL(it) }
            }
        }

        /**
         * v9 → v10 (v23): tuval arka plan resmi/deseni kolonu, arka plan kütüphanesi (desenler ve hazır resimler),
         * 3B Şekillerim ve hazır 3B dokuları. Mevcut tuvaller ve çizimler aynen korunur.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `canvases` ADD COLUMN `background` TEXT NOT NULL DEFAULT ''")
                db.execSQL(PaperAssetEntity.CREATE_SQL)
                db.execSQL(PaperAssetEntity.INDEX_SQL)
                db.execSQL(Model3DEntity.CREATE_SQL)
                db.execSQL(Texture3DEntity.CREATE_SQL)
            }
        }

        /**
         * v10 → v11 (v30): şema değişmez. Yapay zekâ ayarı artık tuvalde gerçekten kullanıldığından (öneriler, seçimde
         * öneri, 3B öneri) önceden oluşturulan tuvallerin hepsinde bir kez açılır; sonra her tuval kendi ayarını korur.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `canvases` SET `aiEnabled` = 1")
            }
        }

        /**
         * v11 → v12: sunucuyla ilgili yapılmış/yapılacak işlerin olay tablosu (`sync_events`) ve cihazın kullanıcısı
         * (`local_user`: Google hesabı bilgileri ve doğum tarihi). Mevcut veriler değişmez.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL DEFAULT '', `dedupeKey` TEXT, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastError` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `nextAttemptAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`doneAt` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_events_status_nextAttemptAt` ON `sync_events` (`status`, `nextAttemptAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_events_dedupeKey` ON `sync_events` (`dedupeKey`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_user` (`id` TEXT NOT NULL, `firebaseUid` TEXT NOT NULL, `displayName` TEXT, " +
                        "`givenName` TEXT, `familyName` TEXT, `email` TEXT, `emailVerified` INTEGER NOT NULL, `photoUrl` TEXT, " +
                        "`accountCreatedAt` INTEGER, `birthDate` TEXT, `signedIn` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `syncedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_user_firebaseUid` ON `local_user` (`firebaseUid`)")
            }
        }

        /** v12 → v13: dil ayarı (`language_settings`, tek satır). Mevcut veriler değişmez. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `language_settings` (`id` INTEGER NOT NULL, `languageCode` TEXT NOT NULL, " +
                        "`auto` INTEGER NOT NULL, `systemLanguage` TEXT, `systemCountry` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * v13 → v14: kütüphane şekilleri tablosu (`catalog_vectors`) ve kullanıcının kullanım şartları/KVKK onayı
         * (`local_user.termsAcceptedAt`, `termsVersion`). Mevcut kayıtlar korunur.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CatalogVectorEntity.CREATE_SQL)
                db.execSQL(CatalogVectorEntity.INDEX_SQL)
                db.execSQL("ALTER TABLE `local_user` ADD COLUMN `termsAcceptedAt` INTEGER")
                db.execSQL("ALTER TABLE `local_user` ADD COLUMN `termsVersion` TEXT")
            }
        }

        /** v14 → v15: sinir ağları tablosu (ağlar dosya yerine veritabanında; eski dosyalar açılışta taşınır). */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CatalogNetEntity.CREATE_SQL)
            }
        }

        /**
         * v15 → v16 (v39): abonelik planları (`plans`), erişim hakları (`features`, `plan_features`), kullanıcının
         * önbelleklenen aboneliği (`subscriptions`) ve çizim görevinin ilerlemesi (`challenge_progress`; eski ayar
         * dosyasındaki en iyi puanlar açılışta bir kez taşınır). Mevcut veriler değişmez.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(PlanEntity.CREATE_SQL)
                db.execSQL(FeatureEntity.CREATE_SQL)
                db.execSQL(PlanFeatureEntity.CREATE_SQL)
                db.execSQL(PlanFeatureEntity.INDEX_SQL)
                db.execSQL(SubscriptionEntity.CREATE_SQL)
                db.execSQL(ChallengeProgressEntity.CREATE_SQL)
            }
        }

        /**
         * v40 GEÇİCİ yer tutucu (16 → 17): 17 numarası başka bir v40 özelliğine ayrıldı; birleştirmede onun göçüyle
         * değiştirilir ve bu nesne silinir. Bu dalda şema değişmez.
         */
        val MIGRATION_16_17_PLACEHOLDER = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /**
         * v17 → v18 (v40): çocuk tuvaline girişlerin sayacı (`kids_entries`; maskot ilk 5 girişte çizim görevini önerir).
         * Mevcut veriler değişmez; tablo zaten varsa dokunulmaz.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(KidsEntryEntity.CREATE_SQL)
            }
        }

        /**
         * v18 → v19 (v40): tuvalde geçirilen etkin düzenleme süresi (`canvas_time`). Mevcut veriler değişmez; eski
         * tuvallerin süresi 0'dan başlar.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CanvasTimeEntity.CREATE_SQL)
            }
        }

        /**
         * v19 → v20 (v40): abonelik ayrıntıları (`subscriptions`): iptal zamanı ve nedeni, duraklatmanın kendiliğinden
         * devam zamanı. Mevcut satırlar korunur (yeni sütunlar boş); sütun zaten varsa eklenmez.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SubscriptionEntity.CREATE_SQL)
                val existing = HashSet<String>()
                db.query("PRAGMA table_info(`subscriptions`)").use { c ->
                    val name = c.getColumnIndex("name")
                    while (c.moveToNext()) existing += c.getString(name)
                }
                SubscriptionEntity.V40_COLUMNS.forEach { (column, type) ->
                    if (column !in existing) db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `$column` $type")
                }
            }
        }

        /**
         * v20 → v21 (v42): çizgi roman tema paketleri (`theme_packs`, `comic_assets`, `theme_pack_assets`). Mevcut
         * veriler değişmez; tablolar açılışta katalogdan doldurulur.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(com.karapp.drawguidance.data.comicassets.ThemePackEntity.CREATE_SQL)
                db.execSQL(com.karapp.drawguidance.data.comicassets.ComicAssetEntity.CREATE_SQL)
                db.execSQL(com.karapp.drawguidance.data.comicassets.ComicAssetEntity.INDEX_SQL)
                db.execSQL(com.karapp.drawguidance.data.comicassets.ThemePackAssetEntity.CREATE_SQL)
                db.execSQL(com.karapp.drawguidance.data.comicassets.ThemePackAssetEntity.INDEX_SQL)
                // v42 Kaydedilenler: dosya türü ve varsayılan kayıt yerinde mi (eski kayıtlar adresinden bulunur)
                val existing = HashSet<String>()
                db.query("PRAGMA table_info(`saved_images`)").use { c ->
                    val name = c.getColumnIndex("name")
                    while (c.moveToNext()) existing += c.getString(name)
                }
                if ("mime" !in existing) db.execSQL("ALTER TABLE `saved_images` ADD COLUMN `mime` TEXT NOT NULL DEFAULT 'image/png'")
                if ("defaultLocation" !in existing) db.execSQL("ALTER TABLE `saved_images` ADD COLUMN `defaultLocation` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE `saved_images` SET `mime` = 'image/jpeg' WHERE lower(`location`) LIKE '%.jpg' OR lower(`location`) LIKE '%.jpeg' OR lower(`uri`) LIKE '%.jpg' OR lower(`uri`) LIKE '%.jpeg'")
                db.execSQL("UPDATE `saved_images` SET `mime` = 'image/webp' WHERE lower(`location`) LIKE '%.webp' OR lower(`uri`) LIKE '%.webp'")
                db.execSQL("UPDATE `saved_images` SET `defaultLocation` = 0 WHERE `uri` LIKE '%.documents/%' OR `uri` LIKE '%/tree/%' OR `uri` LIKE '%/document/%'")
            }
        }

        /**
         * v43: olay tablosunda gelen teslimatlar (paket, bildirim, duyuru, güncelleme) ve durumları; Android izinleri
         * tablosu. Sütun zaten varsa (yarım kalmış göç) atlanır.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existing = HashSet<String>()
                db.query("PRAGMA table_info(`sync_events`)").use { c ->
                    val name = c.getColumnIndex("name")
                    while (c.moveToNext()) existing += c.getString(name)
                }
                for (col in SyncEventEntity.V43_COLUMNS) {
                    val name = col.substringAfter('`').substringBefore('`')
                    if (name !in existing) db.execSQL("ALTER TABLE `sync_events` ADD COLUMN $col")
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_events_remoteId` ON `sync_events` (`remoteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_events_direction_kind` ON `sync_events` (`direction`, `kind`)")
                db.execSQL(AppPermissionEntity.CREATE_SQL)
            }
        }

        /** v44 çocuk kilidi tablosu. */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(KidsLockEntity.CREATE_SQL)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17_PLACEHOLDER, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
            MIGRATION_21_22, MIGRATION_22_23,
        )
    }
}
