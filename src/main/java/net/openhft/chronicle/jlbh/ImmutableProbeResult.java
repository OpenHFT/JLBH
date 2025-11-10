//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

/*
 * Copyright 2016-2025 chronicle.software
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
 */

package net.openhft.chronicle.jlbh;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * Immutable implementation of {@link JLBHResult.ProbeResult}.
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
