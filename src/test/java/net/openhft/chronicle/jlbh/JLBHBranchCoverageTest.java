/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.affinity.AffinityLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JLBHBranchCoverageTest {

    @Test
    @DisplayName("rejects resource tracing when explicitly enabled")
    void shouldRejectResourceTracingWhenEnabled() {
        String original = System.getProperty("jvm.resource.tracing");
        try {
            System.setProperty("jvm.resource.tracing", "");
            JLBHOptions options = optionsWithTask(new NoOpTask());
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> new JLBH(options, silentPrintStream(), null),
                    "Constructor should reject enabled resource tracing");
            assertTrue(ex.getMessage().contains("jvm.resource.tracing"),
                    "Exception message should reference jvm.resource.tracing; message was: " + ex.getMessage());
        } finally {
            restoreSystemProperty("jvm.resource.tracing", original);
        }
    }

    @Test
    @DisplayName("accepts resource tracing when explicitly disabled")
    void shouldAcceptResourceTracingWhenDisabled() {
        String original = System.getProperty("jvm.resource.tracing");
        try {
            System.setProperty("jvm.resource.tracing", "false");
            JLBHOptions options = optionsWithTask(new NoOpTask());
            JLBH jlbh = new JLBH(options, silentPrintStream(), null);
            assertNotNull(jlbh, "JLBH should construct when resource tracing is disabled");
            assertTrue(jlbh.percentileRuns().isEmpty(),
                    "percentileRuns should be empty for a fresh benchmark instance");
            assertTrue(jlbh.additionalPercentileRuns().isEmpty(),
                    "additionalPercentileRuns should be empty for a fresh benchmark instance");
        } finally {
            restoreSystemProperty("jvm.resource.tracing", original);
        }
    }

    @Test
    @DisplayName("requires a benchmark task to be configured")
    void shouldRequireTaskInOptions() {
        String original = System.getProperty("jvm.resource.tracing");
        try {
            System.clearProperty("jvm.resource.tracing");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> new JLBH(new JLBHOptions(), silentPrintStream(), null),
                    "Constructor should reject missing benchmark tasks");
            assertTrue(ex.getMessage().contains("jlbhTask must be set"),
                    "Exception message should explain the missing jlbhTask; message was: " + ex.getMessage());
        } finally {
            restoreSystemProperty("jvm.resource.tracing", original);
        }
    }

    @ParameterizedTest(name = "iterations {0} yields length {1} and mod {2}")
    @DisplayName("computes length and mod based on iterations")
    @CsvSource({
            "1000, 5000000000, 1000",
            "20000000, 10000000000, 1000000",
            "60000000, 20000000000, 1000000",
            "250000000, 60000000000, 10000000"
    })
    void shouldComputeLengthAndMod(long iterations, long expectedLength, long expectedMod) {
        JLBHOptions options = optionsWithTask(new NoOpTask()).iterations(iterations);
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        assertEquals(expectedLength, jlbh.getLength(),
                "length should match the expected threshold for iterations=" + iterations);
        assertEquals(expectedMod, jlbh.getMod(),
                "mod should match the expected threshold for iterations=" + iterations);
    }

    @Test
    @DisplayName("schedules coordinated omission with future start times")
    void shouldScheduleWithCoordinatedOmission() {
        CountingTask task = new CountingTask();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JLBHOptions options = optionsWithTask(task)
                .accountForCoordinatedOmission(true)
                .latencyDistributor(latency -> 20_000_000)
                .pauseAfterWarmupMS(1);
        JLBH jlbh = new JLBH(options, capturePrintStream(output), null);
        jlbh.start();
        assertEquals(options.getWarmUpIterations() + options.getIterations(), task.runCount(),
                "task should run for warmup and measured iterations");
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(text.contains("Warm up complete ("),
                "Output should include the warmup completion line; output was: " + text);
        assertTrue(text.contains("Pausing after warmup for 1 ms"),
                "Output should include the pause-after-warmup message; output was: " + text);
    }

    @Test
    @DisplayName("pauses for long latency without coordinated omission")
    void shouldPauseForLargeLatencyWithoutCoordinatedOmission() {
        CountingTask task = new CountingTask();
        JLBHOptions options = optionsWithTask(task)
                .accountForCoordinatedOmission(false)
                .latencyDistributor(latency -> 3_000_000);
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        jlbh.start();
        assertEquals(options.getWarmUpIterations() + options.getIterations(), task.runCount(),
                "task should run for warmup and measured iterations without coordinated omission");
    }

    @Test
    @DisplayName("busy waits for moderate latency without coordinated omission")
    void shouldBusyWaitForModerateLatencyWithoutCoordinatedOmission() {
        CountingTask task = new CountingTask();
        JLBHOptions options = optionsWithTask(task)
                .accountForCoordinatedOmission(false)
                .latencyDistributor(latency -> 2_000_000)
                .acquireLock(() -> null);
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        jlbh.start();
        assertEquals(options.getWarmUpIterations() + options.getIterations(), task.runCount(),
                "task should run for warmup and measured iterations with busy-wait scheduling");
    }

    @Test
    @DisplayName("aborts when interrupted during a long run")
    void shouldAbortWhenInterruptedDuringLongRun() {
        AbortOnRunTask task = new AbortOnRunTask();
        int warmupIterations = 1;
        int iterations = 1024;
        JLBHOptions options = optionsWithTask(task)
                .accountForCoordinatedOmission(false)
                .latencyDistributor(latency -> 0)
                .warmUpIterations(warmupIterations)
                .iterations(iterations);
        JLBH jlbh = new JLBH(options, silentPrintStream(), null);
        jlbh.start();
        assertEquals(1, task.abortCount(),
                "abort should be requested once during the long run");
        assertEquals(warmupIterations + iterations - 1, task.runCount(),
                "run count should stop one iteration before completion after interrupt");
    }

    @Test
    @DisplayName("respects skip-first-run configuration in summary output")
    void shouldRespectSkipFirstRunSettingInSummary() {
        double[] run1 = {1_000, 1_000, 1_000, 1_000};
        double[] run2 = {2_000, 2_000, 2_000, 2_000};
        List<double[]> percentileRuns = Arrays.asList(run1, run2);

        JLBHOptions skipOptions = optionsWithTask(new NoOpTask())
                .iterations(1)
                .runs(2)
                .skipFirstRun(true);
        JLBH skipJLBH = new JLBH(skipOptions, silentPrintStream(), null);
        StringBuilder skipOutput = new StringBuilder();
        skipJLBH.printPercentilesSummary("skip", percentileRuns, skipOutput);
        String skipText = skipOutput.toString();
        assertTrue(skipText.contains(" 0.00"),
                "Skip-first summary should report zero variance; output was: " + skipText);

        JLBHOptions includeOptions = optionsWithTask(new NoOpTask())
                .iterations(1)
                .runs(2)
                .skipFirstRun(false);
        JLBH includeJLBH = new JLBH(includeOptions, silentPrintStream(), null);
        StringBuilder includeOutput = new StringBuilder();
        includeJLBH.printPercentilesSummary("include", percentileRuns, includeOutput);
        String includeText = includeOutput.toString();
        assertTrue(includeText.contains("40.00"),
                "Include-first summary should report non-zero variance; output was: " + includeText);
    }

    @Test
    @DisplayName("starts OS jitter monitor with affinity enabled")
    void shouldStartOsJitterMonitorWithAffinity() {
        Assumptions.assumeTrue(AffinityLock.PROCESSORS > 1, "Affinity requires more than one processor");
        CountingTask task = new CountingTask();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JLBHOptions options = optionsWithTask(task)
                .recordOSJitter(true)
                .jitterAffinity(true)
                .iterations(1)
                .runs(1);
        JLBH jlbh = new JLBH(options, capturePrintStream(output), null);
        jlbh.start();
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(text.contains("Jitter thread running with affinity."),
                "Output should include the jitter affinity log line; output was: " + text);
    }

    private static JLBHOptions optionsWithTask(JLBHTask task) {
        return new JLBHOptions()
                .throughput(1_000_000)
                .warmUpIterations(1)
                .iterations(2)
                .runs(1)
                .recordOSJitter(false)
                .jlbhTask(task);
    }

    private static PrintStream silentPrintStream() {
        return new PrintStream(new ByteArrayOutputStream(), true);
    }

    private static PrintStream capturePrintStream(ByteArrayOutputStream output) {
        return new PrintStream(output, true);
    }

    private static void restoreSystemProperty(String key, String original) {
        if (original == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, original);
        }
    }

    private static final class CountingTask implements JLBHTask {
        private final AtomicInteger runCount = new AtomicInteger();
        private JLBH jlbh;

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
        }

        @Override
        public void run(long startTimeNs) {
            runCount.incrementAndGet();
            jlbh.sampleNanos(1);
        }

        int runCount() {
            return runCount.get();
        }
    }

    private static final class AbortOnRunTask implements JLBHTask {
        private final AtomicInteger runCount = new AtomicInteger();
        private final AtomicInteger abortCount = new AtomicInteger();
        private JLBH jlbh;

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
        }

        @Override
        public void run(long startTimeNs) {
            int count = runCount.incrementAndGet();
            jlbh.sampleNanos(1);
            if (count == 10) {
                abortCount.incrementAndGet();
                jlbh.abort();
            }
        }

        int runCount() {
            return runCount.get();
        }

        int abortCount() {
            return abortCount.get();
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
