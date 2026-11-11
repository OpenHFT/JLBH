/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Completion evidence for the supported HotSpot major-GC notification profile. */
final class GcCompletion {
    private final CountDownLatch done = new CountDownLatch(1);
    void observe(String cause, String action, boolean newerCollection, boolean markerCleared) {
        if ("System.gc()".equals(cause) && "end of major GC".equals(action)
                && newerCollection && markerCleared)
            done.countDown();
    }

    void await(long timeoutMS) {
        try {
            if (!done.await(timeoutMS, TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Post-warmup GC completion not observed; explicit GC may be "
                        + "disabled or unsupported. Use disableGcAfterWarmup(true) to opt out explicitly.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for post-warmup GC", interrupted);
        }
    }
}
