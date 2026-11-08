package com.karapp.drawguidance.server.support;

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Bütün test sınıflarının paylaştığı gerçek MongoDB 7 (Docker, tek üyeli kopya kümesi). Bir kez başlar, JVM
 * kapanınca Testcontainers kaldırır. Her test bağlamı kendi veritabanı adını kullanır.
 */
public final class TestMongo {
    private static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    static {
        MONGO.start();
    }

    private TestMongo() {}

    public static String url(String database) { return MONGO.getReplicaSetUrl(database); }
}
