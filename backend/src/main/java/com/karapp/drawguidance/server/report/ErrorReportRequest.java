package com.karapp.drawguidance.server.report;

import java.util.List;

/**
 * v37 {@code POST /api/v1/reports} gövdesi (uygulamanın hata/çökme raporu; alan adları uygulamanın gönderdikleriyle
 * aynı). Bilinmeyen alanlar yok sayılır; uzun alanlar kesilir, geçersiz olanlar 400 döner (kurallar
 * {@link ErrorReportService#normalize}).
 *
 * @param kind          {@code CRASH} (uygulama çöktü), {@code ERROR} (yakalanan hata) ya da {@code USER} (kullanıcının
 *                      kendi bildirimi); zorunlu
 * @param message       hata iletisi
 * @param stackTrace    yığın dökümü ({@code Throwable.stackTraceToString()})
 * @param feature       hatanın olduğu özellik/ekran etiketi (ör. {@code canvas.editor})
 * @param appVersion    uygulama sürümü (versionName, ör. {@code 37.0.0})
 * @param versionCode   uygulama sürüm kodu
 * @param sdk           Android API düzeyi ({@code Build.VERSION.SDK_INT})
 * @param device        cihaz (ör. {@code "samsung SM-A515F"})
 * @param language      uygulama dili (BCP-47, ör. {@code tr})
 * @param installId     kuruluma özgü rastgele UUID; zorunlu
 * @param deviceTime    hatanın cihazdaki zamanı (Unix ms)
 * @param note          kullanıcının pencereye yazdığı not (isteğe bağlı)
 * @param breadcrumbs   son eylemler (eskiden yeniye kısa yazılar)
 * @param exceptionType isteğe bağlı istisna sınıfı (ör. {@code java.lang.IllegalStateException}); yoksa yığın
 *                      dökümünün ilk satırından çıkarılır
 */
public record ErrorReportRequest(
        String kind,
        String message,
        String stackTrace,
        String feature,
        String appVersion,
        Long versionCode,
        Integer sdk,
        String device,
        String language,
        String installId,
        Long deviceTime,
        String note,
        List<String> breadcrumbs,
        String exceptionType) {}
