package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * v39 {@code POST /api/v1/billing/rtdn}: Google Play gerçek zamanlı bildirimlerinin Pub/Sub itme ucu. Firebase
 * jetonu istemez; {@code drawguidance.billing.pubsub.audience} ayarlıysa Pub/Sub'ın OIDC jetonu denetlenir.
 * 2xx yanıt iletiyi onaylar; başka her yanıtta Pub/Sub iletiyi yeniden gönderir.
 */
@RestController
public class RtdnController {
    public static final String PATH = "/api/v1/billing/rtdn";

    private final RtdnService rtdn;
    private final PubSubPushVerifier verifier;
    private final ObjectMapper json;

    public RtdnController(RtdnService rtdn, PubSubPushVerifier verifier, ObjectMapper json) {
        this.rtdn = rtdn;
        this.verifier = verifier;
        this.json = json;
    }

    @PostMapping(value = PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public RtdnService.Outcome push(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                    @RequestBody byte[] body) {
        verifier.check(authorization);
        JsonNode envelope;
        try {
            envelope = json.readTree(body);
        } catch (IOException e) {
            throw ApiException.badRequest("bad_request", "Gövde JSON değil");
        }
        if (envelope == null || !envelope.isObject()) throw ApiException.badRequest("bad_request", "Gövde bir JSON nesnesi olmalı");
        return rtdn.handle(envelope);
    }
}
