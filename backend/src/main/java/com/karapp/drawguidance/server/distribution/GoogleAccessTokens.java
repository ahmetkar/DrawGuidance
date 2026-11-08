package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.billing.play.ServiceAccountCredentials;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * v43 hizmet hesabıyla OAuth 2.0 erişim jetonu (JWT taşıyıcı onayı, RFC 7523) — tek kapsam için, süresi dolmadan bir
 * dakika öncesine kadar bellekte. Jeton günlüğe yazılmaz.
 */
public final class GoogleAccessTokens {
    static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ServiceAccountCredentials credentials;
    private final String scope;
    private final ObjectMapper json;
    private final HttpClient http;
    private String token;
    private Instant until = Instant.EPOCH;

    public GoogleAccessTokens(ServiceAccountCredentials credentials, String scope, ObjectMapper json, HttpClient http) {
        this.credentials = credentials;
        this.scope = scope;
        this.json = json;
        this.http = http;
    }

    public ServiceAccountCredentials credentials() { return credentials; }

    public synchronized String get() throws IOException {
        Instant now = Instant.now();
        if (token != null && now.isBefore(until)) return token;
        String form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + enc(credentials.assertion(now, scope));
        HttpResponse<byte[]> r;
        try {
            r = http.send(HttpRequest.newBuilder(URI.create(credentials.tokenUri())).timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Kesildi", e);
        }
        if (r.statusCode() != 200) throw new IOException("Google erişim jetonu alınamadı (HTTP " + r.statusCode() + ")");
        JsonNode n = json.readTree(r.body());
        String t = n.path("access_token").asText(null);
        if (t == null) throw new IOException("Google erişim jetonu yanıtında access_token yok");
        token = t;
        until = now.plusSeconds(Math.max(60, n.path("expires_in").asLong(3600)) - 60);
        return t;
    }

    /** Jeton geçersizleşti (401): bir sonraki istekte yenisi alınır. */
    public synchronized void invalidate() { until = Instant.EPOCH; }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
