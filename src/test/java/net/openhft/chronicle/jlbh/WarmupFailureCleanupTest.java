/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;
import net.openhft.chronicle.core.threads.EventLoop;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class WarmupFailureCleanupTest {
    @Test void handlerRegistrationErrorTerminatesJitterMonitor() throws Exception {
        JLBH benchmark = new JLBH(new JLBHOptions().jlbhTask(new JLBHTask() {
            public void init(JLBH jlbh) { }
            public void run(long start) { }
        }));
        Field field = JLBH.class.getDeclaredField("osJitterMonitor");
        field.setAccessible(true);
        Thread monitor = (Thread) field.get(benchmark);
        Field runningField = monitor.getClass().getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(monitor);
        AssertionError failure = new AssertionError("handler registration failed");
        EventLoop loop = (EventLoop) Proxy.newProxyInstance(EventLoop.class.getClassLoader(), new Class<?>[]{EventLoop.class}, (p, method, args) -> {
            if (method.getName().equals("addHandler")) {
                throw failure;
            }
            return null;
        });
        try {
            assertSame(failure, assertThrows(AssertionError.class, () -> benchmark.eventLoopHandler(loop)));
            monitor.join(2000);
            assertFalse(monitor.isAlive(), "monitor leaked after Error");
        } finally {
            running.set(false);
            monitor.join(2000);
        }
    }
}
