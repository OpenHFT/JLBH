/*
 * Copyright 2016-2025 chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

/**
 * Strategy interface used by JLBH to adjust the target latency between benchmark
 * iterations. Implementations take an expected average latency and return the
 * actual delay that should be used before invoking the next task. This allows
 * different distributions (such as constant or randomised) to be applied when
 * controlling the pace of a test.
 */
@FunctionalInterface
public interface LatencyDistributor {
    /**
     * Adjust the delay between benchmark iterations.
     *
     * @param averageLatencyNS the nominal delay in nanoseconds derived from the
     *                          configured throughput
     * @return the actual number of nanoseconds to wait before the next
     *         iteration is executed
     */
    long apply(long averageLatencyNS);
}
