//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

package net.openhft.chronicle.jlbh;

/**
 * Interface for tasks using the JLBH framework.
 */
public interface JLBHTask {
    /**
     * This method is called before the benchmark is started.
     * It gives the task a chance to do initialisation.
     *
     * @param jlbh A reference to the JLBH which is needed so that {@code jlbh.sample()}
     *             can be invoked when the benchmark is complete. It can also be used to
     *             create more probes into the benchamrk.
     */
    void init(JLBH jlbh);

    /**
     * This method is called for each iteration of the benchmark.
     * The timestamp passed into the method is not the same as {@code System.nanoTime()}.
     * It is the calculated time that the test is supposed to have started.
     *
     * @param startTimeNS The time that should be used as the start time for the sample.
     */
    void run(long startTimeNS);

    /**
     * Notify that warmup phase is over
     */
    default void warmedUp() {
    }

    /**
     * Notify that a run has completed
     */
    default void runComplete() {
    }

    /**
     * This method is used for any clean up that might be required by the benchmark.
     */
    default void complete() {
    }
}
