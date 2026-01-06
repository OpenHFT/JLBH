/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Double.POSITIVE_INFINITY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PercentileSummaryTest {

    private static final double DELTA = 0.00001;
    private static final double[] PERCENTILES = {0.5, 0.9, 0.97, 0.99, 0.997, 0.999, 0.9997, 0.9999, 1.0};

    @Test
    @DisplayName("Missing percentile slots return POSITIVE_INFINITY")
    public void testThatMissingPercentilesAreOmitted() {
        final PercentileSummary percentileSummary = new PercentileSummary(false, constantSummaries(), defaultPercentiles());
        percentileSummary.printSummary();

        // first run
        assertEquals(0.002, percentileSummary.getPercentileForRun(0, 0), DELTA,
                "run0 50th percentile value should be 0.002");
        assertEquals(POSITIVE_INFINITY, percentileSummary.getPercentileForRun(1, 0), DELTA,
                "run0 missing percentile should return POSITIVE_INFINITY");
        assertEquals(0.002, percentileSummary.getPercentileForRun(8, 0), DELTA,
                "run0 worst percentile value should be 0.002");

        // second run
        assertEquals(0.003, percentileSummary.getPercentileForRun(0, 1), DELTA,
                "run1 50th percentile value should be 0.003");
        assertEquals(0.003, percentileSummary.getPercentileForRun(1, 1), DELTA,
                "run1 90th percentile value should be 0.003");
        assertEquals(POSITIVE_INFINITY, percentileSummary.getPercentileForRun(2, 1), DELTA,
                "run1 missing percentile should return POSITIVE_INFINITY");
        assertEquals(0.003, percentileSummary.getPercentileForRun(8, 1), DELTA,
                "run1 worst percentile value should be 0.003");
    }

    @Test
    @DisplayName("Worst percentile uses final bucket value")
    public void testThatWorstIsRenderedCorrectly() {
        List<double[]> percentileSummaries = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            double[] summary = new double[i];
            for (int j = 0; j < i; j++) {
                summary[j] = j + 2;
            }
            percentileSummaries.add(summary);
        }
        final PercentileSummary percentileSummary = new PercentileSummary(false, percentileSummaries, defaultPercentiles());
        percentileSummary.printSummary();

        assertEquals(0.003, percentileSummary.getPercentileForRun(8, 0), DELTA,
                "run0 worst percentile value should be 0.003");
        assertEquals(0.004, percentileSummary.getPercentileForRun(8, 1), DELTA,
                "run1 worst percentile value should be 0.004");
        assertEquals(0.005, percentileSummary.getPercentileForRun(8, 2), DELTA,
                "run2 worst percentile value should be 0.005");
        assertEquals(0.006, percentileSummary.getPercentileForRun(8, 3), DELTA,
                "run3 worst percentile value should be 0.006");
        assertEquals(0.007, percentileSummary.getPercentileForRun(8, 4), DELTA,
                "run4 worst percentile value should be 0.007");
        assertEquals(0.008, percentileSummary.getPercentileForRun(8, 5), DELTA,
                "run5 worst percentile value should be 0.008");
        assertEquals(0.009, percentileSummary.getPercentileForRun(8, 6), DELTA,
                "run6 worst percentile value should be 0.009");
        assertEquals(0.01, percentileSummary.getPercentileForRun(8, 7), DELTA,
                "run7 worst percentile value should be 0.01");
    }

    @Test
    @DisplayName("Variance calculation reflects min and max values")
    public void testThatVarianceIsCalculatedCorrectly() {
        final PercentileSummary percentileSummary = new PercentileSummary(false, constantSummaries(), defaultPercentiles());
        percentileSummary.printSummary();

        assertEquals((0.009 - 0.002) / (0.009 + 0.002 / 2) * 100, percentileSummary.calculateVariance(0), DELTA,
                "variance at index 0 should match min and max");
        assertEquals((0.009 - 0.003) / (0.009 + 0.003 / 2) * 100, percentileSummary.calculateVariance(1), DELTA,
                "variance at index 1 should match min and max");
        assertEquals((0.009 - 0.004) / (0.009 + 0.004 / 2) * 100, percentileSummary.calculateVariance(2), DELTA,
                "variance at index 2 should match min and max");
        assertEquals((0.009 - 0.005) / (0.009 + 0.005 / 2) * 100, percentileSummary.calculateVariance(3), DELTA,
                "variance at index 3 should match min and max");
        assertEquals(0, percentileSummary.calculateVariance(PERCENTILES.length - 2), DELTA,
                "variance for final index should be zero");
    }

    @Test
    @DisplayName("Variance calculation skips first run when configured")
    public void testVarianceSkipFirst() {
        final PercentileSummary percentileSummary = new PercentileSummary(true, constantSummaries(), defaultPercentiles());
        percentileSummary.printSummary();

        // 50th percentile
        assertEquals((0.009 - 0.003) / (0.009 + 0.003 / 2) * 100, percentileSummary.calculateVariance(0), DELTA,
                "variance with skipFirst should ignore run0 for 50th percentile");
        // 90th has no value in the first run, so nothing to skip?
        assertEquals((0.009 - 0.003) / (0.009 + 0.003 / 2) * 100, percentileSummary.calculateVariance(1), DELTA,
                "variance with skipFirst should handle 90th percentile");
    }

    @Test
    @DisplayName("Row callback receives expected percentiles and values")
    public void testForEachRow() {
        List<double[]> percentileSummaries = new ArrayList<>();
        double[] percentiles = {0.5, 0.9, 0.97, 1.0};
        for (int i = 2; i < percentiles.length; i++) {
            double[] summary = new double[i];
            for (int j = 0; j < i; j++) {
                summary[j] = i;
            }
            percentileSummaries.add(summary);
        }
        final PercentileSummary percentileSummary = new PercentileSummary(false, percentileSummaries, percentiles);
        final List<Double> receivedPercentiles = new ArrayList<>();
        final List<double[]> receivedValues = new ArrayList<>();
        final List<Double> receivedVariances = new ArrayList<>();
        percentileSummary.forEachRow((percentile, values, variance) -> {
            receivedPercentiles.add(percentile);
            receivedValues.add(values);
            receivedVariances.add(variance);
        });
        assertArrayEquals(new Double[]{0.5, 0.9, 1.0}, receivedPercentiles.toArray(new Double[]{}),
                "forEachRow should emit the expected percentile sequence");
        assertArrayEquals(new double[]{0.002, 0.003}, receivedValues.get(0), DELTA,
                "forEachRow should emit values for percentile 0.5");
        assertArrayEquals(new double[]{POSITIVE_INFINITY, 0.003}, receivedValues.get(1), DELTA,
                "forEachRow should emit values for percentile 0.9");
        assertArrayEquals(new double[]{0.002, 0.003}, receivedValues.get(2), DELTA,
                "forEachRow should emit values for percentile 1.0");
        assertArrayEquals(new Double[]{25.0, 0.0, 25.0}, receivedVariances.toArray(new Double[]{}),
                "forEachRow should emit expected variance values");
    }

    private static List<double[]> constantSummaries() {
        List<double[]> percentileSummaries = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            double[] summary = new double[i];
            Arrays.fill(summary, i);
            percentileSummaries.add(summary);
        }
        return percentileSummaries;
    }

    private static double[] defaultPercentiles() {
        return Arrays.copyOf(PERCENTILES, PERCENTILES.length);
    }
}
