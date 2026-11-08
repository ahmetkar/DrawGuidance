package com.karapp.drawguidance.server.billing.play;

import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * v39 Google Play Developer API istemcisi: {@code java.net.http} + hizmet hesabı JWT'si (Google istemci kitaplığı
 * yok). Erişim jetonu süresi dolmadan bir dakika öncesine kadar bellekte tutulur. Hizmet hesabı dosyası
 * ({@code drawguidance.billing.service-account-file}) yoksa ya da okunamazsa {@link #configured()} {@code false}
 * olur ve hiçbir çağrı yapılmaz. Dosyanın içeriği ve jetonlar günlüğe yazılmaz.
 */
public class HttpGooglePlayApi implements GooglePlayApi {
    private static final Logger log = LoggerFactory.getLogger(HttpGooglePlayApi.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ServiceAccountCredentials credentials;
    private final String apiBase;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private String accessToken;
    private Instant accessTokenUntil = Instant.EPOCH;

    public HttpGooglePlayApi(ServiceAccountCredentials credentials, String apiBase, ObjectMapper json) {
        this.credentials = credentials;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.json = json;
    }

    /** Ayarlardan: dosya verilmemişse ya da okunamazsa yapılandırılmamış istemci. */
    public static HttpGooglePlayApi fromProperties(DrawGuidanceProperties.Billing billing, ObjectMapper json) {
        ServiceAccountCredentials creds = null;
        if (billing.serviceAccountFile() == null) {
            log.warn("Google Play hizmet hesabı ayarlı değil (GOOGLE_PLAY_SERVICE_ACCOUNT_JSON); abonelik doğrulaması kapalı");
        } else {
            try {
                creds = ServiceAccountCredentials.load(Path.of(billing.serviceAccountFile()), json);
                log.info("Google Play hizmet hesabı yüklendi: {}", creds.clientEmail());
            } catch (IOException | RuntimeException e) {
                log.error("Google Play hizmet hesabı dosyası okunamadı ({}): {}; abonelik doğrulaması kapalı",
                        billing.serviceAccountFile(), e.getMessage());
            }
        }
        return new HttpGooglePlayApi(creds, billing.apiBaseUrl(), json);
    }

    @Override
    public boolean configured() { return credentials != null; }

    @Override
    public PlaySubscription getSubscription(String packageName, String purchaseToken) throws PlayApiException {
        String url = apiBase + "/androidpublisher/v3/applications/" + enc(packageName) + "/purchases/subscriptionsv2/tokens/" + enc(purchaseToken);
        HttpResponse<byte[]> r = send(HttpRequest.newBuilder(URI.create(url)).GET());
        try {
            return PlaySubscription.parse(json.readTree(r.body()));
        } catch (IOException e) {
            throw new PlayApiException(502, "Play yanıtı okunamadı");
        }
    }

    @Override
    public void acknowledge(String packageName, String productId, String purchaseToken) throws PlayApiException {
        String url = apiBase + "/androidpublisher/v3/applications/" + enc(packageName) + "/purchases/subscriptions/" + enc(productId)
                + "/tokens/" + enc(purchaseToken) + ":acknowledge";
        send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder request) throws PlayApiException {
        if (credentials == null) throw new PlayApiException(503, "Google Play hizmet hesabı ayarlı değil");
        HttpResponse<byte[]> r = exchange(request.timeout(TIMEOUT).header("Authorization", "Bearer " + token()).build());
        if (r.statusCode() == 401) {
            // Jeton geçersizleşmiş olabilir: bir kez yenile
            synchronized (this) { accessTokenUntil = Instant.EPOCH; }
            r = exchange(request.setHeader("Authorization", "Bearer " + token()).build());
        }
        if (r.statusCode() / 100 != 2) throw new PlayApiException(r.statusCode(), "Play API HTTP " + r.statusCode() + ": " + errorMessage(r.body()));
        return r;
    }

    private HttpResponse<byte[]> exchange(HttpRequest request) throws PlayApiException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new PlayApiException("Google'a ulaşılamadı: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PlayApiException("Kesildi", e);
        }
    }

    /** OAuth erişim jetonu (JWT taşıyıcı onayıyla alınır, süresi dolana kadar yeniden kullanılır). */
    synchronized String token() throws PlayApiException {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenUntil)) return accessToken;
        String form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + enc(credentials.assertion(now));
        HttpResponse<byte[]> r = exchange(HttpRequest.newBuilder(URI.create(credentials.tokenUri())).timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build());
        if (r.statusCode() != 200) {
            throw new PlayApiException(r.statusCode() >= 500 ? 503 : 502, "Google erişim jetonu alınamadı (HTTP " + r.statusCode() + ")");
        }
        try {
            JsonNode n = json.readTree(r.body());
            String token = n.path("access_token").asText(null);
            if (token == null) throw new PlayApiException(502, "Google erişim jetonu yanıtında access_token yok");
            accessToken = token;
            accessTokenUntil = now.plusSeconds(Math.max(60, n.path("expires_in").asLong(3600)) - 60);
            return token;
        } catch (IOException e) {
            throw new PlayApiException(502, "Google erişim jetonu yanıtı okunamadı");
        }
    }

    private String errorMessage(byte[] body) {
        try {
            return json.readTree(body).path("error").path("message").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
