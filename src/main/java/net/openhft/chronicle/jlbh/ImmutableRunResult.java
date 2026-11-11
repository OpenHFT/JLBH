/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import static java.time.temporal.ChronoUnit.NANOS;
import static java.util.Collections.unmodifiableMap;
import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.*;

/**
 * Immutable summary of the latency statistics gathered during a single run of a probe.
 *
 * <p>Instances are created from the percentile values produced by the benchmark's
 * {@code Histogram} and expose these values as {@link Duration} objects via the
 * {@link JLBHResult.RunResult} interface. The underlying map returned by
 * {@link #percentiles()} is unmodifiable so that {@code ImmutableRunResult}
 * objects can be freely shared between threads after a run completes.</p>
 */
final class ImmutableRunResult implements JLBHResult.RunResult {

    /**
     * Percentiles in the order {@code Histogram.percentilesFor(long)} produces them; that
     * array is always a prefix of this order followed by the worst value. Higher
     * percentiles produced for 30 million or more samples have no constant and are omitted.
     */
    private static final Percentile[] HISTOGRAM_ORDER = {
            PERCENTILE_50TH, PERCENTILE_90TH, PERCENTILE_99TH, PERCENTILE_99_7TH,
            PERCENTILE_99_9TH, PERCENTILE_99_97TH, PERCENTILE_99_99TH,
            PERCENTILE_99_997TH, PERCENTILE_99_999TH};

    private final Map<Percentile, Duration> percentiles;

    /**
     * Construct a result from an array of percentile values expressed in
     * nanoseconds.
     *
     * @param percentiles percentile values returned for a single run
     */
    public ImmutableRunResult(double[] percentiles) {
        this.percentiles = asMap(percentiles);
    }

    private static Map<Percentile, Duration> asMap(double[] percentiles) {
        final Map<Percentile, Duration> data = new EnumMap<>(Percentile.class);
        final int measured = Math.min(percentiles.length - 1, HISTOGRAM_ORDER.length);
        for (int i = 0; i < measured; i++) {
            data.put(HISTOGRAM_ORDER[i], durationOf(percentiles[i]));
        }
        data.put(WORST, durationOf(percentiles[percentiles.length - 1]));
        return unmodifiableMap(data);
    }

    private static Duration durationOf(double percentile) {
        return Duration.of((long) percentile, NANOS);
    }

    @Override
    @NotNull
    public Map<Percentile, Duration> percentiles() {
        return percentiles;
    }

    @Override
    @NotNull
    public Duration get50thPercentile() {
        return percentiles.get(PERCENTILE_50TH);
    }

    @Override
    @NotNull
    public Duration get90thPercentile() {
        return percentiles.get(PERCENTILE_90TH);
    }

    @Override
    @NotNull
    public Duration get99thPercentile() {
        return percentiles.get(PERCENTILE_99TH);
    }

    @Override
    @Nullable
    public Duration get999thPercentile() {
        return percentiles.get(PERCENTILE_99_9TH);
    }

    @Override
    @Nullable
    public Duration get9999thPercentile() {
        return percentiles.get(PERCENTILE_99_99TH);
    }

    @Override
    @NotNull
    public Duration getWorst() {
        return percentiles.get(WORST);
    }

    @Override
    public String toString() {
        return "ImmutableRunResult{" +
                "percentiles=" + percentiles +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImmutableRunResult summary = (ImmutableRunResult) o;

        return percentiles.equals(summary.percentiles);

    }

    @Override
    public int hashCode() {
        return percentiles.hashCode();
    }
}
