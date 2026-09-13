/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh.util;

import net.openhft.chronicle.jlbh.JLBHResult;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * Utility class that writes the output of a {@link net.openhft.chronicle.jlbh.JLBH}
 * run to a CSV file.
 * <p>
 * The generated CSV contains a header row followed by one line per probe.  Each
 * line starts with the probe name and is followed by the 50th, 90th, 99th,
 * 999th and 9999th percentile latencies as well as the worst recorded latency
 * in nanoseconds.
 * <p>
 * A typical usage pattern is to obtain the {@link JLBHResult} from a
 * {@code JLBHResultConsumer} and then call one of the {@code runResultToCSV}
 * methods:
 *
 * <pre>{@code
 * JLBHResult result = consumer.get();
 * JLBHResultSerializer.runResultToCSV(result);
 * }</pre>
 *
 * which will create a {@code result.csv} file in the working directory.  Other
 * overloads allow specifying the file name, the set of probes to export and
 * whether the OS jitter probe should be included.
 * <p>
 * Files use UTF-8, preserving Unicode probe names independently of the platform's
 * default charset. Malformed Unicode and output failures are reported as
 * {@link IOException}; output may be incomplete when an exception is thrown.
 */
public class JLBHResultSerializer {
    public static final String THE_PROBE = "TheProbe";
    public static final String RESULT_CSV = "result.csv";
    public static final String END_TO_END = "endToEnd";
    public static final String OS_JITTER = "OSJitter";

    /**
     * Write the summary results for all probes to {@link #RESULT_CSV}.
     * <p>
     * The CSV contains an {@code endToEnd} row followed by one row for each
     * additional probe in {@code jlbhResult}. Metrics from the OS jitter
     * monitor are also appended.
     *
     * @param jlbhResult benchmark output to serialise
     * @throws IOException if the file cannot be written
     */
    public static void runResultToCSV(JLBHResult jlbhResult) throws IOException {
        runResultToCSV(jlbhResult, RESULT_CSV, jlbhResult.probeNames(), true);
    }

    /**
     * Write the summary results for all probes to the supplied file.
     * <p>
     * OS jitter metrics are included by default.
     *
     * @param jlbhResult the benchmark result to serialise
     * @param fileName   path of the CSV file to create
     * @throws IOException if the file cannot be written
     */
    public static void runResultToCSV(JLBHResult jlbhResult, String fileName) throws IOException {
        runResultToCSV(jlbhResult, fileName, jlbhResult.probeNames(), true);
    }

    /**
     * Write the summary results for the named probe and the end-to-end probe to
     * the supplied file.  OS jitter metrics are also included.
     *
     * @param jlbhResult the benchmark result to serialise
     * @param fileName   path of the CSV file to create
     * @param probeName  name of the additional probe to export
     * @throws IOException if the file cannot be written
     */
    public static void runResultToCSV(JLBHResult jlbhResult, String fileName, String probeName) throws IOException {
        runResultToCSV(jlbhResult, fileName, Collections.singletonList(probeName), true);
    }

    /**
     * Serialize the last run results for the selected probes to the given file.
     * <p>
     * The CSV always starts with the {@code endToEnd} results. For each entry in
     * {@code namesOfProbes} a row is written if that probe exists. When
     * {@code includeOSJitter} is {@code true} the OS jitter probe is appended.
     *
     * @param jlbhResult       the benchmark result to serialise
     * @param fileName         path of the CSV file to create
     * @param namesOfProbes    additional probes to export
     * @param includeOSJitter  whether to include OS jitter metrics
     * @throws IOException if the file cannot be written
     */
    public static void runResultToCSV(JLBHResult jlbhResult, String fileName, Iterable<String> namesOfProbes, boolean includeOSJitter) throws IOException {
        writeResultsToCSV(jlbhResult, Files.newOutputStream(Paths.get(fileName)), namesOfProbes, includeOSJitter);
    }

    // Own both layers so the stream closes even when flushing the writer fails.
    static void writeResultsToCSV(JLBHResult jlbhResult, OutputStream output,
                                  Iterable<String> namesOfProbes, boolean includeOSJitter) throws IOException {
        try (OutputStream stream = output;
             Writer pw = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8.newEncoder()))) {
            writeHeader(pw);

            JLBHResult.ProbeResult probeResult = jlbhResult.endToEnd();
            writeProbeResult(pw, END_TO_END, probeResult);

            for (String probeName : namesOfProbes) {
                Optional<JLBHResult.ProbeResult> optProbe = jlbhResult.probe(probeName);
                if (optProbe.isPresent())
                    writeProbeResult(pw, probeName, optProbe.get());
            }
            if (includeOSJitter) {
                Optional<JLBHResult.ProbeResult> osJitterResult = jlbhResult.osJitter();
                if (osJitterResult.isPresent())
                    writeProbeResult(pw, OS_JITTER, osJitterResult.get());
            }
            pw.flush();
        }
    }

    private static void writeProbeResult(Writer pw, String probeName, JLBHResult.ProbeResult probeResult) throws IOException {
        JLBHResult.@NotNull RunResult runResult = probeResult.summaryOfLastRun();
        writeRow(probeName, pw, runResult);
    }

    private static void writeRow(String probeName, Writer pw, JLBHResult.RunResult runResult) throws IOException {
        writeValue(pw, probeName);
        writeValue(pw, runResult.get50thPercentile());
        writeValue(pw, runResult.get90thPercentile());
        writeValue(pw, runResult.get99thPercentile());
        writeValue(pw, runResult.get999thPercentile());
        writeValue(pw, runResult.get9999thPercentile());
        writeValue(pw, runResult.getWorst());
        pw.write("\n");
    }

    private static void writeHeader(Writer pw) throws IOException {
        writeValue(pw, "");
        writeValue(pw, "50th p-le");
        writeValue(pw, "90th p-le");
        writeValue(pw, "99th p-le");
        writeValue(pw, "999th p-le");
        writeValue(pw, "9999th p-le");
        writeValue(pw, "Worst");
        pw.append("\n");

    }

    private static void writeValue(Writer pw, Duration runResult) throws IOException {
        if (runResult != null) {
            pw.write(Long.toString(runResult.toNanos()));
        }
        pw.write(",");
    }

    private static void writeValue(Writer pw, String runResult) throws IOException {
        pw.write(runResult);
        pw.write(",");
    }
}
