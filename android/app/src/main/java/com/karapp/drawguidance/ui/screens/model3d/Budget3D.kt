package com.karapp.drawguidance.ui.screens.model3d

import com.karapp.drawguidance.data.settings.Model3DPrefs
import com.karapp.drawguidance.platform.DeviceCaps

/**
 * v25 3B düzenleyicinin cihaz sınıfına göre bütçeleri ([DeviceCaps.tier]): katı cisim işlemlerinde üçgen sınırı (aşılırsa
 * önce sadeleştirilir), 3B fırça cisimlerinin çevre dilimi ve nokta sayısı, delik silindirinin dilimi, resim dışa
 * aktarmada en uzun kenar ve örnekleme bütçesi, varsayılan görüntü kalitesi, gölge dokusu ve yazılım çizicinin
 * çözünürlüğü. Böylece Android 5 – 16 arasındaki eski/zayıf cihazlarda işlemler makul sürede biter, belleğe sığar.
 */
object Budget3D {
    val tier: DeviceCaps.Tier get() = DeviceCaps.tier

    val csgTriangles: Int get() = when (tier) { DeviceCaps.Tier.LOW -> 4_000; DeviceCaps.Tier.MID -> 12_000; DeviceCaps.Tier.HIGH -> 30_000 }
    val brushSides: Int get() = when (tier) { DeviceCaps.Tier.LOW -> 8; DeviceCaps.Tier.MID -> 10; DeviceCaps.Tier.HIGH -> 14 }
    val brushSamples: Int get() = when (tier) { DeviceCaps.Tier.LOW -> 90; DeviceCaps.Tier.MID -> 160; DeviceCaps.Tier.HIGH -> 240 }
    val holeSegments: Int get() = when (tier) { DeviceCaps.Tier.LOW -> 16; DeviceCaps.Tier.MID -> 24; DeviceCaps.Tier.HIGH -> 32 }

    /** Resim dışa aktarmada en uzun kenar (piksel). */
    val exportMaxSide: Int get() = minOf(DeviceCaps.exportMaxSide, when (tier) { DeviceCaps.Tier.LOW -> 1600; DeviceCaps.Tier.MID -> 2048; DeviceCaps.Tier.HIGH -> 3072 })

    /** Yazılım çizicinin bir karede işleyebileceği en çok piksel (örnekleme dahil). */
    val exportPixels: Long get() = when (tier) { DeviceCaps.Tier.LOW -> 2_500_000L; DeviceCaps.Tier.MID -> 6_000_000L; DeviceCaps.Tier.HIGH -> 12_000_000L }

    val defaultQuality: Int get() = when (tier) {
        DeviceCaps.Tier.LOW -> Model3DPrefs.QUALITY_FAST
        DeviceCaps.Tier.MID -> Model3DPrefs.QUALITY_BALANCED
        DeviceCaps.Tier.HIGH -> Model3DPrefs.QUALITY_HIGH
    }

    /** Ekran kartı gölge dokusunun kenarı. */
    val shadowSize: Int get() = if (tier == DeviceCaps.Tier.LOW) 128 else 256

    /** Yazılım çizicinin çözünürlük katı. */
    val softwareScale: Float get() = if (tier == DeviceCaps.Tier.LOW) 0.75f else 1f

    /** İçe aktarmada önerilen ayrıntı (0 az, 1 orta, 2 çok). */
    val importDetail: Int get() = if (tier == DeviceCaps.Tier.LOW) 0 else 1

    val label: String get() = when (tier) { DeviceCaps.Tier.LOW -> "giriş düzeyi"; DeviceCaps.Tier.MID -> "orta düzey"; DeviceCaps.Tier.HIGH -> "üst düzey" }
}
