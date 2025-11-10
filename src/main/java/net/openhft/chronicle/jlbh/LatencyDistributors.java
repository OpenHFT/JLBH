//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

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
    NORMAL {
        @Override
        public long apply(long averageLatencyNS) {
            return averageLatencyNS;
        }
    },
    RANDOM {
        @Override
        public long apply(long averageLatencyNS) {
            return ThreadLocalRandom.current()
                    .nextLong(1000, 2 * averageLatencyNS - 1000);
        }
    },
    RANDOM2 {
        @Override
        public long apply(long averageLatencyNS) {
            final float f = ThreadLocalRandom.current().nextFloat();
            return (long) (1000 + 4 * (averageLatencyNS - 1000) * f * f * f);
        }
    }
}
