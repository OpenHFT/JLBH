//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

/*
 * Copyright 2016-2025 chronicle.software
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
 * Utilities for emitting JLBH latency results as TeamCity build statistics.
 * <p>
 * TeamCity parses log lines of the form
 * {@code ##teamcity[buildStatisticValue key='someKey' value='someValue']}. The
 * methods in this class produce such lines so that percentiles measured by JLBH
 * can be consumed by a TeamCity build configuration. The output is usually
 * written to {@link System#out} but any {@link PrintStream} may be supplied.
 * <p>
 * A typical pattern is to invoke {@link #teamCityStatsLastRun(String, JLBH, long, PrintStream)}
 * once a benchmark has completed:
 *
 * <pre>{@code
 * JLBH jlbh = ...;
 * TeamCityHelper.teamCityStatsLastRun("myBenchmark", jlbh,
 *         options.iterations(), System.out);
 * }</pre>
 */
public final class TeamCityHelper {

    // Suppresses default constructor, ensuring non-instantiability.
    private TeamCityHelper() {
    }

    /**
     * Print the contents of a {@link Histogram} in a TeamCity friendly format.
     *
     * <p>For each percentile, a line similar to the following is written:</p>
     * <pre>
     * ##teamcity[buildStatisticValue key='Latency.50.0.java17' value='123.0']
     * </pre>
     *
     * @param name        base name for the generated TeamCity statistic keys
     * @param histo       the histogram whose percentiles will be printed
     * @param printStream the stream to write the output to
     */
    public static void histo(@NotNull String name, @NotNull Histogram histo, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(histo.totalCount());
        printPercentiles(name, printStream, percentages, histo.getPercentiles());
    }

    /**
     * Prints end to end and probe specific statistics for the last benchmark
     * run in a TeamCity friendly manner.
     *
     * <p>For each percentile a {@code buildStatisticValue} service message is
     * emitted so that TeamCity can record the values as build statistics.</p>
     *
     * @param prefix     prefix used to construct the TeamCity statistic key. The
     *                   generated key has the form
     *                   {@code prefix.&lt;probe&gt;.&lt;percentile&gt;}
     * @param jlbh       benchmark instance providing access to the percentile
     *                   data for the last run
     * @param iterations total number of iterations executed in the run; used to
     *                   derive the set of percentiles that will be output
     * @param printStream destination to which the TeamCity service messages are
     *                    written
     */
    public static void teamCityStatsLastRun(@NotNull String prefix, @NotNull JLBH jlbh,
                                            long iterations, @NotNull PrintStream printStream) {
        double[] percentages = Histogram.percentilesFor(iterations);
        printPercentiles(prefix + ".end-to-end", printStream, percentages, jlbh.percentileRuns());
        for (Map.Entry<String, List<double[]>> entry : jlbh.additionalPercentileRuns().entrySet()) {
            printPercentiles(prefix + "." + entry.getKey(), printStream, percentages, entry.getValue());
        }
    }

    /**
     * Helper that prints the percentile values for the most recent run.
     *
     * <p>The {@code valuesList} parameter is a list of percentile arrays, one
     * for each run that was executed. The last element in the list represents
     * the latest run and its values are used when printing.</p>
     */
    private static void printPercentiles(@NotNull String s,
                                         @NotNull PrintStream printStream,
                                         double[] percentages,
                                         @NotNull List<double[]> valuesList) {
        double[] values = valuesList.get(valuesList.size() - 1);
        printPercentiles(s, printStream, percentages, values);
    }

    private static void printPercentiles(@NotNull String s, @NotNull PrintStream printStream, double[] percentages, double[] values) {
        PercentileSummary summary = new PercentileSummary(false, Collections.singletonList(values), percentages);
        String extra = Jvm.isAzulZing() ? ".zing" : Jvm.isJava15Plus() ? ".java17" : "";
        summary.forEachRow(((percentile, rowValues, variance) ->
                printStream.println("##teamcity[buildStatisticValue key='" + s + "." + percentile + extra + "' value='" + rowValues[0] + "']")));
    }
}
