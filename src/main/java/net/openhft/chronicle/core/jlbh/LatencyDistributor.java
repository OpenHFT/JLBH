package net.openhft.chronicle.core.jlbh;

@FunctionalInterface
@Deprecated(/* moved from net.openhft.chronicle.core.jlbh to net.openhft.chronicle.jlbh */) // For removal in x.23
public interface LatencyDistributor {
    long apply(long averageLatencyNS);
}
