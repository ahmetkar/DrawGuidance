package com.karapp.drawguidance.server.user;

import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * {@code users} belgesi. {@code _id} istemcinin ürettiği UUID'dir (ilk kayıtta konur, sonra değişmez);
 * {@code firebaseUid} benzersizdir ve bütün sorgular onunla yapılır.
 */
public record User(@Id String id, String firebaseUid, String displayName, String givenName, String familyName,
                   String email, Boolean emailVerified, String photoUrl, Long accountCreatedAt, String birthDate,
                   Long clientUpdatedAt, Instant createdAt, Instant updatedAt, Long version,
                   Long termsAcceptedAt, String termsVersion,
                   // Google ya da Apple ile giriş ("google.com" / "apple.com"); jetondan yazılır, yanıtta yok
                   String signInProvider) {

    /** Sözleşmedeki yanıt alanları (sırasıyla). */
    public record Response(String id, String firebaseUid, String displayName, String givenName, String familyName,
                           String email, boolean emailVerified, String photoUrl, Long accountCreatedAt, String birthDate,
                           Instant createdAt, Instant updatedAt, long version,
                           // v36 kullanım şartları ve KVKK aydınlatma metninin onay zamanı ve sürümü
                           Long termsAcceptedAt, String termsVersion) {}

    public Response toResponse() {
        return new Response(id, firebaseUid, displayName, givenName, familyName, email, Boolean.TRUE.equals(emailVerified),
                photoUrl, accountCreatedAt, birthDate, createdAt, updatedAt, version == null ? 0L : version,
                termsAcceptedAt, termsVersion);
    }
}
