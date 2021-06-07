package net.openhft.chronicle.core.jlbh;

@FunctionalInterface
@Deprecated(/* moved from net.openhft.chronicle.core.jlbh to net.openhft.chronicle.jlbh */)
public interface LatencyDistributor {
    long apply(long averageLatencyNS);
}
