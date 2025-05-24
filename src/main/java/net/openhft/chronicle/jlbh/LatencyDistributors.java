/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
