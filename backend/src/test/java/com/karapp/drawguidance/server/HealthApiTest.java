package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.support.Http.Res;
import com.karapp.drawguidance.server.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Sağlık ucu, kaldırılan uçlar ve ortak hata biçimi. */
class HealthApiTest extends ApiContextTest {

    @Test
    void healthReportsVersionDatabaseAndTopology() {
        Res r = http.get("/api/health");
        assertEquals(200, r.status());
        JsonNode h = r.json();
        assertEquals("ok", h.get("status").asText());
        assertEquals("44.2.0", h.get("version").asText());
        assertEquals("drawguidance", h.get("service").asText());
        assertEquals("up", h.get("database").asText());
        assertEquals("REPLICA_SET", h.get("topology").asText());
        assertEquals(200, http.get("/actuator/health").status());
    }

    @Test
    void removedUserContentEndpointsAreGone() {
        String token = "Bearer " + TestTokens.valid("uid-" + UUID.randomUUID());
        for (String path : new String[]{"/api/v1/canvases", "/api/v1/canvases/" + UUID.randomUUID(), "/api/v1/folders",
                "/api/v1/images/x", "/api/v1/models3d/x", "/api/v1/assets/missing", "/api/v1/sync/changes", "/actuator/metrics", "/"}) {
            for (String[] headers : new String[][]{{}, {"Authorization", token}, {"X-User-Id", "default-user"}}) {
                Res r = http.get(path, headers);
                assertEquals(404, r.status(), path + " " + r.body());
                assertEquals("not_found", r.error(), path);
                assertNotNull(r.json().get("message").asText(null), path);
            }
        }
    }

    @Test
    void mvcErrorsUseTheContractErrorBody() {
        String token = "Bearer " + TestTokens.valid("uid-" + UUID.randomUUID());
        Res wrongMethod = http.call("POST", "/api/v1/users/me", "{}", "Authorization", token);
        assertEquals(405, wrongMethod.status());
        assertEquals("method_not_allowed", wrongMethod.error());
        Res readOnly = http.call("PUT", "/api/v1/catalog/templates", "{}");
        assertEquals(405, readOnly.status());
        assertEquals("method_not_allowed", readOnly.error());
        Res wrongType = http.call("PUT", "/api/v1/users/me", null, "Authorization", token, "Content-Type", "text/plain");
        assertEquals(415, wrongType.status());
        assertEquals("unsupported_media_type", wrongType.error());
        // Gövde yalnız iki alan taşır
        JsonNode body = wrongMethod.json();
        assertEquals(2, body.size());
        assertFalse(body.get("message").asText().isBlank());
    }
}
