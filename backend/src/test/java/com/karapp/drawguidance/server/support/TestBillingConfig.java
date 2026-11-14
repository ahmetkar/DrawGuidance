package com.karapp.drawguidance.server.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** v39 testlerde Google Play API yerine {@link FakeGooglePlayApi}, v50 App Store yerine {@link FakeAppStoreApi} ({@code @Primary}). */
@TestConfiguration(proxyBeanMethods = false)
public class TestBillingConfig {
    @Bean
    @Primary
    FakeGooglePlayApi fakeGooglePlayApi() { return new FakeGooglePlayApi(); }

    /** v50 App Store yerine sahte uygulama. */
    @Bean
    @Primary
    FakeAppStoreApi fakeAppStoreApi() { return new FakeAppStoreApi(); }
}
