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

    @NotNull
    ProbeResult endToEnd();

    @NotNull
    Optional<ProbeResult> probe(String probeName);

    Set<String> probeNames();

    Optional<ProbeResult> osJitter();

    interface ProbeResult {

        @NotNull
        RunResult summaryOfLastRun();

        @NotNull
        List<RunResult> eachRunSummary();
    }

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
