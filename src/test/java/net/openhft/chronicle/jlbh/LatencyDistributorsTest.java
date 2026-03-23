/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyDistributorsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void averageOk(LatencyDistributor ld) {
        long base = 10_000; // e.g. 100_000/s
        long sum = 0;
        final int count = 100_000;
        for (int i = 0; i < count; i++)
            sum += ld.apply(base);
        assertEquals(count * base, sum, sum / 50.0);
    }

    public static Collection<Object[]> data() {
        return Arrays.stream(LatencyDistributors.values())
                .map(x -> new Object[]{x})
                .collect(Collectors.toList());
    }
}
