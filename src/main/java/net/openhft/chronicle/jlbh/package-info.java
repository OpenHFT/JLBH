//
// Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
//

/**
 * Provides the classes for the Java Latency Benchmark Harness.
 *
 * <p>The {@link net.openhft.chronicle.jlbh.JLBH} runner measures latency of
 * code executing under a steady throughput. Benchmarks implement
 * {@link net.openhft.chronicle.jlbh.JLBHTask} and are configured via
 * {@link net.openhft.chronicle.jlbh.JLBHOptions}. Additional probes may be
 * registered to time individual stages and operating system jitter can be
 * recorded.
 *
 * <p>Results are published as immutable {@link net.openhft.chronicle.jlbh.JLBHResult}
 * instances. A {@link net.openhft.chronicle.jlbh.JLBHResultConsumer} bridges the
 * harness and the code retrieving the result. Output helpers include
 * {@link net.openhft.chronicle.jlbh.TeamCityHelper} for TeamCity messages and
 * {@link net.openhft.chronicle.jlbh.util.JLBHResultSerializer} for CSV files.
 */
package net.openhft.chronicle.jlbh;
