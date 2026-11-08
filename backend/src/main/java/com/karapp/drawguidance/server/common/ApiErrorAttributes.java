package com.karapp.drawguidance.server.common;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot'un {@code /error} sayfası (denetleyiciye ulaşmadan oluşan hatalar) da ortak biçimde döner:
 * {@code {"error": "<kod>", "message": "<Türkçe açıklama>"}}.
 */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> base = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = base.get("status") instanceof Integer s ? s : 500;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", ApiError.codeFor(status));
        out.put("message", ApiError.messageFor(status));
        return out;
    }
}
