package com.karapp.drawguidance.server.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * v40 Slack gelen web kancasının yerel sahtesi: gövdeleri sıraya koyar, ayarlanan durum koduyla yanıtlar. Hem
 * {@code SlackNotifierTest} (kendi örneği) hem uçtan uca testler (paylaşılan {@link #SHARED}) kullanır.
 */
public final class SlackStub implements AutoCloseable {
    public static final String PATH = "/services/T000/B000/gizli-anahtar";
    private static SlackStub shared;

    private final HttpServer server;
    private final BlockingQueue<String> bodies = new LinkedBlockingQueue<>();
    private volatile int status = 200;

    public SlackStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext(PATH, ex -> {
            try (InputStream in = ex.getRequestBody()) {
                bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        server.start();
    }

    /** Uçtan uca testlerin paylaştığı örnek (JVM boyunca açık). */
    public static synchronized SlackStub shared() {
        if (shared == null) shared = new SlackStub();
        return shared;
    }

    public static String url() { return shared().webhookUrl(); }

    public String webhookUrl() { return "http://127.0.0.1:" + server.getAddress().getPort() + PATH; }

    public void status(int status) { this.status = status; }

    /** Sıradaki gövde (en çok 5 sn beklenir; gelmezse {@code null}). */
    public String next() throws InterruptedException { return bodies.poll(5, TimeUnit.SECONDS); }

    /** Kısa süre içinde gövde gelmediğini doğrulamak için. */
    public String poll(long millis) throws InterruptedException { return bodies.poll(millis, TimeUnit.MILLISECONDS); }

    public void clear() { bodies.clear(); }

    @Override
    public void close() { server.stop(0); }
}
