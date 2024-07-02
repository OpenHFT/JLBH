package net.openhft.chronicle.jlbh;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

public class ImmutableJLBHResultTest extends TestCase {

    public void testEndToEnd() {
        JLBHResult.ProbeResult probeResult = new SimpleProbeResult();
        JLBHResult result = new ImmutableJLBHResult(probeResult, Collections.emptyMap(), null);
        assertEquals(probeResult, result.endToEnd());
    }

    public void testProbe() {
        JLBHResult.ProbeResult probeResult = new SimpleProbeResult();
        Map<String, JLBHResult.ProbeResult> additionalProbeResults = new HashMap<>();
        additionalProbeResults.put("testProbe", probeResult);
        JLBHResult result = new ImmutableJLBHResult(probeResult, additionalProbeResults, null);
        assertEquals(Optional.of(probeResult), result.probe("testProbe"));
        assertEquals(Optional.empty(), result.probe("nonexistentProbe"));
    }

    public void testProbeNames() {
        JLBHResult.ProbeResult probeResult = new SimpleProbeResult();
        Map<String, JLBHResult.ProbeResult> additionalProbeResults = new HashMap<>();
        additionalProbeResults.put("testProbe", probeResult);
        JLBHResult result = new ImmutableJLBHResult(probeResult, additionalProbeResults, null);
        assertEquals(Collections.singleton("testProbe"), result.probeNames());
    }

    public void testOsJitter() {
        JLBHResult.ProbeResult probeResult = new SimpleProbeResult();
        JLBHResult result = new ImmutableJLBHResult(probeResult, Collections.emptyMap(), probeResult);
        assertEquals(Optional.of(probeResult), result.osJitter());
    }

    static class SimpleProbeResult implements JLBHResult.ProbeResult {

        @Override
        @NotNull
        public JLBHResult.RunResult summaryOfLastRun() {
            return new SimpleRunResult();
        }

        @Override
        @NotNull
        public List<JLBHResult.RunResult> eachRunSummary() {
            return Collections.singletonList(new SimpleRunResult());
        }

        static class SimpleRunResult implements JLBHResult.RunResult {

            @Override
            public Map<Percentile, Duration> percentiles() {
                Map<Percentile, Duration> map = new EnumMap<>(Percentile.class);
                map.put(Percentile.PERCENTILE_50TH, Duration.ofNanos(50));
                map.put(Percentile.PERCENTILE_90TH, Duration.ofNanos(90));
                map.put(Percentile.PERCENTILE_99TH, Duration.ofNanos(99));
                map.put(Percentile.PERCENTILE_99_9TH, Duration.ofNanos(999));
                map.put(Percentile.PERCENTILE_99_99TH, Duration.ofNanos(9999));
                map.put(Percentile.WORST, Duration.ofNanos(10000));
                return map;
            }

            @Override
            public Duration get50thPercentile() {
                return Duration.ofNanos(50);
            }

            @Override
            public Duration get90thPercentile() {
                return Duration.ofNanos(90);
            }

            @Override
            public Duration get99thPercentile() {
                return Duration.ofNanos(99);
            }

            @Override
            public Duration get999thPercentile() {
                return Duration.ofNanos(999);
            }

            @Override
            public Duration get9999thPercentile() {
                return Duration.ofNanos(9999);
            }

            @Override
            public Duration getWorst() {
                return Duration.ofNanos(10000);
            }
        }
    }
}
