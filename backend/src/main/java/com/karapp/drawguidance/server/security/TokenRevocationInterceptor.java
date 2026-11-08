package com.karapp.drawguidance.server.security;

import com.karapp.drawguidance.server.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * v40 hesabı silinmiş kullanıcının eski jetonuyla yazma isteği ({@code POST/PUT/PATCH}) 401 {@code token_revoked}
 * (bkz. {@link TokenRevocations}). {@code GET} ve {@code DELETE} (silmenin yinelenmesi) etkilenmez.
 */
public class TokenRevocationInterceptor implements HandlerInterceptor {
    static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH");

    private final TokenRevocations revocations;

    public TokenRevocationInterceptor(TokenRevocations revocations) { this.revocations = revocations; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!WRITES.contains(request.getMethod())) return true;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (!(a instanceof JwtAuthenticationToken jwt) || !revocations.isRevoked(jwt.getToken())) return true;
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"token revoked\"");
        ApiError.write(response, 401, "token_revoked", "Bu hesap silindi; devam etmek için yeniden giriş yapın");
        return false;
    }
}
