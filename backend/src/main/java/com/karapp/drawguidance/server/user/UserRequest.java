package com.karapp.drawguidance.server.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/users/me} gövdesi. {@code id} (UUID) yalnız ilk kayıtta kullanılır; {@code birthDate}
 * {@code yyyy-MM-dd} ya da null. Bilinmeyen alanlar yok sayılır.
 */
public record UserRequest(
        @Size(max = 64) String id,
        @NotBlank @Size(max = 128) String firebaseUid,
        @Size(max = 200) String displayName,
        @Size(max = 100) String givenName,
        @Size(max = 100) String familyName,
        @Email @Size(max = 320) String email,
        Boolean emailVerified,
        @Size(max = 2048) String photoUrl,
        @PositiveOrZero Long accountCreatedAt,
        @Size(max = 32) String birthDate,
        @PositiveOrZero Long clientUpdatedAt,
        // v36 kullanım şartları ve KVKK onayı (hesap açılırken); eski istemciler göndermez
        @PositiveOrZero Long termsAcceptedAt,
        @Size(max = 32) String termsVersion) {}
