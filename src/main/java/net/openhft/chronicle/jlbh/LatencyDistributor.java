package net.openhft.chronicle.jlbh;

@FunctionalInterface
public interface LatencyDistributor {
    long apply(long averageLatencyNS);
}
