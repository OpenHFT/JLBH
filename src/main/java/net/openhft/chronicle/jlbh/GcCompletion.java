/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/** Portable reclamation evidence; does not claim that all VM activity has stopped. */
final class GcCompletion {
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final WeakReference<Object> marker = new WeakReference<>(new Object(), queue);

    void await(long timeoutMS) {
        long budget = TimeUnit.MILLISECONDS.toNanos(timeoutMS);
        long start = System.nanoTime();
        try {
            long remaining;
            while ((remaining = budget - (System.nanoTime() - start)) > 0) {
                if (queue.remove(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))) == marker)
                    return;
            }
            throw new IllegalStateException("Post-warmup GC completion not observed; explicit GC may be "
                    + "disabled or unsupported. Use disableGcAfterWarmup(true) to opt out explicitly.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for post-warmup GC", interrupted);
        }
    }
}
