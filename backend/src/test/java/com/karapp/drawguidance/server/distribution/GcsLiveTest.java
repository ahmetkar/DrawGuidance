package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.billing.play.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * v43 gerçek Cloud Storage denemesi (yalnız GCS_LIVE_KEY ve GCS_LIVE_BUCKET verilince çalışır): küçük bir nesne yüklenir,
 * MD5'i okunur ve imzalı adresle kimliksiz indirilir.
 */
@EnabledIfEnvironmentVariable(named = "GCS_LIVE_KEY", matches = ".+")
class GcsLiveTest {
    @Test
    void uploadAndSignedDownload() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ServiceAccountCredentials c = ServiceAccountCredentials.load(Path.of(System.getenv("GCS_LIVE_KEY")), json);
        HttpClient http = HttpClient.newHttpClient();
        GcsClient gcs = new GcsClient(c, json, http, null);
        String bucket = System.getenv("GCS_LIVE_BUCKET");
        byte[] data = ("DrawGuidance deneme " + Instant.now()).getBytes(StandardCharsets.UTF_8);
        String object = "deneme/dg-probe.txt";
        gcs.upload(bucket, object, data, "text/plain");
        assertEquals(GcsClient.md5Base64(data), gcs.md5(bucket, object));
        String url = gcs.signedGetUrl(bucket, object, Duration.ofMinutes(5), Instant.now());
        HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode(), new String(r.body(), StandardCharsets.UTF_8));
        assertEquals(new String(data, StandardCharsets.UTF_8), new String(r.body(), StandardCharsets.UTF_8));
    }
}
