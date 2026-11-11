/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.*;

class GcCompletionTest {
    @Test
    void interruptedCallerNeverRequestsCollection() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> PostWarmupGc.await(
                    new PrintStream(new ByteArrayOutputStream()), 100, () -> fail("requested GC")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptionRemainsVisible() {
        GcCompletion completion = new GcCompletion();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> completion.await(100));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void requestFailureIsNotSwallowed() {
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        AssertionError failure = new AssertionError("request failed");
        assertSame(failure, assertThrows(AssertionError.class,
                () -> PostWarmupGc.await(out, 100, () -> { throw failure; })));
    }

    @Test
    void settlingPauseHonoursMinimumAndInterruption() {
        long start = System.nanoTime();
        SettlingPause.await(10);
        assertTrue(System.nanoTime() - start >= 10_000_000L);
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> SettlingPause.await(100));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void defaultsAndExplicitOptionsAreIndependent() {
        JLBHOptions options = new JLBHOptions();
        assertFalse(options.disableGcAfterWarmup);
        assertEquals(100, options.pauseAfterWarmupMS);
        assertEquals(10_000, options.gcAfterWarmupTimeoutMS);
        options.pauseAfterWarmupMS(0);
        assertFalse(options.disableGcAfterWarmup);
        options.disableGcAfterWarmup(true);
        assertTrue(options.disableGcAfterWarmup);
        assertThrows(IllegalArgumentException.class, () -> options.pauseAfterWarmupMS(-1));
        assertThrows(IllegalArgumentException.class, () -> options.gcAfterWarmupTimeoutMS(0));
    }

}
