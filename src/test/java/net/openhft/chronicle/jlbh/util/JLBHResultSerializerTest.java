/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh.util;

import net.openhft.chronicle.jlbh.JLBHResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class JLBHResultSerializerTest {

    @TempDir
    File tmp;

    private static final Duration P50 = Duration.ofNanos(100);
    private static final Duration P90 = Duration.ofNanos(200);
    private static final Duration P99 = Duration.ofNanos(300);
    private static final Duration P999 = Duration.ofNanos(400);
    private static final Duration WORST = Duration.ofNanos(600);

    @Test
    void shouldWriteSelectedProbesWithoutOsJitter() throws IOException {
        FakeRunResult endToEnd = new FakeRunResult(P50, P90, P99, P999, null, WORST);
        FakeRunResult probe = new FakeRunResult(P50.multipliedBy(2), P90, P99, P999, null, WORST);
        FakeResult result = new FakeResult(endToEnd, Collections.singletonMap("TheProbe", probe), Optional.empty());

        File subsetFile = new File(tmp, "subset.csv");
        subsetFile.createNewFile();
        Path out = subsetFile.toPath();
        JLBHResultSerializer.runResultToCSV(result, out.toString(), Collections.singletonList("TheProbe"), false);

        List<String> lines = Files.readAllLines(out, UTF_8);
        assertEquals(3, lines.size());
        assertEquals(",50th p-le,90th p-le,99th p-le,999th p-le,9999th p-le,Worst,", lines.get(0));
        assertEquals("endToEnd,100,200,300,400,,600,", lines.get(1));
        assertEquals("TheProbe,200,200,300,400,,600,", lines.get(2));
    }

    @Test
    void shouldIncludeOsJitterAndAdditionalProbes() throws IOException {
        FakeRunResult endToEnd = new FakeRunResult(P50, P90, P99, P999, Duration.ofNanos(500), WORST);
        FakeRunResult probe = new FakeRunResult(P50, P90.multipliedBy(2), P99, P999, Duration.ofNanos(700), WORST);
        FakeRunResult osJitter = new FakeRunResult(Duration.ofNanos(10), Duration.ofNanos(20), Duration.ofNanos(30),
                Duration.ofNanos(40), Duration.ofNanos(50), Duration.ofNanos(60));

        FakeResult result = new FakeResult(endToEnd, Collections.singletonMap("Custom", probe), Optional.of(osJitter));

        File fullFile = new File(tmp, "full.csv");
        fullFile.createNewFile();
        Path out = fullFile.toPath();
        JLBHResultSerializer.runResultToCSV(result, out.toString(), Arrays.asList("Custom", "Missing"), true);

        List<String> lines = Files.readAllLines(out, UTF_8);
        assertEquals(4, lines.size());
        assertEquals("endToEnd,100,200,300,400,500,600,", lines.get(1));
        assertEquals("Custom,100,400,300,400,700,600,", lines.get(2));
        assertEquals("OSJitter,10,20,30,40,50,60,", lines.get(3));
    }

    @Test
    void shouldDefaultToResultCsvInWorkingDirectory() throws IOException {
        FakeRunResult runResult = new FakeRunResult(P50, P90, P99, P999, Duration.ofNanos(500), WORST);
        FakeResult result = new FakeResult(runResult, Collections.singletonMap("Probe", runResult), Optional.of(runResult));

        Path output = Paths.get(JLBHResultSerializer.RESULT_CSV);
        Files.deleteIfExists(output);
        JLBHResultSerializer.runResultToCSV(result);
        assertTrue(Files.exists(output));
        List<String> lines = Files.readAllLines(output, UTF_8);
        assertEquals(4, lines.size());
        Files.deleteIfExists(output);
    }

    static Stream<Arguments> probeNames() {
        return Stream.of(
                Arguments.of("ASCII", "ASCII".getBytes(US_ASCII)),
                Arguments.of("\u00e9", new byte[]{(byte) 0xc3, (byte) 0xa9}),
                Arguments.of("\u20ac", new byte[]{(byte) 0xe2, (byte) 0x82, (byte) 0xac}),
                Arguments.of("\ud83d\ude80", new byte[]{(byte) 0xf0, (byte) 0x9f, (byte) 0x9a, (byte) 0x80}));
    }

    @ParameterizedTest
    @MethodSource("probeNames")
    void shouldWriteExactUtf8BytesAndRoundTripProbeNames(String suffix, byte[] encodedSuffix) throws IOException {
        String name = "probe-" + suffix;
        FakeResult result = resultWithProbe(name);
        Path output = tmp.toPath().resolve("unicode.csv");

        JLBHResultSerializer.runResultToCSV(result, output.toString(), Collections.singletonList(name), false);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write((",50th p-le,90th p-le,99th p-le,999th p-le,9999th p-le,Worst,\n"
                + "endToEnd,100,200,300,400,,600,\nprobe-").getBytes(US_ASCII));
        expected.write(encodedSuffix);
        expected.write(",100,200,300,400,,600,\n".getBytes(US_ASCII));
        assertArrayEquals(expected.toByteArray(), Files.readAllBytes(output));
        assertEquals(name + ",100,200,300,400,,600,", Files.readAllLines(output, UTF_8).get(2));
    }

    @Test
    void shouldRejectMalformedUnicodeRatherThanReplaceIt() {
        String name = "probe-\ud800";
        Path output = tmp.toPath().resolve("malformed.csv");

        assertThrows(MalformedInputException.class, () -> JLBHResultSerializer.runResultToCSV(
                resultWithProbe(name), output.toString(), Collections.singletonList(name), false));
    }

    @ParameterizedTest
    @EnumSource(OutputFailure.class)
    void shouldPropagateOutputFailuresAndCloseTheStream(OutputFailure failure) {
        // A large name forces a write while a probe row is being emitted, before the final flush.
        char[] characters = new char[32_768];
        Arrays.fill(characters, 'p');
        String name = new String(characters);
        FailingOutputStream output = new FailingOutputStream(failure);

        IOException error = assertThrows(IOException.class, () -> JLBHResultSerializer.writeResultsToCSV(
                resultWithProbe(name), output, Collections.singletonList(name), false));

        assertEquals("failure during " + failure, error.getMessage());
        assertTrue(output.closed, "The destination must close even when writing or flushing fails");
    }

    private static FakeResult resultWithProbe(String name) {
        FakeRunResult run = new FakeRunResult(P50, P90, P99, P999, null, WORST);
        return new FakeResult(run, Collections.singletonMap(name, run), Optional.empty());
    }

    enum OutputFailure { WRITE, FLUSH, CLOSE }

    private static final class FailingOutputStream extends OutputStream {
        private final OutputFailure failure;
        private boolean closed;

        private FailingOutputStream(OutputFailure failure) {
            this.failure = failure;
        }

        @Override
        public void write(int value) throws IOException {
            failOn(OutputFailure.WRITE);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            failOn(OutputFailure.WRITE);
        }

        @Override
        public void flush() throws IOException {
            failOn(OutputFailure.FLUSH);
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                failOn(OutputFailure.CLOSE);
            }
        }

        private void failOn(OutputFailure operation) throws IOException {
            if (operation == failure)
                throw new IOException("failure during " + failure);
        }
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
