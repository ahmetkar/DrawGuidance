package com.karapp.drawguidance.server.config;

import com.karapp.drawguidance.server.common.Stores;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parçalı küme (mongos) için açılışta parçalama ({@link IndexInitializer} dizinlerden önce çağırır). Yalnız
 * {@code users} parçalanır: anahtar {@code {firebaseUid: 1}} ve benzersiz. MongoDB parçalı koleksiyonda benzersiz
 * dizini yalnız parça anahtarıyla başlıyorsa uygulayabildiği için anahtar {@code _id} karması değil
 * {@code firebaseUid}'dir; bütün kullanıcı sorguları da bu alanla yapıldığından tek parçaya gider (Firebase
 * kimlikleri rastgele olduğundan parçalara dağılır). Katalog koleksiyonları küçüktür, parçalanmaz. İşlemler tekrar
 * çalıştırılabilir (zaten parçalıysa atlanır). Yalnız {@code drawguidance.mongo.sharding.enabled=true} iken.
 */
@Component
@ConditionalOnProperty(prefix = "drawguidance.mongo.sharding", name = "enabled", havingValue = "true")
public class ShardingInitializer {
    private static final Logger log = LoggerFactory.getLogger(ShardingInitializer.class);

    /** Koleksiyon → parça anahtarı (hepsi benzersiz). */
    public static final Map<String, Document> KEYS = Map.of(Stores.USERS, new Document(IndexInitializer.FIREBASE_UID, 1));

    private final MongoClient client;
    private final MongoTemplate mongo;

    public ShardingInitializer(MongoClient client, MongoTemplate mongo) {
        this.client = client;
        this.mongo = mongo;
    }

    public List<String> shard() {
        var admin = client.getDatabase("admin");
        Document hello = admin.runCommand(new Document("hello", 1));
        if (!"isdbgrid".equals(hello.getString("msg"))) {
            log.warn("Parçalama açık ama bağlantı mongos değil; atlandı");
            return List.of();
        }
        String db = mongo.getDb().getName();
        ignoreKnown(() -> admin.runCommand(new Document("enableSharding", db)));
        List<String> done = new ArrayList<>();
        KEYS.forEach((coll, key) -> {
            ignoreKnown(() -> admin.runCommand(new Document("shardCollection", db + "." + coll).append("key", key).append("unique", true)));
            done.add(coll);
        });
        log.info("Parçalama hazır: {} ({})", db, done);
        return done;
    }

    private static void ignoreKnown(Runnable r) {
        try {
            r.run();
        } catch (MongoCommandException e) {
            // 23: AlreadyInitialized, 20: zaten parçalı (eski sürümler)
            if (e.getErrorCode() != 23 && e.getErrorCode() != 20 && !String.valueOf(e.getErrorMessage()).contains("already")) throw e;
        }
    }
}
