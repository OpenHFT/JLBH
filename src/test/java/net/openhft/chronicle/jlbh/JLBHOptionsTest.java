/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.affinity.AffinityLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class JLBHOptionsTest {

    @Test
    @DisplayName("Applies configuration options to internal fields")
    public void shouldApplyAllConfigurationOptions() throws Exception {
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

        assertEquals(84, getField(options, "throughput", Integer.class).intValue(),
                "throughput should match the last configured value");
        assertEquals(TimeUnit.MILLISECONDS, getField(options, "throughputTimeUnit", TimeUnit.class),
                "throughput time unit should be MILLISECONDS");
        assertSame(distributor, getField(options, "latencyDistributor", LatencyDistributor.class),
                "latency distributor should be the configured instance");
        assertFalse(getField(options, "accountForCoordinatedOmission", Boolean.class),
                "accountForCoordinatedOmission should be false");
        assertEquals(12, getField(options, "recordJitterGreaterThanNs", Integer.class).intValue(),
                "recordJitterGreaterThanNs should be 12");
        assertFalse(getField(options, "recordOSJitter", Boolean.class),
                "recordOSJitter should be false");
        assertEquals(123, getField(options, "warmUpIterations", Integer.class).intValue(),
                "warmUpIterations should be 123");
        assertEquals(5, getField(options, "runs", Integer.class).intValue(),
                "runs should be 5");
        assertEquals(1_000_000L, getField(options, "iterations", Long.class).longValue(),
                "iterations should be 1,000,000");
        assertSame(task, getField(options, "jlbhTask", JLBHTask.class),
                "jlbhTask should reference the configured task");
        assertEquals(77, getField(options, "pauseAfterWarmupMS", Integer.class).intValue(),
                "pauseAfterWarmupMS should be 77");
        assertEquals(JLBHOptions.SkipFirstRun.SKIP, getField(options, "skipFirstRun", JLBHOptions.SkipFirstRun.class),
                "skipFirstRun should be SKIP when set to true");
        assertTrue(getField(options, "jitterAffinity", Boolean.class),
                "jitterAffinity should be true");
        assertSame(customSupplier, getField(options, "acquireLock", Supplier.class),
                "acquireLock should reference the custom supplier");
        assertEquals(9876L, getField(options, "timeout", Long.class).longValue(),
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
    @DisplayName("Respects skipFirstRun(false) setting")
    public void shouldRespectSkipFirstRunFalse() throws Exception {
        JLBHOptions options = new JLBHOptions().skipFirstRun(false);
        assertEquals(JLBHOptions.SkipFirstRun.NO_SKIP, getField(options, "skipFirstRun", JLBHOptions.SkipFirstRun.class),
                "skipFirstRun should be NO_SKIP when configured as false");
    }

    private static <T> T getField(JLBHOptions options, String name, Class<T> type) throws Exception {
        Field field = JLBHOptions.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(options));
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
