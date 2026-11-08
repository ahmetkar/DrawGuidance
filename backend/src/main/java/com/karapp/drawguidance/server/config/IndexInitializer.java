package com.karapp.drawguidance.server.config;

import com.karapp.drawguidance.server.common.Stores;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Açılışta dizinler (varsa dokunulmaz). Parçalı kümede önce parçalama yapılır ({@link ShardingInitializer}).
 *
 * <ul>
 *   <li>{@code users}: {@code firebaseUid} benzersiz. Eşzamanlı ilk kayıtlarda tek belge kalmasını bu dizin sağlar.
 *       Kısmi dizindir ({@code firebaseUid} alanı olan belgeler): v30'dan kalan, {@code firebaseUid}'siz eski
 *       profil belgeleri dizin kurulumunu bozmaz. Parçalı kümede aynı alanın benzersiz parça anahtarı dizini zaten
 *       varsa yenisi kurulmaz.</li>
 *   <li>Katalog koleksiyonları yalnız {@code _id} ile okunur (öntanımlı dizin yeterli).</li>
 *   <li>v37 {@code error_reports}: alış zamanı ({@code receivedAt}, yeniden eskiye) ve parmak izi + zaman;
 *       {@code error_report_groups}: son görülme ({@code lastSeen}).</li>
 *   <li>v39 {@code plan_features}: plan + özellik benzersiz. {@code subscriptions} ({@code _id} = uid): satın alma
 *       jetonu benzersiz (kısmi: alanı yazı olanlar), eski jeton ({@code linkedPurchaseToken}), bitiş zamanı + durum.
 *       {@code subscription_transactions}: sipariş numarası + tür benzersiz (kısmi: {@code orderId} olanlar; aynı
 *       siparişin aynı hareketi bir kez yazılır), kullanıcı + zaman, jeton. {@code billing_webhook_events}
 *       ({@code _id} = Pub/Sub messageId, tekrar işlemeyi önler): durum + alış zamanı, jeton ve 180 günlük TTL
 *       ({@code receivedAt}; Pub/Sub iletiyi en çok 7 gün yeniden gönderir).</li>
 *   <li>v39 {@code challenge_progress} ({@code _id} = uid): {@code firebaseUid} benzersiz.</li>
 *   <li>v40 {@code deletion_requests}: e-posta + durum + zaman (yineleme denetimi) ve 365 günlük TTL
 *       ({@code createdAt}); {@code support_messages}: 730 günlük TTL ({@code createdAt}); {@code error_reports}
 *       ({@code receivedAt}) ve {@code error_report_groups} ({@code lastSeen}) için de 730 günlük TTL.</li>
 * </ul>
 */
@Component
public class IndexInitializer extends StartupTask {
    public static final String FIREBASE_UID = "firebaseUid";

    private final MongoTemplate mongo;
    private final ObjectProvider<ShardingInitializer> sharding;

    public IndexInitializer(MongoTemplate mongo, ObjectProvider<ShardingInitializer> sharding) {
        super("dizinler", Duration.ofSeconds(15));
        this.mongo = mongo;
        this.sharding = sharding;
    }

    @Override
    protected void runTask() { ensure(); }

    public void ensure() {
        sharding.ifAvailable(ShardingInitializer::shard);
        IndexOperations users = mongo.indexOps(Stores.USERS);
        boolean hasUnique = users.getIndexInfo().stream().anyMatch(i -> i.isUnique()
                && i.getIndexFields().size() == 1 && FIREBASE_UID.equals(i.getIndexFields().get(0).getKey()));
        if (!hasUnique) {
            users.createIndex(new Index().on(FIREBASE_UID, Sort.Direction.ASC).unique().named("firebaseUid_1")
                    .partial(PartialIndexFilter.of(Criteria.where(FIREBASE_UID).exists(true))));
        }
        IndexOperations reports = mongo.indexOps(Stores.ERROR_REPORTS);
        reports.createIndex(new Index().on("receivedAt", Sort.Direction.DESC).named("receivedAt_-1"));
        reports.createIndex(new Index().on("fingerprint", Sort.Direction.ASC).on("receivedAt", Sort.Direction.DESC).named("fingerprint_1_receivedAt_-1"));
        mongo.indexOps(Stores.ERROR_REPORT_GROUPS).createIndex(new Index().on("lastSeen", Sort.Direction.DESC).named("lastSeen_-1"));
        // v40 saklama süresi (gizlilik politikası): raporlar ve son görülmesi 2 yılı geçen gruplar TTL ile silinir
        reports.createIndex(new Index().on("receivedAt", Sort.Direction.ASC).expire(Duration.ofDays(730)).named("receivedAt_ttl"));
        mongo.indexOps(Stores.ERROR_REPORT_GROUPS).createIndex(new Index().on("lastSeen", Sort.Direction.ASC).expire(Duration.ofDays(730)).named("lastSeen_ttl"));
        billing();
        mongo.indexOps(Stores.CHALLENGE_PROGRESS).createIndex(new Index().on(FIREBASE_UID, Sort.Direction.ASC).unique().named("firebaseUid_1"));
        IndexOperations deletion = mongo.indexOps(Stores.DELETION_REQUESTS);
        deletion.createIndex(new Index().on("email", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                .named("email_1_status_1_createdAt_-1"));
        deletion.createIndex(new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofDays(365)).named("createdAt_ttl"));
        mongo.indexOps(Stores.SUPPORT_MESSAGES).createIndex(new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofDays(730)).named("createdAt_ttl"));
        // v40 jeton iptal kayıtları expireAt anında silinir
        // v40 geri bildirim: kullanıcı/zaman ve 730 günlük TTL
        mongo.indexOps(Stores.FEEDBACK).createIndex(new Index().on("uid", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("uid_1_createdAt_-1"));
        mongo.indexOps(Stores.FEEDBACK).createIndex(new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofDays(730)).named("createdAt_ttl"));
        mongo.indexOps(Stores.TOKEN_REVOCATIONS).createIndex(new Index().on("expireAt", Sort.Direction.ASC).expire(Duration.ZERO).named("expireAt_ttl"));
        log.info("MongoDB dizinleri hazır");
    }

    /** v39 abonelik koleksiyonlarının dizinleri. */
    private void billing() {
        mongo.indexOps(Stores.PLAN_FEATURES).createIndex(new Index().on("planId", Sort.Direction.ASC).on("feature", Sort.Direction.ASC)
                .unique().named("planId_1_feature_1"));
        mongo.indexOps(Stores.PLANS).createIndex(new Index().on("productId", Sort.Direction.ASC).named("productId_1"));
        IndexOperations subs = mongo.indexOps(Stores.SUBSCRIPTIONS);
        subs.createIndex(new Index().on("purchaseToken", Sort.Direction.ASC).unique().named("purchaseToken_1")
                .partial(PartialIndexFilter.of(Criteria.where("purchaseToken").type(2))));
        subs.createIndex(new Index().on("linkedPurchaseToken", Sort.Direction.ASC).named("linkedPurchaseToken_1")
                .partial(PartialIndexFilter.of(Criteria.where("linkedPurchaseToken").type(2))));
        subs.createIndex(new Index().on("expiryTime", Sort.Direction.ASC).on("status", Sort.Direction.ASC).named("expiryTime_1_status_1"));
        IndexOperations txs = mongo.indexOps(Stores.SUBSCRIPTION_TRANSACTIONS);
        txs.createIndex(new Index().on("orderId", Sort.Direction.ASC).on("type", Sort.Direction.ASC).unique().named("orderId_1_type_1")
                .partial(PartialIndexFilter.of(Criteria.where("orderId").exists(true))));
        txs.createIndex(new Index().on("userId", Sort.Direction.ASC).on("at", Sort.Direction.DESC).named("userId_1_at_-1"));
        txs.createIndex(new Index().on("purchaseToken", Sort.Direction.ASC).named("purchaseToken_1"));
        IndexOperations events = mongo.indexOps(Stores.BILLING_WEBHOOK_EVENTS);
        events.createIndex(new Index().on("receivedAt", Sort.Direction.ASC).expire(Duration.ofDays(180)).named("receivedAt_ttl"));
        events.createIndex(new Index().on("status", Sort.Direction.ASC).on("receivedAt", Sort.Direction.DESC).named("status_1_receivedAt_-1"));
        events.createIndex(new Index().on("purchaseToken", Sort.Direction.ASC).named("purchaseToken_1")
                .partial(PartialIndexFilter.of(Criteria.where("purchaseToken").type(2))));
    }
}
