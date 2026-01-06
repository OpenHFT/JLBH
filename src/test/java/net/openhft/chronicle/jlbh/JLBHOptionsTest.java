/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.affinity.AffinityLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class JLBHOptionsTest {

    @Test
    @DisplayName("Applies configuration options to internal fields")
    void shouldApplyAllConfigurationOptions() throws Exception {
        JLBHOptions options = new JLBHOptions();
        LatencyDistributor distributor = averageLatencyNS -> averageLatencyNS * 2;
        Supplier<AffinityLock> customSupplier = () -> null;
        JLBHTask task = new NoOpTask();

        options.throughput(42)
                .throughput(84, TimeUnit.MILLISECONDS)
                .latencyDistributor(distributor)
                .accountForCoordinatedOmission(false)
                .recordJitterGreaterThanNs(12)
                .recordOSJitter(false)
                .warmUpIterations(123)
                .runs(5)
                .iterations(200)
                .iterations(1_000_000L)
                .jlbhTask(task)
                .pauseAfterWarmupMS(77)
                .skipFirstRun(true)
                .jitterAffinity(true)
                .acquireLock(customSupplier)
                .timeout(9876L);

        assertEquals(84, options.getThroughput(),
                "throughput should match the last configured value");
        assertEquals(TimeUnit.MILLISECONDS, options.getThroughputTimeUnit(),
                "throughput time unit should be MILLISECONDS");
        assertSame(distributor, options.getLatencyDistributor(),
                "latency distributor should be the configured instance");
        assertFalse(options.isAccountForCoordinatedOmission(),
                "coordinated omission flag should remain disabled after configuration");
        assertEquals(12, options.getRecordJitterGreaterThanNs(),
                "recordJitterGreaterThanNs should be 12");
        assertFalse(options.isRecordOSJitter(),
                "OS jitter flag should remain disabled after configuration");
        assertEquals(123, options.getWarmUpIterations(),
                "warmUpIterations should be 123");
        assertEquals(5, options.getRuns(),
                "runs should be 5");
        assertEquals(1_000_000L, options.getIterations(),
                "iterations should be 1,000,000");
        assertSame(task, options.getJLBHTask(),
                "jlbhTask should reference the configured task");
        assertEquals(77, options.getPauseAfterWarmupMS(),
                "pauseAfterWarmupMS should be 77");
        assertEquals(JLBHOptions.SkipFirstRun.SKIP, options.getSkipFirstRun(),
                "skipFirstRun should be SKIP when set to true");
        assertTrue(options.isJitterAffinity(),
                "jitter affinity flag should be enabled after configuration");
        assertSame(customSupplier, options.getAcquireLock(),
                "acquireLock should reference the custom supplier");
        assertEquals(9876L, options.getTimeout(),
                "timeout should be 9876");

        String printable = options.toString();
        assertTrue(printable.contains("runs=5"),
                printable + " should contain runs=5");
        assertTrue(printable.contains("iterations=1000000"),
                printable + " should contain iterations=1000000");
        assertTrue(printable.contains("latencyDistributor="),
                printable + " should contain latencyDistributor=");
    }

    @Test
    @DisplayName("Uses NO_SKIP when skipFirstRun is configured as false")
    void shouldRespectSkipFirstRunFalse() throws Exception {
        JLBHOptions options = new JLBHOptions().skipFirstRun(false);
        assertEquals(JLBHOptions.SkipFirstRun.NO_SKIP, options.getSkipFirstRun(),
                "skipFirstRun should be NO_SKIP when configured as false");
    }

    private static final class NoOpTask implements JLBHTask {
        @Override
        public void init(JLBH jlbh) {
            // no-op
        }

        @Override
        public void run(long startTimeNs) {
            // no-op
        }
    }
}
