/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;

/**
 * Immutable snapshot of JLBH run results with thread-safe, read-only access.
 * <p>
 * Instances of this class are constructed with the results of a benchmark run
 * and provide read-only access to that data. All fields are {@code final} and
 * collections returned are unmodifiable, so once created the contents cannot be
 * changed. This makes the class thread safe and allows it to be freely shared
 * between threads.
 */
final class ImmutableJLBHResult implements JLBHResult {

    @NotNull
    private final ProbeResult endToEndProbeResult;
    @NotNull
    private final Map<String, ProbeResult> additionalProbeResults;
    private final ProbeResult osJitterResult;

    /**
     * Creates an immutable snapshot of a JLBH run's probe data.
     *
     * @param endToEndProbeResult    summary of the default end-to-end probe
     * @param additionalProbeResults map of additional probe names to their results
     * @param osJitterResult         operating system jitter probe result, may be {@code null}
     */
    ImmutableJLBHResult(@NotNull ProbeResult endToEndProbeResult,
                        @NotNull Map<String, ? extends ProbeResult> additionalProbeResults,
                        ProbeResult osJitterResult) {
        this.endToEndProbeResult = endToEndProbeResult;
        this.additionalProbeResults = unmodifiableMap(additionalProbeResults);
        this.osJitterResult = osJitterResult;
    }

    @Override
    @NotNull
    public ProbeResult endToEnd() {
        return endToEndProbeResult;
    }

    @Override
    @NotNull
    public Optional<ProbeResult> probe(String probeName) {
        return Optional.ofNullable(additionalProbeResults.get(probeName));
    }

    @Override
    public Set<String> probeNames() {
        return additionalProbeResults.keySet();
    }

    @Override
    public Optional<ProbeResult> osJitter() {
        return Optional.ofNullable(osJitterResult);
    }
}
