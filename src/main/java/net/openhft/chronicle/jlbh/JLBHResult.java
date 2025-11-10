//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

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

        /**
         * Returns the latency recorded for each measured percentile.
         *
         * <p>The returned map is keyed by {@link Percentile} values and the
         * associated {@link Duration} represents the observed latency at that
         * percentile. Expected keys include
         * {@link Percentile#PERCENTILE_50TH 50th},
         * {@link Percentile#PERCENTILE_90TH 90th},
         * {@link Percentile#PERCENTILE_99TH 99th},
         * {@link Percentile#PERCENTILE_99_7TH 99.7th},
         * {@link Percentile#PERCENTILE_99_9TH 99.9th},
         * {@link Percentile#PERCENTILE_99_97TH 99.97th},
         * {@link Percentile#PERCENTILE_99_99TH 99.99th},
         * {@link Percentile#PERCENTILE_99_999TH 99.999th}, and
         * {@link Percentile#WORST WORST} for the highest latency.</p>
         *
         * <p>Percentiles that are not measured for a particular run may be
         * absent from the map.</p>
         *
         * @return mapping of percentile identifiers to latency values
         */
        @NotNull
        Map<Percentile, Duration> percentiles();

        /**
         * Returns the median latency of this run.
         * <p>
         * The value corresponds to the 50th percentile of all recorded
         * measurements.
         *
         * @return duration representing the 50th percentile latency
         */
        @NotNull
        Duration get50thPercentile();

        /**
         * Obtain the latency at the 90th percentile of all recorded values.
         *
         * @return 90th percentile latency
         */
        @NotNull
        Duration get90thPercentile();

        /**
         * Obtain the latency recorded at the 99th percentile for the run.
         *
         * @return duration representing the 99th percentile
         */
        @NotNull
        Duration get99thPercentile();

        /**
         * Returns the 99.9th percentile latency recorded for this run.
         *
         * @return 99.9th percentile latency or {@code null} if the percentile was not collected
         */
        @Nullable
        Duration get999thPercentile();

        /**
         * Returns the 99.99th percentile latency recorded for the run.
         *
         * <p>If the benchmark did not capture this percentile a
         * {@code null} value will be returned.</p>
         *
         * @return duration of the 99.99th percentile or {@code null} when
         *         not available
         */
        @Nullable
        Duration get9999thPercentile();

        /**
         * Returns the maximum latency observed during this run.
         *
         * @return the duration of the slowest event
         */
        @NotNull
        Duration getWorst();

        enum Percentile {
            /** 50th percentile (median). */
            PERCENTILE_50TH,
            /** 90th percentile. */
            PERCENTILE_90TH,
            /** 99th percentile. */
            PERCENTILE_99TH,
            /** 99.7th percentile. */
            PERCENTILE_99_7TH,
            /** 99.9th percentile. */
            PERCENTILE_99_9TH,
            /** 99.97th percentile. */
            PERCENTILE_99_97TH,
            /** 99.99th percentile. */
            PERCENTILE_99_99TH,
            /** 99.999th percentile. */
            PERCENTILE_99_999TH,
            /** Highest (worst) observed latency. */
            WORST
        }
    }
}
