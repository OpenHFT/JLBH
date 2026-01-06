/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.PERCENTILE_99_7TH;
import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.PERCENTILE_99_97TH;
import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.PERCENTILE_99_9TH;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableRunResultTest {

    @Test
    @DisplayName("exposes optional percentiles when they are present")
    void shouldExposeOptionalPercentilesWhenPresent() {
        double[] values = {100, 200, 300, 400, 500, 600, 700, 800};
        ImmutableRunResult result = new ImmutableRunResult(values);

        assertEquals(Duration.ofNanos(100), result.get50thPercentile(),
                "50th percentile should match the first value");
        assertEquals(Duration.ofNanos(200), result.get90thPercentile(),
                "90th percentile should match the second value");
        assertEquals(Duration.ofNanos(300), result.get99thPercentile(),
                "99th percentile should match the third value");
        assertEquals(Duration.ofNanos(400), result.percentiles().get(PERCENTILE_99_7TH),
                "99.7th percentile should map to the fourth value");
        assertEquals(Duration.ofNanos(500), result.percentiles().get(PERCENTILE_99_9TH),
                "99.9th percentile should map to the fifth value");
        assertEquals(Duration.ofNanos(600), result.percentiles().get(PERCENTILE_99_97TH),
                "99.97th percentile should map to the sixth value");
        assertEquals(Duration.ofNanos(700), result.get9999thPercentile(),
                "99.99th percentile should map to the seventh value");
        assertEquals(Duration.ofNanos(800), result.getWorst(),
                "Worst percentile should map to the final value");
    }

    @Test
    @DisplayName("omits optional percentiles and matches equality rules")
    void shouldOmitOptionalPercentilesAndMatchEqualityRules() {
        double[] values = {100, 200, 300, 400};
        ImmutableRunResult result = new ImmutableRunResult(values);

        assertNull(result.percentiles().get(PERCENTILE_99_7TH),
                "99.7th percentile should be absent when not supplied");
        assertNull(result.get999thPercentile(),
                "99.9th percentile should be absent when not supplied");
        assertNull(result.get9999thPercentile(),
                "99.99th percentile should be absent when not supplied");

        assertEquals(result, result, "equals should return true when comparing the same instance");
        assertNotEquals(null, result, "equals should return false when comparing to null");
        assertNotEquals("not a result", result, "equals should return false when comparing to another type");

        ImmutableRunResult same = new ImmutableRunResult(values);
        assertEquals(result, same,
                "results with the same percentile data should be equal");
        assertEquals(result.hashCode(), same.hashCode(),
                "hashCode should match for equal percentile data");
    }
}
