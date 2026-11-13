package com.karapp.drawguidance.server.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * v50 {@code POST /api/v1/subscriptions/verify-apple} gövdesi: StoreKit 2 işleminin imzalı hali
 * ({@code Transaction.jwsRepresentation}; Apple'ın imzaladığı, x5c zincirli JWS).
 */
public record AppleVerifyRequest(@NotBlank @Size(max = 16384) String signedTransaction) {}
