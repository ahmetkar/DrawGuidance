package com.karapp.drawguidance.server.web;

import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.security.TokenRevocationInterceptor;
import com.karapp.drawguidance.server.security.TokenRevocations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * v40 kötüye kullanım korumaları: genel gövde sınırı süzgeci (Spring Security'den önce) ve API istek sınırları
 * ({@link ApiRateLimitInterceptor}, kimlik denetiminden sonra) ve silinen hesabın eski jetonuyla yazma engeli
 * ({@link TokenRevocationInterceptor}).
 */
@Configuration
public class ApiLimitsConfig implements WebMvcConfigurer {
    private final DrawGuidanceProperties props;
    private final PublicForms forms;
    private final TokenRevocations revocations;

    public ApiLimitsConfig(DrawGuidanceProperties props, PublicForms forms, TokenRevocations revocations) {
        this.props = props;
        this.forms = forms;
        this.revocations = revocations;
    }

    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter() {
        FilterRegistrationBean<RequestBodyLimitFilter> r = new FilterRegistrationBean<>(new RequestBodyLimitFilter(props));
        r.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        r.addUrlPatterns("/api/*");
        return r;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Önce iptal denetimi (reddedilen istek kullanıcının yazma kotasını harcamasın), sonra sınırlar
        registry.addInterceptor(new TokenRevocationInterceptor(revocations)).addPathPatterns("/api/v1/**");
        registry.addInterceptor(new ApiRateLimitInterceptor(props, forms)).addPathPatterns("/api/v1/**");
    }
}
