/*
 * Copyright 2013-2026 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

/**
 * {@link JLBHResultConsumer} implementation that stores the last result in a
 * {@code volatile} field.
 *
 * <p>The accepted {@link JLBHResult} is expected to be immutable. Storing it in
 * a {@code volatile} variable ensures that once {@link #accept(JLBHResult)}
 * returns, any thread invoking {@link #get()} will observe the same instance
 * without further synchronisation.</p>
 */
final class ThreadSafeJLBHResultConsumer implements JLBHResultConsumer {

    // The stored JLBHResult must be immutable so it can be safely published
    // to other threads without additional synchronisation.
    private volatile JLBHResult result;

    /**
     * Stores the provided result for retrieval from other threads.
     * <p>
     * The {@code result} <strong>must</strong> be immutable as the consumer
     * keeps only a reference to it. Any subsequent mutation would break the
     * thread-safety guarantees.
     *
     * @param result Result provided by the JLBH; must not be mutable
     */
    @Override
    public void accept(JLBHResult result) {
        this.result = result;
    }

    /**
     * Returns the result previously supplied via {@link #accept(JLBHResult)}.
     *
     * @return the last accepted {@link JLBHResult}, or {@code null} if none has been provided
     */
    @Override
    public JLBHResult get() {
        return result;
    }
}
