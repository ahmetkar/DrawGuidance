package com.karapp.drawguidance.server.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.support.SlackStub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v40 {@link SlackNotifier}: yerel sahte web kancasına gönderilen gövde, e-posta gizleme, kaçışlama, adres yokken
 * hiçbir şey yapmama ve hata durumunda adresin günlüğe düşmemesi.
 */
@ExtendWith(OutputCaptureExtension.class)
class SlackNotifierTest {
    private final ObjectMapper json = new ObjectMapper();

    private static DrawGuidanceProperties props(String webhook) {
        return new DrawGuidanceProperties(null, null, null, null, null, null, null, new DrawGuidanceProperties.Slack(webhook), null);
    }

    @Test
    void postsTextPayloadWithMaskedEmailAndEscapedFields() throws Exception {
        try (SlackStub stub = new SlackStub()) {
            SlackNotifier slack = new SlackNotifier(props(stub.webhookUrl()), json);
            assertTrue(slack.enabled());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("E-posta", SlackNotifier.maskEmail("Ahmet.Kar@gmail.com"));
            fields.put("Konu", "Merhaba <!channel> & <https://kotu.example|tıkla>\nikinci satır");
            fields.put("Boş", "  ");
            boolean ok = slack.send("Yeni destek mesajı", fields).get(10, TimeUnit.SECONDS);
            assertTrue(ok);

            JsonNode body = json.readTree(stub.next());
            String text = body.path("text").asText();
            assertTrue(text.startsWith("*Yeni destek mesajı*"), text);
            assertTrue(text.contains("• E-posta: A***@gmail.com"), text);
            assertFalse(text.contains("Ahmet.Kar"), "tam e-posta gitmez");
            assertTrue(text.contains("&lt;!channel&gt; &amp; &lt;https://kotu.example|tıkla&gt; ikinci satır"), text);
            assertFalse(text.contains("<!channel>"), "bahsetme kaçışlanır");
            assertFalse(text.contains("Boş"), "boş alan atlanır");
            assertFalse(body.path("unfurl_links").asBoolean(true));
        }
    }

    @Test
    void doesNothingWhenUrlUnset() throws Exception {
        for (String url : new String[]{null, "", "   "}) {
            SlackNotifier slack = new SlackNotifier(props(url), json);
            assertFalse(slack.enabled());
            assertFalse(slack.send("başlık", Map.of("a", "b")).get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void invalidUrlDisablesWithoutLoggingIt(CapturedOutput out) {
        SlackNotifier slack = new SlackNotifier(props("ftp://hooks.example/gizli-anahtar-123"), json);
        assertFalse(slack.enabled());
        assertFalse(out.getAll().contains("gizli-anahtar-123"));
    }

    @Test
    void failureIsBestEffortAndNeverLogsUrl(CapturedOutput out) throws Exception {
        try (SlackStub stub = new SlackStub()) {
            stub.status(500);
            SlackNotifier slack = new SlackNotifier(props(stub.webhookUrl()), json);
            assertFalse(slack.send("deneme").get(10, TimeUnit.SECONDS));
        }
        // Kapalı kapı: bağlantı hatası da istisna fırlatmaz
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) { closedPort = s.getLocalPort(); }
        String dead = "http://127.0.0.1:" + closedPort + SlackStub.PATH;
        SlackNotifier slack = new SlackNotifier(props(dead), json);
        assertFalse(slack.send("deneme").get(15, TimeUnit.SECONDS));
        String log = out.getAll();
        assertTrue(log.contains("Slack bildirimi gönderilemedi: HTTP 500"), log);
        assertFalse(log.contains(SlackStub.PATH), "web kancası adresi günlüğe yazılmaz");
        assertFalse(log.contains("gizli-anahtar"), "web kancası adresi günlüğe yazılmaz");
    }

    @Test
    void secretsHiddenInToString() {
        String s = new DrawGuidanceProperties.Slack("https://hooks.slack.com/services/T/B/sir").toString();
        assertFalse(s.contains("sir"), s);
        assertNull(new DrawGuidanceProperties.Slack("  ").webhookUrl());
    }

    @Test
    void masksEmails() {
        assertEquals("a***@gmail.com", SlackNotifier.maskEmail("ahmet@gmail.com"));
        assertEquals("a***@gmail.com", SlackNotifier.maskEmail(" a@gmail.com "));
        assertEquals("ç***@örnek.com.tr", SlackNotifier.maskEmail("çizer@örnek.com.tr"));
        assertEquals("***", SlackNotifier.maskEmail("gecersiz"));
        assertEquals("***", SlackNotifier.maskEmail("@gmail.com"));
        assertEquals("***", SlackNotifier.maskEmail("ahmet@"));
        assertEquals("***", SlackNotifier.maskEmail(null));
    }
}
