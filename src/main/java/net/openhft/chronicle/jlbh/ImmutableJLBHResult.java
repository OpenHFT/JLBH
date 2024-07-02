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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;

/**
 * Immutable implementation of the JLBHResult interface.
 * Encapsulates the results of JLBH probes including end-to-end latency and optional OS jitter results.
 */
final class ImmutableJLBHResult implements JLBHResult {

    // The end-to-end probe result
    @NotNull
    private final ProbeResult endToEndProbeResult;

    // Additional probe results mapped by probe name
    @NotNull
    private final Map<String, ProbeResult> additionalProbeResults;

    // Optional OS jitter result
    private final ProbeResult osJitterResult;

    /**
     * Constructs an ImmutableJLBHResult with the given probe results.
     *
     * @param endToEndProbeResult The end-to-end probe result
     * @param additionalProbeResults A map of additional probe results
     * @param osJitterResult The OS jitter result
     */
    ImmutableJLBHResult(@NotNull ProbeResult endToEndProbeResult, @NotNull Map<String, ? extends ProbeResult> additionalProbeResults, ProbeResult osJitterResult) {
        this.endToEndProbeResult = endToEndProbeResult;
        this.additionalProbeResults = unmodifiableMap(additionalProbeResults);
        this.osJitterResult = osJitterResult;
    }

    /**
     * Returns the end-to-end probe result.
     *
     * @return The end-to-end probe result
     */
    @Override
    @NotNull
    public ProbeResult endToEnd() {
        return endToEndProbeResult;
    }

    /**
     * Returns the probe result for the specified probe name.
     *
     * @param probeName The name of the probe
     * @return An Optional containing the probe result if present, otherwise empty
     */
    @Override
    @NotNull
    public Optional<ProbeResult> probe(String probeName) {
        return Optional.ofNullable(additionalProbeResults.get(probeName));
    }

    /**
     * Returns a set of all probe names.
     *
     * @return A set of all probe names
     */
    @Override
    public Set<String> probeNames() {
        return additionalProbeResults.keySet();
    }

    /**
     * Returns the OS jitter probe result.
     *
     * @return An Optional containing the OS jitter probe result if present, otherwise empty
     */
    @Override
    public Optional<ProbeResult> osJitter() {
        return Optional.ofNullable(osJitterResult);
    }
}
