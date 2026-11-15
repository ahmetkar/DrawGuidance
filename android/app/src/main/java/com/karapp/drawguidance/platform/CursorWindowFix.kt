package com.karapp.drawguidance.platform

/**
 * v52 SQLite imleç penceresi: Android 9+ varsayılanı 2 MB. Tuval içeriği (çizimlerin ikili verisi) tek satırda durduğu
 * için uzun bir çizimden sonra satır 2 MB'ı aşınca Room okuması `SQLiteBlobTooBigException` ("Row too big to fit into
 * CursorWindow") ile çöküyordu: tuval listesi kayıttan sonra yenilenince uygulama kapanıyordu. Pencere boyutu (gizli
 * alan, yaygın düzeltme) cihazın belleğine göre büyütülür; erişilemezse sessizce eski boyutta kalır.
 */
object CursorWindowFix {
    @Volatile var appliedBytes: Int = 0
        private set

    fun apply() {
        runCatching {
            val bytes = when {
                DeviceCaps.lowRam -> 12 * 1024 * 1024
                else -> 32 * 1024 * 1024
            }
            val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            val current = field.getInt(null)
            if (current < bytes) field.setInt(null, bytes)
            appliedBytes = field.getInt(null)
        }
    }
}
