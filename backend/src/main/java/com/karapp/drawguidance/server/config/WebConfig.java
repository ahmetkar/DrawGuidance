package com.karapp.drawguidance.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * v40 CORS: yalnız {@code /api/v1/**} ve yalnız {@code drawguidance.web.allowed-origins} kökenleri (ortam değişkeni
 * {@code WEB_ALLOWED_ORIGINS}, virgülle ayrılmış; öntanımlı {@code https://drawguidance.ahmetkar.com} ve
 * {@code http://localhost:3000}) ve isteğe bağlı {@code drawguidance.web.allowed-origin-patterns} kalıpları
 * ({@code WEB_ALLOWED_ORIGIN_PATTERNS}, ör. Vercel önizlemeleri için {@code https://drawguidance-*.vercel.app}).
 * Yöntemler GET, POST, DELETE; başlıklar {@code Authorization} ve {@code Content-Type}; çerez yok (kimlik Firebase ID jetonuyla, {@code Authorization: Bearer ...}). Böylece web
 * sitesi tarayıcıdan {@code DELETE /api/v1/users/me}, silme talebi ve destek formu uçlarını çağırabilir.
 *
 * <p>Uygulama (Android) tarayıcı olmadığı için CORS'tan etkilenmez. v37–v39'daki "her köken" ayarı kaldırıldı.
 * Spring Security bu kaynağı ({@code corsConfigurationSource}) kullanır: izinsiz kökenin ön kontrolü 403 döner.
 */
@Configuration
public class WebConfig {
    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Bean
    CorsConfigurationSource corsConfigurationSource(DrawGuidanceProperties props) {
        List<String> origins = props.web().allowedOrigins();
        List<String> patterns = props.web().allowedOriginPatterns();
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        if (!patterns.isEmpty()) cors.setAllowedOriginPatterns(patterns);
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        log.info("CORS izinli kökenler (/api/v1/**): {} kalıplar: {}", origins, patterns);
        return source;
    }
}
