/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkLifecycleBoundaryTest {
    @Test
    void bothTimingModesCompleteFastAndSlowRunsWithOrderedTimestamps() {
        for (boolean coordinated : new boolean[]{false, true}) {
            for (int throughput : new int[]{100, 1_000_000}) {
                List<Long> starts = new ArrayList<>();
                JLBHTask task = new JLBHTask() {
                    JLBH harness;
                    boolean warmed;
                    public void init(JLBH jlbh) { harness = jlbh; }
                    @Override
                    public void warmedUp() { warmed = true; }
                    public void run(long start) {
                        if (warmed) { starts.add(start); assertTrue(start <= System.nanoTime()); }
                        harness.sample(100);
                    }
                };
                new JLBH(options(task).throughput(throughput).accountForCoordinatedOmission(coordinated),
                        new PrintStream(new ByteArrayOutputStream()), null).start();
                assertEquals(20, starts.size());
                for (int i = 1; i < starts.size(); i++) assertTrue(starts.get(i) > starts.get(i - 1));
            }
        }
    }

    private JLBHOptions options(JLBHTask task) {
        JLBHOptions options = new JLBHOptions().warmUpIterations(1).iterations(20).runs(1)
                .throughput(400).accountForCoordinatedOmission(false).recordOSJitter(false).jlbhTask(task);
        options.acquireLock = () -> null;
        return options;
    }

    @Test
    void nonCoordinatedSlowRateWaitsUntilEachDeadline() {
        List<Long> timestamps = new ArrayList<>();
        JLBHTask task = new JLBHTask() {
            JLBH harness;
            boolean warmed;
            public void init(JLBH jlbh) { harness = jlbh; }
            @Override
            public void warmedUp() { warmed = true; }
            public void run(long start) {
                if (warmed) timestamps.add(start);
                harness.sample(100);
            }
        };
        new JLBH(options(task), new PrintStream(new ByteArrayOutputStream()), null).start();
        assertEquals(20, timestamps.size());
        for (int i = 1; i < timestamps.size(); i++)
            assertTrue(timestamps.get(i) - timestamps.get(i - 1) >= 2_500_000,
                    "task began before its 2.5 ms interval elapsed");
    }

    @Test
    void failingResultConsumerStillStopsJitterMonitor() throws Exception {
        JLBHTask task = new JLBHTask() {
            JLBH harness;
            public void init(JLBH jlbh) { harness = jlbh; }
            public void run(long start) { harness.sample(100); }
        };
        IllegalStateException failure = new IllegalStateException("consumer failed");
        JLBH harness = new JLBH(options(task).recordOSJitter(true),
                new PrintStream(new ByteArrayOutputStream()), result -> { throw failure; });
        Field field = JLBH.class.getDeclaredField("osJitterMonitor");
        field.setAccessible(true);
        Thread monitor = (Thread) field.get(harness);
        try {
            assertSame(failure, assertThrows(IllegalStateException.class, harness::start));
            monitor.join(2000);
            assertFalse(monitor.isAlive(), "failed reporting must not leave the jitter thread spinning");
        } finally {
            Method terminate = monitor.getClass().getDeclaredMethod("terminate");
            terminate.setAccessible(true);
            terminate.invoke(monitor);
            monitor.join(2000);
        }
    }
}
