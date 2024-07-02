/*
 * Copyright 2016-2020 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.jlbh;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.annotation.SingleThreaded;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.core.threads.EventHandler;
import net.openhft.chronicle.core.threads.EventLoop;
import net.openhft.chronicle.core.threads.InvalidEventHandlerException;
import net.openhft.chronicle.core.util.Histogram;
import net.openhft.chronicle.core.util.NanoSampler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Java Latency Benchmark Harness (JLBH).
 * The harness is intended to be used for benchmarks where coordinated omission is an issue.
 * Typically, these would be of the producer/consumer nature where the start time for the benchmark may be on a different thread than the end time.
 * <p>
 * This tool was inspired by JMH.
 * <p>
 * This class is not thread-safe.
 */
@SingleThreaded
@SuppressWarnings("unused")
public class JLBH implements NanoSampler {
    // Constants and configuration variables
    public static final int TIME_CALL_NANO_TIME = 18;
    private final SortedMap<String, Histogram> additionHistograms = new ConcurrentSkipListMap<>();
    private final long latencyBetweenTasks; // Wait time between invocations in nanoseconds
    private final LatencyDistributor latencyDistributor;
    @NotNull
    private final JLBHOptions jlbhOptions;
    @NotNull
    private final PrintStream printStream;
    private final Consumer<JLBHResult> resultConsumer;
    @NotNull
    private final List<double[]> percentileRuns;
    @NotNull
    private final Map<String, List<double[]>> additionalPercentileRuns;
    @NotNull
    private final OSJitterMonitor osJitterMonitor = new OSJitterMonitor();
    @NotNull
    private final Histogram endToEndHistogram = createHistogram();
    @NotNull
    private final Histogram osJitterHistogram = createHistogram();
    @NotNull
    private final AtomicBoolean warmUpComplete = new AtomicBoolean();
    private final AtomicBoolean abortTestRun = new AtomicBoolean();
    private final long mod;
    private final long length;
    private volatile long noResultsReturned; // Result counter
    private boolean warmedUp; // Warm-up state flag
    private volatile Thread testThread; // Thread running the test

    /**
     * Constructs a JLBH instance with the specified options.
     *
     * @param jlbhOptions Options to run the benchmark
     */
    public JLBH(@NotNull JLBHOptions jlbhOptions) {
        this(jlbhOptions, System.out, null);
    }

    /**
     * Constructs a JLBH instance with the specified options, print stream, and result consumer.
     * Use this constructor if you want to test the latencies in a more automated fashion.
     * The result is passed to the result consumer after the JLBH::start method returns.
     *
     * @param jlbhOptions    Options to run the benchmark
     * @param printStream    Used to print text output. Use System.out to show the result on your standard out (e.g. screen)
     * @param resultConsumer If provided, accepts the result data to be retrieved after the latencies have been measured
     */
    public JLBH(@NotNull JLBHOptions jlbhOptions, @NotNull PrintStream printStream, Consumer<JLBHResult> resultConsumer) {

        final String resourceTracing = System.getProperty("jvm.resource.tracing");

        if (resourceTracing != null && (resourceTracing.isEmpty() || Boolean.parseBoolean(resourceTracing))) {
            System.out.println("***** WARNING : JLBH cannot be run if jvm.resource.tracing=" + resourceTracing + ", please remove all \"jvm.resource.tracing\" as this will corrupt your stats *****");
            System.exit(-1);
        }

        this.jlbhOptions = jlbhOptions;
        this.printStream = printStream;
        this.resultConsumer = resultConsumer;
        if (jlbhOptions.jlbhTask == null) throw new IllegalStateException("jlbhTask must be set");
        latencyBetweenTasks = jlbhOptions.throughputTimeUnit.toNanos(1) / jlbhOptions.throughput;
        percentileRuns = new ArrayList<>();
        additionalPercentileRuns = new TreeMap<>();
        latencyDistributor = jlbhOptions.latencyDistributor;

        this.length = jlbhOptions.iterations > 200_000_000 ? 60_000_000_000L
                : jlbhOptions.iterations > 50_000_000 ? 20_000_000_000L
                : jlbhOptions.iterations > 10_000_000 ? 10_000_000_000L
                : 5_000_000_000L;
        long mod2;
        for (mod2 = 1000; mod2 <= jlbhOptions.iterations / 200; mod2 *= 10) {
        }
        this.mod = mod2;
    }

    /**
     * Pads the given CharSequence until it reaches the specified length, using the provided character.
     *
     * @param cs The CharSequence to pad
     * @param length The target length
     * @param ch The character to pad with
     * @return The padded CharSequence
     */
    static CharSequence padUntil(CharSequence cs, int length, char ch) {
        StringBuilder sb = new StringBuilder(cs);
        while (sb.length() < length)
            sb.append(ch);
        return sb;
    }

    /**
     * Adds a probe to measure a section of the benchmark.
     *
     * @param name Name of the probe
     * @return The NanoSampler associated with the probe
     */
    public NanoSampler addProbe(String name) {
        return additionHistograms.computeIfAbsent(name, n -> createHistogram());
    }

    /**
     * Returns a map of additional percentile runs.
     *
     * @return A map containing additional percentile runs
     */
    @NotNull
    public Map<String, List<double[]>> additionalPercentileRuns() {
        return additionalPercentileRuns;
    }

    /**
     * Aborts the benchmark run.
     */
    public void abort() {
        abortTestRun.set(true);
        testThread.interrupt();
    }

    /**
     * Starts the benchmark.
     */
    public void start() {
        startTimeoutCheckerIfRequired();

        this.testThread = Thread.currentThread();
        initStartOSJitterMonitor();
        long warmupStart = warmup();
        int interruptCheckThrottle = 0;
        int interruptCheckThrottleMask = 1024 - 1;
        AffinityLock lock = jlbhOptions.acquireLock.get();
        try {
            for (int run = 0; run < jlbhOptions.runs && !abortTestRun.get(); run++) {

                long runStart = System.currentTimeMillis();
                long startTimeNs = System.nanoTime(), lastPrint = startTimeNs;

                final long iterations = jlbhOptions.iterations;

                for (int i = 0; i < iterations; i++) {

                    if (i % 16 == 0 && i % mod == 0 && startTimeNs > lastPrint + length) {
                        System.out.printf("... run %,d out of %,d%n", i, iterations);
                        lastPrint = startTimeNs;
                        startTimeNs = System.nanoTime();
                    }

                    if (i == 0 && run == 0) {
                        waitForWarmupToComplete(warmupStart);
                        runStart = System.currentTimeMillis();
                        startTimeNs = System.nanoTime();

                    } else {
                        final long latencyBetweenTasks = latencyDistributor.apply(this.latencyBetweenTasks);
                        if (jlbhOptions.accountForCoordinatedOmission) {
                            startTimeNs += latencyBetweenTasks;
                            final long now = System.nanoTime();
                            if (now < startTimeNs) {
                                long millis = (startTimeNs - now) / 1000000 - 2;
                                if (millis > 0) {
                                    Jvm.pause(millis);
                                }
                                // Account for jitter in Thread.sleep() and wait until a fixed point in time
                                startTimeNs = busyWaitUntil(startTimeNs);
                            }

                        } else {
                            if (latencyBetweenTasks > 2e6) {
                                long end = System.nanoTime() + latencyBetweenTasks;
                                Jvm.pause(latencyBetweenTasks / 1_000_000 - 1);
                                // Account for jitter in Thread.sleep() and wait until a fixed point in time
                                startTimeNs = busyWaitUntil(startTimeNs);

                            } else {
                                startTimeNs += latencyBetweenTasks - 14;
                                long nowNS = System.nanoTime();
                                if (startTimeNs < nowNS + TIME_CALL_NANO_TIME) {
                                    startTimeNs = nowNS;
                                } else {
                                    // Account for jitter in Thread.sleep() and wait until a fixed point in time
                                    startTimeNs = busyWaitUntil(startTimeNs);
                                }
                            }
                        }
                    }

                    if ((interruptCheckThrottle = (interruptCheckThrottle + 1) & interruptCheckThrottleMask) == 0
                            && testThread.isInterrupted()) {
                        break;
                    }

                    jlbhOptions.jlbhTask.run(startTimeNs);
                }

                endOfRun(run, runStart);
            }
        } finally {
            endOfAllRuns();

            osJitterMonitor.terminate();
            //noinspection ResultOfMethodCallIgnored
            Thread.interrupted(); // Reset thread interrupted status.
            Jvm.pause(5);
            if (lock != null)
                lock.release();
            Jvm.pause(5);
        }
    }

    /**
     * Busy waits until the specified time.
     *
     * @param startTimeNs The target time in nanoseconds
     * @return The actual start time in nanoseconds
     */
    private static long busyWaitUntil(long startTimeNs) {
        long nanoTime;
        do {
            nanoTime = System.nanoTime();
        } while (startTimeNs > nanoTime);
        startTimeNs = nanoTime;
        return startTimeNs;
    }

    /**
     * Starts the timeout checker if required.
     */
    private void startTimeoutCheckerIfRequired() {
        if (jlbhOptions.timeout > 0) {
            Thread sampleTimeoutChecker = new Thread(this::checkSampleTimeout);
            sampleTimeoutChecker.setDaemon(true);
            sampleTimeoutChecker.start();
        }
    }

    /**
     * Waits for the warm-up to complete.
     *
     * @param warmupStart The start time of the warm-up period
     */
    private void waitForWarmupToComplete(long warmupStart) {
        while (!warmUpComplete.get()) {
            Jvm.pause(500);
            printStream.println("Complete: " + noResultsReturned);
            if (testThread.isInterrupted()) {
                return;
            }
        }
        printStream.println("Warm up complete (" + jlbhOptions.warmUpIterations + " iterations took " +
                ((System.currentTimeMillis() - warmupStart) / 1000.0) + " s)");
        if (jlbhOptions.pauseAfterWarmupMS != 0) {
            printStream.println("Pausing after warmup for " + jlbhOptions.pauseAfterWarmupMS + " ms");
            Jvm.pause(jlbhOptions.pauseAfterWarmupMS);
        }
        jlbhOptions.jlbhTask.warmedUp();
    }

    /**
     * Initializes and starts the OS jitter monitor if required.
     */
    private void initStartOSJitterMonitor() {
        jlbhOptions.jlbhTask.init(this);
        if (jlbhOptions.recordOSJitter) {
            osJitterMonitor.setDaemon(true);
            osJitterMonitor.start();
        }
    }

    /**
     * Performs the warm-up phase of the benchmark.
     *
     * @return The start time of the warm-up period
     */
    private long warmup() {
        long warmupStart = System.currentTimeMillis();
        for (int i = 0; i < jlbhOptions.warmUpIterations; i++) {
            jlbhOptions.jlbhTask.run(System.nanoTime());
        }
        return warmupStart;
    }

    /**
     * Ends all runs and prints the percentiles summary.
     */
    private void endOfAllRuns() {
        printPercentilesSummary("end to end", percentileRuns, printStream);
        if (additionalPercentileRuns.size() > 0) {
            additionalPercentileRuns.forEach((label, percentileRuns1) -> printPercentilesSummary(label, percentileRuns1, printStream));
        }

        consumeResults();

        jlbhOptions.jlbhTask.complete();
    }

    /**
     * Returns the list of percentile runs.
     *
     * @return A list containing the percentile runs
     */
    public List<double[]> percentileRuns() {
        return percentileRuns;
    }

    /**
     * Handles the end of a benchmark run.
     *
     * @param run The current run number
     * @param runStart The start time of the run in milliseconds
     */
    private void endOfRun(int run, long runStart) {
        // Wait until all iterations are completed or the test is aborted
        while (!abortTestRun.get() && endToEndHistogram.totalCount() < jlbhOptions.iterations) {
            Thread.yield();
        }

        long totalRunTime = System.currentTimeMillis() - runStart;

        // Add the current run's percentiles to the list
        percentileRuns.add(endToEndHistogram.getPercentiles());

        // Print the run results
        printStream.println(padUntil("-------------------------------- BENCHMARK RESULTS (RUN " + (run + 1) + ") " + timeUnitToString(TimeUnit.MICROSECONDS) + " ----", 100, '-'));
        printStream.println("Run time: " + totalRunTime / 1000.0 + " s, distribution: " + latencyDistributor);
        printStream.println("Correcting for co-ordinated:" + jlbhOptions.accountForCoordinatedOmission);
        printStream.println("Target throughput:" + jlbhOptions.throughput + "/" + timeUnitToString(jlbhOptions.throughputTimeUnit) + " = 1 message every " + (latencyBetweenTasks / 1000) + "us");
        printStream.printf("%-48s", format("End to End: (%,d)", endToEndHistogram.totalCount()));
        printStream.println(endToEndHistogram.toMicrosFormat());

        // Print additional histograms
        if (additionHistograms.size() > 0) {
            additionHistograms.forEach((key, value) -> {
                List<double[]> ds = additionalPercentileRuns.computeIfAbsent(key,
                        i -> new ArrayList<>());
                ds.add(value.getPercentiles());
//                if (value.totalCount() != jlbhOptions.iterations)
//                    warning = " WARNING " + value.totalCount() + "!=" + jlbhOptions.iterations;
                printStream.printf("%-48s", format("%s (%,d)", key, value.totalCount()));
                printStream.println(value.toMicrosFormat());
            });
        }

        // Print OS jitter histogram if recording OS jitter
        if (jlbhOptions.recordOSJitter) {
            printStream.printf("%-48s", format("OS Jitter (%,d)", osJitterHistogram.totalCount()));
            printStream.println(osJitterHistogram.toMicrosFormat());
        }

        printStream.println(padUntil("----", 100, '-'));

        // Signal the task that the run is complete
        jlbhOptions.jlbhTask.runComplete();

        // Reset counters and histograms for the next run
        noResultsReturned = 0;
        additionHistograms.values().forEach(Histogram::reset);
        endToEndHistogram.reset();
        osJitterMonitor.reset();
    }

    /**
     * Checks for sample timeouts and aborts the test if a timeout occurs.
     */
    private void checkSampleTimeout() {
        long previousSampleCount = 0;
        long previousSampleTime = 0;

        while (true) {
            Jvm.pause(TimeUnit.SECONDS.toMillis(10));

            if (previousSampleCount < noResultsReturned) {
                previousSampleCount = noResultsReturned;
                previousSampleTime = System.currentTimeMillis();
            } else {
                if (previousSampleTime < (System.currentTimeMillis() - jlbhOptions.timeout)) {
                    printStream.println("Sample timed out. Aborting test...");
                    abort();
                    break;
                }
            }
        }
    }

    /**
     * Installs JLBH as a handler on the event loop thread instead of starting the benchmark directly.
     *
     * @param eventLoop The EventLoop to add the handler to
     */
    public void eventLoopHandler(@NotNull EventLoop eventLoop) {
        if (!jlbhOptions.accountForCoordinatedOmission)
            throw new UnsupportedOperationException();
        initStartOSJitterMonitor();
        eventLoop.addHandler(new WarmupHandler());
        Jvm.pause(100);
        waitForWarmupToComplete(System.currentTimeMillis());
        eventLoop.addHandler(new JLBHEventHandler());
    }

    /**
     * Consumes the benchmark results and passes them to the result consumer.
     */
    private void consumeResults() {
        if (resultConsumer != null) {
            final JLBHResult.ProbeResult endToEndProbeResult = new ImmutableProbeResult(percentileRuns);
            final Map<String, ImmutableProbeResult> additionalProbeResults = additionalPercentileRuns.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            probe -> new ImmutableProbeResult(probe.getValue())));
            List<double[]> percentileRuns = Collections.singletonList(osJitterHistogram.getPercentiles());
            ImmutableProbeResult osJitter = new ImmutableProbeResult(percentileRuns);
            resultConsumer.accept(new ImmutableJLBHResult(endToEndProbeResult, additionalProbeResults, osJitter));
        }
    }

    /**
     * Prints the summary of percentiles for the benchmark.
     *
     * @param label The label for the summary
     * @param percentileRuns The list of percentile runs
     * @param appendable The Appendable to print the summary to
     */
    public void printPercentilesSummary(
            String label,
            @NotNull List<double[]> percentileRuns,
            Appendable appendable) {
        try {
            appendable.append(
                            padUntil("-------------------------------- SUMMARY (" + label + ") " + timeUnitToString(TimeUnit.MICROSECONDS) + " ----", 100, '-'))
                    .append("\n");
            double[] percentiles = Histogram.percentilesFor(jlbhOptions.iterations);
            boolean skipFirst = percentiles.length > 3;
            if (jlbhOptions.skipFirstRun == JLBHOptions.SKIP_FIRST_RUN.SKIP) {
                skipFirst = true;
            } else if (jlbhOptions.skipFirstRun == JLBHOptions.SKIP_FIRST_RUN.NO_SKIP) {
                skipFirst = false;
            }
            PercentileSummary percentileSummary = new PercentileSummary(skipFirst, percentileRuns, percentiles);

            appendable.append(generateRunSummaryHeader(jlbhOptions.runs)).append('\n');
            percentileSummary.forEachRow((percentile, values, variance) -> {
                try {
                    appendable.append(formatPercentile(percentile));
                    for (double value : values) {
                        appendable.append(format("%12.2f ", value));
                    }
                    appendable.append(format("%12.2f%n", variance));
                } catch (IOException e) {
                    throw new IORuntimeException("Error writing percentile summary", e);
                }
            });
            appendable.append(padUntil("----", 100, '-'))
                    .append("\n");
        } catch (IOException e) {
            throw Jvm.rethrow(e);
        }
    }

    /**
     * Formats the percentile value for display.
     *
     * @param percentile The percentile value to format
     * @return A formatted string representing the percentile
     */
    private String formatPercentile(double percentile) {
        String s;
        if (percentile == 1) {
            s = "worst";
        } else {
            double p2 = Math.round(percentile * 1e6) / 1e4;
            s = Double.toString(p2);
        }
        s += ":     ";
        return s.substring(0, 9);
    }

    /**
     * Adds a formatted percentile run to the given StringBuilder.
     *
     * @param sb   The StringBuilder to append to
     * @param pr   The percentile run string
     * @param runs The number of runs
     */
    private void addPrToPrint(@NotNull StringBuilder sb, String pr, int runs) {
        sb.append(pr);
        for (int i = 0; i < runs; i++) {
            sb.append("%12.2f ");
        }
        sb.append("%12.2f");
        sb.append("%n");
    }

    /**
     * Generates the run summary header.
     *
     * @param runs The number of runs
     * @return A string representing the run summary header
     */
    private String generateRunSummaryHeader(int runs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Percentile");
        for (int i = 1; i < runs + 1; i++) {
            if (i == 1)
                sb.append("   run").append(i);
            else
                sb.append("         run").append(i);
        }
        sb.append("      % Variation");
        return sb.toString();
    }

    /**
     * Converts a TimeUnit to its corresponding string representation.
     *
     * @param timeUnit The TimeUnit to convert
     * @return A string representing the TimeUnit
     */
    private String timeUnitToString(@NotNull TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS:
                return "ns";
            case MICROSECONDS:
                return "us";
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "min";
            case HOURS:
                return "h";
            case DAYS:
                return "day";
            default:
                throw new IllegalArgumentException("Unrecognized time unit value '" + timeUnit + "'");
        }
    }

    /**
     * Samples a duration in nanoseconds.
     *
     * @param durationNs The duration in nanoseconds
     */
    @Override
    public void sampleNanos(long durationNs) {
        sample(durationNs);
    }

    /**
     * Samples a duration in nanoseconds and updates histograms accordingly.
     *
     * @param durationNs The duration in nanoseconds
     */
    public void sample(long durationNs) {
        noResultsReturned++;
        if (noResultsReturned < jlbhOptions.warmUpIterations && !warmedUp) {
            endToEndHistogram.sample(durationNs);
            return;
        }
        if (noResultsReturned == jlbhOptions.warmUpIterations && !warmedUp) {
            warmedUp = true;
            endToEndHistogram.reset();
            if (!additionHistograms.isEmpty()) {
                additionHistograms.values().forEach(Histogram::reset);
            }
            warmUpComplete.set(true);
            return;
        }
        endToEndHistogram.sample(durationNs);
    }

    /**
     * Creates and returns a new Histogram instance.
     *
     * @return A new Histogram instance
     */
    @NotNull
    protected Histogram createHistogram() {
        return new Histogram(35, 8, 100);
    }

    /**
     * Monitors and records OS jitter.
     */
    private final class OSJitterMonitor extends Thread {
        final AtomicBoolean reset = new AtomicBoolean(false);
        final AtomicBoolean running = new AtomicBoolean(false);

        @Override
        public void run() {
            running.set(true);

            // Ensure this thread is not bound by its parent.
            Affinity.setAffinity(AffinityLock.BASE_AFFINITY);
            @Nullable AffinityLock affinityLock = null;
            if (jlbhOptions.jitterAffinity) {
                printStream.println("Jitter thread running with affinity.");
                affinityLock = AffinityLock.acquireLock();
            }

            try {
                long lastTime = System.nanoTime(), start = lastTime;
                //noinspection InfiniteLoopStatement
                while (running.get()) {
                    if (reset.compareAndSet(true, false)) {
                        osJitterHistogram.reset();
                        lastTime = System.nanoTime();
                    }
                    for (int i = 0; i < 1000; i++) {
                        long time = System.nanoTime();
                        if (time - lastTime > jlbhOptions.recordJitterGreaterThanNs) {
                            osJitterHistogram.sampleNanos(time - lastTime);
                        }
                        lastTime = time;
                    }
                    if (lastTime > start + 60e9)
                        Jvm.pause(1);
                }
            } finally {
                if (affinityLock != null)
                    affinityLock.release();
            }
        }

        /**
         * Resets the jitter monitor.
         */
        void reset() {
            reset.set(true);
        }

        /**
         * Terminates the jitter monitor.
         */
        void terminate() {
            running.set(false);
        }
    }

    /**
     * EventHandler implementation for handling JLBH events.
     */
    private final class JLBHEventHandler implements EventHandler {
        private int run;
        private long iteration, i;
        private long runStart;
        private long nextInvokeTime;
        private boolean waitingForEndOfRun = false;
        private long lastPrint;

        JLBHEventHandler() {
            resetTime();
            this.lastPrint = nextInvokeTime;
        }

        /**
         * Resets the timing for the next run.
         */
        private void resetTime() {
            runStart = System.currentTimeMillis();
            nextInvokeTime = System.nanoTime() + latencyBetweenTasks;
        }

        /**
         * Performs the action associated with this EventHandler.
         *
         * @return True if the handler was busy, false otherwise
         * @throws InvalidEventHandlerException If the event handler is invalid
         */
        @Override
        public boolean action() throws InvalidEventHandlerException {
            boolean busy = false;
            final long iterations = jlbhOptions.iterations;

            if (!waitingForEndOfRun) {
                long now = System.nanoTime();
                if (now >= nextInvokeTime) {
                    jlbhOptions.jlbhTask.run(nextInvokeTime);
                    nextInvokeTime += latencyBetweenTasks;
                    ++iteration;
                    busy = true;

                    if (i >= iterations - 1) {
                        waitingForEndOfRun = true;
                        i = 0;
                        run++;
                    } else {
                        i++;
                    }

                    if (i % 16 == 0 && i % mod == 0 && nextInvokeTime > lastPrint + length) {
                        System.out.printf("... run %,d out of %,d%n", i, iterations);
                        lastPrint = nextInvokeTime;
                    }
                }
            } else {
                if (endToEndHistogram.totalCount() >= iterations) {
                    endOfRun(run - 1, runStart);
                    resetTime();
                    waitingForEndOfRun = false;
                    if (run == jlbhOptions.runs) {
                        endOfAllRuns();
                        throw new InvalidEventHandlerException();
                    }
                }
            }

            return busy;
        }
    }

    /**
     * EventHandler implementation for handling warm-up events.
     */
    private final class WarmupHandler implements EventHandler {
        private int iteration;

        /**
         * Performs the action associated with this EventHandler.
         *
         * @return True if the handler was busy, false otherwise
         * @throws InvalidEventHandlerException If the event handler is invalid
         */
        @Override
        public boolean action() throws InvalidEventHandlerException {
            if (iteration >= jlbhOptions.warmUpIterations)
                throw InvalidEventHandlerException.reusable();

            jlbhOptions.jlbhTask.run(System.nanoTime());
            ++iteration;
            return true;
        }

        /**
         * Called when the event loop starts.
         */
        @Override
        public void loopStarted() {
            testThread = Thread.currentThread();
        }
    }
}
