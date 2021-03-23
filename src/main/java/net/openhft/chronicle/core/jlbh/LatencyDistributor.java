package net.openhft.chronicle.core.jlbh;

@FunctionalInterface
public interface LatencyDistributor {
    long apply(long averageLatencyNS);
}
