/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.threads.MediumEventLoop;
import net.openhft.chronicle.threads.Pauser;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

/** Forked integration probe so collector flags cannot be hidden by the test JVM. */
public class WarmupGcProcess {
    public static void main(String[] args) throws Exception {
        boolean loopMode = Boolean.parseBoolean(args[0]);
        boolean optOut = Boolean.parseBoolean(args[1]);
        AtomicLong pauseAt = new AtomicLong();
        PrintStream output = new PrintStream(System.out) {
            @Override
            public void println(String line) {
                if (line.startsWith("Pausing after warmup")) pauseAt.set(System.nanoTime());
                super.println(line);
            }
        };
        CountDownLatch done = new CountDownLatch(1);
        final int[] measured = {0};
        final int[] published = {0};
        JLBHTask task = new JLBHTask() {
            JLBH benchmark;
            boolean ready;
            int calls;
            public void init(JLBH benchmark) { this.benchmark = benchmark; }
            public void run(long start) {
                if (ready) measured[0]++;
                benchmark.sample(System.nanoTime() - start);
                if (++calls == 10) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    System.out.println("LAST_WARMUP_COMPLETED");
                }
            }
            public void warmedUp() {
                if (pauseAt.get() == 0 || System.nanoTime() - pauseAt.get() < TimeUnit.MILLISECONDS.toNanos(100))
                    throw new AssertionError("100 ms settling delay was not applied");
                ready = true;
                System.out.println("WARMED_UP_CALLBACK");
            }
            public void complete() { done.countDown(); }
        };
        JLBHOptions options = new JLBHOptions().warmUpIterations(10).iterations(10).runs(2)
                .throughput(10_000).recordOSJitter(false).jlbhTask(task);
        if (Boolean.parseBoolean(args[2])) options.gcAfterWarmupTimeoutMS(500);
        if (optOut) options.disableGcAfterWarmup(true);
        JLBH benchmark = new JLBH(options, output, result -> published[0]++);
        try {
            if (loopMode) {
                try (MediumEventLoop loop = new MediumEventLoop(null, "gc-probe", Pauser.busy(), true, null)) {
                    loop.start();
                    benchmark.eventLoopHandler(loop);
                    if (!done.await(5, TimeUnit.SECONDS)) throw new AssertionError("benchmark did not finish");
                }
            } else benchmark.start();
            if (measured[0] != 20) throw new AssertionError("measured=" + measured[0]);
            if (published[0] != 1) throw new AssertionError("published=" + published[0]);
            System.out.println("MEASURED=20");
        } catch (IllegalStateException failure) {
            System.out.println("REFUSED measured=" + measured[0] + " results=" + benchmark.percentileRuns().size() + " published=" + published[0]);
            throw failure;
        }
    }
}
