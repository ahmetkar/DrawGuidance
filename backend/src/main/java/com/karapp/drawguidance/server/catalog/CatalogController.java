package com.karapp.drawguidance.server.catalog;

import com.karapp.drawguidance.server.common.ApiException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Katalog uçları (herkese açık, salt okunur). Her yanıtın ETag'i {@code "cat-<sürüm>"}; {@code If-None-Match}
 * tutarsa gövdesiz 304. Bölüm gövdeleri istemci kabul ediyorsa ({@code Accept-Encoding: gzip}) önceden sıkıştırılmış
 * olarak gider: Tomcat güçlü ETag'li yanıtları kendisi sıkıştırmaz.
 *
 * <p>v37 ikili biçim ({@link CatalogBinary}): vektörler ve temel ağlar
 * {@code Accept: application/vnd.drawguidance.catalog+bin} (ya da {@code ?format=bin}) isteyene ikili gider; öntanımlı
 * JSON değişmedi (eski uygulamalar). İkili yanıtın ETag'i {@code -bin} ile biter ({@code "cat-<sürüm>-bin"}), yanıtlar
 * {@code Vary: Accept, Accept-Encoding} taşır. Şablonlar yalnız JSON'dur ({@code Accept} ikiliyi de içerse JSON gelir;
 * {@code ?format=bin} ise 400).
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    static final MediaType BINARY = MediaType.parseMediaType(CatalogBinary.MEDIA_TYPE);

    private final CatalogService service;

    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/manifest")
    public ResponseEntity<CatalogService.Manifest> manifest(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        CatalogMeta meta = service.meta();
        String etag = etag(meta.versionOrZero());
        if (matches(ifNoneMatch, etag)) return notModified(etag, null);
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(CatalogService.manifest(meta));
    }

    /**
     * v34 {@code ?kind=KIDS} (ya da ART, NOTES, COMIC, SIMPLE_ART, SIMPLE_NOTES): yalnız o tuval türünde kullanılan
     * öğeler (öğenin {@code kinds} alanı). ETag türü de içerir.
     */
    @GetMapping("/templates")
    public ResponseEntity<byte[]> templates(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding,
                                            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                            @RequestParam(value = "kind", required = false) String canvasKind,
                                            @RequestParam(value = "format", required = false) String format) {
        return items(CatalogKind.TEMPLATES, ifNoneMatch, acceptEncoding, accept, format, canvasKind);
    }

    @GetMapping("/vectors")
    public ResponseEntity<byte[]> vectors(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                          @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding,
                                          @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                          @RequestParam(value = "kind", required = false) String canvasKind,
                                          @RequestParam(value = "format", required = false) String format) {
        return items(CatalogKind.VECTORS, ifNoneMatch, acceptEncoding, accept, format, canvasKind);
    }

    @GetMapping("/mode-nets")
    public ResponseEntity<byte[]> modeNets(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                           @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding,
                                           @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                           @RequestParam(value = "format", required = false) String format) {
        return items(CatalogKind.MODE_NETS, ifNoneMatch, acceptEncoding, accept, format, null);
    }

    /** v34 süzmede kabul edilen tuval türleri. */
    static final java.util.Set<String> CANVAS_KINDS = java.util.Set.of("ART", "NOTES", "COMIC", "SIMPLE_ART", "SIMPLE_NOTES", "KIDS");

    private ResponseEntity<byte[]> items(CatalogKind kind, String ifNoneMatch, String acceptEncoding, String accept, String formatParam,
                                         String canvasKind) {
        String filter = canvasKind == null || canvasKind.isBlank() ? null : canvasKind.trim().toUpperCase(Locale.ROOT);
        if (filter != null && !CANVAS_KINDS.contains(filter)) {
            throw ApiException.badRequest("unknown_kind", "Bilinmeyen tuval türü: " + canvasKind);
        }
        CatalogService.Format format = format(kind, accept, formatParam);
        String vary = CatalogBinary.supports(kind) ? HttpHeaders.ACCEPT + ", " + HttpHeaders.ACCEPT_ENCODING : HttpHeaders.ACCEPT_ENCODING;
        CatalogMeta meta = service.meta();
        String etag = etag(meta.versionOrZero(), filter, format);
        if (matches(ifNoneMatch, etag)) return notModified(etag, vary);
        CatalogService.Body body = service.items(kind, meta, filter, format);
        boolean gzip = acceptsGzip(acceptEncoding);
        ResponseEntity.BodyBuilder out = ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache())
                .contentType(format == CatalogService.Format.BINARY ? BINARY : MediaType.APPLICATION_JSON).header(HttpHeaders.VARY, vary);
        if (gzip) out.header(HttpHeaders.CONTENT_ENCODING, "gzip");
        return out.body(gzip ? body.gzip() : body.bytes());
    }

    static String etag(long version) { return "\"cat-" + version + "\""; }

    /** v37 bölüm ETag'i: {@code "cat-<sürüm>[-<tür>][-bin]"} (ikili ve JSON gövdeler ayrı temsillerdir). */
    static String etag(long version, String canvasKind, CatalogService.Format format) {
        return "\"cat-" + version + (canvasKind == null ? "" : "-" + canvasKind) + (format == CatalogService.Format.BINARY ? "-bin" : "") + "\"";
    }

    /**
     * v37 yanıt biçimi: {@code ?format=json|bin} öncelikli (bilinmeyen değer 400 {@code unknown_format}, ikilisi olmayan
     * bölümde {@code bin} 400 {@code unsupported_format}); yoksa {@code Accept} ikili türü JSON'dan düşük olmayan
     * önceliklerle istiyorsa ikili, değilse JSON.
     */
    static CatalogService.Format format(CatalogKind kind, String accept, String param) {
        if (param != null && !param.isBlank()) {
            switch (param.trim().toLowerCase(Locale.ROOT)) {
                case "json" -> { return CatalogService.Format.JSON; }
                case "bin", "binary" -> {
                    if (!CatalogBinary.supports(kind)) {
                        throw ApiException.badRequest("unsupported_format", "Bu bölüm yalnız JSON olarak verilir: " + kind.fileName());
                    }
                    return CatalogService.Format.BINARY;
                }
                default -> throw ApiException.badRequest("unknown_format", "Bilinmeyen biçim: " + param + " (json ya da bin)");
            }
        }
        return CatalogBinary.supports(kind) && prefersBinary(accept) ? CatalogService.Format.BINARY : CatalogService.Format.JSON;
    }

    /** {@code Accept} ikili türü {@code q > 0} ile içeriyor ve {@code application/json}'a ondan yüksek öncelik vermiyor mu. */
    static boolean prefersBinary(String header) {
        if (header == null || header.isBlank()) return false;
        double bin = 0, json = 0;
        for (String part : header.split(",")) {
            String[] p = part.trim().split(";");
            String type = p[0].trim().toLowerCase(Locale.ROOT);
            double q = quality(p);
            if (type.equals(CatalogBinary.MEDIA_TYPE)) bin = Math.max(bin, q);
            else if (type.equals(MediaType.APPLICATION_JSON_VALUE)) json = Math.max(json, q);
        }
        return bin > 0 && bin >= json;
    }

    private static double quality(String[] params) {
        double q = 1;
        for (int i = 1; i < params.length; i++) {
            String s = params[i].trim();
            if (s.startsWith("q=")) {
                try { q = Double.parseDouble(s.substring(2)); } catch (NumberFormatException e) { q = 0; }
            }
        }
        return q;
    }

    private static <T> ResponseEntity<T> notModified(String etag, String vary) {
        ResponseEntity.HeadersBuilder<?> b = ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CacheControl.noCache());
        if (vary != null) b.header(HttpHeaders.VARY, vary);
        return b.build();
    }

    /** {@code If-None-Match}: liste, zayıf ({@code W/}) ve {@code *} biçimleri; tırnaksız değer de kabul edilir. */
    static boolean matches(String header, String etag) {
        if (header == null || header.isBlank()) return false;
        for (String part : header.split(",")) {
            String t = part.trim();
            if (t.equals("*")) return true;
            if (t.startsWith("W/")) t = t.substring(2);
            if (t.equals(etag) || ("\"" + t + "\"").equals(etag)) return true;
        }
        return false;
    }

    /** {@code Accept-Encoding} gzip'e izin veriyor mu ({@code q=0} reddeder; açık gzip girdisi {@code *}'dan önce gelir). */
    static boolean acceptsGzip(String header) {
        if (header == null || header.isBlank()) return false;
        Double gzip = null, any = null;
        for (String part : header.split(",")) {
            String[] p = part.trim().split(";");
            String name = p[0].trim();
            double q = quality(p);
            if (name.equalsIgnoreCase("gzip") || name.equalsIgnoreCase("x-gzip")) gzip = q;
            else if (name.equals("*")) any = q;
        }
        if (gzip != null) return gzip > 0;
        return any != null && any > 0;
    }
}
