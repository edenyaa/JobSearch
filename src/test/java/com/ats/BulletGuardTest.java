package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.BulletGuard;
import com.ats.domain.BulletGuard.Verdict;
import org.junit.jupiter.api.Test;

class BulletGuardTest {

    private final BulletGuard guard = new BulletGuard();
    private static final String RESUME = "Java Spring Boot PostgreSQL Docker";

    @Test
    public void GivenGroundedRewrite_WhenChecking_ThenItIsAcceptedAndMetricIsPreserved() {
        Verdict verdict = guard.check("Built APIs serving 2M requests", "Built Java APIs that served 2M requests", RESUME);

        assertTrue(verdict.accepted());
        assertTrue(verdict.metricPreserved());
    }

    @Test
    public void GivenRewriteThatDropsANumber_WhenChecking_ThenItIsRejectedAndMetricIsNotPreserved() {
        Verdict verdict = guard.check("Cut latency by 40%", "Reduced latency", RESUME);

        assertFalse(verdict.accepted());
        assertFalse(verdict.metricPreserved());
        assertTrue(verdict.reason().contains("drops a number"));
    }

    @Test
    public void GivenRewriteThatAddsANumber_WhenChecking_ThenItIsRejected() {
        Verdict verdict = guard.check("Built APIs", "Built APIs with 99.9% uptime", RESUME);

        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("adds numbers"));
    }

    @Test
    public void GivenRewriteThatAddsATechnologyNotInTheResume_WhenChecking_ThenItIsRejected() {
        Verdict verdict = guard.check("Built APIs", "Built APIs on Kubernetes", RESUME);

        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("Kubernetes"));
    }

    @Test
    public void GivenRewriteUsingATechnologyElsewhereInTheResume_WhenChecking_ThenItIsAccepted() {
        Verdict verdict = guard.check("Built APIs", "Built APIs with Docker", RESUME);

        assertTrue(verdict.accepted());
    }

    @Test
    public void GivenRewriteUsingAnAliasOfAResumeTechnology_WhenChecking_ThenItIsAccepted() {
        Verdict verdict = guard.check("Tuned queries", "Tuned Postgres queries", RESUME);

        assertTrue(verdict.accepted());
    }

    @Test
    public void GivenBuzzword_WhenChecking_ThenItIsRejected() {
        Verdict verdict = guard.check("Built APIs", "Spearheaded API design", RESUME);

        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("spearheaded"));
    }

    @Test
    public void GivenEveryBannedWord_WhenChecking_ThenEachOneIsRejected() {
        for (String word : BulletGuard.BANNED_WORDS) {
            assertFalse(guard.check("Built APIs", "Built " + word + " APIs", RESUME).accepted(), word);
        }
    }

    @Test
    public void GivenBlankOrNullRewrite_WhenChecking_ThenItIsRejected() {
        assertFalse(guard.check("Built APIs", "   ", RESUME).accepted());
        assertFalse(guard.check("Built APIs", null, RESUME).accepted());
    }

    @Test
    public void GivenVeryLongRewrite_WhenChecking_ThenItIsRejected() {
        Verdict verdict = guard.check("Built APIs", "Built APIs " + "and more ".repeat(60), RESUME);

        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("too long"));
    }

    @Test
    public void GivenMetricsInDifferentUnits_WhenChecking_ThenTheyAreComparedExactly() {
        assertTrue(guard.check("Handled 10k users", "Handled 10k users daily", RESUME).metricPreserved());
        assertFalse(guard.check("Handled 10k users", "Handled 10 users daily", RESUME).accepted());
        assertTrue(guard.check("Saved 3x time", "Saved 3x time", RESUME).metricPreserved());
    }

    @Test
    public void GivenWordContainingABuzzword_WhenChecking_ThenItIsNotFlagged() {
        assertNull(guard.bannedWordIn("Built a ninjaturtle parser"));
        assertEquals("ninja", guard.bannedWordIn("Ninja coder"));
        assertNull(guard.bannedWordIn(null));
    }

    @Test
    public void GivenSmallEdit_WhenChecking_ThenItIsAccepted() {
        assertTrue(guard.check("Wrote REST APIs in Java for billing", "Built REST APIs in Java for billing", RESUME).accepted());
    }

    @Test
    public void GivenCompleteRewrite_WhenChecking_ThenItIsRejectedAsTooDifferent() {
        Verdict verdict = guard.check("Wrote REST APIs in Java for billing", "Designed payment services handling invoices", RESUME);

        assertFalse(verdict.accepted());
        assertTrue(verdict.reason().contains("too much"));
    }

}
