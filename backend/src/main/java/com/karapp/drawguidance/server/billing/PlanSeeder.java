package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.billing.apple.AppleBillingProperties;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.config.StartupTask;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * v39 açılışta plan tohumlaması ({@code plans}, {@code plan_features}). Tekrar çalıştırılabilir: planlar
 * {@code _id} ile yerine yazılır (Play ürün/temel plan kimliği ve fiyat {@code drawguidance.billing.premium.*}
 * ayarlarından), tohumdaki planların listede olmayan özellikleri silinir. Tohumda olmayan planlara dokunulmaz.
 * Birden çok sunucu aynı anda çalıştırsa da sonuç aynıdır (yerine yazma).
 */
@Component
public class PlanSeeder extends StartupTask {
    private final MongoTemplate mongo;
    private final DrawGuidanceProperties.Premium premium;
    private final List<String> appleProductIds;

    public PlanSeeder(MongoTemplate mongo, DrawGuidanceProperties props, AppleBillingProperties apple) {
        super("plan tohumlaması", Duration.ofSeconds(15));
        this.mongo = mongo;
        this.premium = props.billing().premium();
        this.appleProductIds = apple.productIds();
    }

    @Override
    protected void runTask() { seed(); }

    /** Tohumlanan planlar (sırasıyla). */
    public List<Document> plans() {
        Document free = new Document("_id", Plans.FREE).append("name", "Ücretsiz")
                .append("description", "Temel çizim, not ve çizgi roman araçları; reklamlı")
                .append("productId", null).append("basePlanId", null).append("appleProductIds", List.of())
                .append("price", new Document("amountMicros", 0L).append("currency", premium.currency()).append("display", "0 TL"))
                .append("period", null).append("active", true).append("sort", 0);
        Document paid = new Document("_id", Plans.PREMIUM).append("name", "Premium")
                .append("description", "Gelişmiş resim, şekil, diyagram, çizgi roman ve 3B önerileri; reklamsız")
                .append("productId", premium.productId()).append("basePlanId", premium.basePlanId())
                // v50 App Store ürünleri (aynı Premium hakları)
                .append("appleProductIds", appleProductIds)
                .append("price", new Document("amountMicros", premium.priceMicros()).append("currency", premium.currency())
                        .append("display", premium.priceDisplay()))
                .append("period", premium.period()).append("active", true).append("sort", 1);
        return List.of(free, paid);
    }

    /** Plan → özellik anahtarları (Ücretsiz: hiçbiri, Premium: hepsi). */
    static List<String> featuresOf(String planId) {
        return Plans.PREMIUM.equals(planId) ? Plans.FEATURE_KEYS : List.of();
    }

    public void seed() {
        MongoCollection<Document> plans = mongo.getCollection(Stores.PLANS);
        MongoCollection<Document> features = mongo.getCollection(Stores.PLAN_FEATURES);
        Date now = new Date();
        for (Document plan : plans()) {
            String id = plan.getString("_id");
            plans.replaceOne(Filters.eq("_id", id), new Document(plan).append("updatedAt", now), new ReplaceOptions().upsert(true));
            List<String> keys = featuresOf(id);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                String fid = id + ":" + keys.get(i);
                ids.add(fid);
                // limit: null = sınırsız (ileride sayıyla sınırlı özellikler için)
                Document f = new Document("_id", fid).append("planId", id).append("feature", keys.get(i))
                        .append("enabled", true).append("limit", null).append("sort", i).append("updatedAt", now);
                features.replaceOne(Filters.eq("_id", fid), f, new ReplaceOptions().upsert(true));
            }
            features.deleteMany(Filters.and(Filters.eq("planId", id), Filters.nin("_id", ids)));
        }
        log.info("Planlar hazır: premium = {}/{} + App Store {} ({} özellik)", premium.productId(), premium.basePlanId(), appleProductIds,
                Plans.FEATURE_KEYS.size());
    }
}
