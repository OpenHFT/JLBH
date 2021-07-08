package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.Histogram;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

public class TeamCityHelper {

    public static void histo(@NotNull String name, @NotNull Histogram histo, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(histo.totalCount());
        printPercentiles(name, printStream, percentages, histo.getPercentiles());
    }

    /**
     * prints out stats for the last run in a TeamCity friendly manner
     */
    public static void teamCityStatsLastRun(@NotNull String prefix, @NotNull JLBH jlbh, int iterations, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(iterations);
        printPercentiles(prefix + ".end-to-end", printStream, percentages, jlbh.percentileRuns());
        for (Map.Entry<String, List<double[]>> entry : jlbh.additionalPercentileRuns().entrySet()) {
            printPercentiles(prefix + "." + entry.getKey(), printStream, percentages, entry.getValue());
        }
    }

    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, @NotNull double[] percentages, @NotNull List<double[]> valuesList) {
        double[] values = valuesList.get(valuesList.size() - 1);
        printPercentiles(s, printStream, percentages, values);
    }

    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, @NotNull double[] percentages, double[] values) {
        if (percentages.length != values.length) {
            percentages = shortenArray(percentages, values.length);
        }
        for (int i=0; i<percentages.length; i++) {
            printStream.println("##teamcity[buildStatisticValue key='" + s + "." + percentages[i] + "' value='" + values[i] / 1_000 + "']");
        }
    }

    @NotNull
    private static double[] shortenArray(@NotNull double[] percentages, int newLen) {
        double[] percentages2 = new double[newLen];
        System.arraycopy(percentages, 0, percentages2, 0, percentages2.length - 1);
        percentages2[newLen - 1] = percentages[percentages.length - 1];
        return percentages2;
    }
}
