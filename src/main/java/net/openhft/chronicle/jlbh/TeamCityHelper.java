/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.Histogram;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility class to assist with printing benchmark statistics in a TeamCity-friendly format.
 */
public final class TeamCityHelper {

    // Suppresses default constructor, ensuring non-instantiability.
    private TeamCityHelper() {
    }

    /**
     * Prints histogram data in a TeamCity-friendly format.
     *
     * @param name       The name of the histogram
     * @param histo      The histogram to print
     * @param printStream The PrintStream to print to
     */
    public static void histo(@NotNull String name, @NotNull Histogram histo, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(histo.totalCount());
        printPercentiles(name, printStream, percentages, histo.getPercentiles());
    }

    /**
     * Prints out statistics for the last run in a TeamCity-friendly format.
     *
     * @param prefix      The prefix for the statistics
     * @param jlbh        The JLBH instance
     * @param iterations  The number of iterations
     * @param printStream The PrintStream to print to
     */
    public static void teamCityStatsLastRun(@NotNull String prefix, @NotNull JLBH jlbh, long iterations, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(iterations);
        printPercentiles(prefix + ".end-to-end", printStream, percentages, jlbh.percentileRuns());
        for (Map.Entry<String, List<double[]>> entry : jlbh.additionalPercentileRuns().entrySet()) {
            printPercentiles(prefix + "." + entry.getKey(), printStream, percentages, entry.getValue());
        }
    }

    /**
     * Prints percentiles for the given values list in a TeamCity-friendly format.
     *
     * @param s           The prefix for the statistics
     * @param printStream The PrintStream to print to
     * @param percentages The percentile values
     * @param valuesList  The list of percentile values
     */
    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, double[] percentages, @NotNull List<double[]> valuesList) {
        double[] values = valuesList.get(valuesList.size() - 1);
        printPercentiles(s, printStream, percentages, values);
    }

    /**
     * Prints percentiles for the given values in a TeamCity-friendly format.
     *
     * @param s           The prefix for the statistics
     * @param printStream The PrintStream to print to
     * @param percentages The percentile values
     * @param values      The percentile values
     */
    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, double[] percentages, double[] values) {
        PercentileSummary summary = new PercentileSummary(false, Collections.singletonList(values), percentages);
        String extra = Jvm.isAzulZing() ? ".zing" : Jvm.isJava15Plus() ? ".java17" : "";
        summary.forEachRow((percentile, rowValues, variance) ->
                printStream.println("##teamcity[buildStatisticValue key='" + s + "." + percentile + extra + "' value='" + rowValues[0] + "']"));
    }
}
