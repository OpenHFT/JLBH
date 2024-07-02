package net.openhft.chronicle.jlbh.util;

import junit.framework.TestCase;
import net.openhft.chronicle.jlbh.JLBHResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class JLBHResultSerializerTest extends TestCase {

    public void testRunResultToCSVDefault() throws IOException {
        JLBHResult resultMock = new SimpleJLBHResult();
        Path tempFile = Files.createTempFile("test", ".csv");
        JLBHResultSerializer.runResultToCSV(resultMock, tempFile.toString());

        // Assertions for file content
        Files.delete(tempFile);
    }

    public void testRunResultToCSVWithFileName() throws IOException {
        JLBHResult resultMock = new SimpleJLBHResult();
        Path tempFile = Files.createTempFile("test", ".csv");
        JLBHResultSerializer.runResultToCSV(resultMock, tempFile.toString(), JLBHResultSerializer.END_TO_END);

        // Assertions for file content
        Files.delete(tempFile);
    }

    public void testRunResultToCSVWithProbes() throws IOException {
        JLBHResult resultMock = new SimpleJLBHResult();
        Path tempFile = Files.createTempFile("test", ".csv");
        JLBHResultSerializer.runResultToCSV(resultMock, tempFile.toString(), resultMock.probeNames(), true);

        // Assertions for file content
        Files.delete(tempFile);
    }

    public void testRunResultToCSVWithOSJitter() throws IOException {
        JLBHResult resultMock = new SimpleJLBHResult();
        Path tempFile = Files.createTempFile("test", ".csv");
        JLBHResultSerializer.runResultToCSV(resultMock, tempFile.toString(), resultMock.probeNames(), true);

        // Assertions for file content
        Files.delete(tempFile);
    }

    static class SimpleJLBHResult implements JLBHResult {

        @Override
        public ProbeResult endToEnd() {
            return new SimpleProbeResult();
        }

        @Override
        public Optional<ProbeResult> probe(String probeName) {
            return Optional.of(new SimpleProbeResult());
        }

        @Override
        public Set<String> probeNames() {
            return new HashSet<>(Arrays.asList(JLBHResultSerializer.END_TO_END, JLBHResultSerializer.THE_PROBE));
        }

        @Override
        public Optional<ProbeResult> osJitter() {
            return Optional.of(new SimpleProbeResult());
        }

        static class SimpleProbeResult implements ProbeResult {

            @Override
            public RunResult summaryOfLastRun() {
                return new SimpleRunResult();
            }

            @Override
            public List<RunResult> eachRunSummary() {
                return Collections.singletonList(new SimpleRunResult());
            }

            static class SimpleRunResult implements RunResult {

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
}
