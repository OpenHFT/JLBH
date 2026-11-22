/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.Histogram;
import net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImmutableRunResultTest {

    /**
     * Constants for the leading percentiles, 50th up to and including 99.999th, with the fraction
     * each stands for. They are listed independently of the production table so that a constant
     * keyed to the wrong array position is caught. {@link Histogram#percentilesFor(long)} produces
     * still higher percentiles from 30 million samples; those have no constant and are left out.
     * Names are resolved when used, so the test still compiles against a JLBH without
     * PERCENTILE_99_997TH and fails on an assertion there.
     */
    private static final String[] NAMES = {
            "PERCENTILE_50TH", "PERCENTILE_90TH", "PERCENTILE_99TH", "PERCENTILE_99_7TH",
            "PERCENTILE_99_9TH", "PERCENTILE_99_97TH", "PERCENTILE_99_99TH",
            "PERCENTILE_99_997TH", "PERCENTILE_99_999TH"};
    private static final double[] FRACTIONS = {
            0.5, 0.9, 0.99, 0.997, 0.999, 0.9997, 0.9999, 0.99997, 0.99999};

    /**
     * Every percentile {@link Histogram#percentilesFor(long)} produces, up to 99.999th, must be
     * keyed by its own constant, for each sample-count threshold, and nothing else is mapped.
     */
    @Test
    void mapsEveryNamedPercentileThatTheHistogramProduces() {
        for (long count : new long[]{1_000, 20_000, 100_000, 300_000, 1_000_000,
                3_000_000, 10_000_000, 30_000_000}) {
            double[] fractions = Histogram.percentilesFor(count);
            double[] values = distinctValuesFor(count);
            Map<Percentile, Duration> map = new ImmutableRunResult(values).percentiles();

            Map<Percentile, Duration> expected = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(values.length - 1, NAMES.length); i++) {
                assertEquals(FRACTIONS[i], fractions[i], 1e-12,
                        "fraction at index " + i + " for " + count + " samples");
                expected.put(constant(NAMES[i]), Duration.ofNanos((long) values[i]));
            }
            expected.put(WORST, Duration.ofNanos((long) values[values.length - 1]));

            assertEquals(expected, map, "percentiles mapped for " + count + " samples");
            assertEquals(new ArrayList<>(expected.values()), new ArrayList<>(map.values()),
                    "order for " + count + " samples");
        }
    }

    @Test
    void tenMillionSamplesIncludeThe99_997thAnd99_999thPercentiles() {
        double[] values = distinctValuesFor(10_000_000);
        Map<Percentile, Duration> map = new ImmutableRunResult(values).percentiles();

        assertEquals(10, values.length);
        assertEquals(0.99997, Histogram.percentilesFor(10_000_000)[7], 0.0);
        assertEquals(0.99999, Histogram.percentilesFor(10_000_000)[8], 0.0);
        assertEquals(Duration.ofNanos(9_000), map.get(PERCENTILE_99_999TH));
        assertEquals(Duration.ofNanos(8_000), map.get(constant("PERCENTILE_99_997TH")));
        assertEquals(Duration.ofNanos(7_000), map.get(PERCENTILE_99_99TH));
        assertEquals(Duration.ofNanos(10_000), map.get(WORST));
    }

    private static Percentile constant(String name) {
        for (Percentile percentile : Percentile.values())
            if (percentile.name().equals(name))
                return percentile;
        throw new AssertionError("No Percentile constant " + name);
    }

    /**
     * Returns strictly increasing values, 1000 ns apart, shaped like
     * {@code Histogram.getPercentiles()} for {@code count} samples.
     */
    private static double[] distinctValuesFor(long count) {
        double[] values = new double[Histogram.percentilesFor(count).length];
        for (int i = 0; i < values.length; i++)
            values[i] = (i + 1) * 1_000.0;
        return values;
    }
}
