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
 * Immutable view of the data produced by a {@link JLBH} run.
 * <p>
 * A typical usage pattern is to provide a {@link JLBHResultConsumer} when
 * constructing the benchmark. Once {@link JLBH#start()} completes the
 * consumer can be queried for the {@code JLBHResult} instance. All data is
 * published immutably so it may be read from any thread after the run
 * finishes.
 * <p>
 * The result contains a default end-to-end probe as well as any additional
 * probes registered via {@link JLBH#addProbe(String)}. Each probe exposes the
 * summary of the last run and the metrics for every individual run.
 */
public interface JLBHResult {

    /**
     * Returns the statistics for the end to end latency probe.
     *
     * @return probe result summarising the end to end measurements
     */
    @NotNull
    ProbeResult endToEnd();

    /**
     * Returns the results for the given probe name.
     * <p>
     * If no probe exists with the supplied name the returned {@code Optional}
     * will be empty.
     *
     * @param probeName name of the probe for which results are requested
     * @return an {@code Optional} containing the probe results or
     *         {@link Optional#empty()} if the probe is not present
     */
    @NotNull
    Optional<ProbeResult> probe(String probeName);

    /**
     * Return the names of all additional probes configured for the benchmark.
     * <p>
     * These correspond to the probe names supplied via
     * {@link JLBH#addProbe(String)} and therefore do not include the default
     * {@code endToEnd} or {@code osJitter} probes.
     *
     * @return names of the user defined probes
     */
    Set<String> probeNames();

    /**
     * Returns statistics gathered by the operating system jitter monitor
     * if it was enabled for the benchmark run.
     * <p>
     * When jitter recording was disabled an {@link Optional#empty() empty}
     * value is returned.
     *
     * @return optional OS jitter probe results
     */
    Optional<ProbeResult> osJitter();

    /**
     * Aggregated latency statistics for a single probe.
     *
     * <p>A probe may be executed in multiple runs.  For each run a
     * {@link RunResult} is recorded and implementations of this interface
     * expose both the summary of the last run and a list of summaries for all
     * runs.</p>
     */
    interface ProbeResult {

        /**
         * Obtain the summary statistics for the most recent run of this probe.
         *
         * @return summary of the last run
         */
        @NotNull
        RunResult summaryOfLastRun();

        /**
         * Obtain the summary statistics for each run of this probe.
         *
         * @return list of run summaries in execution order
         */
        @NotNull
        List<RunResult> eachRunSummary();
    }

    /**
     * Latency metrics recorded for a single run of a probe.
     *
     * <p>Implementations expose the commonly used percentile values as
     * {@link Duration} instances and also provide access to the entire set of
     * calculated percentiles via {@link #percentiles()}.</p>
     */
    interface RunResult {

        @NotNull
        Map<Percentile, Duration> percentiles();

        @NotNull
        Duration get50thPercentile();

        @NotNull
        Duration get90thPercentile();

        @NotNull
        Duration get99thPercentile();

        @Nullable
        Duration get999thPercentile();

        @Nullable
        Duration get9999thPercentile();

        @NotNull
        Duration getWorst();

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
