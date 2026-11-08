package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.config.StartupTask;
import com.karapp.drawguidance.server.distribution.cli.BaseSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * v43 açılışta taban paket kayıtları: sunucuyla gelen {@code base-packages/manifest.json} (dosyaları Cloud Storage'a
 * {@code taban-paket --yukle} ile önceden yüklenmiş liste) veritabanına işlenir. Böylece yerel ya da uzak her sunucu,
 * elle komut çalıştırmadan çizgi roman, not ve resim modlarının taban paketlerini bilir.
 *
 * <p>Tekrar çalıştırılabilir: eksik paket eklenir, değişen güncellenir (sürümü artar), aynısına dokunulmaz.
 * Kova ayarlı değilse ya da liste başka bir kova içinse atlanır. {@code SEED_BASE_PACKAGES=false} ile kapatılır.
 */
@Component
public class BasePackagesStartup extends StartupTask {
    static final String RESOURCE = "base-packages/manifest.json";

    private final BaseSeeder seeder;
    private final ObjectMapper json;
    private final boolean enabled;

    public BasePackagesStartup(BaseSeeder seeder, ObjectMapper json,
                               @Value("${drawguidance.distribution.seed-base-packages:true}") boolean enabled) {
        super("taban paket kaydı", Duration.ofSeconds(30));
        this.seeder = seeder;
        this.json = json;
        this.enabled = enabled;
    }

    @Override
    protected void runTask() {
        if (!enabled) return;
        ClassPathResource r = new ClassPathResource(RESOURCE);
        if (!r.exists()) return;
        JsonNode root;
        try (InputStream in = r.getInputStream()) {
            root = json.readTree(in);
        } catch (IOException e) {
            log.error("Taban paket listesi okunamadı: {}", e.getMessage());
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            int n = seeder.registerBundled(root, out);
            String text = buf.toString(StandardCharsets.UTF_8).trim();
            if (n > 0) log.info("Taban paketler:\n{}", text);
            else log.info("{}", text);
        } catch (UncheckedIOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
