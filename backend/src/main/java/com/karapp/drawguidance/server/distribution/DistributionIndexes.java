package com.karapp.drawguidance.server.distribution;

import com.karapp.drawguidance.server.config.StartupTask;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * v43 dağıtım koleksiyonlarının dizinleri (açılışta; varsa dokunulmaz):
 * <ul>
 *   <li>{@code deliveries}: {@code (deviceKey, kind, refId, version)} benzersiz (aynı şey bir cihaza bir kez);
 *       işçi için {@code (stage, nextAttemptAt)}; gelen kutusu için {@code (uid, deviceKey, stage)}; gönderim
 *       raporu için {@code (dispatchId, stage)}; bitmiş teslimatlar 365 gün sonra silinir (TTL, {@code deliveredAt}).</li>
 *   <li>{@code devices}: {@code uid}, {@code lastSeenAt} ve {@code inboxChangedAt} (uzun yoklama izleyicisi).</li>
 *   <li>{@code dispatches}: {@code status}; içerik koleksiyonları {@code mode}/{@code createdAt}.</li>
 * </ul>
 */
@Component
public class DistributionIndexes extends StartupTask {
    private final MongoTemplate mongo;

    public DistributionIndexes(MongoTemplate mongo) {
        super("dağıtım dizinleri", Duration.ofSeconds(15));
        this.mongo = mongo;
    }

    @Override
    protected void runTask() {
        IndexOperations del = mongo.indexOps(Dist.DELIVERIES);
        del.createIndex(new Index().on("deviceKey", Sort.Direction.ASC).on("kind", Sort.Direction.ASC).on("refId", Sort.Direction.ASC)
                .on("version", Sort.Direction.ASC).unique().named("device_kind_ref_version"));
        del.createIndex(new Index().on("stage", Sort.Direction.ASC).on("nextAttemptAt", Sort.Direction.ASC).named("stage_1_nextAttemptAt_1"));
        del.createIndex(new Index().on("uid", Sort.Direction.ASC).on("deviceKey", Sort.Direction.ASC).on("stage", Sort.Direction.ASC).named("uid_device_stage"));
        del.createIndex(new Index().on("dispatchId", Sort.Direction.ASC).on("stage", Sort.Direction.ASC).named("dispatchId_1_stage_1"));
        del.createIndex(new Index().on("deliveredAt", Sort.Direction.ASC).expire(Duration.ofDays(365)).named("deliveredAt_ttl"));
        IndexOperations dev = mongo.indexOps(Dist.DEVICES);
        dev.createIndex(new Index().on("uid", Sort.Direction.ASC).named("uid_1"));
        dev.createIndex(new Index().on("lastSeenAt", Sort.Direction.DESC).named("lastSeenAt_-1"));
        // Uzun yoklamaların kopyalar arası uyandırılması (son değişen gelen kutuları)
        dev.createIndex(new Index().on("inboxChangedAt", Sort.Direction.ASC).named("inboxChangedAt_1"));
        mongo.indexOps(Dist.DISPATCHES).createIndex(new Index().on("status", Sort.Direction.ASC).named("status_1"));
        mongo.indexOps(Dist.PACKAGES).createIndex(new Index().on("mode", Sort.Direction.ASC).on("updatedAt", Sort.Direction.DESC).named("mode_1_updatedAt_-1"));
        log.info("dağıtım dizinleri hazır");
    }
}
