package com.karapp.drawguidance.server.distribution;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v43 uzun yoklamanın (long polling) uyandırılması — Firebase/FCM olmadan, çok sayıda cihaz için:
 *
 * <ul>
 *   <li>Bekleyen her istek bellekte bir {@link CompletableFuture}'dır; sanal iş parçacığında bekler (iş parçacığı
 *       havuzu tükenmez).</li>
 *   <li>Aynı kopyada değişiklik olunca ({@link #signal}) hemen uyanır.</li>
 *   <li>Başka sunucu kopyalarındaki değişiklikler için kopya başına <b>tek</b> izleyici ({@link #POLL} aralıkla) son
 *       değişen cihazları dizinli tek sorguyla okur ({@code devices.inboxChangedAt}); bekleyen sayısından bağımsız
 *       sabit maliyet. Bekleyen yoksa sorgu yapılmaz.</li>
 *   <li>Bekleyen sayısı sınırı ({@code maxWaiters}) aşılırsa yeni istek beklemeden yanıtlanır ve daha uzun yoklama
 *       aralığı önerilir (yük atma).</li>
 * </ul>
 */
@Component
public class InboxSignals implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(InboxSignals.class);
    static final Duration POLL = Duration.ofSeconds(2);

    private final Map<String, Set<CompletableFuture<Boolean>>> waiters = new ConcurrentHashMap<>();
    private final AtomicInteger count = new AtomicInteger();
    private final DeviceService devices;
    private final DistributionProperties props;
    private volatile boolean running;
    private volatile Thread thread;
    private volatile Date lastSeen = new Date();

    public InboxSignals(DeviceService devices, DistributionProperties props) {
        this.devices = devices;
        this.props = props;
    }

    /** Bekleyici ekler; sınır doluysa null (çağıran beklemeden yanıtlar). */
    public CompletableFuture<Boolean> register(String deviceKey) {
        if (count.incrementAndGet() > props.maxWaiters()) {
            count.decrementAndGet();
            return null;
        }
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        waiters.computeIfAbsent(deviceKey, k -> ConcurrentHashMap.newKeySet()).add(f);
        return f;
    }

    public void unregister(String deviceKey, CompletableFuture<Boolean> f) {
        Set<CompletableFuture<Boolean>> set = waiters.get(deviceKey);
        if (set != null && set.remove(f)) {
            count.decrementAndGet();
            if (set.isEmpty()) waiters.remove(deviceKey, set);
        }
    }

    /** Bu cihazların bekleyen yoklamalarını uyandırır. */
    public void signal(Collection<String> deviceKeys) {
        for (String k : deviceKeys) {
            Set<CompletableFuture<Boolean>> set = waiters.get(k);
            if (set != null) for (CompletableFuture<Boolean> f : List.copyOf(set)) f.complete(true);
        }
    }

    public int waiting() { return count.get(); }

    /** Sunucu yoğun mu (bekleyenler sınırın %80'ini geçti). */
    public boolean busy() { return count.get() > props.maxWaiters() * 0.8; }

    // ------------------------------------------------------------------ kopyalar arası izleyici

    void watchOnce() {
        if (count.get() == 0) {
            lastSeen = new Date();
            return;
        }
        Date since = new Date(lastSeen.getTime() - 500);
        Date max = lastSeen;
        List<Document> changed = devices.changedSince(since, 5000);
        java.util.ArrayList<String> keys = new java.util.ArrayList<>(changed.size());
        for (Document d : changed) {
            keys.add(d.getString("_id"));
            Date at = d.getDate("inboxChangedAt");
            if (at != null && at.after(max)) max = at;
        }
        lastSeen = max;
        signal(keys);
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        thread = Thread.ofVirtual().name("dg-inbox-signals").start(() -> {
            while (running) {
                try {
                    watchOnce();
                } catch (RuntimeException e) {
                    log.debug("gelen kutusu izleyicisi: {}", e.getMessage());
                }
                try {
                    Thread.sleep(POLL);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
    }

    @Override
    public void stop() {
        running = false;
        Thread t = thread;
        if (t != null) t.interrupt();
        // Bekleyenler hemen yanıtlansın (kapanış)
        waiters.values().forEach(s -> s.forEach(f -> f.complete(false)));
    }

    @Override
    public boolean isRunning() { return running; }
}
