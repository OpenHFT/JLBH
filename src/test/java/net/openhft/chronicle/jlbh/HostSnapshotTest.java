/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HostSnapshotTest {
    @Test
    void preservesValidZeroLoadAndCpuWithoutConfusingThemWithUnknown() {
        String line = HostSnapshot.capture(() -> 0.0, () -> 8, () -> 0, () -> 1234).line("run-end", 2);
        assertTrue(line.startsWith("Host: schema=1 phase=run-end run=2 process_id=1234 captured_at_ms="));
        assertTrue(line.contains("java_thread_id=" + Thread.currentThread().getId()));
        assertTrue(line.endsWith("load_average_1m=0.0 available_processors=8 sampled_cpu=0"));
    }

    @Test
    void unsupportedAndNonFiniteLoadReadingsRemainUnknown() {
        for (double load : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            String line = HostSnapshot.capture(() -> load, () -> 0, () -> -1, () -> -1).line("before-init", 0);
            assertTrue(line.endsWith("load_average_1m=unknown available_processors=unknown sampled_cpu=unknown"));
        }
    }

    @Test
    void failingSensorsAreIsolated() {
        String line = HostSnapshot.capture(() -> { throw new SecurityException("denied"); },
                () -> 4, () -> { throw new UnsatisfiedLinkError("unsupported"); }, () -> { throw new SecurityException(); }).line("run-end", 1);
        assertTrue(line.contains("process_id=unknown"));
        assertTrue(line.endsWith("load_average_1m=unknown available_processors=4 sampled_cpu=unknown"));
        line = HostSnapshot.capture(() -> 1.25,
                () -> { throw new IllegalStateException(); }, () -> 7, () -> 42).line("run-end", 1);
        assertTrue(line.endsWith("load_average_1m=1.25 available_processors=unknown sampled_cpu=7"));
    }
}
