/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.affinity.Affinity;

import java.lang.management.ManagementFactory;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Diagnostic point sample; neither an ownership record nor a benchmark-validity judgement. */
final class HostSnapshot {
    private static final String UNKNOWN = "unknown";
    private final long capturedAtMillis;
    private final long javaThreadId;
    private final String loadAverage;
    private final String processors;
    private final String sampledCpu;
    private final String processId;

    // Thread.threadId() is unavailable on the supported Java 8 baseline.
    @SuppressWarnings({"deprecation", "java:S1874"})
    private HostSnapshot(String loadAverage, String processors, String sampledCpu, String processId) {
        this.capturedAtMillis = System.currentTimeMillis();
        this.javaThreadId = Thread.currentThread().getId();
        this.loadAverage = loadAverage;
        this.processors = processors;
        this.sampledCpu = sampledCpu;
        this.processId = processId;
    }

    static HostSnapshot capture() {
        return capture(() -> ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),
                () -> Runtime.getRuntime().availableProcessors(), Affinity::getCpu, () -> Affinity.getAffinityImpl().getProcessId());
    }

    // Each sensor fails independently; unavailable readings must not masquerade as zero load.
    static HostSnapshot capture(DoubleSupplier load, IntSupplier processors, IntSupplier cpu, IntSupplier pid) {
        return new HostSnapshot(readLoad(load), readInt(processors, 1), readInt(cpu, 0), readInt(pid, 1));
    }

    String line(String phase, int run) {
        return "Host: schema=1 phase=" + phase + " run=" + run
                + " process_id=" + processId + " captured_at_ms=" + capturedAtMillis + " java_thread_id=" + javaThreadId
                + " load_average_1m=" + loadAverage + " available_processors=" + processors
                + " sampled_cpu=" + sampledCpu;
    }

    private static String readLoad(DoubleSupplier sensor) {
        try {
            double value = sensor.getAsDouble();
            return Double.isFinite(value) && value >= 0 ? Double.toString(value) : UNKNOWN;
        } catch (RuntimeException | LinkageError unavailable) {
            return UNKNOWN;
        }
    }

    private static String readInt(IntSupplier sensor, int minimum) {
        try {
            int value = sensor.getAsInt();
            return value >= minimum ? Integer.toString(value) : UNKNOWN;
        } catch (RuntimeException | LinkageError unavailable) {
            return UNKNOWN;
        }
    }
}
