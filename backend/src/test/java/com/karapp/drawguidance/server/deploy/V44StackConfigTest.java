package com.karapp.drawguidance.server.deploy;

import com.karapp.drawguidance.server.config.SecretFilesEnvironment;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v44 üretim yığını (docker/swarm/stack.yml) yayına hazır: v43 dağıtımının zorunlu ayarları (Cloud Storage kovası,
 * hizmet hesabı, cihaz bilgisi anahtarı) Docker sırrı olarak verilir; hiçbir sır düz değer olarak yazılmaz.
 */
class V44StackConfigTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String file) throws Exception {
        try (Reader r = Files.newBufferedReader(Path.of(file))) { return new Yaml().load(r); }
    }

    @Test
    @SuppressWarnings("unchecked")
    void apiServiceHasDistributionSecretsAndNoInlineSecrets() throws Exception {
        Map<String, Object> stack = load("docker/swarm/stack.yml");
        Map<String, Object> api = (Map<String, Object>) ((Map<String, Object>) stack.get("services")).get("api");
        Map<String, Object> env = (Map<String, Object>) api.get("environment");
        assertEquals("prod", env.get("SPRING_PROFILES_ACTIVE"));
        assertEquals("dgpackets", env.get("GCS_BUCKET"));
        assertEquals("/run/secrets/gcs.json", env.get("GCS_SERVICE_ACCOUNT_JSON"));
        assertEquals("/run/secrets/device_data_key", env.get("DEVICE_DATA_KEY_FILE"));
        assertFalse(env.containsKey("DEVICE_DATA_KEY"), "anahtar düz değer olarak verilmez");
        assertEquals("ahmetkar8040@gmail.com", env.get("BILLING_EXEMPT_EMAILS"));
        // Parola, anahtar, webhook gibi değerler ortamda düz yazılmaz (yalnız *_FILE)
        for (String k : env.keySet()) {
            if (k.endsWith("_FILE")) assertTrue(String.valueOf(env.get(k)).startsWith("/run/secrets/"), k);
            for (String s : SecretFilesEnvironment.SECRETS) {
                if (s.equals("FIREBASE_PROJECT_ID") || s.equals("MONGODB_URI") || s.startsWith("PUBSUB_")) continue;
                assertFalse(k.equals(s), k + " düz değer olmamalı");
            }
        }
        List<Map<String, Object>> secrets = (List<Map<String, Object>>) api.get("secrets");
        assertTrue(secrets.stream().anyMatch(m -> "gcs_service_account".equals(m.get("source")) && "gcs.json".equals(m.get("target"))));
        assertTrue(secrets.stream().anyMatch(m -> "device_data_key".equals(m.get("source"))));
        Map<String, Object> declared = (Map<String, Object>) stack.get("secrets");
        for (String name : List.of("gcs_service_account", "device_data_key", "play_service_account", "mongo_app_password")) {
            Map<String, Object> d = (Map<String, Object>) declared.get(name);
            assertNotNull(d, name);
            assertEquals(Boolean.TRUE, d.get("external"), name);
        }
        assertTrue(SecretFilesEnvironment.SECRETS.contains("DEVICE_DATA_KEY"));
        assertTrue(SecretFilesEnvironment.PATHS.contains("GCS_SERVICE_ACCOUNT_JSON"));
    }

    @Test
    void versionAndExemptConfig() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yml.contains("version: 44.2.0"));
        assertTrue(yml.contains("exempt-emails: ${BILLING_EXEMPT_EMAILS:"));
        String prod = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertTrue(prod.contains("device-key: ${DEVICE_DATA_KEY}"), "üretimde anahtar zorunlu (öntanımlı yok)");
    }
}
