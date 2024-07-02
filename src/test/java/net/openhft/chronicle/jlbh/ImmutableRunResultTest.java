package net.openhft.chronicle.jlbh;

import junit.framework.TestCase;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public class ImmutableRunResultTest extends TestCase {

    public void testConstructorAndGetPercentiles() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        Map<JLBHResult.RunResult.Percentile, Duration> expectedPercentiles = new EnumMap<>(JLBHResult.RunResult.Percentile.class);
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_50TH, Duration.ofNanos(50));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_90TH, Duration.ofNanos(90));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_99TH, Duration.ofNanos(99));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_99_7TH, Duration.ofNanos(997));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_99_9TH, Duration.ofNanos(999));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_99_97TH, Duration.ofNanos(9997));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.PERCENTILE_99_99TH, Duration.ofNanos(9999));
        expectedPercentiles.put(JLBHResult.RunResult.Percentile.WORST, Duration.ofNanos(10000));

        assertEquals(expectedPercentiles, runResult.percentiles());
    }

    public void testGet50thPercentile() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        assertEquals(Duration.ofNanos(50), runResult.get50thPercentile());
    }

    public void testGet90thPercentile() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        assertEquals(Duration.ofNanos(90), runResult.get90thPercentile());
    }

    public void testGet99thPercentile() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        assertEquals(Duration.ofNanos(99), runResult.get99thPercentile());
    }

    public void testGet999thPercentile() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        assertEquals(Duration.ofNanos(999), runResult.get999thPercentile());
    }

    public void testGet9999thPercentile() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        assertEquals(Duration.ofNanos(9999), runResult.get9999thPercentile());
    }

    public void testGetWorst() {
        double[] percentilesArray = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult = new ImmutableRunResult(percentilesArray);

        assertEquals(Duration.ofNanos(10000), runResult.getWorst());
    }

    public void testEqualsAndHashCode() {
        double[] percentilesArray1 = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        double[] percentilesArray2 = {50, 90, 99, 997, 999, 9997, 9999, 10000};
        ImmutableRunResult runResult1 = new ImmutableRunResult(percentilesArray1);
        ImmutableRunResult runResult2 = new ImmutableRunResult(percentilesArray2);

        assertEquals(runResult1, runResult2);
        assertEquals(runResult1.hashCode(), runResult2.hashCode());
    }
}
