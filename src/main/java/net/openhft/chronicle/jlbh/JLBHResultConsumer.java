/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bridge between a {@link JLBH} run and the code that retrieves its final result snapshot.
 * <p>
 * Implementations receive the immutable {@link JLBHResult} from the harness
 * via {@link #accept(Object)} and later supply it through {@link #get()}.
 * This allows benchmark logic running on other threads to obtain the final
 * measurements once a run completes.
 * </p>
 * <p>
 * The {@link #newThreadSafeInstance()} factory returns a simple implementation
 * that publishes the most recent result using a {@code volatile} field for
 * safe cross-thread visibility.
 * </p>
 */
public interface JLBHResultConsumer extends Consumer<JLBHResult>, Supplier<JLBHResult> {

    /**
     * Creates a thread-safe consumer storing the last {@link JLBHResult} in a
     * {@code volatile} field.
     *
     * <p>The returned consumer simply publishes the provided result reference
     * for other threads to read via {@link #get()} with no defensive copying.
     * Because of this the {@code JLBHResult} instance supplied to
     * {@link #accept(Object)} <strong>must be immutable</strong> and never
     * mutated after publication. Failing to obey this contract would break the
     * thread-safety guarantees.</p>
     *
     * <p>When using this consumer outside of {@link JLBH}, ensure that every
     * result passed to it is immutable.</p>
     *
     * @return consumer that safely exposes the last result across threads
     */
    static JLBHResultConsumer newThreadSafeInstance() {
        return new ThreadSafeJLBHResultConsumer();
    }
}
