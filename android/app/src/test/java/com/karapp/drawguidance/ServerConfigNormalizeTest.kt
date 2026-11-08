package com.karapp.drawguidance

import com.karapp.drawguidance.data.settings.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConfigNormalizeTest {
    @Test fun bareIpGetsSchemeAndDefaultPort() =
        assertEquals("http://192.168.1.20:4747/", ServerConfig.normalize("192.168.1.20"))

    @Test fun explicitPortIsKept() =
        assertEquals("http://192.168.1.20:8080/", ServerConfig.normalize(" 192.168.1.20:8080 "))

    @Test fun pathAndQueryAreDropped() =
        assertEquals("http://192.168.1.20:4747/", ServerConfig.normalize("http://192.168.1.20:4747/api/health?x=1"))

    // v44 https adresinde kapı yazılmazsa 443 (yayın sunucusu gibi); kapısız http adresine hata ayıklama kapısı eklenir
    @Test fun httpsHostWithoutPortKeepsStandardPort() =
        assertEquals("https://example.com/", ServerConfig.normalize("https://example.com"))

    @Test fun emptyOrGarbageIsRejected() {
        assertNull(ServerConfig.normalize(""))
        assertNull(ServerConfig.normalize("http://"))
        assertNull(ServerConfig.normalize("not a url with spaces"))
    }
}
