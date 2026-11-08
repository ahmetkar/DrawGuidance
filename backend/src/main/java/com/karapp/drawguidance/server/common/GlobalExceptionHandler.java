package com.karapp.drawguidance.server.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Tüm hatalar sözleşmedeki JSON biçiminde döner: {@code {"error": "<kod>", "message": "<Türkçe açıklama>"}}.
 * Spring MVC'nin kendi hataları (405, 415, 406 …) {@link ResponseEntityExceptionHandler} üzerinden doğru durum
 * koduyla, aynı gövdeyle döner.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Object> body(HttpStatusCode status, HttpHeaders headers, String code, String message) {
        return ResponseEntity.status(status).headers(headers).body(new ApiError(code, message));
    }

    private static ResponseEntity<Object> body(HttpStatusCode status, String code, String message) {
        return body(status, HttpHeaders.EMPTY, code, message);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> api(ApiException e) { return body(e.status(), e.code(), e.getMessage()); }

    /**
     * v40 iletiler istemciye ham istisna iletisiyle dönmez (iç ayrıntı, sınıf adı, sorgu sızmasın): parametre türü
     * hatasında yalnız parametre adı, öbürlerinde genel ileti; ayrıntı günlüğe (DEBUG) yazılır.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> typeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", e.getName() + ": geçersiz değer");
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<Object> bad(Exception e) {
        log.debug("Geçersiz istek: {}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, "bad_request", "Geçersiz istek");
    }

    /** v40 genel gövde sınırı aşıldı (parça parça gönderilen gövde okunurken). */
    @ExceptionHandler(BodyTooLargeException.class)
    ResponseEntity<Object> tooLarge(BodyTooLargeException e) { return tooLargeBody(e.max()); }

    private static ResponseEntity<Object> tooLargeBody(long max) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).header(HttpHeaders.CONNECTION, "close")
                .body(new ApiError("too_large", "İstek gövdesi çok büyük (en çok " + Math.max(1, max / 1024) + " KB)"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Object> duplicate(DuplicateKeyException e) { return body(HttpStatus.CONFLICT, "duplicate", "Aynı kayıt zaten var"); }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<Object> db(DataAccessResourceFailureException e) {
        log.warn("Veritabanına ulaşılamadı: {}", e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, "database_unavailable", "Veritabanına şu an ulaşılamıyor");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> other(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BodyTooLargeException big) return tooLargeBody(big.max());
        }
        log.error("Beklenmeyen hata", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Sunucu hatası");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, "validation", msg.isEmpty() ? "Geçersiz alanlar" : msg);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BodyTooLargeException big) return tooLargeBody(big.max());
        }
        return body(HttpStatus.BAD_REQUEST, "bad_request", "İstek gövdesi okunamadı (geçersiz JSON ya da alan türü)");
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException e, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        return body(HttpStatus.NOT_FOUND, "not_found", "Böyle bir uç yok: /" + e.getResourcePath());
    }

    /** MVC'nin geri kalan hataları: durum kodu korunur, gövde ortak biçimde. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        return body(status, headers, ApiError.codeFor(status.value()), ApiError.messageFor(status.value()));
    }
}
