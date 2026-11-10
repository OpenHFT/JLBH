/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sample-timeout watchdog checks progress every ten seconds, so the false-abort cases need
 * benchmarks that last longer than one or two checks.
 */
class JLBHSampleTimeoutTest {

    @Test
    @Timeout(30)
    void watchdogStopsWhenStartReturns() {
        JLBH jlbh = new JLBH(options(new SamplingTask(0), 1_000, 100, 10_000, 1)
                .timeout(60_000), silent(), null);

        jlbh.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (watchdogAlive() && System.nanoTime() < deadline)
            Jvm.pause(10);
        assertFalse(watchdogAlive(), "sample-timeout watchdog outlived start()");
        assertFalse(Thread.currentThread().isInterrupted(), "caller left interrupted");
    }

    /**
     * No samples arrive during a ten second {@code init}, which is within the 30 second timeout,
     * so the first watchdog check must not abort the benchmark.
     */
    @Test
    @Timeout(60)
    void firstCheckMeasuresFromTheStartOfTheBenchmark() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JLBH jlbh = new JLBH(options(new SamplingTask(10_500), 1_000, 100, 10_000, 1)
                .timeout(30_000), new PrintStream(output, true), null);

        jlbh.start();

        assertFalse(output.toString().contains("Sample timed out"), output.toString());
        assertEquals(1, jlbh.percentileRuns().size());
    }

    /**
     * Run one counts the warm-up samples as well, so every later run has fewer samples than run
     * one had at the first check. The per-run reset of that count is progress, not a stall.
     */
    @Test
    @Timeout(90)
    void perRunCountResetIsNotAStall() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JLBH jlbh = new JLBH(options(new SamplingTask(0), 20_000, 1_050, 100, 2)
                .timeout(5_000), new PrintStream(output, true), null);

        jlbh.start();

        assertFalse(output.toString().contains("Sample timed out"), output.toString());
        assertEquals(2, jlbh.percentileRuns().size());
    }

    /**
     * The fixes must not disable the watchdog: a benchmark that never records a sample is still
     * aborted at the first check after the timeout, and start() returns with the caller not left
     * interrupted.
     */
    @Test
    @Timeout(30)
    void aGenuineStallIsStillAborted() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JLBH jlbh = new JLBH(options(new NoSampleTask(), 1_000, 100, 10_000, 1)
                .timeout(1_000), new PrintStream(output, true), null);

        long start = System.nanoTime();
        jlbh.start();
        long elapsedS = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

        assertTrue(output.toString().contains("Sample timed out"), output.toString());
        assertTrue(elapsedS < 20, "aborted after " + elapsedS + " s");
        assertFalse(Thread.currentThread().isInterrupted(), "caller left interrupted");
    }

    @Test
    void abortBeforeStartDoesNotThrow() {
        JLBH jlbh = new JLBH(options(new SamplingTask(0), 1, 1, 10_000, 1), silent(), null);

        jlbh.abort();
    }

    private static JLBHOptions options(JLBHTask task, int warmUp, int iterations, int throughput, int runs) {
        return new JLBHOptions()
                .warmUpIterations(warmUp)
                .iterations(iterations)
                .throughput(throughput)
                .runs(runs)
                .recordOSJitter(false)
                .jlbhTask(task);
    }

    private static PrintStream silent() {
        return new PrintStream(new ByteArrayOutputStream());
    }

    private static boolean watchdogAlive() {
        for (StackTraceElement[] stack : Thread.getAllStackTraces().values())
            for (StackTraceElement frame : stack)
                if (frame.getClassName().equals(JLBH.class.getName())
                        && frame.getMethodName().equals("checkSampleTimeout"))
                    return true;
        return false;
    }

    private static final class NoSampleTask implements JLBHTask {
        @Override
        public void init(JLBH jlbh) {
        }

        @Override
        public void run(long startTimeNs) {
        }
    }

    private static final class SamplingTask implements JLBHTask {
        private final long initPauseMs;
        private JLBH jlbh;

        SamplingTask(long initPauseMs) {
            this.initPauseMs = initPauseMs;
        }

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
            Jvm.pause(initPauseMs);
        }

        @Override
        public void run(long startTimeNs) {
            jlbh.sample(System.nanoTime() - startTimeNs);
        }
    }
}
