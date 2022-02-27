package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.Histogram;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TeamCityHelper {

    // Suppresses default constructor, ensuring non-instantiability.
    private TeamCityHelper() {
    }

    public static void histo(@NotNull String name, @NotNull Histogram histo, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(histo.totalCount());
        printPercentiles(name, printStream, percentages, histo.getPercentiles());
    }

    /**
     * prints out stats for the last run in a TeamCity friendly manner
     */
    public static void teamCityStatsLastRun(@NotNull String prefix, @NotNull JLBH jlbh, long iterations, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(iterations);
        printPercentiles(prefix + ".end-to-end", printStream, percentages, jlbh.percentileRuns());
        for (Map.Entry<String, List<double[]>> entry : jlbh.additionalPercentileRuns().entrySet()) {
            printPercentiles(prefix + "." + entry.getKey(), printStream, percentages, entry.getValue());
        }
    }

    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, double[] percentages, @NotNull List<double[]> valuesList) {
        double[] values = valuesList.get(valuesList.size() - 1);
        printPercentiles(s, printStream, percentages, values);
    }

    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, double[] percentages, double[] values) {
        PercentileSummary summary = new PercentileSummary(false, Collections.singletonList(values), percentages);
        summary.forEachRow(((percentile, rowValues, variance) -> {
            printStream.println("##teamcity[buildStatisticValue key='" + s + "." + percentile + "' value='" + rowValues[0] + "']");
        }));
    }
}
