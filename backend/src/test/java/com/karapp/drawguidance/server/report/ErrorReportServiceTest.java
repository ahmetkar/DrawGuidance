package com.karapp.drawguidance.server.report;

import com.karapp.drawguidance.server.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v37 hata raporunun denetimi, kısaltılması ve parmak izi (veritabanı gerekmez). */
class ErrorReportServiceTest {
    static final String INSTALL = "0F8FAD5B-D9CB-469F-A165-70867728950E";
    static final String STACK = """
            java.lang.IllegalStateException: Tuval yüklenemedi: 42
            \tat com.karapp.drawguidance.canvas.CanvasLoader.load(CanvasLoader.kt:120)
            \tat com.karapp.drawguidance.canvas.CanvasViewModel$open$1.invokeSuspend(CanvasViewModel.kt:88)
            \tat kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
            Caused by: java.io.IOException: disk dolu
            \tat java.io.FileOutputStream.write(FileOutputStream.java:1)
            """;

    static ErrorReportRequest request(String kind, String message, String stack, String note) {
        return new ErrorReportRequest(kind, message, stack, "canvas.editor", "37.0.0", 37000L, 34, "Pixel 7", "tr", INSTALL,
                1760000000000L, note, List.of("a", "b"), null);
    }

    static ErrorReportRequest with(UnaryOperator<Object[]> change) {
        Object[] f = {"CRASH", "ileti", STACK, "canvas.editor", "37.0.0", 37000L, 34, "Pixel 7", "tr", INSTALL, 1760000000000L, null, null, null};
        f = change.apply(f);
        @SuppressWarnings("unchecked")
        List<String> crumbs = (List<String>) f[12];
        return new ErrorReportRequest((String) f[0], (String) f[1], (String) f[2], (String) f[3], (String) f[4], (Long) f[5],
                (Integer) f[6], (String) f[7], (String) f[8], (String) f[9], (Long) f[10], (String) f[11], crumbs, (String) f[13]);
    }

    static String code(ErrorReportRequest r) {
        return assertThrows(ApiException.class, () -> ErrorReportService.normalize(r)).code();
    }

    @Test
    void normalizesFields() {
        ErrorReportService.Normalized n = ErrorReportService.normalize(with(f -> {
            f[0] = " crash ";
            f[3] = "  canvas\u0007.editor \n";
            f[7] = "   ";
            f[12] = java.util.Arrays.asList("x", null, " ", "y");
            return f;
        }));
        assertEquals("CRASH", n.kind());
        assertEquals(INSTALL.toLowerCase(), n.installId());
        assertEquals("canvas.editor", n.feature(), "denetim karakterleri silinir, kırpılır");
        assertNull(n.device(), "boş alan yok sayılır");
        assertEquals(List.of("x", "y"), n.breadcrumbs());
        assertEquals("java.lang.IllegalStateException", n.exceptionType());
        assertTrue(n.truncated().isEmpty());
        assertEquals(34, n.sdk());
        assertEquals(1760000000000L, n.deviceTime());
        // Verilen istisna türü öncelikli
        assertEquals("x.Y", ErrorReportService.normalize(with(f -> { f[13] = "x.Y"; return f; })).exceptionType());
    }

    @Test
    void rejectsInvalidReports() {
        assertEquals("invalid_kind", code(with(f -> { f[0] = null; return f; })));
        assertEquals("invalid_kind", code(with(f -> { f[0] = "PANIC"; return f; })));
        assertEquals("invalid_install_id", code(with(f -> { f[9] = null; return f; })));
        assertEquals("invalid_install_id", code(with(f -> { f[9] = "abc"; return f; })));
        assertEquals("empty_report", code(with(f -> { f[1] = " "; f[2] = null; f[11] = ""; return f; })));
        assertEquals("validation", code(with(f -> { f[6] = 0; return f; })));
        assertEquals("validation", code(with(f -> { f[6] = 1001; return f; })));
        assertEquals("validation", code(with(f -> { f[5] = -1L; return f; })));
        assertEquals("validation", code(with(f -> { f[10] = -1L; return f; })));
        assertEquals("bad_request", assertThrows(ApiException.class, () -> ErrorReportService.normalize(null)).code());
        // Yalnız not yeterli (USER)
        assertEquals("USER", ErrorReportService.normalize(request("USER", null, null, "öneri")).kind());
    }

    @Test
    void truncatesLongFieldsWithoutSplittingSurrogates() {
        String emoji = "a".repeat(3_998) + "😀" + "b".repeat(10);
        ErrorReportService.Normalized n = ErrorReportService.normalize(with(f -> {
            f[1] = emoji;
            f[2] = "java.lang.Error: x\n" + "y".repeat(100_000) + "\nSON";
            f[12] = new ArrayList<>(java.util.stream.IntStream.range(0, 70).mapToObj(i -> "e" + i).toList());
            return f;
        }));
        assertEquals("a".repeat(3_998) + "…", n.message(), "vekil çifti bölünmez");
        assertTrue(n.stackTrace().length() <= ErrorReportService.MAX_STACK);
        assertTrue(n.stackTrace().startsWith("java.lang.Error: x\n"));
        assertTrue(n.stackTrace().endsWith("\nSON"));
        assertEquals(50, n.breadcrumbs().size());
        assertEquals("e20", n.breadcrumbs().get(0));
        assertEquals(List.of("message", "stackTrace", "breadcrumbs"), n.truncated());
        for (char c : n.message().toCharArray()) assertFalse(Character.isSurrogate(c));
    }

    @Test
    void fingerprintIgnoresLineNumbersMessagesAndSyntheticNames() {
        String a = ErrorReportService.normalize(request("CRASH", "Tuval yüklenemedi: 42", STACK, null)).fingerprint();
        String moved = STACK.replace("CanvasLoader.kt:120", "CanvasLoader.kt:150").replace("$open$1", "$open$7").replace("42", "9")
                .replace("disk dolu", "başka neden").replace("FileOutputStream.write", "Other.call");
        String b = ErrorReportService.normalize(request("CRASH", "Tuval yüklenemedi: 9", moved, null)).fingerprint();
        assertEquals(a, b, "satır, ileti, $1 ve Caused by sonrası parmak izini değiştirmez");
        assertEquals(32, a.length());
        assertTrue(a.matches("[0-9a-f]{32}"));
        assertNotEquals(a, ErrorReportService.normalize(request("ERROR", "x", STACK, null)).fingerprint(), "tür farklı");
        assertNotEquals(a, ErrorReportService.normalize(request("CRASH", "x", STACK.replace("CanvasLoader.load", "CanvasLoader.save"), null)).fingerprint());
        assertNotEquals(a, ErrorReportService.normalize(request("CRASH", "x", STACK.replace("IllegalState", "IllegalArgument"), null)).fingerprint());
        // Yığın yoksa ileti (sayılar #)
        assertEquals(ErrorReportService.normalize(request("ERROR", "zaman aşımı 30 ms", null, null)).fingerprint(),
                ErrorReportService.normalize(request("ERROR", "zaman aşımı 45 ms", null, null)).fingerprint());
        assertEquals(List.of("com.karapp.drawguidance.canvas.CanvasLoader.load", "com.karapp.drawguidance.canvas.CanvasViewModel$open$#.invokeSuspend",
                "kotlinx.coroutines.DispatchedTask.run"), ErrorReportService.topFrames(STACK, 5));
    }
}
