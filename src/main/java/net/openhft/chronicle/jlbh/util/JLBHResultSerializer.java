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
 * This class provides methods to serialize JLBH results to a CSV file.
 * It is useful for analytics and performance tracking.
 */
public class JLBHResultSerializer {
    // Constants for probe names and file name
    public static final String THE_PROBE = "TheProbe";
    public static final String RESULT_CSV = "result.csv";
    public static final String END_TO_END = "endToEnd";
    public static final String OS_JITTER = "OSJitter";

    /**
     * Serializes the JLBH result to a default CSV file.
     *
     * @param jlbhResult The JLBH result object to serialize
     * @throws IOException If an I/O error occurs
     */
    public static void runResultToCSV(JLBHResult jlbhResult) throws IOException {
        runResultToCSV(jlbhResult, RESULT_CSV, jlbhResult.probeNames(), true);
    }

    /**
     * Serializes the JLBH result to the specified CSV file.
     *
     * @param jlbhResult The JLBH result object to serialize
     * @param fileName The name of the CSV file to write to
     * @throws IOException If an I/O error occurs
     */
    public static void runResultToCSV(JLBHResult jlbhResult, String fileName) throws IOException {
        runResultToCSV(jlbhResult, fileName, jlbhResult.probeNames(), true);
    }

    /**
     * Serializes the JLBH result for a specific probe to the specified CSV file.
     *
     * @param jlbhResult The JLBH result object to serialize
     * @param fileName The name of the CSV file to write to
     * @param probeName The name of the probe to include in the CSV
     * @throws IOException If an I/O error occurs
     */
    public static void runResultToCSV(JLBHResult jlbhResult, String fileName, String probeName) throws IOException {
        runResultToCSV(jlbhResult, fileName, Collections.singletonList(probeName), true);
    }

    /**
     * Serializes the JLBH result to the specified CSV file, including selected probes.
     *
     * @param jlbhResult The JLBH result object to serialize
     * @param fileName The name of the CSV file to write to
     * @param namesOfProbes An iterable of probe names to include in the CSV
     * @param includeOSJitter Flag to include OS jitter in the CSV
     * @throws IOException If an I/O error occurs
     */
    public static void runResultToCSV(JLBHResult jlbhResult, String fileName, Iterable<String> namesOfProbes, boolean includeOSJitter) throws IOException {
        try (Writer pw = new BufferedWriter(new PrintWriter(Files.newOutputStream(Paths.get(fileName))))) {
            writeHeader(pw);

            // Write end-to-end probe results
            JLBHResult.ProbeResult probeResult = jlbhResult.endToEnd();
            writeProbeResult(pw, END_TO_END, probeResult);

            // Write results for each specified probe
            for (String probeName : namesOfProbes) {
                Optional<JLBHResult.ProbeResult> optProbe = jlbhResult.probe(probeName);
                optProbe.ifPresent(probe -> writeProbeResult(pw, probeName, probe));
            }

            // Optionally write OS jitter results
            if (!includeOSJitter) return;
            Optional<JLBHResult.ProbeResult> osJitterResult = jlbhResult.osJitter();
            osJitterResult.ifPresent(osJitterRes -> writeProbeResult(pw, OS_JITTER, osJitterRes));
        }
    }

    /**
     * Writes the results of a probe to the CSV writer.
     *
     * @param pw The Writer object to write to
     * @param probeName The name of the probe
     * @param probeResult The result of the probe
     */
    private static void writeProbeResult(Writer pw, String probeName, JLBHResult.ProbeResult probeResult) {
        try {
            JLBHResult.@NotNull RunResult runResult = probeResult.summaryOfLastRun();
            writeRow(probeName, pw, runResult);
        } catch (IOException e) {
            throw new RuntimeException("Error writing probe results: " + probeName, e);
        }
    }

    /**
     * Writes a row of results to the CSV writer.
     *
     * @param probeName The name of the probe
     * @param pw The Writer object to write to
     * @param runResult The result of the run
     * @throws IOException If an I/O error occurs
     */
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

    /**
     * Writes the header row to the CSV writer.
     *
     * @param pw The Writer object to write to
     * @throws IOException If an I/O error occurs
     */
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

    /**
     * Writes a Duration value to the CSV writer.
     *
     * @param pw The Writer object to write to
     * @param runResult The Duration value to write
     * @throws IOException If an I/O error occurs
     */
    private static void writeValue(Writer pw, Duration runResult) throws IOException {
        if (runResult != null) {
            pw.write(Long.toString(runResult.toNanos()));
        }
        pw.write(",");
    }

    /**
     * Writes a String value to the CSV writer.
     *
     * @param pw The Writer object to write to
     * @param runResult The String value to write
     * @throws IOException If an I/O error occurs
     */
    private static void writeValue(Writer pw, String runResult) throws IOException {
        pw.write(runResult);
        pw.write(",");
    }
}
