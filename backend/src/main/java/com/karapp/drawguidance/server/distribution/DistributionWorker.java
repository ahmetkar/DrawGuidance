package com.karapp.drawguidance.server.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * v43 arka plan işçisi (Firebase yok): belli aralıklarla (öntanımlı 10 sn)
 * <ol>
 *   <li>etkin gönderimleri hedef cihazlara teslimat olarak toplu açar; o cihazların gelen kutusu sürümü artar ve
 *       uygulaması açık olan cihazların bekleyen yoklaması hemen uyanır ("yeni içerik var" bilgisi gider),</li>
 *   <li>bilgisi alınmış ama verisi o an hazırlanamamış teslimatları içerik başına toplu ilerletir ("veri gönderildi").</li>
 * </ol>
 * Uygulaması kapalı cihazlar bilgiyi arka plan yoklamasında (WorkManager, ~15 dk) alır. Birden çok sunucu kopyası
 * güvenle çalışır: gönderimler kirayla, aşama geçişleri koşullu güncellemeyle yapılır.
 */
@Component
public class DistributionWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(DistributionWorker.class);

    private final DeliveryService deliveries;
    private final DistributionProperties props;
    private final Clock clock;
    private volatile Thread thread;
    private volatile boolean running;

    public DistributionWorker(DeliveryService deliveries, DistributionProperties props) {
        this.deliveries = deliveries;
        this.props = props;
        this.clock = Clock.systemUTC();
    }

    /** Tek tur; işlenen sayılar (testler ve CLI {@code isci tur} doğrudan çağırır). */
    public synchronized Map<String, Integer> tick() {
        Instant now = clock.instant();
        int expanded = deliveries.expand(now);
        int data = deliveries.prepareData(now);
        return Map.of("acilan", expanded, "veriHazir", data);
    }

    @Override
    public void start() {
        if (!props.workersEnabled() || running) return;
        running = true;
        thread = Thread.ofVirtual().name("dg-distribution").start(() -> {
            while (running) {
                try {
                    Map<String, Integer> n = tick();
                    if (n.values().stream().anyMatch(v -> v > 0)) log.info("dağıtım turu: {}", n);
                } catch (RuntimeException e) {
                    log.warn("dağıtım turu yapılamadı: {}", e.getMessage());
                }
                try {
                    Thread.sleep(props.workerInterval());
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
    }

    @Override
    public boolean isRunning() { return running; }
}
