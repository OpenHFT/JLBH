package net.openhft.chronicle.jlbh;

import junit.framework.TestCase;
import net.openhft.affinity.Affinity;

import java.util.concurrent.TimeUnit;

public class JLBHOptionsTest extends TestCase {

    public void testThroughput() {
        JLBHOptions options = new JLBHOptions().throughput(5000);
        assertEquals(5000, options.throughput);
        assertEquals(TimeUnit.SECONDS, options.throughputTimeUnit);
    }

    public void testThroughputWithTimeUnit() {
        JLBHOptions options = new JLBHOptions().throughput(5000, TimeUnit.MILLISECONDS);
        assertEquals(5000, options.throughput);
        assertEquals(TimeUnit.MILLISECONDS, options.throughputTimeUnit);
    }

    public void testLatencyDistributor() {
        JLBHOptions options = new JLBHOptions().latencyDistributor(LatencyDistributors.RANDOM);
        assertEquals(LatencyDistributors.RANDOM, options.latencyDistributor);
    }

    public void testAccountForCoordinatedOmission() {
        JLBHOptions options = new JLBHOptions().accountForCoordinatedOmission(false);
        assertFalse(options.accountForCoordinatedOmission);
    }

    public void testRecordJitterGreaterThanNs() {
        JLBHOptions options = new JLBHOptions().recordJitterGreaterThanNs(500);
        assertEquals(500, options.recordJitterGreaterThanNs);
    }

    public void testRecordOSJitter() {
        JLBHOptions options = new JLBHOptions().recordOSJitter(false);
        assertFalse(options.recordOSJitter);
    }

    public void testWarmUpIterations() {
        JLBHOptions options = new JLBHOptions().warmUpIterations(5000);
        assertEquals(5000, options.warmUpIterations);
    }

    public void testRuns() {
        JLBHOptions options = new JLBHOptions().runs(5);
        assertEquals(5, options.runs);
    }

    public void testIterations() {
        JLBHOptions options = new JLBHOptions().iterations(200000);
        assertEquals(200000, options.iterations);
    }

    public void testIterationsLong() {
        JLBHOptions options = new JLBHOptions().iterations(300000L);
        assertEquals(300000L, options.iterations);
    }

    public void testPauseAfterWarmupMS() {
        JLBHOptions options = new JLBHOptions().pauseAfterWarmupMS(1000);
        assertEquals(1000, options.pauseAfterWarmupMS);
    }

    public void testSkipFirstRun() {
        JLBHOptions options = new JLBHOptions().skipFirstRun(true);
        assertEquals(JLBHOptions.SKIP_FIRST_RUN.SKIP, options.skipFirstRun);
    }

    public void testJitterAffinity() {
        JLBHOptions options = new JLBHOptions().jitterAffinity(true);
        assertTrue(options.jitterAffinity);
    }

    public void testAcquireLock() {
        JLBHOptions options = new JLBHOptions().acquireLock(Affinity::acquireLock);
        assertNotNull(options.acquireLock);
    }

    public void testTimeout() {
        JLBHOptions options = new JLBHOptions().timeout(60000L);
        assertEquals(60000L, options.timeout);
    }
}
