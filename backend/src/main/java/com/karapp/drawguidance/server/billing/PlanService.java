package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.common.Stores;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** v39 planlar ve erişim hakları ({@code plans} + {@code plan_features}; ikisi de küçük, her istekte okunur). */
@Service
public class PlanService {
    private final MongoTemplate mongo;

    public PlanService(MongoTemplate mongo) { this.mongo = mongo; }

    /** {@code GET /api/v1/plans} öğesi. {@code limits}: yalnız sınırı olan özellikler (anahtar → sayı). */
    public record PlanView(String id, String name, String description, String productId, String basePlanId, Price price,
                           String period, int sort, List<String> features, Map<String, Long> limits) {}

    public record Price(long amountMicros, String currency, String display) {}

    /** Plan özellikleri: açık özellik anahtarları (sıralı) ve sınırları. */
    public record Features(List<String> keys, Map<String, Long> limits) {
        static final Features NONE = new Features(List.of(), Map.of());
    }

    /** Etkin planlar (sıraya göre), özellikleriyle. */
    public List<PlanView> activePlans() {
        List<PlanView> out = new ArrayList<>();
        for (Document p : mongo.getCollection(Stores.PLANS).find(Filters.eq("active", true)).sort(Sorts.ascending("sort", "_id"))) {
            out.add(view(p));
        }
        return out;
    }

    public Document plan(String id) {
        return mongo.getCollection(Stores.PLANS).find(Filters.eq("_id", id)).first();
    }

    /** Play ürününe (ve varsa temel planına) karşılık gelen plan; yoksa {@code null}. */
    public Document planForProduct(String productId, String basePlanId) {
        if (productId == null) return null;
        for (Document p : mongo.getCollection(Stores.PLANS).find(Filters.eq("productId", productId))) {
            String base = p.getString("basePlanId");
            if (base == null || basePlanId == null || base.equals(basePlanId)) return p;
        }
        return null;
    }

    public Features features(String planId) {
        if (planId == null) return Features.NONE;
        List<String> keys = new ArrayList<>();
        Map<String, Long> limits = new LinkedHashMap<>();
        for (Document f : mongo.getCollection(Stores.PLAN_FEATURES)
                .find(Filters.and(Filters.eq("planId", planId), Filters.ne("enabled", false))).sort(Sorts.ascending("sort", "feature"))) {
            String key = f.getString("feature");
            keys.add(key);
            if (f.get("limit") instanceof Number n) limits.put(key, n.longValue());
        }
        return new Features(List.copyOf(keys), java.util.Collections.unmodifiableMap(limits));
    }

    private PlanView view(Document p) {
        Document price = p.get("price", Document.class);
        Features f = features(p.getString("_id"));
        return new PlanView(p.getString("_id"), p.getString("name"), p.getString("description"), p.getString("productId"),
                p.getString("basePlanId"),
                price == null ? null : new Price(num(price.get("amountMicros")), price.getString("currency"), price.getString("display")),
                p.getString("period"), (int) num(p.get("sort")), f.keys(), f.limits());
    }

    private static long num(Object o) { return o instanceof Number n ? n.longValue() : 0L; }
}
