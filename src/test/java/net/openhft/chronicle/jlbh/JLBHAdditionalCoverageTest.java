/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.nio.charset.StandardCharsets;
import net.openhft.chronicle.core.util.NanoSampler;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class JLBHAdditionalCoverageTest {
    /**
     * Exercises {@link JLBH#abort()} to ensure the running harness can be stopped safely.
     */
    @Test
    public void shouldAbortWhenRequested() {
        AbortOnRunTask task = new AbortOnRunTask();
        JLBHOptions options = newHarness(task)
                .warmUpIterations(2)
                .iterations(20)
                .runs(5);
        JLBH jlbh = new JLBH(options, silentPrintStream(), JLBHResultConsumer.newThreadSafeInstance());

        jlbh.start();

        assertTrue("abort was not triggered", task.abortCount() > 0);
        assertTrue("task should finish quickly after abort", task.totalRuns() < 5 * 20);
    }

    /**
     * Verifies that configuring a timeout starts the watchdog thread without error.
     */
    @Test
    public void shouldStartTimeoutCheckerWhenTimeoutConfigured() {
        CountingTask task = new CountingTask();
        JLBHOptions options = newHarness(task)
                .warmUpIterations(1)
                .iterations(5)
                .runs(1)
                .timeout(1)
                .recordOSJitter(false);
        JLBH jlbh = new JLBH(options, silentPrintStream(), JLBHResultConsumer.newThreadSafeInstance());

        jlbh.start();

        assertTrue("samples should have been recorded", task.totalRuns() >= 1);
    }

    /**
     * Ensures {@link JLBH#eventLoopHandler(net.openhft.chronicle.core.threads.EventLoop)} rejects use when
     * coordinated omission compensation is disabled.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectEventLoopWhenCoordinatedOmissionDisabled() {
        JLBHOptions options = newHarness(new NoOpTask())
                .accountForCoordinatedOmission(false)
                .recordOSJitter(false);
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        jlbh.eventLoopHandler(null);
    }

    /**
     * Covers helper methods that format percentile output.
     */
    @Test
    public void shouldFormatRunSummaries() throws Exception {
        JLBHOptions options = newHarness(new NoOpTask());
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        Method addPr = JLBH.class.getDeclaredMethod("addPrToPrint", StringBuilder.class, String.class, int.class);
        addPr.setAccessible(true);
        StringBuilder sb = new StringBuilder();
        addPr.invoke(jlbh, sb, "99.9:     ", 2);
        assertEquals("99.9:     %12.2f %12.2f %12.2f%n", sb.toString());

        Method header = JLBH.class.getDeclaredMethod("generateRunSummaryHeader", int.class);
        header.setAccessible(true);
        assertEquals("Percentile   run1         run2      % Variation", header.invoke(jlbh, 2));

        Method unit = JLBH.class.getDeclaredMethod("timeUnitToString", TimeUnit.class);
        unit.setAccessible(true);
        assertEquals("ns", unit.invoke(jlbh, TimeUnit.NANOSECONDS));
        assertEquals("us", unit.invoke(jlbh, TimeUnit.MICROSECONDS));
        assertEquals("ms", unit.invoke(jlbh, TimeUnit.MILLISECONDS));
        assertEquals("s", unit.invoke(jlbh, TimeUnit.SECONDS));
        assertEquals("min", unit.invoke(jlbh, TimeUnit.MINUTES));
        assertEquals("h", unit.invoke(jlbh, TimeUnit.HOURS));
        assertEquals("day", unit.invoke(jlbh, TimeUnit.DAYS));
    }

    private static JLBHOptions newHarness(JLBHTask task) {
        return new JLBHOptions()
                .warmUpIterations(1)
                .iterations(1)
                .throughput(10_000)
                .runs(1)
                .recordOSJitter(false)
                .jlbhTask(task);
    }

    private static PrintStream silentPrintStream() {
        try {
            return new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 not supported", e);
        }
    }

    private static final class AbortOnRunTask implements JLBHTask {
        private JLBH jlbh;
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicInteger abortCount = new AtomicInteger();
        private final AtomicInteger totalRuns = new AtomicInteger();

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
        }

        @Override
        public void run(long startTimeNs) {
            int current = count.incrementAndGet();
            long duration = Math.max(0, System.nanoTime() - startTimeNs);
            jlbh.sample(duration);
            if (current == 10) {
                abortCount.incrementAndGet();
                jlbh.abort();
            }
            totalRuns.incrementAndGet();
        }

        @Override
        public void complete() {
            // no-op
        }

        int abortCount() {
            return abortCount.get();
        }

        int totalRuns() {
            return totalRuns.get();
        }
    }

    private static final class CountingTask implements JLBHTask {
        private final AtomicInteger totalRuns = new AtomicInteger();
        private JLBH harness;
        private NanoSampler sampler;

        @Override
        public void init(JLBH jlbh) {
            this.harness = jlbh;
            this.sampler = jlbh.addProbe("count");
        }

        @Override
        public void run(long startTimeNs) {
            totalRuns.incrementAndGet();
            long duration = System.nanoTime() - startTimeNs;
            harness.sample(duration);
            sampler.sampleNanos(duration);
        }

        @Override
        public void complete() {
            // no-op
        }

        int totalRuns() {
            return totalRuns.get();
        }
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
