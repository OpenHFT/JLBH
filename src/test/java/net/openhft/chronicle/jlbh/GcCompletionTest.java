/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.Test;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.NotificationFilter;
import javax.management.ListenerNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class GcCompletionTest {
    @Test
    void acceptsOnlyFreshExplicitMajorCompletionWithAClearedMarker() {
        GcCompletion completion = new GcCompletion();
        completion.observe("System.gc()", "end of major GC", false, true);
        completion.observe("System.gc()", "end of major GC", true, false);
        completion.observe("Allocation Failure", "end of major GC", true, true);
        completion.observe("System.gc()", "end of minor GC", true, true);
        assertThrows(IllegalStateException.class, () -> completion.await(1));
        completion.observe("System.gc()", "end of major GC", true, true);
        assertDoesNotThrow(() -> completion.await(1));
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
    void removesListenersAfterTimeoutAndRequestFailure() {
        CountingEmitter emitter = new CountingEmitter();
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        assertThrows(IllegalStateException.class,
                () -> PostWarmupGc.await(out, 1, Collections.singletonList(emitter), () -> { }));
        assertEquals(0, emitter.active);
        assertThrows(IllegalArgumentException.class,
                () -> PostWarmupGc.await(out, 1, Collections.singletonList(emitter), () -> {
                    throw new IllegalArgumentException("request failed");
                }));
        assertEquals(0, emitter.active);
    }

    @Test
    void partialRegistrationAndUnsupportedVmDoNotLeakOrRequestGc() {
        CountingEmitter good = new CountingEmitter();
        CountingEmitter bad = new CountingEmitter() {
            public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
                throw new SecurityException("denied");
            }
        };
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        assertThrows(SecurityException.class,
                () -> PostWarmupGc.await(out, 1, Arrays.asList(good, bad), () -> fail("request")));
        assertEquals(0, good.active);
        assertThrows(IllegalStateException.class,
                () -> PostWarmupGc.await(out, 1, Collections.emptyList(), () -> fail("request")));
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

    private static class CountingEmitter extends NotificationBroadcasterSupport {
        int active;
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
            super.addNotificationListener(listener, filter, handback);
            active++;
        }
        public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
            super.removeNotificationListener(listener);
            active--;
        }
    }
}
