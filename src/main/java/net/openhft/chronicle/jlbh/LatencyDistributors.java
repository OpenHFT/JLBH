/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Predefined strategies used to vary the latency applied by JLBH.
 * <ul>
 *     <li>{@link #NORMAL} - return the supplied latency unchanged.</li>
 *     <li>{@link #RANDOM} - choose a value uniformly between one microsecond
 *     and roughly twice the average.</li>
 *     <li>{@link #RANDOM2} - skewed random distribution that favours smaller
 *     values but can return up to about four times the average latency.</li>
 * </ul>
 */
public enum LatencyDistributors implements LatencyDistributor {
    /**
     * Returns the supplied latency unchanged, preserving the configured nanosecond interval.
     */
    NORMAL {
        @Override
        public long apply(long averageLatencyNS) {
            return averageLatencyNS;
        }
    },
    /** Uniformly random latency between ~1us and roughly double the average. */
    RANDOM {
        @Override
        public long apply(long averageLatencyNS) {
            return ThreadLocalRandom.current()
                    .nextLong(1000, 2 * averageLatencyNS - 1000);
        }
    },
    /** Skewed random latency favouring smaller values but allowing larger spikes. */
    RANDOM2 {
        @Override
        public long apply(long averageLatencyNS) {
            final float f = ThreadLocalRandom.current().nextFloat();
            return (long) (1000 + 4 * (averageLatencyNS - 1000) * f * f * f);
        }
    }
}
