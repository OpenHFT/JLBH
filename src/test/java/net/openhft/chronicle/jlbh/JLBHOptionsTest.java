//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

package net.openhft.chronicle.jlbh;

import net.openhft.affinity.AffinityLock;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class JLBHOptionsTest {

    @Test
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

        assertEquals(84, getField(options, "throughput", Integer.class).intValue());
        assertEquals(TimeUnit.MILLISECONDS, getField(options, "throughputTimeUnit", TimeUnit.class));
        assertSame(distributor, getField(options, "latencyDistributor", LatencyDistributor.class));
        assertFalse(getField(options, "accountForCoordinatedOmission", Boolean.class));
        assertEquals(12, getField(options, "recordJitterGreaterThanNs", Integer.class).intValue());
        assertFalse(getField(options, "recordOSJitter", Boolean.class));
        assertEquals(123, getField(options, "warmUpIterations", Integer.class).intValue());
        assertEquals(5, getField(options, "runs", Integer.class).intValue());
        assertEquals(1_000_000L, getField(options, "iterations", Long.class).longValue());
        assertSame(task, getField(options, "jlbhTask", JLBHTask.class));
        assertEquals(77, getField(options, "pauseAfterWarmupMS", Integer.class).intValue());
        assertEquals(JLBHOptions.SKIP_FIRST_RUN.SKIP, getField(options, "skipFirstRun", JLBHOptions.SKIP_FIRST_RUN.class));
        assertTrue(getField(options, "jitterAffinity", Boolean.class));
        assertSame(customSupplier, getField(options, "acquireLock", Supplier.class));
        assertEquals(9876L, getField(options, "timeout", Long.class).longValue());

        String printable = options.toString();
        assertTrue(printable.contains("runs=5"));
        assertTrue(printable.contains("iterations=1000000"));
        assertTrue(printable.contains("latencyDistributor="));
    }

    @Test
    public void shouldRespectSkipFirstRunFalse() throws Exception {
        JLBHOptions options = new JLBHOptions().skipFirstRun(false);
        assertEquals(JLBHOptions.SKIP_FIRST_RUN.NO_SKIP, getField(options, "skipFirstRun", JLBHOptions.SKIP_FIRST_RUN.class));
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
