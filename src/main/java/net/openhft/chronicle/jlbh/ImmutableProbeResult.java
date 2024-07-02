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

import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * Immutable implementation of the JLBHResult.ProbeResult interface.
 * Encapsulates the results of a probe's runs.
 */
final class ImmutableProbeResult implements JLBHResult.ProbeResult {

    // A list of run summaries for this probe
    @NotNull
    private final List<JLBHResult.RunResult> runsSummary;

    /**
     * Constructs an ImmutableProbeResult with the given percentile run data.
     *
     * @param percentileRuns A list of percentile run data arrays
     */
    public ImmutableProbeResult(List<double[]> percentileRuns) {
        runsSummary = unmodifiableList(percentileRuns.stream().map(ImmutableRunResult::new).collect(toList()));
    }

    /**
     * Returns the summary of the last run for this probe.
     *
     * @return The summary of the last run
     */
    @NotNull
    @Override
    public JLBHResult.RunResult summaryOfLastRun() {
        return runsSummary.get(runsSummary.size() - 1);
    }

    /**
     * Returns a list of summaries for each run of this probe.
     *
     * @return A list of run summaries
     */
    @NotNull
    @Override
    public List<JLBHResult.RunResult> eachRunSummary() {
        return runsSummary;
    }
}
