/*
 * Copyright 2016-2020 chronicle.software
 *
 *       https://chronicle.software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
 * Immutable implementation of the JLBHResult.RunResult interface.
 * Encapsulates the results for various percentiles and worst-case scenarios.
 */
final class ImmutableRunResult implements JLBHResult.RunResult {

    // A map of percentiles to their corresponding durations
    private final Map<Percentile, Duration> percentiles;

    /**
     * Constructs an ImmutableRunResult with the given percentile's data.
     *
     * @param percentiles An array of percentile values
     */
    public ImmutableRunResult(double[] percentiles) {
        this.percentiles = asMap(percentiles);
    }

    /**
     * Converts an array of percentiles into a map of Percentile to Duration.
     *
     * @param percentiles An array of percentile values
     * @return An unmodifiable map of Percentile to Duration
     */
    private static Map<Percentile, Duration> asMap(double[] percentiles) {
        final Map<Percentile, Duration> data = new EnumMap<>(Percentile.class);
        // TODO: duplicate of knowledge with Histogram.percentilesFor()
        data.put(PERCENTILE_50TH, durationOf(percentiles[0]));
        data.put(PERCENTILE_90TH, durationOf(percentiles[1]));
        data.put(PERCENTILE_99TH, durationOf(percentiles[2]));
        if (percentiles.length > 4) {
            data.put(PERCENTILE_99_7TH, durationOf(percentiles[3]));
        }
        if (percentiles.length > 5) {
            data.put(PERCENTILE_99_9TH, durationOf(percentiles[4]));
        }
        if (percentiles.length > 6) {
            data.put(PERCENTILE_99_97TH, durationOf(percentiles[5]));
        }
        if (percentiles.length > 7) {
            data.put(PERCENTILE_99_99TH, durationOf(percentiles[6]));
        }
        data.put(WORST, durationOf(percentiles[percentiles.length - 1]));
        return unmodifiableMap(data);
    }

    /**
     * Converts a percentile value to a Duration.
     *
     * @param percentile The percentile value in nanoseconds
     * @return A Duration representing the percentile value
     */
    private static Duration durationOf(double percentile) {
        return Duration.of((long) percentile, NANOS);
    }

    /**
     * Returns a map of percentiles to durations.
     *
     * @return An unmodifiable map of percentiles to durations
     */
    @Override
    @NotNull
    public Map<Percentile, Duration> percentiles() {
        return percentiles;
    }

    /**
     * Returns the duration of the 50th percentile.
     *
     * @return The duration of the 50th percentile
     */
    @Override
    @NotNull
    public Duration get50thPercentile() {
        return percentiles.get(PERCENTILE_50TH);
    }

    /**
     * Returns the duration of the 90th percentile.
     *
     * @return The duration of the 90th percentile
     */
    @Override
    @NotNull
    public Duration get90thPercentile() {
        return percentiles.get(PERCENTILE_90TH);
    }

    /**
     * Returns the duration of the 99th percentile.
     *
     * @return The duration of the 99th percentile
     */
    @Override
    @NotNull
    public Duration get99thPercentile() {
        return percentiles.get(PERCENTILE_99TH);
    }

    /**
     * Returns the duration of the 99.9th percentile, if available.
     *
     * @return The duration of the 99.9th percentile, or null if not available
     */
    @Override
    @Nullable
    public Duration get999thPercentile() {
        return percentiles.get(PERCENTILE_99_9TH);
    }

    /**
     * Returns the duration of the 99.99th percentile, if available.
     *
     * @return The duration of the 99.99th percentile, or null if not available
     */
    @Override
    @Nullable
    public Duration get9999thPercentile() {
        return percentiles.get(PERCENTILE_99_99TH);
    }

    /**
     * Returns the duration of the worst-case scenario.
     *
     * @return The duration of the worst-case scenario
     */
    @Override
    @NotNull
    public Duration getWorst() {
        return percentiles.get(WORST);
    }

    /**
     * Returns a string representation of the ImmutableRunResult.
     *
     * @return A string representation of the ImmutableRunResult
     */
    @Override
    public String toString() {
        return "ImmutableRunResult{" +
                "percentiles=" + percentiles +
                '}';
    }

    /**
     * Checks for equality between this ImmutableRunResult and another object.
     *
     * @param o The object to compare with
     * @return True if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImmutableRunResult summary = (ImmutableRunResult) o;

        return percentiles.equals(summary.percentiles);
    }

    /**
     * Returns the hash code for this ImmutableRunResult.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return percentiles.hashCode();
    }
}
