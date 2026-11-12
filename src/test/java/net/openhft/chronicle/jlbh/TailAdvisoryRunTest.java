/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.threads.MediumEventLoop;
import net.openhft.chronicle.threads.Pauser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class TailAdvisoryRunTest {
    private static final class Task implements JLBHTask {
        JLBH benchmark;
        int samples;
        int completedRuns;
        boolean measured;
        final boolean abort;
        final CountDownLatch done = new CountDownLatch(1);
        Task(boolean abort) { this.abort = abort; }
        public void init(JLBH benchmark) { this.benchmark = benchmark; }
        public void warmedUp() { measured = true; samples = 0; }
        public void run(long start) {
            int sample = samples++;
            benchmark.sample(sample == 9999 ? 100_000 : sample < 9800 ? 100 : sample < 9980 ? 1000 : 50_000);
            if (abort && measured && samples == 9999) benchmark.abort();
        }
        public void runComplete() { completedRuns++; }
        public void complete() { done.countDown(); }
    }
    private static final class Run {
        final Task task;
        final JLBH benchmark;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        Run(boolean disabled, boolean abort) {
            task = new Task(abort);
            JLBHOptions options = new JLBHOptions().jlbhTask(task).iterations(10_000).warmUpIterations(1).runs(1)
                    .throughput(1_000_000).accountForCoordinatedOmission(true).recordOSJitter(false)
                    .disableGcAfterWarmup(true).pauseAfterWarmupMS(0).acquireLock(() -> null)
                    .disableTailLatencyAdvisory(disabled);
            benchmark = new JLBH(options, new PrintStream(output), null);
        }
        void execute(boolean eventLoop) throws Exception {
            if (eventLoop) {
                try (MediumEventLoop loop = new MediumEventLoop(null, "tail-check", Pauser.busy(), true, null)) {
                    loop.start(); benchmark.eventLoopHandler(loop);
                    assertTrue(task.done.await(10, TimeUnit.SECONDS));
                }
            } else benchmark.start();
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void completedRunsUseConfiguredOutputAndDisablePreservesResults(boolean eventLoop) throws Exception {
        Run enabled = new Run(false, false); enabled.execute(eventLoop);
        Run disabled = new Run(true, false); disabled.execute(eventLoop);
        String text = enabled.output.toString();
        assertTrue(text.contains("Tail-latency advisory: run=1 samples=10000 P99.97/P99.7="), text);
        assertEquals(1, text.split("Tail-latency advisory:", -1).length - 1);
        assertFalse(disabled.output.toString().contains("Tail-latency advisory:"));
        assertArrayEquals(disabled.benchmark.percentileRuns().get(0), enabled.benchmark.percentileRuns().get(0));
        assertEquals(10_000, enabled.task.samples); assertEquals(1, enabled.task.completedRuns);
    }
    @Test void abortedRunDoesNotEmitACompletedRunAdvisory() throws Exception {
        Run run = new Run(false, true);
        try { run.execute(false); }
        finally { Thread.interrupted(); }
        assertFalse(run.output.toString().contains("Tail-latency advisory:"));
    }
}
