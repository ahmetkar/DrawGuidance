package com.karapp.drawguidance.server.common;

import java.io.IOException;

/** v40 istek gövdesi genel sınırı aştı ({@code RequestBodyLimitFilter}); 413 {@code too_large} olarak döner. */
public class BodyTooLargeException extends IOException {
    private final long max;

    public BodyTooLargeException(long max) {
        super("İstek gövdesi " + max + " baytı aşıyor");
        this.max = max;
    }

    public long max() { return max; }
}
