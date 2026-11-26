/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InterruptedRunCleanupTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void interruptionStopsIssuingAndDrainingSamples(int mode) throws Exception {
        boolean atEnd = mode != 0;
        boolean duringDrain = mode == 1;
        int iterations = 5000;
        AtomicInteger calls = new AtomicInteger();
        JLBHTask task = new JLBHTask() {
            JLBH harness;
            boolean warmed;
            public void init(JLBH value) { harness = value; }
            @Override
            public void warmedUp() { warmed = true; }
            public void run(long start) {
                if (warmed && calls.incrementAndGet() == (atEnd ? iterations : 1)) {
                    Thread.currentThread().interrupt();
                    if (duringDrain) return; // The last asynchronous result will never arrive.
                }
                harness.sample(100);
            }
        };
        JLBHOptions options = new JLBHOptions().warmUpIterations(1).iterations(iterations)
                .runs(2).throughput(1_000_000).recordOSJitter(false)
                .accountForCoordinatedOmission(false).jlbhTask(task);
        options.acquireLock = () -> null;
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            JLBH harness = new JLBH(options, output, null);
            FutureTask<Void> result = new FutureTask<>(() -> {
                harness.start();
                assertFalse(Thread.currentThread().isInterrupted(), "start must clear the interrupt");
                return null;
            });
            Thread worker = new Thread(result, "interrupted-jlbh-regression");
            worker.start();
            try {
                assertDoesNotThrow(() -> result.get(5, TimeUnit.SECONDS),
                        "an interrupted run must not wait for samples that will never arrive");
                if (atEnd) assertEquals(iterations, calls.get());
                else assertTrue(calls.get() > 0 && calls.get() < iterations);
            } finally {
                // Release the deliberately stuck baseline without leaving a test worker behind.
                if (worker.isAlive()) harness.abort();
                worker.join(5000);
                assertFalse(worker.isAlive(), "benchmark worker did not terminate");
            }
        }
    }
}
