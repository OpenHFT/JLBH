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
import java.util.Arrays;
import java.util.Optional;

/**
 * Serializes JLBH result to CSV file. Useful for analytics.
 */
public class JLBHResultSerializer {
    public static final String THE_PROBE = "TheProbe";
    public static final String RESULT_CSV = "result.csv";
    public static final String END_TO_END = "endToEnd";


    public static void runResultToCSV(JLBHResult jlbhResult) throws IOException {
        runResultToCSV(jlbhResult, RESULT_CSV, jlbhResult.probeNames());
    }

    public static void runResultToCSV(JLBHResult jlbhResult, String fileName) throws IOException {
        runResultToCSV(jlbhResult, fileName, jlbhResult.probeNames());
    }

    public static void runResultToCSV(JLBHResult jlbhResult, String fileName, String probe) throws IOException {
        runResultToCSV(jlbhResult, fileName, Arrays.asList(probe));
    }

    public static void runResultToCSV(JLBHResult jlbhResult, String fileName, Iterable<String> probes) throws IOException {
        try (Writer pw = new BufferedWriter(new PrintWriter(Files.newOutputStream(Paths.get(fileName))))) {
            writeHeader(pw);

            JLBHResult.ProbeResult probeResult = jlbhResult.endToEnd();

            writeProbeResultRows(probeResult, pw, END_TO_END);
            for (String probe : probes) {
                writeProbeResult(jlbhResult, pw, probe);
            }
        }
    }


    private static void writeProbeResult(JLBHResult jlbhResult, Writer pw, String probeName) {
        Optional<JLBHResult.ProbeResult> probeResult = jlbhResult.probe(probeName);
        probeResult.ifPresent(probeResult1 -> {
            try {
                writeProbeResultRows(probeResult1, pw, probeName);
            } catch (IOException e) {
                throw new RuntimeException("Error writing probe results: "+probeName, e);
            }
        });
    }

    private static void writeProbeResultRows(JLBHResult.ProbeResult probeResult, Writer pw, String probeName) throws IOException {
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
            pw.write(Integer.toString(runResult.getNano()));
        }
        pw.write(",");
    }

    private static void writeValue(Writer pw, String runResult) throws IOException {
        pw.write(runResult);
        pw.write(",");
    }
}
