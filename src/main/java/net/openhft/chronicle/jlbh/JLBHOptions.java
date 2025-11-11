/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.annotation.SingleThreaded;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Data structure to store the options to pass into the JLBH constructor
 */
@SingleThreaded
public class JLBHOptions {
    int throughput = 10_000;

    LatencyDistributor latencyDistributor = LatencyDistributors.NORMAL;

    TimeUnit throughputTimeUnit = TimeUnit.SECONDS;
    boolean accountForCoordinatedOmission = true;
    int recordJitterGreaterThanNs = 1_000;
    boolean recordOSJitter = true;
    int warmUpIterations = Jvm.compileThreshold() * 6 / 5;
    int runs = 3;
    long iterations = 100_000;
    JLBHTask jlbhTask;
    int pauseAfterWarmupMS = 0;
    @NotNull
    SKIP_FIRST_RUN skipFirstRun = SKIP_FIRST_RUN.NOT_SET;
    boolean jitterAffinity;
    Supplier<AffinityLock> acquireLock = Affinity::acquireLock;
    long timeout;
    /**
     * Number of iterations per second to be pushed through the benchmark
     *
     * @param throughput defaults to 10,000
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions throughput(int throughput) {
        return throughput(throughput, TimeUnit.SECONDS);
    }

    /**
     * Number of iterations per time unit to be pushed through the benchmark
     *
     * @param throughput         defaults to 10,000
     * @param throughputTimeUnit defaults to {@code TimeUnit.SECOND}
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions throughput(int throughput, TimeUnit throughputTimeUnit) {
        this.throughput = throughput;
        this.throughputTimeUnit = throughputTimeUnit;
        return this;
    }

    /**
     * Allow the distribution to be altered pseudo-randomly
     *
     * @param latencyDistributor function to take the average latency and multiply it by a function.
     */
    public JLBHOptions latencyDistributor(LatencyDistributor latencyDistributor) {
        this.latencyDistributor = latencyDistributor;
        return this;
    }

    /**
     * Determines whether the start time is the time the event was supposed to have happened
     * (i.e. accounting for co-ordinated omission) or whether the the start time is just
     * the a factor of the throughput (i.e. not accounting for co-ordinated omission).
     *
     * @param accountForCoordinatedOmission defaults to true
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions accountForCoordinatedOmission(boolean accountForCoordinatedOmission) {
        this.accountForCoordinatedOmission = accountForCoordinatedOmission;
        return this;
    }

    /**
     * Determines how much jitter to record.
     *
     * @param recordJitterGreaterThanNs Defaults to 1000
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions recordJitterGreaterThanNs(int recordJitterGreaterThanNs) {
        this.recordJitterGreaterThanNs = recordJitterGreaterThanNs;
        return this;
    }

    /**
     * Determines whether or not to record jitter
     *
     * @param recordOSJitter Defaults to true
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions recordOSJitter(boolean recordOSJitter) {
        this.recordOSJitter = recordOSJitter;
        return this;
    }

    /**
     * Determines how many warmup iterations to perform.
     * Note: warmup iterations are continuous.
     *
     * @param warmUp Defaults to 10,000
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions warmUpIterations(int warmUp) {
        this.warmUpIterations = warmUp;
        return this;
    }

    /**
     * Number of runs of the benchmark
     *
     * @param runs Defaults to 3
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions runs(int runs) {
        this.runs = runs;
        return this;
    }

    /**
     * Number of iterations of the benchmark not including warmup.
     *
     * @param iterations Defaults to 100,000
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions iterations(int iterations) {
        this.iterations = iterations;
        return this;
    }

    /**
     * Variant of {@link #iterations(int)} that accepts a {@code long} so that
     * iteration counts greater than {@code Integer#MAX_VALUE} can be specified.
     *
     * @param iterations total number of iterations to run
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions iterations(long iterations) {
        this.iterations = iterations;
        return this;
    }

    /**
     * The latency benchmark to be run.
     *
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions jlbhTask(JLBHTask JLBHTask) {
        this.jlbhTask = JLBHTask;
        return this;
    }

    /**
     * Option to set a pause after the warmup is complete
     *
     * @param pauseMS pause in ms default to 0
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions pauseAfterWarmupMS(int pauseMS) {
        this.pauseAfterWarmupMS = pauseMS;
        return this;
    }

    /**
     * Option to skip first run from being included in the variation statistics.
     *
     * @param skip default to true if runs greater than 3
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions skipFirstRun(boolean skip) {
        skipFirstRun = skip ? SKIP_FIRST_RUN.SKIP : SKIP_FIRST_RUN.NO_SKIP;
        return this;
    }

    /**
     * Should the jitter thread set affinity or not
     *
     * @param jitterAffinity default is false
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    @NotNull
    public JLBHOptions jitterAffinity(boolean jitterAffinity) {
        this.jitterAffinity = jitterAffinity;
        return this;
    }

    /**
     * Sets the supplier used to acquire an {@link AffinityLock} when the
     * benchmark starts.
     *
     * @param acquireLock supplier that provides the lock, defaults to
     *                    {@code Affinity::acquireLock}
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    public JLBHOptions acquireLock(Supplier<AffinityLock> acquireLock) {
        this.acquireLock = acquireLock;
        return this;
    }

    /**
     * Sets the maximum time to wait for the next sample to be produced.
     * <p>
     * If no additional samples are recorded within the specified number of
     * milliseconds the running benchmark is aborted. A value of {@code 0}
     * disables the timeout check.
     *
     * @param timeout timeout in milliseconds
     * @return Instance of the JLBHOptions to be used in the builder pattern.
     */
    public JLBHOptions timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("JLBHOptions{");
        sb.append("runs=").append(runs);
        sb.append(", iterations=").append(iterations);
        sb.append(", warmUpIterations=").append(warmUpIterations);
        sb.append(", pauseAfterWarmupMS=").append(pauseAfterWarmupMS);
        sb.append(", accountForCoordinatedOmission=").append(accountForCoordinatedOmission);
        sb.append(", skipFirstRun=").append(skipFirstRun);
        sb.append(", recordOSJitter=").append(recordOSJitter);
        sb.append(", recordJitterGreaterThanNs=").append(recordJitterGreaterThanNs);
        sb.append(", throughput=").append(throughput);
        sb.append(", throughputTimeUnit=").append(throughputTimeUnit);
        sb.append(", latencyDistributor=").append(latencyDistributor);
        sb.append(", jitterAffinity=").append(jitterAffinity);
        sb.append(", timeout=").append(timeout);
        sb.append(", jlbhTask=").append(jlbhTask);
        sb.append(", acquireLock=").append(acquireLock);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Options controlling whether the results of the first run are included
     * when calculating run-to-run variation.
     * <ul>
     *     <li>{@link #NOT_SET} - behaviour is chosen automatically based on the
     *     number of runs (the first run is skipped if there are more than three
     *     runs).</li>
     *     <li>{@link #SKIP} - always skip the first run.</li>
     *     <li>{@link #NO_SKIP} - always include the first run.</li>
     * </ul>
     */
    enum SKIP_FIRST_RUN {
        NOT_SET, SKIP, NO_SKIP
    }
}
