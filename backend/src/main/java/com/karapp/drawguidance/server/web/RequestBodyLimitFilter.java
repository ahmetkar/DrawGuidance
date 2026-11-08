package com.karapp.drawguidance.server.web;

import com.karapp.drawguidance.server.common.ApiError;
import com.karapp.drawguidance.server.common.BodyTooLargeException;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.report.ErrorReportController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * v40 genel istek gövdesi sınırı ({@code /api/**}). Spring MVC JSON gövdelerini ({@code @RequestBody}) sınırsız
 * belleğe okur ve Tomcat'in {@code max-http-form-post-size} ayarı yalnız form gövdelerine uygulanır; bu süzgeç
 * olmadan herkese açık bir uca (ör. RTDN) gönderilen büyük gövde belleği doldurabilirdi.
 *
 * <ul>
 *   <li>{@code Content-Length} sınırı aşıyorsa gövde okunmadan 413 {@code too_large} (kimlik denetiminden önce).</li>
 *   <li>Uzunluğu bildirilmeyen (parça parça) gövde sayılarak okunur; sınır aşılınca {@link BodyTooLargeException}
 *       (hata işleyicisi 413'e çevirir).</li>
 *   <li>Sınır: {@code drawguidance.limits.max-body-bytes} (öntanımlı 64 KB); rapor ucu {@code reports.max-body-bytes}.</li>
 * </ul>
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final long defaultMax;
    private final long reportsMax;

    public RequestBodyLimitFilter(DrawGuidanceProperties props) {
        this.defaultMax = props.limits().maxBodyBytes();
        this.reportsMax = props.reports().maxBodyBytes();
    }

    long limitFor(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(ErrorReportController.PATH) ? reportsMax : defaultMax;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long max = limitFor(request);
        if (request.getContentLengthLong() > max) {
            response.setHeader(HttpHeaders.CONNECTION, "close");
            ApiError.write(response, 413, "too_large", "İstek gövdesi çok büyük (en çok " + Math.max(1, max / 1024) + " KB)");
            return;
        }
        chain.doFilter(new Limited(request, max), response);
    }

    /** Gövdeyi sayarak okuyan istek. */
    static final class Limited extends HttpServletRequestWrapper {
        private final long max;
        private ServletInputStream stream;

        Limited(HttpServletRequest request, long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new Counting(super.getInputStream(), max);
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String enc = getCharacterEncoding();
            Charset cs = enc == null ? StandardCharsets.UTF_8 : Charset.forName(enc);
            return new BufferedReader(new InputStreamReader(getInputStream(), cs));
        }
    }

    static final class Counting extends ServletInputStream {
        private final ServletInputStream in;
        private final long max;
        private long count;

        Counting(ServletInputStream in, long max) {
            this.in = in;
            this.max = max;
        }

        private void add(long n) throws BodyTooLargeException {
            if (n > 0 && (count += n) > max) throw new BodyTooLargeException(max);
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) add(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = in.read(buf, off, len);
            add(n);
            return n;
        }

        @Override
        public boolean isFinished() { return in.isFinished(); }

        @Override
        public boolean isReady() { return in.isReady(); }

        @Override
        public void setReadListener(ReadListener listener) { in.setReadListener(listener); }
    }
}
