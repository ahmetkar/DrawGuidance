package com.karapp.drawguidance.server.challenge;

import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * v39 çocuk modu "Çizim Görevi" ilerlemesi ({@code challenge_progress}): Firebase uid başına tek belge
 * ({@code _id} = uid, {@code firebaseUid} benzersiz dizinli), {@code levels} haritası seviye sırası (0–49, yazı
 * anahtar; v40'ta 15'ten 50 seviyeye çıktı, eski 0–14 kayıtları aynı kalır) → {@code {bestScore, stars, updatedAt}}.
 *
 * <p><b>Birleştirme:</b> gelen her seviye için en yüksek puan kalır, puan asla düşmez. Puan artınca yıldız sunucuda
 * yeniden hesaplanır ({@link #stars}) ve {@code updatedAt} gelen değer (yoksa sunucu zamanı) olur; eşit ya da düşük
 * puan yok sayılır. Eşzamanlı yazmalar iyimser kilitle ({@code version}) birleştirilir, çakışmada yeniden denenir.
 */
@Service
public class ChallengeService {
    /** Seviye sayısı (sıra 0–49). v40: 15 → 50 (son 15 seviye uygulamada Premium; sunucu yalnız puan tutar). */
    public static final int LEVELS = 50;
    /** Geçme puanı. */
    public static final int PASS_SCORE = 60;
    static final int MAX_ENTRIES = 100;

    private final MongoTemplate mongo;

    public ChallengeService(MongoTemplate mongo) { this.mongo = mongo; }

    /** Gelen seviye kaydı ({@code updatedAt}: istemcinin Unix ms zamanı, isteğe bağlı). */
    public record Entry(Integer level, Integer bestScore, Long updatedAt) {}

    /** Yanıttaki seviye. */
    public record Level(int level, int bestScore, int stars, boolean passed, long updatedAt) {}

    /** {@code GET}/{@code PUT /api/v1/challenge/progress} yanıtı. */
    public record Progress(List<Level> levels, int levelCount, int passScore, int passedCount, int totalStars, Instant updatedAt) {}

    /** Puan → yıldız: 60'ın altı 0, 60–74 1, 75–89 2, 90–100 3. */
    public static int stars(int score) {
        if (score >= 90) return 3;
        if (score >= 75) return 2;
        if (score >= PASS_SCORE) return 1;
        return 0;
    }

    private MongoCollection<Document> coll() { return mongo.getCollection(Stores.CHALLENGE_PROGRESS); }

    public Progress get(String uid) { return view(coll().find(Filters.eq("_id", uid)).first()); }

    public Progress merge(String uid, List<Entry> entries) {
        if (entries == null) throw ApiException.badRequest("validation", "levels listesi gerekli");
        if (entries.size() > MAX_ENTRIES) throw ApiException.badRequest("validation", "En çok " + MAX_ENTRIES + " seviye kaydı gönderilebilir");
        for (Entry e : entries) validate(e);
        for (int attempt = 0; attempt < 10; attempt++) {
            Document current = coll().find(Filters.eq("_id", uid)).first();
            Document levels = current == null ? new Document() : new Document(current.get("levels", Document.class) == null
                    ? Map.of() : current.get("levels", Document.class));
            boolean changed = false;
            long now = System.currentTimeMillis();
            for (Entry e : entries) {
                String key = String.valueOf(e.level());
                Document old = levels.get(key, Document.class);
                int oldScore = old == null || !(old.get("bestScore") instanceof Number n) ? -1 : n.intValue();
                if (e.bestScore() > oldScore) {
                    levels.put(key, new Document("bestScore", e.bestScore()).append("stars", stars(e.bestScore()))
                            .append("updatedAt", e.updatedAt() == null ? now : e.updatedAt()));
                    changed = true;
                }
            }
            if (!changed && current != null) return view(current);
            long version = current == null ? 0L : ((Number) current.getOrDefault("version", 0L)).longValue();
            Document next = new Document("_id", uid).append("firebaseUid", uid).append("levels", sorted(levels))
                    .append("version", version + 1).append("updatedAt", new Date())
                    .append("createdAt", current == null ? new Date() : current.get("createdAt"));
            try {
                if (current == null) {
                    coll().insertOne(next);
                    return view(next);
                }
                if (coll().replaceOne(Filters.and(Filters.eq("_id", uid), Filters.eq("version", version)), next).getMatchedCount() == 1) {
                    return view(next);
                }
            } catch (MongoWriteException ex) {
                if (ex.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw ex;
            }
            // Çakışma: başka bir istek araya girdi, yeniden oku ve birleştir
        }
        throw ApiException.conflict("conflict", "İlerleme aynı anda çok kez güncellendi; yeniden deneyin");
    }

    private static void validate(Entry e) {
        if (e == null || e.level() == null || e.level() < 0 || e.level() >= LEVELS) {
            throw ApiException.badRequest("invalid_level", "level 0–" + (LEVELS - 1) + " arasında olmalı");
        }
        if (e.bestScore() == null || e.bestScore() < 0 || e.bestScore() > 100) {
            throw ApiException.badRequest("invalid_score", "bestScore 0–100 arasında olmalı");
        }
        if (e.updatedAt() != null && e.updatedAt() < 0) throw ApiException.badRequest("validation", "updatedAt ≥ 0 olmalı");
    }

    private static Document sorted(Document levels) {
        TreeMap<Integer, Object> byLevel = new TreeMap<>();
        levels.forEach((k, v) -> byLevel.put(Integer.parseInt(k), v));
        Document out = new Document();
        byLevel.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    static Progress view(Document doc) {
        List<Level> out = new ArrayList<>();
        int passed = 0, totalStars = 0;
        Document levels = doc == null ? null : doc.get("levels", Document.class);
        if (levels != null) {
            for (Map.Entry<String, Object> e : sorted(levels).entrySet()) {
                if (!(e.getValue() instanceof Document d)) continue;
                int score = ((Number) d.getOrDefault("bestScore", 0)).intValue();
                int stars = stars(score);
                long at = d.get("updatedAt") instanceof Number n ? n.longValue() : 0L;
                out.add(new Level(Integer.parseInt(e.getKey()), score, stars, score >= PASS_SCORE, at));
                if (score >= PASS_SCORE) passed++;
                totalStars += stars;
            }
        }
        Instant updated = doc != null && doc.get("updatedAt") instanceof Date d ? d.toInstant() : null;
        return new Progress(out, LEVELS, PASS_SCORE, passed, totalStars, updated);
    }
}
