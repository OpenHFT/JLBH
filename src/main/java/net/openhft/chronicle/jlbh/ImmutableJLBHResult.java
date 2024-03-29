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

final class ImmutableJLBHResult implements JLBHResult {

    @NotNull
    private final ProbeResult endToEndProbeResult;
    @NotNull
    private final Map<String, ProbeResult> additionalProbeResults;
    private final ProbeResult osJitterResult;

    ImmutableJLBHResult(@NotNull ProbeResult endToEndProbeResult, @NotNull Map<String, ? extends ProbeResult> additionalProbeResults, ProbeResult osJitterResult) {
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
