/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.NanoSampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JLBHAdditionalCoverageTest {
    /**
     * Exercises {@link JLBH#abort()} to ensure the running harness can be stopped safely.
     */
    @Test
    @DisplayName("aborts a run when the task requests it")
    void shouldAbortWhenRequested() {
        AbortOnRunTask task = new AbortOnRunTask();
        JLBHOptions options = newHarness(task)
                .warmUpIterations(2)
                .iterations(20)
                .runs(5);
        JLBH jlbh = new JLBH(options, silentPrintStream(), JLBHResultConsumer.newThreadSafeInstance());

        jlbh.start();

        assertTrue(task.abortCount() > 0, "abort was not triggered");
        assertTrue(task.totalRuns() < 5 * 20, "task should finish quickly after abort");
    }

    /**
     * Verifies that configuring a timeout starts the watchdog thread without error.
     */
    @Test
    @DisplayName("starts the timeout checker when configured")
    void shouldStartTimeoutCheckerWhenTimeoutConfigured() {
        CountingTask task = new CountingTask();
        JLBHOptions options = newHarness(task)
                .warmUpIterations(1)
                .iterations(5)
                .runs(1)
                .timeout(100)
                .recordOSJitter(false);
        JLBH jlbh = new JLBH(options, silentPrintStream(), JLBHResultConsumer.newThreadSafeInstance());

        jlbh.start();

        assertTrue(task.totalRuns() >= 1, "samples should have been recorded");
    }

    /**
     * Ensures {@link JLBH#eventLoopHandler(net.openhft.chronicle.core.threads.EventLoop)} rejects use when
     * coordinated omission compensation is disabled.
     */
    @Test
    @DisplayName("rejects event loop use when coordinated omission is disabled")
    void shouldRejectEventLoopWhenCoordinatedOmissionDisabled() {
        JLBHOptions options = newHarness(new NoOpTask())
                .accountForCoordinatedOmission(false)
                .recordOSJitter(false);
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        assertThrows(UnsupportedOperationException.class,
                () -> jlbh.eventLoopHandler(null),
                "eventLoopHandler should reject when coordinated omission is disabled");
    }

    /**
     * Covers helper methods that format percentile output.
     */
    @Test
    @DisplayName("formats run summary output helper methods")
    void shouldFormatRunSummaries() {
        JLBHOptions options = newHarness(new NoOpTask());
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        StringBuilder sb = new StringBuilder();
        jlbh.addPrToPrint(sb, "99.9:     ", 2);
        assertEquals("99.9:     %12.2f %12.2f %12.2f%n", sb.toString(),
                "addPrToPrint should format a percentile row with three columns");

        assertEquals("Percentile   run1         run2      % Variation", jlbh.generateRunSummaryHeader(2),
                "generateRunSummaryHeader should include run numbers and variation");

        assertEquals("ns", jlbh.timeUnitToString(TimeUnit.NANOSECONDS),
                "timeUnitToString should map NANOSECONDS to ns");
        assertEquals("us", jlbh.timeUnitToString(TimeUnit.MICROSECONDS),
                "timeUnitToString should map MICROSECONDS to us");
        assertEquals("ms", jlbh.timeUnitToString(TimeUnit.MILLISECONDS),
                "timeUnitToString should map MILLISECONDS to ms");
        assertEquals("s", jlbh.timeUnitToString(TimeUnit.SECONDS),
                "timeUnitToString should map SECONDS to s");
        assertEquals("min", jlbh.timeUnitToString(TimeUnit.MINUTES),
                "timeUnitToString should map MINUTES to min");
        assertEquals("h", jlbh.timeUnitToString(TimeUnit.HOURS),
                "timeUnitToString should map HOURS to h");
        assertEquals("day", jlbh.timeUnitToString(TimeUnit.DAYS),
                "timeUnitToString should map DAYS to day");
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
