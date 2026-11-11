/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.io.PrintStream;

/** Requests collection once and waits for the post-warm-up marker to be reclaimed. */
final class PostWarmupGc {
    private PostWarmupGc() {
    }

    static void await(PrintStream output, long timeoutMS) {
        await(output, timeoutMS, System::gc);
    }

    static void await(PrintStream output, long timeoutMS, Runnable request) {
        if (Thread.currentThread().isInterrupted())
            throw new IllegalStateException("Interrupted before post-warmup GC");
        GcCompletion completion = new GcCompletion();
        output.println("Requesting post-warmup System.gc() and waiting for completion");
        request.run();
        completion.await(timeoutMS);
        output.println("Post-warmup GC marker reclamation observed");
    }
}
