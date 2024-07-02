/*
 * Copyright 2016-2020 chronicle.software
 *
 *       https://chronicle.software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package net.openhft.chronicle.jlbh;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interface representing the results of a JLBH benchmark run.
 */
public interface JLBHResult {

    /**
     * Returns the end-to-end probe result.
     *
     * @return The end-to-end probe result
     */
    @NotNull
    ProbeResult endToEnd();

    /**
     * Returns the probe result for the specified probe name.
     *
     * @param probeName The name of the probe
     * @return An Optional containing the probe result if present, otherwise empty
     */
    @NotNull
    Optional<ProbeResult> probe(String probeName);

    /**
     * Returns a set of all probe names.
     *
     * @return A set of all probe names
     */
    Set<String> probeNames();

    /**
     * Returns the OS jitter probe result.
     *
     * @return An Optional containing the OS jitter probe result if present, otherwise empty
     */
    Optional<ProbeResult> osJitter();

    /**
     * Interface representing the result of a single probe.
     */
    interface ProbeResult {

        /**
         * Returns the summary of the last run for this probe.
         *
         * @return The summary of the last run
         */
        @NotNull
        RunResult summaryOfLastRun();

        /**
         * Returns a list of summaries for each run of this probe.
         *
         * @return A list of run summaries
         */
        @NotNull
        List<RunResult> eachRunSummary();
    }

    /**
     * Interface representing the result of a single run.
     */
    interface RunResult {

        /**
         * Returns a map of percentiles to durations.
         *
         * @return A map of percentiles to durations
         */
        @NotNull
        Map<Percentile, Duration> percentiles();

        /**
         * Returns the duration of the 50th percentile.
         *
         * @return The duration of the 50th percentile
         */
        @NotNull
        Duration get50thPercentile();

        /**
         * Returns the duration of the 90th percentile.
         *
         * @return The duration of the 90th percentile
         */
        @NotNull
        Duration get90thPercentile();

        /**
         * Returns the duration of the 99th percentile.
         *
         * @return The duration of the 99th percentile
         */
        @NotNull
        Duration get99thPercentile();

        /**
         * Returns the duration of the 99.9th percentile, if available.
         *
         * @return The duration of the 99.9th percentile, or null if not available
         */
        @Nullable
        Duration get999thPercentile();

        /**
         * Returns the duration of the 99.99th percentile, if available.
         *
         * @return The duration of the 99.99th percentile, or null if not available
         */
        @Nullable
        Duration get9999thPercentile();

        /**
         * Returns the duration of the worst-case scenario.
         *
         * @return The duration of the worst-case scenario
         */
        @NotNull
        Duration getWorst();

        /**
         * Enum representing various percentiles.
         */
        enum Percentile {
            PERCENTILE_50TH,
            PERCENTILE_90TH,
            PERCENTILE_99TH,
            PERCENTILE_99_7TH,
            PERCENTILE_99_9TH,
            PERCENTILE_99_97TH,
            PERCENTILE_99_99TH,
            PERCENTILE_99_999TH,
            WORST
        }
    }
}
