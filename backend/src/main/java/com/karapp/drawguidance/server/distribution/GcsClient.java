package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.billing.play.ServiceAccountCredentials;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

/**
 * v43 Google Cloud Storage: süreli imzalı indirme adresi (V4, {@code GOOG4-RSA-SHA256}) ve nesne yükleme (JSON API,
 * {@code uploadType=media}). Google istemci kitaplığı yok; imza hizmet hesabının özel anahtarıyla JDK'da atılır.
 *
 * <p>Bucket herkese kapalı tutulur: cihaz resimleri yalnız sunucunun verdiği, en çok 7 gün geçerli adreslerle indirir.
 * İmzalı adres yalnız {@code GET} ve yalnız o nesne içindir (başlık olarak yalnız {@code host} imzalanır).
 */
public final class GcsClient {
    public static final String HOST = "storage.googleapis.com";
    static final String SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final ServiceAccountCredentials credentials;
    private final GoogleAccessTokens tokens;
    private final ObjectMapper json;
    private final HttpClient http;
    /** API kökü (testlerde sahte sunucu). */
    private final String apiBase;

    public GcsClient(ServiceAccountCredentials credentials, ObjectMapper json, HttpClient http, String apiBase) {
        this.credentials = credentials;
        this.json = json;
        this.http = http;
        this.apiBase = apiBase == null ? "https://" + HOST : apiBase.replaceAll("/+$", "");
        this.tokens = new GoogleAccessTokens(credentials, SCOPE, json, http);
    }

    /** {@code gs://bucket/nesne} → (bucket, nesne); biçim bozuksa null. */
    public static String[] parse(String gsUrl) {
        if (gsUrl == null || !gsUrl.startsWith("gs://")) return null;
        String rest = gsUrl.substring(5);
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) return null;
        return new String[]{rest.substring(0, slash), rest.substring(slash + 1)};
    }

    /** Süreli imzalı GET adresi. */
    public String signedGetUrl(String bucket, String object, Duration ttl, Instant now) {
        long seconds = Math.max(1, Math.min(ttl.toSeconds(), 604_800));
        String stamp = STAMP.format(now);
        String date = stamp.substring(0, 8);
        String scope = date + "/auto/storage/goog4_request";
        String path = "/" + bucket + "/" + encodePath(object);
        String query = "X-Goog-Algorithm=GOOG4-RSA-SHA256"
                + "&X-Goog-Credential=" + encode(credentials.clientEmail() + "/" + scope)
                + "&X-Goog-Date=" + stamp
                + "&X-Goog-Expires=" + seconds
                + "&X-Goog-SignedHeaders=host";
        String canonical = "GET\n" + path + "\n" + query + "\nhost:" + HOST + "\n\nhost\nUNSIGNED-PAYLOAD";
        String toSign = "GOOG4-RSA-SHA256\n" + stamp + "\n" + scope + "\n" + sha256Hex(canonical);
        String signature = HexFormat.of().formatHex(credentials.sign(toSign.getBytes(StandardCharsets.UTF_8)));
        return "https://" + HOST + path + "?" + query + "&X-Goog-Signature=" + signature;
    }

    /** Nesnenin MD5'i (base64; yoksa null). Yüklemede aynı içerik yeniden gönderilmesin diye. */
    public String md5(String bucket, String object) throws IOException {
        HttpResponse<byte[]> r = send(HttpRequest.newBuilder(URI.create(apiBase + "/storage/v1/b/" + encode(bucket) + "/o/" + encode(object))).GET());
        if (r.statusCode() == 404) return null;
        if (r.statusCode() / 100 != 2) throw new IOException("Cloud Storage HTTP " + r.statusCode() + ": " + error(r.body()));
        JsonNode n = json.readTree(r.body());
        return n.path("md5Hash").asText(null);
    }

    /** Nesneyi yükler (varsa üzerine yazar). */
    public void upload(String bucket, String object, byte[] data, String contentType) throws IOException {
        String url = apiBase + "/upload/storage/v1/b/" + encode(bucket) + "/o?uploadType=media&name=" + encode(object);
        HttpResponse<byte[]> r = send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)));
        if (r.statusCode() / 100 != 2) throw new IOException("Yüklenemedi (" + object + "): HTTP " + r.statusCode() + ": " + error(r.body()));
    }

    public static String md5Base64(byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder b) throws IOException {
        try {
            HttpResponse<byte[]> r = http.send(b.timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + tokens.get()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() == 401) {
                tokens.invalidate();
                r = http.send(b.setHeader("Authorization", "Bearer " + tokens.get()).build(), HttpResponse.BodyHandlers.ofByteArray());
            }
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Kesildi", e);
        }
    }

    private String error(byte[] body) {
        try {
            return json.readTree(body).path("error").path("message").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** RFC 3986 yüzde kodlaması (yalnız A-Z a-z 0-9 - . _ ~ olduğu gibi kalır). */
    static String encode(String s) {
        StringBuilder b = new StringBuilder();
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
            int c = x & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') b.append((char) c);
            else b.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16))).append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
        }
        return b.toString();
    }

    /** Nesne adının yol kodlaması ("/" korunur). */
    static String encodePath(String object) {
        String[] parts = object.split("/", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append('/');
            b.append(encode(parts[i]));
        }
        return b.toString();
    }
}
