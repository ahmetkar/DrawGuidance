package com.karapp.drawguidance.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;

/**
 * Açılış işi (dizinler, katalog eşitlemesi). Bütün bileşenler oluştuktan sonra, <b>sunucu istek kabul etmeden
 * önce</b> bir kez çalışır (ilk istek eksik dizine ya da boş kataloğa düşmez). Veritabanı o an hazır değilse açılış
 * durmaz: uyarı yazılır ve iş arka planda belli aralıklarla, başarılı olana kadar yeniden denenir.
 */
public abstract class StartupTask implements SmartInitializingSingleton, DisposableBean {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final String label;
    private final Duration retryDelay;
    private volatile Thread retry;
    private volatile boolean closed;

    protected StartupTask(String label, Duration retryDelay) {
        this.label = label;
        this.retryDelay = retryDelay;
    }

    /** Asıl iş; tekrar çalıştırılabilir olmalı. */
    protected abstract void runTask();

    @Override
    public final void afterSingletonsInstantiated() {
        if (attempt()) return;
        retry = Thread.ofVirtual().name("dg-startup-" + label).start(this::retryLoop);
    }

    private boolean attempt() {
        try {
            runTask();
            return true;
        } catch (RuntimeException e) {
            log.warn("{} yapılamadı (veritabanı hazır değil mi?): {}; {} sn sonra yeniden denenecek", label, e.getMessage(), retryDelay.toSeconds());
            return false;
        }
    }

    private void retryLoop() {
        while (!closed) {
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                return;
            }
            if (closed) return;
            if (attempt()) return;
        }
    }

    @Override
    public void destroy() {
        closed = true;
        Thread t = retry;
        if (t != null) t.interrupt();
    }
}
