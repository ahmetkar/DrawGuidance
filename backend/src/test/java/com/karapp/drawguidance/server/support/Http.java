package com.karapp.drawguidance.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/** Gerçek HTTP ile sunucuya istek (vekil sunucusuz, yerel adres). */
public final class Http {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final int port;

    public Http(int port) { this.port = port; }

    public record Res(int status, byte[] bytes, HttpHeaders headers) {
        public String body() { return new String(bytes, StandardCharsets.UTF_8); }

        public JsonNode json() {
            try {
                return JSON.readTree(bytes);
            } catch (IOException e) {
                throw new AssertionError("JSON değil (" + status + "): " + body(), e);
            }
        }

        /** Gövde gzip'liyse açılmış hali. */
        public JsonNode gunzipJson() {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                return JSON.readTree(in);
            } catch (IOException e) {
                throw new AssertionError("gzip'li JSON değil (" + status + ")", e);
            }
        }

        public String header(String name) { return headers.firstValue(name).orElse(null); }

        public String etag() { return header("ETag"); }

        public String error() { return json().path("error").asText(null); }
    }

    /** {@code headers}: ad, değer, ad, değer … */
    public Res call(String method, String path, String body, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30));
        boolean typed = false;
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
            typed |= headers[i].equalsIgnoreCase("Content-Type");
        }
        // Gövde verilip Content-Type verilmediyse JSON
        if (body != null && !typed) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<byte[]> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Res(r.statusCode(), r.body(), r.headers());
        } catch (IOException e) {
            throw new AssertionError(method + " " + path + " başarısız", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    public Res get(String path, String... headers) { return call("GET", path, null, headers); }
}
