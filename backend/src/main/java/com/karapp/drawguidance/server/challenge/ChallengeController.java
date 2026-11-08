package com.karapp.drawguidance.server.challenge;

import com.karapp.drawguidance.server.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * v39 çocuk modu "Çizim Görevi" ilerleme eşitlemesi (Firebase ID jetonu zorunlu). {@code PUT} gövdesi ya
 * {@code {"levels": [...]}} ya da doğrudan dizi {@code [...]}; öğe {@code {level, bestScore, updatedAt}}.
 */
@RestController
@RequestMapping(ChallengeController.PATH)
public class ChallengeController {
    public static final String PATH = "/api/v1/challenge";

    private final ChallengeService challenge;
    private final ObjectMapper json;

    public ChallengeController(ChallengeService challenge, ObjectMapper json) {
        this.challenge = challenge;
        this.json = json;
    }

    @GetMapping("/progress")
    public ChallengeService.Progress get(@AuthenticationPrincipal Jwt jwt) {
        return challenge.get(jwt.getSubject());
    }

    @PutMapping("/progress")
    public ChallengeService.Progress put(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        JsonNode list = body != null && body.isObject() ? body.get("levels") : body;
        if (list == null || !list.isArray()) throw ApiException.badRequest("validation", "levels dizisi gerekli");
        List<ChallengeService.Entry> entries = new ArrayList<>();
        for (JsonNode n : list) {
            if (!n.isObject()) throw ApiException.badRequest("bad_request", "Seviye kaydı bir JSON nesnesi olmalı");
            entries.add(new ChallengeService.Entry(integer(n.get("level")), integer(n.get("bestScore")),
                    n.hasNonNull("updatedAt") && n.get("updatedAt").canConvertToLong() && n.get("updatedAt").isIntegralNumber()
                            ? n.get("updatedAt").asLong() : null));
        }
        return challenge.merge(jwt.getSubject(), entries);
    }

    private static Integer integer(JsonNode n) {
        return n != null && n.isIntegralNumber() && n.canConvertToInt() ? n.asInt() : null;
    }
}
