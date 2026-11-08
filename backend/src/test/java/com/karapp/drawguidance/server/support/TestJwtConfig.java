package com.karapp.drawguidance.server.support;

import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.security.FirebaseTokens;
import com.nimbusds.jose.JOSEException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Testlerde jeton çözücü: Google JWKS yerine yerel RSA açık anahtarı, kurallar üretimdekinin aynısı
 * ({@link FirebaseTokens#validator}). Üretimdeki JWKS çözücüsü de bağlamda durur; bu {@code @Primary} olduğu için
 * kaynak sunucusu bunu kullanır.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfig {
    @Bean
    @Primary
    JwtDecoder testJwtDecoder(DrawGuidanceProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
        decoder.setJwtValidator(FirebaseTokens.validator(props.firebase()));
        return decoder;
    }
}
