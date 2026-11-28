/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.util.concurrent.TimeUnit;

/** A monotonic minimum settling interval, including on coarse operating-system timers. */
final class SettlingPause {
    private SettlingPause() {
    }

    static void await(long milliseconds) {
        long budget = TimeUnit.MILLISECONDS.toNanos(milliseconds);
        long start = System.nanoTime();
        try {
            long remaining;
            while ((remaining = budget - (System.nanoTime() - start)) > 0)
                TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during post-warmup pause", interrupted);
        }
    }
}
