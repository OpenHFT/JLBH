/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.threads.EventLoop;
import net.openhft.chronicle.threads.MediumEventLoop;
import net.openhft.chronicle.threads.Pauser;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Progress lines are only printed once a run has lasted more than five seconds, so each case
 * runs a single seven second run in a bounded child VM.
 */
public class ProgressOutputProcess {
    private EventLoop eventLoop;

    public static void main(String[] args) {
        java.util.Locale.setDefault(java.util.Locale.ROOT);
        ProgressOutputProcess probe = new ProgressOutputProcess();
        try {
            probe.progressLinesGoToTheConfiguredPrintStream(Boolean.parseBoolean(args[0]));
            System.out.println("progress routing verified");
        } finally {
            probe.after();
        }
    }

    void after() {
        Closeable.closeQuietly(eventLoop);
        eventLoop = null;
    }

    void progressLinesGoToTheConfiguredPrintStream(boolean runFromEventLoop) {
        ByteArrayOutputStream configured = new ByteArrayOutputStream();
        ByteArrayOutputStream standardOut = new ByteArrayOutputStream();
        JLBHResultConsumer results = JLBHResultConsumer.newThreadSafeInstance();
        JLBHOptions options = new JLBHOptions()
                .warmUpIterations(1_000)
                .iterations(7_000)
                .throughput(1_000)
                .runs(1)
                .recordOSJitter(false)
                .jlbhTask(new SamplingTask());

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(standardOut, true));
        JLBH jlbh = new JLBH(options, new PrintStream(configured, true), results);
        try {
            if (runFromEventLoop) {
                eventLoop = new MediumEventLoop(null, "el", Pauser.busy(), true, null);
                eventLoop.start();
                jlbh.eventLoopHandler(eventLoop);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (results.get() == null && System.nanoTime() < deadline)
                    Jvm.pause(50);
            } else {
                jlbh.start();
            }
        } finally {
            jlbh.abort();
            System.setOut(originalOut);
        }

        assertNotNull(results.get(), "benchmark did not complete");
        assertTrue(configured.toString().contains(",000 out of 7,000"), configured.toString());
        assertFalse(standardOut.toString().contains("... run "), standardOut.toString());
    }

    private static final class SamplingTask implements JLBHTask {
        private JLBH jlbh;

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
        }

        @Override
        public void run(long startTimeNs) {
            jlbh.sample(System.nanoTime() - startTimeNs);
        }
    }
}
