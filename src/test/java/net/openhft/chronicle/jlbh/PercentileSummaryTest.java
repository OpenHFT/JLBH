package net.openhft.chronicle.jlbh;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.POSITIVE_INFINITY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PercentileSummaryTest {

    public static final double DELTA = 0.00001;

    @Test
    public void testThatMissingPercentilesAreOmitted() {
        List<double[]> percentileSummaries = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            double[] summary = new double[i];
            for (int j = 0; j < i; j++) {
                summary[j] = i;
            }
            percentileSummaries.add(summary);
        }
        double[] percentiles = new double[]{0.5, 0.9, 0.97, 0.99, 0.997, 0.999, 0.9997, 0.9999, 1.0};

        final PercentileSummary percentileSummary = new PercentileSummary(false, percentileSummaries, percentiles);
        percentileSummary.printSummary();

        // first run
        assertEquals(0.002, percentileSummary.getPercentileForRun(0, 0), DELTA);
        assertEquals(POSITIVE_INFINITY, percentileSummary.getPercentileForRun(1, 0), DELTA);
        assertEquals(0.002, percentileSummary.getPercentileForRun(8, 0), DELTA);

        // second run
        assertEquals(0.003, percentileSummary.getPercentileForRun(0, 1), DELTA);
        assertEquals(0.003, percentileSummary.getPercentileForRun(1, 1), DELTA);
        assertEquals(POSITIVE_INFINITY, percentileSummary.getPercentileForRun(2, 1), DELTA);
        assertEquals(0.003, percentileSummary.getPercentileForRun(8, 1), DELTA);
    }

    @Test
    public void testThatWorstIsRenderedCorrectly() {
        List<double[]> percentileSummaries = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            double[] summary = new double[i];
            for (int j = 0; j < i; j++) {
                summary[j] = j + 2;
            }
            percentileSummaries.add(summary);
        }
        double[] percentiles = new double[]{0.5, 0.9, 0.97, 0.99, 0.997, 0.999, 0.9997, 0.9999, 1.0};

        final PercentileSummary percentileSummary = new PercentileSummary(false, percentileSummaries, percentiles);
        percentileSummary.printSummary();

        assertEquals(0.003, percentileSummary.getPercentileForRun(8, 0), DELTA);
        assertEquals(0.004, percentileSummary.getPercentileForRun(8, 1), DELTA);
        assertEquals(0.005, percentileSummary.getPercentileForRun(8, 2), DELTA);
        assertEquals(0.006, percentileSummary.getPercentileForRun(8, 3), DELTA);
        assertEquals(0.007, percentileSummary.getPercentileForRun(8, 4), DELTA);
        assertEquals(0.008, percentileSummary.getPercentileForRun(8, 5), DELTA);
        assertEquals(0.009, percentileSummary.getPercentileForRun(8, 6), DELTA);
        assertEquals(0.01, percentileSummary.getPercentileForRun(8, 7), DELTA);
    }

    @Test
    public void testThatVarianceIsCalculatedCorrectly() {
        List<double[]> percentileSummaries = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            double[] summary = new double[i];
            for (int j = 0; j < i; j++) {
                summary[j] = i;
            }
            percentileSummaries.add(summary);
        }
        double[] percentiles = new double[]{0.5, 0.9, 0.97, 0.99, 0.997, 0.999, 0.9997, 0.9999, 1.0};

        final PercentileSummary percentileSummary = new PercentileSummary(false, percentileSummaries, percentiles);
        percentileSummary.printSummary();

        assertEquals((0.009 - 0.002) / (0.009 + 0.002 /2) * 100, percentileSummary.calculateVariance(0), DELTA);
        assertEquals((0.009 - 0.003) / (0.009 + 0.003 /2) * 100, percentileSummary.calculateVariance(1), DELTA);
        assertEquals((0.009 - 0.004) / (0.009 + 0.004 /2) * 100, percentileSummary.calculateVariance(2), DELTA);
        assertEquals((0.009 - 0.005) / (0.009 + 0.005 /2) * 100, percentileSummary.calculateVariance(3), DELTA);
        assertEquals(0, percentileSummary.calculateVariance(percentiles.length - 2), DELTA);
    }

    @Test
    public void testVarianceSkipFirst() {
        List<double[]> percentileSummaries = new ArrayList<>();
        for (int i = 2; i < 10; i++) {
            double[] summary = new double[i];
            for (int j = 0; j < i; j++) {
                summary[j] = i;
            }
            percentileSummaries.add(summary);
        }
        double[] percentiles = new double[]{0.5, 0.9, 0.97, 0.99, 0.997, 0.999, 0.9997, 0.9999, 1.0};

        final PercentileSummary percentileSummary = new PercentileSummary(true, percentileSummaries, percentiles);
        percentileSummary.printSummary();

        // 50th percentile
        assertEquals((0.009 - 0.003) / (0.009 + 0.003 /2) * 100, percentileSummary.calculateVariance(0), DELTA);
        // 90th has no value in the first run, so nothing to skip?
        assertEquals((0.009 - 0.003) / (0.009 + 0.003 /2) * 100, percentileSummary.calculateVariance(1), DELTA);
    }

    @Test
    public void testForEachRow() {
        List<double[]> percentileSummaries = new ArrayList<>();
        double[] percentiles = new double[]{0.5, 0.9, 0.97, 1.0};
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
        assertArrayEquals(new Double[] {0.5, 0.9, 1.0}, receivedPercentiles.toArray(new Double[] {}));
        assertArrayEquals(new double[] {0.002, 0.003}, receivedValues.get(0), DELTA);
        assertArrayEquals(new double[] {POSITIVE_INFINITY, 0.003}, receivedValues.get(1), DELTA);
        assertArrayEquals(new double[] {0.002, 0.003}, receivedValues.get(2), DELTA);
        assertArrayEquals(new Double[] {25.0, 0.0, 25.0}, receivedVariances.toArray(new Double[] {}));
    }
}