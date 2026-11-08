package com.karapp.drawguidance.server.distribution.cli;

import com.karapp.drawguidance.server.distribution.ContentService;

import java.util.List;

/** Testlerin CLI'nin paket içi yardımcılarına erişimi. */
public final class DistributionCliAccess {
    private DistributionCliAccess() {}

    public static List<ContentService.ItemInput> items(List<String> specs) { return DistributionCli.parseItems(specs); }

    public static boolean bundledMayReplace(java.util.Date dbUpdatedAt, java.time.Instant generatedAt) {
        return BaseSeeder.bundledMayReplace(dbUpdatedAt, generatedAt);
    }

    public static java.time.Instant generatedAt(com.fasterxml.jackson.databind.JsonNode root) { return BaseSeeder.generatedAt(root); }

    public static com.fasterxml.jackson.databind.JsonNode stampBundled(com.fasterxml.jackson.databind.JsonNode root, String bucket, java.time.Instant now) {
        return BaseSeeder.stampBundled(root, bucket, now);
    }
}
