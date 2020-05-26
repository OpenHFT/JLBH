/*
 * Copyright 2016-2020 Chronicle Software
 *
 * https://chronicle.software
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
package net.openhft.chronicle.core.jlbh;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

class ImmutableProbeResult implements JLBHResult.ProbeResult {

    @NotNull
    private final List<JLBHResult.RunResult> runsSummary;

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
