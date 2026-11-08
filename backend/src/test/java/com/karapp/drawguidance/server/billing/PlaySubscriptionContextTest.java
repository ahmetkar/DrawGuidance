package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.karapp.drawguidance.server.support.FakeGooglePlayApi;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v40 Play {@code subscriptionsv2}'nin iptal ({@code canceledStateContext}) ve duraklatma ({@code pausedStateContext}) bilgisi. */
class PlaySubscriptionContextTest {
    static final Instant START = Instant.parse("2026-09-01T10:00:00Z");
    static final Instant END = Instant.parse("2026-11-01T10:00:00Z");

    ObjectNode base(String state) {
        return FakeGooglePlayApi.sub(state, "drawguidance_premium", "premium-monthly", "GPA.1", START, END, false, true, null, null);
    }

    @Test
    void userCancellationHasTimeAndReason() {
        ObjectNode n = base("SUBSCRIPTION_STATE_CANCELED");
        n.putObject("canceledStateContext").putObject("userInitiatedCancellation").put("cancelTime", "2026-10-20T08:30:00Z")
                .putObject("cancelSurveyResult").put("reason", "CANCEL_SURVEY_REASON_NOT_ENOUGH_USAGE");
        PlaySubscription s = PlaySubscription.parse(n);
        assertEquals(SubscriptionStatus.CANCELED, s.status());
        assertEquals(Instant.parse("2026-10-20T08:30:00Z"), s.cancelTime());
        assertEquals(PlaySubscription.CANCEL_USER, s.cancelReason());
        assertNull(s.autoResumeTime());
    }

    @Test
    void otherCancellationsHaveReasonOnly() {
        for (String[] c : new String[][]{{"systemInitiatedCancellation", "SYSTEM"}, {"developerInitiatedCancellation", "DEVELOPER"},
                {"replacementCancellation", "REPLACEMENT"}}) {
            ObjectNode n = base("SUBSCRIPTION_STATE_CANCELED");
            n.putObject("canceledStateContext").putObject(c[0]);
            PlaySubscription s = PlaySubscription.parse(n);
            assertEquals(c[1], s.cancelReason());
            assertNull(s.cancelTime());
        }
    }

    @Test
    void pausedHasAutoResumeTime() {
        ObjectNode n = base("SUBSCRIPTION_STATE_PAUSED");
        n.putObject("pausedStateContext").put("autoResumeTime", "2026-12-01T00:00:00Z");
        PlaySubscription s = PlaySubscription.parse(n);
        assertEquals(SubscriptionStatus.PAUSED, s.status());
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), s.autoResumeTime());
        assertNull(s.cancelReason());
    }

    @Test
    void acknowledgedCopyKeepsContext() {
        ObjectNode n = base("SUBSCRIPTION_STATE_CANCELED");
        n.put("acknowledgementState", "ACKNOWLEDGEMENT_STATE_PENDING");
        n.putObject("canceledStateContext").putObject("userInitiatedCancellation").put("cancelTime", "2026-10-20T08:30:00Z");
        PlaySubscription s = PlaySubscription.parse(n).acknowledgedCopy();
        assertTrue(s.acknowledged());
        assertEquals(PlaySubscription.CANCEL_USER, s.cancelReason());
        assertEquals(Instant.parse("2026-10-20T08:30:00Z"), s.cancelTime());
    }
}
