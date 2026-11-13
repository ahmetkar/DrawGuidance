package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.apple.AppStoreApi;
import com.karapp.drawguidance.server.billing.apple.AppleBillingProperties;
import com.karapp.drawguidance.server.billing.apple.LibraryAppStoreApi;
import com.karapp.drawguidance.server.billing.play.GooglePlayApi;
import com.karapp.drawguidance.server.billing.play.HttpGooglePlayApi;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;

/** v39 faturalama bileşenleri: Google Play istemcisi, Pub/Sub jeton doğrulayıcısı ve (v50) App Store. */
@Configuration
public class BillingConfig {

    @Bean
    GooglePlayApi googlePlayApi(DrawGuidanceProperties props, ObjectMapper json) {
        return HttpGooglePlayApi.fromProperties(props.billing(), json);
    }

    /** v50 App Store: imza doğrulaması her zaman, Server API yalnız anahtar verildiyse. */
    @Bean
    AppStoreApi appStoreApi(AppleBillingProperties apple, ObjectMapper json) {
        return LibraryAppStoreApi.fromProperties(apple, json);
    }

    @Bean
    PubSubPushVerifier pubSubPushVerifier(DrawGuidanceProperties props, RestTemplateBuilder rest) {
        DrawGuidanceProperties.PubSub pubsub = props.billing().pubsub();
        // v40 Google anahtarları kısa zaman aşımıyla alınır (öntanımlı istemcide zaman aşımı yok); yalnız RS256
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(pubsub.jwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(rest.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(5)).build()).build();
        decoder.setJwtValidator(PubSubPushVerifier.validator(pubsub));
        return new PubSubPushVerifier(pubsub, decoder);
    }
}
