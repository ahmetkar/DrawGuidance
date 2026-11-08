package com.karapp.drawguidance.server.config;

import com.mongodb.MongoCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v40 Docker sırları ({@code *_FILE}) ve URI'siz Mongo kimliği. */
class SecretFilesEnvironmentTest {
    @TempDir
    Path dir;

    Path file(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void readsListedSecretsAndTrimsTrailingNewlines() throws Exception {
        Map<String, Object> out = SecretFilesEnvironment.resolve(Map.of(
                "SLACK_WEBHOOK_URL_FILE", file("slack", "https://hooks.slack.example/x\n").toString(),
                "MONGO_APP_PASSWORD_FILE", file("pw", "p@ss word\r\n").toString(),
                "UNLISTED_FILE", file("other", "x").toString()));
        assertEquals("https://hooks.slack.example/x", out.get("SLACK_WEBHOOK_URL"));
        assertEquals("p@ss word", out.get("MONGO_APP_PASSWORD"));
        assertFalse(out.containsKey("UNLISTED"), "listede olmayan *_FILE okunmaz");
    }

    @Test
    void serviceAccountFileIsPassedAsPath() throws Exception {
        Path json = file("play.json", "{\"client_email\":\"x\"}");
        Map<String, Object> out = SecretFilesEnvironment.resolve(Map.of("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_FILE", json.toString()));
        assertEquals(json.toString(), out.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"));
    }

    @Test
    void conflictsAndMissingFilesFailWithoutLeakingValues() throws Exception {
        Path f = file("salt", "gizli-tuz-degeri");
        IllegalStateException both = assertThrows(IllegalStateException.class, () -> SecretFilesEnvironment.resolve(Map.of(
                "IP_HASH_SALT", "baska-deger", "IP_HASH_SALT_FILE", f.toString())));
        assertFalse(both.getMessage().contains("gizli-tuz-degeri") || both.getMessage().contains("baska-deger"));
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> SecretFilesEnvironment.resolve(Map.of(
                "IP_HASH_SALT_FILE", dir.resolve("yok").toString())));
        assertTrue(missing.getMessage().contains("IP_HASH_SALT_FILE"));
        assertThrows(IllegalStateException.class, () -> SecretFilesEnvironment.resolve(Map.of(
                "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_FILE", dir.resolve("yok.json").toString())));
        assertTrue(SecretFilesEnvironment.resolve(Map.of("IP_HASH_SALT_FILE", " ")).isEmpty());
    }

    @Test
    void mongoCredentialFromSeparatePassword() {
        DrawGuidanceProperties.Mongo none = new DrawGuidanceProperties.Mongo(null, null, null, null, null, null, null, null, null, null, null);
        assertNull(MongoClientConfig.credential(none, "mongodb://h:27017/db"));
        DrawGuidanceProperties.Mongo m = new DrawGuidanceProperties.Mongo(null, null, null, null, null, null, null, null, "app", "s3cr3t", null);
        MongoCredential c = MongoClientConfig.credential(m, "mongodb://mongo1:27017/drawguidance?replicaSet=rs0&authSource=drawguidance");
        assertEquals("app", c.getUserName());
        assertEquals("drawguidance", c.getSource());
        assertArrayEquals("s3cr3t".toCharArray(), c.getPassword());
        assertEquals("admin", MongoClientConfig.credential(m, "mongodb://h:27017/?replicaSet=rs0").getSource());
        assertEquals("dg", MongoClientConfig.credential(m, "mongodb://h:27017/dg").getSource());
        assertFalse(m.toString().contains("s3cr3t"), "parola toString'de görünmez");
        DrawGuidanceProperties.Mongo noUser = new DrawGuidanceProperties.Mongo(null, null, null, null, null, null, null, null, null, "s", null);
        assertThrows(IllegalStateException.class, () -> MongoClientConfig.credential(noUser, "mongodb://h/db"));
    }
}
