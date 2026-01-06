/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * Immutable snapshot of percentile summaries for a probe across all runs.
 *
 * <p>Each probe run is supplied as an array of percentile values which is
 * converted to an {@link ImmutableRunResult} and stored in an unmodifiable
 * list. The order of the list matches the order of execution so the last
 * element represents the most recent run.</p>
 */
final class ImmutableProbeResult implements JLBHResult.ProbeResult {

    @NotNull
    private final List<JLBHResult.RunResult> runsSummary;

    /**
     * Construct a probe result from the percentile values collected during each
     * benchmark run.
     *
     * <p>The {@code percentileRuns} list should contain one array for every run
     * of the benchmark. Each array is expected to be the values returned by a
     * {@link net.openhft.chronicle.core.util.Histogram#getPercentiles()} call,
     * i.e. latency percentiles in nanoseconds.</p>
     *
     * @param percentileRuns list of percentile arrays for each run
     */
    public ImmutableProbeResult(List<double[]> percentileRuns) {
        runsSummary = unmodifiableList(percentileRuns.stream().map(ImmutableRunResult::new).collect(toList()));
    }

    @NotNull
    @Override
    public JLBHResult.RunResult summaryOfLastRun() {
        return runsSummary.get(runsSummary.size() - 1);
    }

    @NotNull
    @Override
    public List<JLBHResult.RunResult> eachRunSummary() {
        return runsSummary;
    }
}
