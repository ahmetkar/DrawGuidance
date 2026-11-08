package com.karapp.drawguidance.server.health;

import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sağlık kontrolü ({@code /api/health}; uygulamanın Ayarlar → Bağlantıyı test et ve ağda sunucu arama bunu kullanır):
 * servis adı ve sürümü, veritabanı durumu ve küme türü (tek sunucu, kopya kümesi, parçalı küme).
 */
@RestController
public class HealthController {
    private final MongoTemplate mongo;
    private final String version;

    public HealthController(MongoTemplate mongo, DrawGuidanceProperties props) {
        this.mongo = mongo;
        this.version = props.version();
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        String db = "down";
        String topology = "UNKNOWN";
        try {
            Document hello = mongo.getDb().runCommand(new Document("hello", 1));
            db = "up";
            if ("isdbgrid".equals(hello.getString("msg"))) topology = "SHARDED";
            else if (hello.getString("setName") != null) topology = "REPLICA_SET";
            else topology = "STANDALONE";
        } catch (RuntimeException ignored) { }
        out.put("status", "up".equals(db) ? "ok" : "degraded");
        out.put("service", "drawguidance");
        out.put("version", version);
        out.put("database", db);
        out.put("topology", topology);
        return out;
    }
}
