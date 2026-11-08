package com.karapp.drawguidance.server.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** v39 {@code POST /api/v1/subscriptions/verify} gövdesi (Play Billing'in {@code Purchase} nesnesinden). */
public record VerifyRequest(
        @NotBlank @Size(max = 255) String packageName,
        @NotBlank @Size(max = 255) String productId,
        @NotBlank @Size(max = 4096) String purchaseToken) {}
