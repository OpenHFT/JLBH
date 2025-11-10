//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

package net.openhft.chronicle.jlbh;

/**
 * Strategy interface used by JLBH to adjust the target latency between benchmark
 * iterations. Implementations take an expected average latency and return the
 * actual delay that should be used before invoking the next task. This allows
 * different distributions (such as constant or randomised) to be applied when
 * controlling the pace of a test.
 */
@FunctionalInterface
public interface LatencyDistributor {
    /**
     * Adjust the delay between benchmark iterations.
     *
     * @param averageLatencyNS the nominal delay in nanoseconds derived from the
     *                          configured throughput
     * @return the actual number of nanoseconds to wait before the next
     *         iteration is executed
     */
    long apply(long averageLatencyNS);
}
