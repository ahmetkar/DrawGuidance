package com.karapp.drawguidance.server.security;

import com.karapp.drawguidance.server.account.DeletionRequestController;
import com.karapp.drawguidance.server.billing.PlanController;
import com.karapp.drawguidance.server.billing.RtdnController;
import com.karapp.drawguidance.server.billing.SubscriptionController;
import com.karapp.drawguidance.server.challenge.ChallengeController;
import com.karapp.drawguidance.server.common.ApiError;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.report.ErrorReportController;
import com.karapp.drawguidance.server.support.SupportMessageController;
import com.karapp.drawguidance.server.appversion.AppVersionController;
import com.karapp.drawguidance.server.feedback.FeedbackController;
import com.karapp.drawguidance.server.sync.SyncPermitController;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Kimlik doğrulama: durumsuz (oturum yok, CSRF kapalı), Firebase ID jetonu {@code Authorization: Bearer ...}.
 *
 * <ul>
 *   <li>Herkese açık: {@code /api/health}, {@code /actuator/health/**}, {@code /api/v1/catalog/**}.</li>
 *   <li>v40 jeton zorunlu: {@code POST /api/v1/reports} (hata raporu; v37'de kimliksiz de kabul ediliyordu, artık
 *       giriş yapmadan sorun bildirilemez) ve {@code POST /api/v1/feedback} (mod başına geri bildirim).</li>
 *   <li>v40 herkese açık: {@code GET /api/v1/app/version} (sürüm bilgisi) ve {@code GET /api/v1/sync/permit}
 *       (içerik eşitleme izni; jetonu süzgeç değil uç kendisi okur, jetonsuzda cihaz kimliği).</li>
 *   <li>Jeton zorunlu: {@code /api/v1/users/**} (jeton yok/geçersiz → 401; anahtarlar alınamazsa → 503); v39
 *       {@code /api/v1/subscriptions/**} ve {@code /api/v1/challenge/**} de aynı.</li>
 *   <li>v39 herkese açık: {@code GET /api/v1/plans}; {@code POST /api/v1/billing/rtdn} (Pub/Sub; Firebase jetonu
 *       okunmaz, ayarlıysa Pub/Sub OIDC jetonunu uç kendisi denetler).</li>
 *   <li>v40 herkese açık (web sitesinin formları, jeton okunmaz): {@code POST /api/v1/account/deletion-requests} ve
 *       {@code POST /api/v1/support/messages}. İstek sınırını uçlar kendisi uygular.</li>
 *   <li>v43 jeton zorunlu: {@code /api/v1/devices/**} (cihaz kaydı) ve {@code /api/v1/inbox/**} (teslimatlar).</li>
 *   <li>v40 CORS: {@code /api/v1/**} için yalnız izinli kökenler ({@code WebConfig}); ön kontrol ({@code OPTIONS})
 *       isteklerini CORS süzgeci kimlik denetiminden önce yanıtlar.</li>
 *   <li>Geri kalan her şey kapalı ve 404 döner (sunucuda başka uç yok; yeni bir uç eklenirse buraya yazılmalı).</li>
 * </ul>
 *
 * v40 güvenlik başlıkları (bütün yanıtlar JSON ya da ikili; sayfa yok): {@code Content-Security-Policy: default-src
 * 'none'; frame-ancestors 'none'}, {@code X-Content-Type-Options: nosniff}, {@code X-Frame-Options: DENY},
 * {@code Referrer-Policy: no-referrer}, HTTPS isteklerinde HSTS (TLS ters vekilde biter; {@code X-Forwarded-Proto}
 * yalnız güvenilen vekilden okunur, bkz. {@code application-prod.yml}). Kimlik isteyen yanıtlar önbelleğe alınmaz
 * ({@code Cache-Control: no-store}). v40 hesabı silinen kullanıcının eski jetonuyla yazma: {@link TokenRevocations}.
 *
 * <p>Jeton yalnız kimlik isteyen yollarda okunur: katalog ve sağlık uçlarına giden (süresi dolmuş
 * olabilecek) bir jeton bu istekleri bozmaz. Hata gövdeleri ortak biçimde: {@code {"error", "message"}}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String USERS = "/api/v1/users";
    /** v39 Firebase jetonu isteyen yollar. */
    static final String[] AUTH_PATHS = {USERS, SubscriptionController.PATH, ChallengeController.PATH,
            // v40 hata raporu ve geri bildirim giriş ister
            ErrorReportController.PATH, FeedbackController.PATH,
            // v43 cihaz kaydı ve gelen kutusu (paket, bildirim, duyuru, güncelleme teslimatları)
            com.karapp.drawguidance.server.distribution.InboxController.DEVICES, com.karapp.drawguidance.server.distribution.InboxController.INBOX};

    /** Üretim: Google securetoken JWKS (anahtarlar önbellekte tutulur, kısa zaman aşımı). */
    @Bean
    JwtDecoder jwtDecoder(DrawGuidanceProperties props, RestTemplateBuilder rest) {
        RestTemplate http = rest.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(5)).build();
        // v40 yalnız RS256 (Firebase'in imza algoritması; "none"/HS256 karışıklığı olmasın)
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.firebase().jwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(http).build();
        decoder.setJwtValidator(FirebaseTokens.validator(props.firebase()));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = new JsonEntryPoint();
        AccessDeniedHandler denied = (req, res, e) -> ApiError.write(res, 403, "forbidden", "Bu işlem için yetkiniz yok");
        AuthenticationFailureHandler failure = (req, res, e) -> {
            if (e instanceof AuthenticationServiceException) {
                log.warn("Firebase jetonu doğrulanamadı (Google anahtarları alınamadı mı?): {}", e.getMessage());
                ApiError.write(res, 503, "auth_unavailable", "Kimlik doğrulama şu an yapılamıyor; biraz sonra yeniden deneyin");
            } else {
                entryPoint.commence(req, res, e);
            }
        };
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        BearerTokenResolver usersOnly = req -> isUsersPath(req) ? bearer.resolve(req) : null;

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/health/**", "/api/v1/catalog/**").permitAll()
                        .requestMatchers(HttpMethod.POST, ErrorReportController.PATH, FeedbackController.PATH).authenticated()
                        .requestMatchers(HttpMethod.GET, AppVersionController.PATH, SyncPermitController.PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, PlanController.PATH).permitAll()
                        .requestMatchers(HttpMethod.POST, RtdnController.PATH).permitAll()
                        .requestMatchers(HttpMethod.POST, DeletionRequestController.PATH, SupportMessageController.PATH).permitAll()
                        .requestMatchers(USERS, USERS + "/**").authenticated()
                        .requestMatchers(SubscriptionController.PATH + "/**", ChallengeController.PATH + "/**").authenticated()
                        .requestMatchers(com.karapp.drawguidance.server.distribution.InboxController.DEVICES + "/**",
                                com.karapp.drawguidance.server.distribution.InboxController.INBOX,
                                com.karapp.drawguidance.server.distribution.InboxController.INBOX + "/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(usersOnly)
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denied)
                        .jwt(Customizer.withDefaults())
                        .addObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(failure);
                                return filter;
                            }
                        }))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
        return http.build();
    }

    static boolean isUsersPath(HttpServletRequest req) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        for (String p : AUTH_PATHS) if (path.equals(p) || path.startsWith(p + "/")) return true;
        return false;
    }

    /** 401 (jeton yok / geçersiz) JSON gövdeyle ve {@code WWW-Authenticate} başlığıyla; kapalı yollar için 404. */
    static final class JsonEntryPoint implements AuthenticationEntryPoint {
        private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

        @Override
        public void commence(HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res, AuthenticationException e) throws java.io.IOException {
            if (!isUsersPath(req)) {
                ApiError.write(res, 404, "not_found", "Böyle bir uç yok: " + req.getRequestURI());
                return;
            }
            bearer.commence(req, res, e);
            if (e instanceof OAuth2AuthenticationException) {
                ApiError.write(res, 401, "invalid_token", "Kimlik jetonu geçersiz ya da süresi dolmuş");
            } else {
                ApiError.write(res, 401, "unauthorized", "Firebase kimlik jetonu gerekli (Authorization: Bearer <ID token>)");
            }
        }
    }
}
