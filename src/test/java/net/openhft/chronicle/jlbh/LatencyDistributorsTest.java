/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyDistributorsTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LatencyDistributors.class)
    @DisplayName("keeps mean latency within tolerance for each distributor")
    void averageOk(LatencyDistributors ld) {
        long base = 10_000; // e.g. 100_000/s
        long sum = 0;
        final int count = 100_000;
        for (int i = 0; i < count; i++)
            sum += ld.apply(base);
        assertEquals(count * (double) base, sum, sum / 50.0, "mean latency should stay within tolerance");
    }
}
