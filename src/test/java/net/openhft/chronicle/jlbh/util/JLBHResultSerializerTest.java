/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh.util;

import net.openhft.chronicle.jlbh.JLBHResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static org.junit.Assert.*;

public class JLBHResultSerializerTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final Duration P50 = Duration.ofNanos(100);
    private static final Duration P90 = Duration.ofNanos(200);
    private static final Duration P99 = Duration.ofNanos(300);
    private static final Duration P999 = Duration.ofNanos(400);
    private static final Duration WORST = Duration.ofNanos(600);

    @Test
    public void shouldWriteSelectedProbesWithoutOsJitter() throws IOException {
        FakeRunResult endToEnd = new FakeRunResult(P50, P90, P99, P999, null, WORST);
        FakeRunResult probe = new FakeRunResult(P50.multipliedBy(2), P90, P99, P999, null, WORST);
        FakeResult result = new FakeResult(endToEnd, Collections.singletonMap("TheProbe", probe), Optional.empty());

        Path out = tmp.newFile("subset.csv").toPath();
        JLBHResultSerializer.runResultToCSV(result, out.toString(), Collections.singletonList("TheProbe"), false);

        List<String> lines = Files.readAllLines(out);
        assertEquals(3, lines.size());
        assertEquals(",50th p-le,90th p-le,99th p-le,999th p-le,9999th p-le,Worst,", lines.get(0));
        assertEquals("endToEnd,100,200,300,400,,600,", lines.get(1));
        assertEquals("TheProbe,200,200,300,400,,600,", lines.get(2));
    }

    @Test
    public void shouldIncludeOsJitterAndAdditionalProbes() throws IOException {
        FakeRunResult endToEnd = new FakeRunResult(P50, P90, P99, P999, Duration.ofNanos(500), WORST);
        FakeRunResult probe = new FakeRunResult(P50, P90.multipliedBy(2), P99, P999, Duration.ofNanos(700), WORST);
        FakeRunResult osJitter = new FakeRunResult(Duration.ofNanos(10), Duration.ofNanos(20), Duration.ofNanos(30),
                Duration.ofNanos(40), Duration.ofNanos(50), Duration.ofNanos(60));

        FakeResult result = new FakeResult(endToEnd, Collections.singletonMap("Custom", probe), Optional.of(osJitter));

        Path out = tmp.newFile("full.csv").toPath();
        JLBHResultSerializer.runResultToCSV(result, out.toString(), Arrays.asList("Custom", "Missing"), true);

        List<String> lines = Files.readAllLines(out);
        assertEquals(4, lines.size());
        assertEquals("endToEnd,100,200,300,400,500,600,", lines.get(1));
        assertEquals("Custom,100,400,300,400,700,600,", lines.get(2));
        assertEquals("OSJitter,10,20,30,40,50,60,", lines.get(3));
    }

    @Test
    public void shouldDefaultToResultCsvInWorkingDirectory() throws IOException {
        FakeRunResult runResult = new FakeRunResult(P50, P90, P99, P999, Duration.ofNanos(500), WORST);
        FakeResult result = new FakeResult(runResult, Collections.singletonMap("Probe", runResult), Optional.of(runResult));

        Path output = Paths.get(JLBHResultSerializer.RESULT_CSV);
        Files.deleteIfExists(output);
        JLBHResultSerializer.runResultToCSV(result);
        assertTrue(Files.exists(output));
        List<String> lines = Files.readAllLines(output);
        assertEquals(4, lines.size());
        Files.deleteIfExists(output);
    }

    private static final class FakeResult implements JLBHResult {
        private final ProbeResult endToEnd;
        private final Map<String, ProbeResult> additionalProbes;
        private final Optional<ProbeResult> osJitter;

        private FakeResult(JLBHResult.RunResult endToEnd,
                           Map<String, JLBHResult.RunResult> probes,
                           Optional<JLBHResult.RunResult> osJitter) {
            this.endToEnd = new FakeProbeResult(endToEnd);
            Map<String, ProbeResult> probeResults = new HashMap<>();
            for (Map.Entry<String, JLBHResult.RunResult> entry : probes.entrySet()) {
                probeResults.put(entry.getKey(), new FakeProbeResult(entry.getValue()));
            }
            this.additionalProbes = probeResults;
            this.osJitter = osJitter.map(FakeProbeResult::new);
        }

        @Override
        public ProbeResult endToEnd() {
            return endToEnd;
        }

        @Override
        public Optional<ProbeResult> probe(String probeName) {
            return Optional.ofNullable(additionalProbes.get(probeName));
        }

        @Override
        public Set<String> probeNames() {
            return additionalProbes.keySet();
        }

        @Override
        public Optional<ProbeResult> osJitter() {
            return osJitter;
        }
    }

    private static final class FakeProbeResult implements JLBHResult.ProbeResult {
        private final JLBHResult.RunResult runResult;

        private FakeProbeResult(JLBHResult.RunResult runResult) {
            this.runResult = runResult;
        }

        @Override
        public JLBHResult.RunResult summaryOfLastRun() {
            return runResult;
        }

        @Override
        public List<JLBHResult.RunResult> eachRunSummary() {
            return Collections.singletonList(runResult);
        }
    }

    private static final class FakeRunResult implements JLBHResult.RunResult {
        private final Duration p50;
        private final Duration p90;
        private final Duration p99;
        private final Duration p999;
        private final Duration p9999;
        private final Duration worst;

        private FakeRunResult(Duration p50,
                              Duration p90,
                              Duration p99,
                              Duration p999,
                              Duration p9999,
                              Duration worst) {
            this.p50 = p50;
            this.p90 = p90;
            this.p99 = p99;
            this.p999 = p999;
            this.p9999 = p9999;
            this.worst = worst;
        }

        @Override
        public Map<Percentile, Duration> percentiles() {
            EnumMap<Percentile, Duration> map = new EnumMap<>(Percentile.class);
            map.put(Percentile.PERCENTILE_50TH, p50);
            map.put(Percentile.PERCENTILE_90TH, p90);
            map.put(Percentile.PERCENTILE_99TH, p99);
            map.put(Percentile.PERCENTILE_99_9TH, p999);
            map.put(Percentile.WORST, worst);
            if (p9999 != null) {
                map.put(Percentile.PERCENTILE_99_99TH, p9999);
            }
            return map;
        }

        @Override
        public Duration get50thPercentile() {
            return p50;
        }

        @Override
        public Duration get90thPercentile() {
            return p90;
        }

        @Override
        public Duration get99thPercentile() {
            return p99;
        }

        @Override
        public Duration get999thPercentile() {
            return p999;
        }

        @Override
        public Duration get9999thPercentile() {
            return p9999;
        }

        @Override
        public Duration getWorst() {
            return worst;
        }
    }
}
