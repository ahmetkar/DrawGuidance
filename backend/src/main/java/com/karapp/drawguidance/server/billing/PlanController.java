package com.karapp.drawguidance.server.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * v39 {@code GET /api/v1/plans} (herkese açık): etkin planlar ve erişim hakları. ETag gövdenin SHA-256 özetidir
 * ({@code "plans-<16 onaltılık>"}); {@code If-None-Match} tutarsa gövdesiz 304.
 */
@RestController
public class PlanController {
    public static final String PATH = "/api/v1/plans";

    private final PlanService plans;
    private final ObjectMapper json;

    public PlanController(PlanService plans, ObjectMapper json) {
        this.plans = plans;
        this.json = json;
    }

    /** Yanıt: planlar ve bilinen bütün özellik anahtarları. */
    public record PlansResponse(List<PlanService.PlanView> plans, List<String> featureKeys) {}

    @GetMapping(PATH)
    public ResponseEntity<byte[]> list(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch)
            throws JsonProcessingException {
        byte[] body = json.writeValueAsBytes(new PlansResponse(plans.activePlans(), Plans.FEATURE_KEYS));
        String etag = "\"plans-" + sha256(body).substring(0, 16) + "\"";
        if (ifNoneMatch != null && (ifNoneMatch.contains(etag) || ifNoneMatch.trim().equals("*"))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
