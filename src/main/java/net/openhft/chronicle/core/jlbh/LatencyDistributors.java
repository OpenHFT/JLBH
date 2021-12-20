package net.openhft.chronicle.core.jlbh;

import java.util.concurrent.ThreadLocalRandom;

@Deprecated(/* moved from net.openhft.chronicle.core.jlbh to net.openhft.chronicle.jlbh */) // For removal in x.23
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
