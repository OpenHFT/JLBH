/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.Histogram;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class TailLatencyAdvisoryTest {
    private DoubleUnaryOperator values(double p50, double p97, double p99, double p997,
                                       double p999, double p9997, double p9999) {
        Map<Double, Double> values = new HashMap<>();
        values.put(.5, p50); values.put(.97, p97); values.put(.99, p99); values.put(.997, p997);
        values.put(.999, p999); values.put(.9997, p9997); values.put(.9999, p9999);
        values.put(1.0, 1_000_000.0);
        return p -> values.get(p);
    }
    private void finds(String expected, DoubleUnaryOperator values) {
        TailLatencyAdvisory.Finding finding = TailLatencyAdvisory.largest(100_000, values);
        assertNotNull(finding); assertEquals(expected, finding.label());
    }
    @Test void eachOfTheFiveRequestedChecksCanTriggerIndependently() {
        finds("P99/P50", values(1, 6, 6, 6, 6, 6, 6));
        finds("P99.7/P97", values(1, 1, 2, 11, 11, 11, 11));
        finds("P99.9/P99", values(1, 1, 1, 2, 11, 11, 11));
        finds("P99.97/P99.7", values(1, 1, 1, 1, 2, 11, 11));
        finds("P99.99/P99.9", values(1, 1, 1, 1, 1, 1, 11));
    }
    @Test void boundariesAreStrictAndLowerRatiosAreSilent() {
        assertNull(TailLatencyAdvisory.largest(100_000, values(1, 1, 5, 10, 50, 100, 500)));
        assertNull(TailLatencyAdvisory.largest(100_000, values(1, 1, 1, 1, 1, 1, 1)));
        finds("P99/P50", values(1, 1, Math.nextUp(5.0), 6, 6, 6, 6));
    }
    @Test void choosesHighestRawQualifyingRatioAndExtremePercentileOnTie() {
        // 9/5 breaches its threshold further than 11/10, but 11 is the larger raw ratio.
        finds("P99.9/P99", values(1, 1, 9, 10, 99, 99, 99));
        finds("P99.99/P99.9", values(1, 1, 1, 20, 20, 400, 400));
    }
    @Test void invalidPairDoesNotHideAnotherValidFinding() {
        finds("P99.99/P99.9", values(0, Double.NaN, 1, Double.POSITIVE_INFINITY, 1, 1, 11));
        assertNull(TailLatencyAdvisory.largest(0, p -> { fail("empty histogram must not be queried"); return 1; }));
        assertNull(TailLatencyAdvisory.largest(10, p -> Double.NaN));
        assertNull(TailLatencyAdvisory.largest(10, p -> 0));
        assertNull(TailLatencyAdvisory.largest(10, p -> -1));
    }
    @Test void realHistogramReportsOneLineWithoutChangingSamples() {
        Histogram histogram = new Histogram(35, 8, 100);
        for (int i = 0; i < 10_000; i++) histogram.sample(i == 9999 ? 100_000 : i < 9800 ? 100 : i < 9980 ? 1000 : 50_000);
        double[] before = histogram.getPercentiles();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TailLatencyAdvisory.report(histogram, 2, new PrintStream(bytes));
        String output = bytes.toString();
        assertTrue(output.contains("run=2 samples=10000 P99.97/P99.7="), output);
        assertTrue(output.contains("This tail discontinuity is probably worth triaging/investigating."), output);
        assertEquals(1, output.split("[\\r\\n]+").length);
        assertEquals(10_000, histogram.totalCount()); assertArrayEquals(before, histogram.getPercentiles());
    }
    @Test void maximumPercentilesAreExcludedBeforeRanking() {
        DoubleUnaryOperator base = values(1, 1, 6, 10, 120, 120, 120);
        DoubleUnaryOperator capped = p -> p == 1.0 ? 120 : base.applyAsDouble(p);
        // The larger P99.9/P99 ratio equals the maximum; retain the lower eligible ratio.
        finds("P99/P50", capped);
        assertNull(TailLatencyAdvisory.largest(100, p -> p == .5 || p == .97 ? 1 : 100));
    }
    @Test void realHistogramIgnoresTailAtWorstSample() {
        Histogram histogram = new Histogram(35, 8, 100);
        for (int i = 0; i < 100; i++) histogram.sample(i < 98 ? 100 : 50_000);
        assertEquals(histogram.max(), histogram.percentile(.99));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TailLatencyAdvisory.report(histogram, 1, new PrintStream(bytes));
        assertEquals("", bytes.toString());
    }
    @Test void normalHistogramHasNoAdvisoryOutput() {
        Histogram histogram = new Histogram(35, 8, 100);
        for (int i = 0; i < 100; i++) histogram.sample(1000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TailLatencyAdvisory.report(histogram, 1, new PrintStream(bytes));
        assertEquals("", bytes.toString());
    }
}
