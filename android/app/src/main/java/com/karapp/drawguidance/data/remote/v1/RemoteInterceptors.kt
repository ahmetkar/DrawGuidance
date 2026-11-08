package com.karapp.drawguidance.data.remote.v1

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** v33 istemci sürümü başlığı (kimlik artık Firebase jetonuyla, yalnız kullanıcı uçlarında). */
class ClientInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("X-Client", "drawguidance-android/33").build())
}

/**
 * v30 yeniden deneme: tekrarlanabilir isteklerde (GET, PUT, DELETE, HEAD) ağ hatası ya da 502/503/504'te üstel
 * bekleyerek en çok [maxAttempts] kez dener (sunucu yeniden başlarken ya da Wi-Fi anlık koptuğunda). POST/PATCH
 * tekrarlanmaz (çift yazma olmasın).
 */
class RetryInterceptor(private val maxAttempts: Int = 3, private val baseDelayMs: Long = 250) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val idempotent = request.method in setOf("GET", "PUT", "DELETE", "HEAD")
        var attempt = 0
        while (true) {
            attempt++
            try {
                val response = chain.proceed(request)
                if (!idempotent || attempt >= maxAttempts || response.code !in setOf(502, 503, 504)) return response
                response.close()
            } catch (e: IOException) {
                if (!idempotent || attempt >= maxAttempts || chain.call().isCanceled()) throw e
            }
            Thread.sleep(baseDelayMs * (1L shl (attempt - 1)))
        }
    }
}
