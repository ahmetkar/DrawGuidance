package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v39 çocuk modu "Çizim Görevi" ilerleme eşitlemesi: en yüksek puan kalır, kullanıcılar ayrı, jeton zorunlu. */
class ChallengeApiTest extends ApiContextTest {
    static final String PATH = "/api/v1/challenge/progress";

    @Autowired
    MongoTemplate mongo;

    static String newUid() { return "uid" + UUID.randomUUID().toString().replace("-", "").substring(0, 25); }

    Res get(String uid) { return http.call("GET", PATH, null, "Authorization", "Bearer " + TestTokens.valid(uid)); }

    Res put(String uid, String body) { return http.call("PUT", PATH, body, "Authorization", "Bearer " + TestTokens.valid(uid)); }

    static JsonNode level(JsonNode progress, int level) {
        for (JsonNode l : progress.get("levels")) if (l.get("level").asInt() == level) return l;
        return null;
    }

    @Test
    void emptyProgressForNewUser() {
        Res r = get(newUid());
        assertEquals(200, r.status(), r.body());
        JsonNode p = r.json();
        assertEquals(0, p.get("levels").size());
        assertEquals(50, p.get("levelCount").asInt());
        assertEquals(60, p.get("passScore").asInt());
        assertEquals(0, p.get("passedCount").asInt());
        assertEquals(0, p.get("totalStars").asInt());
        assertTrue(p.get("updatedAt").isNull());
    }

    @Test
    void mergeKeepsTheBestScore() {
        String uid = newUid();
        Res first = put(uid, "{\"levels\":[{\"level\":0,\"bestScore\":55,\"updatedAt\":1000},{\"level\":3,\"bestScore\":80,\"updatedAt\":2000}]}");
        assertEquals(200, first.status(), first.body());
        JsonNode p = first.json();
        assertEquals(2, p.get("levels").size());
        assertEquals(55, level(p, 0).get("bestScore").asInt());
        assertEquals(0, level(p, 0).get("stars").asInt());
        assertFalse(level(p, 0).get("passed").asBoolean());
        assertEquals(1000, level(p, 0).get("updatedAt").asLong());
        assertEquals(2, level(p, 3).get("stars").asInt());
        assertTrue(level(p, 3).get("passed").asBoolean());
        assertEquals(1, p.get("passedCount").asInt());
        assertEquals(2, p.get("totalStars").asInt());
        assertTrue(Instant.parse(p.get("updatedAt").asText()).isAfter(Instant.now().minusSeconds(60)));

        // Düşük puan yok sayılır, yüksek puan alınır; doğrudan dizi gövdesi de kabul edilir
        Res second = put(uid, "[{\"level\":3,\"bestScore\":40,\"updatedAt\":9000},{\"level\":0,\"bestScore\":92,\"updatedAt\":3000},"
                + "{\"level\":14,\"bestScore\":60},{\"level\":0,\"bestScore\":70,\"updatedAt\":4000}]");
        assertEquals(200, second.status(), second.body());
        JsonNode q = second.json();
        assertEquals(List.of(0, 3, 14), levels(q));
        assertEquals(92, level(q, 0).get("bestScore").asInt());
        assertEquals(3, level(q, 0).get("stars").asInt());
        assertEquals(3000, level(q, 0).get("updatedAt").asLong());
        assertEquals(80, level(q, 3).get("bestScore").asInt(), "puan asla düşmez");
        assertEquals(2000, level(q, 3).get("updatedAt").asLong());
        assertEquals(1, level(q, 14).get("stars").asInt());
        assertTrue(level(q, 14).get("updatedAt").asLong() > 0, "updatedAt yoksa sunucu zamanı");
        assertEquals(3, q.get("passedCount").asInt());
        assertEquals(6, q.get("totalStars").asInt());
        assertEquals(q, get(uid).json());

        // Değişiklik yoksa sürüm artmaz
        long version = ((Number) doc(uid).get("version")).longValue();
        assertEquals(200, put(uid, "{\"levels\":[{\"level\":3,\"bestScore\":80}]}").status());
        assertEquals(version, ((Number) doc(uid).get("version")).longValue());
        assertEquals(uid, doc(uid).getString("firebaseUid"));
    }

    /** v40 50 seviye: 15–49 da kabul edilir, eski 0–14 kayıtları korunur ve en iyi puan birleştirmesi sürer. */
    @Test
    void fiftyLevelsAcceptedAndOldLevelsKept() {
        String uid = newUid();
        // v39 istemcisi yalnız 0–14 gönderir
        assertEquals(200, put(uid, "{\"levels\":[{\"level\":0,\"bestScore\":80},{\"level\":14,\"bestScore\":65}]}").status());
        Res r = put(uid, "{\"levels\":[{\"level\":15,\"bestScore\":70},{\"level\":35,\"bestScore\":90},{\"level\":49,\"bestScore\":61},"
                + "{\"level\":14,\"bestScore\":50}]}");
        assertEquals(200, r.status(), r.body());
        JsonNode p = r.json();
        assertEquals(50, p.get("levelCount").asInt());
        assertEquals(List.of(0, 14, 15, 35, 49), levels(p));
        assertEquals(65, level(p, 14).get("bestScore").asInt(), "puan asla düşmez");
        assertEquals(3, level(p, 35).get("stars").asInt());
        assertEquals(5, p.get("passedCount").asInt());
        // Belgede seviyeler sayı sırasıyla (yazı sırası değil: "15" "2"den sonra)
        assertEquals(List.of("0", "14", "15", "35", "49"), new ArrayList<>(doc(uid).get("levels", Document.class).keySet()));
        // 50 seviyenin hepsi tek istekte
        StringBuilder all = new StringBuilder("{\"levels\":[");
        for (int i = 0; i < 50; i++) all.append(i == 0 ? "" : ",").append("{\"level\":").append(i).append(",\"bestScore\":95}");
        JsonNode full = put(uid, all.append("]}").toString()).json();
        assertEquals(50, full.get("levels").size());
        assertEquals(50, full.get("passedCount").asInt());
        assertEquals(150, full.get("totalStars").asInt());
    }

    @Test
    void concurrentMergesKeepTheMaximum() throws Exception {
        String uid = newUid();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Res>> all = new ArrayList<>();
            for (int s = 50; s <= 90; s += 5) {
                int score = s;
                all.add(pool.submit(() -> put(uid, "{\"levels\":[{\"level\":7,\"bestScore\":" + score + "}]}")));
            }
            for (Future<Res> f : all) assertEquals(200, f.get().status(), f.get().body());
        } finally {
            pool.shutdown();
        }
        assertEquals(90, level(get(uid).json(), 7).get("bestScore").asInt());
        assertEquals(1, mongo.getCollection(Stores.CHALLENGE_PROGRESS).countDocuments(Filters.eq("firebaseUid", uid)));
    }

    @Test
    void usersAreIsolated() {
        String a = newUid(), b = newUid();
        assertEquals(200, put(a, "{\"levels\":[{\"level\":1,\"bestScore\":99}]}").status());
        assertEquals(200, put(b, "{\"levels\":[{\"level\":2,\"bestScore\":61}]}").status());
        assertEquals(List.of(1), levels(get(a).json()));
        assertEquals(List.of(2), levels(get(b).json()));
        assertEquals(0, get(newUid()).json().get("levels").size());
    }

    @Test
    void requiresTokenAndValidBody() {
        assertEquals(401, http.get(PATH).status());
        assertEquals("unauthorized", http.get(PATH).error());
        assertEquals(401, http.call("PUT", PATH, "{\"levels\":[]}").status());
        assertEquals(401, http.get(PATH, "Authorization", "Bearer bozuk").status());

        String uid = newUid();
        assertEquals("invalid_level", put(uid, "{\"levels\":[{\"level\":50,\"bestScore\":10}]}").error());
        assertEquals("invalid_level", put(uid, "{\"levels\":[{\"level\":-1,\"bestScore\":10}]}").error());
        assertEquals("invalid_score", put(uid, "{\"levels\":[{\"level\":1,\"bestScore\":101}]}").error());
        assertEquals("invalid_score", put(uid, "{\"levels\":[{\"level\":1,\"bestScore\":\"80\"}]}").error());
        assertEquals("validation", put(uid, "{\"items\":[]}").error());
        assertEquals(400, put(uid, "bozuk").status());
        assertEquals(0, get(uid).json().get("levels").size(), "geçersiz istek hiçbir şey yazmaz");
    }

    Document doc(String uid) { return mongo.getCollection(Stores.CHALLENGE_PROGRESS).find(Filters.eq("_id", uid)).first(); }

    static List<Integer> levels(JsonNode p) {
        List<Integer> out = new ArrayList<>();
        p.get("levels").forEach(l -> out.add(l.get("level").asInt()));
        return out;
    }
}
