package com.karapp.drawguidance.server.billing.play;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * v39 Google hizmet hesabı anahtarı (Cloud Console'dan indirilen JSON) ve OAuth 2.0 JWT taşıyıcı onayı (RFC 7523):
 * JDK ile RS256 imzalanan {@code assertion} {@code token_uri}'ye gönderilip erişim jetonu alınır. Ek kitaplık yok.
 *
 * <p>Anahtar yalnız bellekte tutulur; {@link #toString()} gizli bilgi içermez.
 */
public final class ServiceAccountCredentials {
    public static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    public static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

    private final String clientEmail;
    private final String keyId;
    private final String tokenUri;
    private final PrivateKey privateKey;
    /** v43 anahtarın ait olduğu Google Cloud projesi ({@code project_id}; FCM HTTP v1 adresi için). */
    private final String projectId;

    public ServiceAccountCredentials(String clientEmail, String keyId, String tokenUri, PrivateKey privateKey) {
        this(clientEmail, keyId, tokenUri, privateKey, null);
    }

    public ServiceAccountCredentials(String clientEmail, String keyId, String tokenUri, PrivateKey privateKey, String projectId) {
        this.clientEmail = clientEmail;
        this.keyId = keyId;
        this.tokenUri = tokenUri == null || tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri;
        this.privateKey = privateKey;
        this.projectId = projectId == null || projectId.isBlank() ? null : projectId;
    }

    public static ServiceAccountCredentials load(Path file, ObjectMapper json) throws IOException {
        return parse(json.readTree(Files.readAllBytes(file)));
    }

    public static ServiceAccountCredentials parse(JsonNode n) throws IOException {
        String email = n.path("client_email").asText(null);
        String pem = n.path("private_key").asText(null);
        if (email == null || email.isBlank() || pem == null || pem.isBlank()) {
            throw new IOException("Hizmet hesabı dosyasında client_email ya da private_key yok");
        }
        return new ServiceAccountCredentials(email, n.path("private_key_id").asText(null), n.path("token_uri").asText(null), privateKey(pem),
                n.path("project_id").asText(null));
    }

    /** PEM (PKCS#8, {@code -----BEGIN PRIVATE KEY-----}) RSA anahtarı. */
    static PrivateKey privateKey(String pem) throws IOException {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "").replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Hizmet hesabı özel anahtarı okunamadı (PKCS#8 PEM bekleniyor)");
        }
    }

    public String clientEmail() { return clientEmail; }

    public String tokenUri() { return tokenUri; }

    /** v43 anahtarın projesi (dosyada yoksa null). */
    public String projectId() { return projectId; }

    /** v43 RSA-SHA256 imzası (Cloud Storage V4 imzalı adresleri için). */
    public byte[] sign(byte[] data) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("İmzalanamadı", e);
        }
    }

    /** Erişim jetonu isteğinin imzalı JWT'si: {@code iss}=hizmet hesabı, {@code aud}=token_uri, 1 saat geçerli. */
    public String assertion(Instant now) { return assertion(now, SCOPE); }

    /** v43 başka bir kapsam için (ör. Cloud Storage, FCM) erişim jetonu isteğinin imzalı JWT'si. */
    public String assertion(Instant now, String scope) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"" + (keyId == null ? "" : ",\"kid\":" + quote(keyId)) + "}";
        long iat = now.getEpochSecond();
        String claims = "{\"iss\":" + quote(clientEmail) + ",\"scope\":" + quote(scope) + ",\"aud\":" + quote(tokenUri)
                + ",\"iat\":" + iat + ",\"exp\":" + (iat + 3600) + "}";
        String signingInput = b64.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64.encodeToString(sig.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT imzalanamadı", e);
        }
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    @Override
    public String toString() { return "ServiceAccountCredentials[" + clientEmail + "]"; }
}
