package net.openhft.chronicle.jlbh.util;

import net.openhft.chronicle.jlbh.JLBHResult;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * Serializes JLBH result to CSV file. Useful for analytics.
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
        try (Writer pw = new BufferedWriter(new PrintWriter(Files.newOutputStream(Paths.get(fileName))))) {
            writeHeader(pw);

            JLBHResult.ProbeResult probeResult = jlbhResult.endToEnd();
            writeProbeResult(pw, END_TO_END, probeResult);

            for (String probeName : namesOfProbes) {
                Optional<JLBHResult.ProbeResult> optProbe = jlbhResult.probe(probeName);
                optProbe.ifPresent(probe -> writeProbeResult(pw, probeName, probe));
            }
            if (!includeOSJitter) return;
            Optional<JLBHResult.ProbeResult> osJitterResult = jlbhResult.osJitter();
            osJitterResult.ifPresent(osJitterRes -> writeProbeResult(pw, OS_JITTER, osJitterRes));
        }
    }

    private static void writeProbeResult(Writer pw, String probeName, JLBHResult.ProbeResult probeResult) {
        try {
            JLBHResult.@NotNull RunResult runResult = probeResult.summaryOfLastRun();
            writeRow(probeName, pw, runResult);
        } catch (IOException e) {
            throw new RuntimeException("Error writing probe results: " + probeName, e);
        }
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
