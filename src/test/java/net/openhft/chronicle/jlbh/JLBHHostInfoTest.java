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
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
// Thread.threadId() is unavailable on the supported Java 8 baseline.
@SuppressWarnings({"deprecation", "java:S1874"})
class JLBHHostInfoTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recordsDeclaredBoundariesOnBothExecutionPaths(boolean eventLoop) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(output, true);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicLong sampledThread = new AtomicLong();
        JLBHTask task = new JLBHTask() {
            private JLBH benchmark;
            public void init(JLBH benchmark) { this.benchmark = benchmark; stream.println("TASK_INIT"); }
            public void run(long start) {
                sampledThread.set(Thread.currentThread().getId());
                benchmark.sample(System.nanoTime() - start);
            }
            @Override
            public void runComplete() { stream.println("RUN_COMPLETE"); }
            @Override
            public void complete() { complete.countDown(); }
        };
        long caller = Thread.currentThread().getId();
        JLBH benchmark = new JLBH(options(task).recordHostInfo(true), stream, null);
        if (eventLoop) {
            try (MediumEventLoop loop = new MediumEventLoop(null, "host-info", Pauser.busy(), true, null)) {
                loop.start();
                benchmark.eventLoopHandler(loop);
                assertTrue(complete.await(5, TimeUnit.SECONDS));
            }
        } else {
            benchmark.start();
            assertEquals(0, complete.getCount());
        }
        String text = output.toString();
        String[] records = Arrays.stream(text.split("\\R")).filter(line -> line.startsWith("Host: ")).toArray(String[]::new);
        assertEquals(3, records.length, text);
        assertTrue(records[0].contains("phase=before-init run=0 "));
        assertTrue(records[0].contains("java_thread_id=" + caller + " "));
        for (int run = 1; run <= 2; run++) {
            assertTrue(records[run].contains("phase=run-end run=" + run + " "));
            assertTrue(records[run].contains("java_thread_id=" + sampledThread.get() + " "));
        }
        assertTrue(text.indexOf(records[0]) < text.indexOf("TASK_INIT"));
        assertTrue(text.indexOf(records[1]) < text.indexOf("RUN_COMPLETE"));
        assertTrue(text.indexOf(records[2]) < text.lastIndexOf("RUN_COMPLETE"));
    }

    @Test
    void defaultRemainsDisabledAndInitialisationFailureIsNotReportedAsAnEnd() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JLBHTask broken = new JLBHTask() {
            public void init(JLBH benchmark) { throw new IllegalStateException("init failed"); }
            public void run(long start) { fail("must not run"); }
        };
        JLBHOptions options = options(broken);
        assertFalse(options.recordHostInfo);
        JLBH disabled = new JLBH(options, new PrintStream(output), null);
        assertThrows(IllegalStateException.class, disabled::start);
        assertFalse(output.toString().contains("Host: "));
        options.recordHostInfo(true);
        JLBH enabled = new JLBH(options, new PrintStream(output), null);
        assertThrows(IllegalStateException.class, enabled::start);
        assertTrue(output.toString().contains("phase=before-init"));
        assertFalse(output.toString().contains("phase=run-end"));
        assertSame(options, options.recordHostInfo(false));
    }

    private static JLBHOptions options(JLBHTask task) {
        return new JLBHOptions().warmUpIterations(10).iterations(10).throughput(10_000)
                .runs(2).recordOSJitter(false).timeout(0).jlbhTask(task);
    }
}
