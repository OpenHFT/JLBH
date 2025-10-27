/*
 * Copyright 2016-2025 chronicle.software
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Java Latency Benchmark Harness (JLBH).
 * <p>
 * JLBH is designed to measure end to end latency of a workload under a
 * configured throughput.  It is intended for benchmarks where coordinated
 * omission matters, typically producer/consumer style tests where the start of
 * an iteration may be executed on a different thread than its completion.
 * <p>
 * This tool was inspired by JMH.
 * <p>
 * The harness itself is not thread safe and should be run from a single
 * thread.  Implementations of {@link JLBHTask} must expect {@link JLBHTask#run(long)}
 * to be invoked by only one thread at a time.  To safely retrieve results from
 * another thread use {@link ThreadSafeJLBHResultConsumer}.
 * <p>
 * Typical usage:
 * <pre>{@code
 * JLBHOptions options = new JLBHOptions()
 *         .throughput(50_000)
 *         .runs(3)
 *         .iterations(100_000)
 *         .jlbhTask(new MyJLBHTask());
 * new JLBH(options).start();
 * }</pre>
 */
@SingleThreaded
@SuppressWarnings({"unused", "this-escape"})
public class JLBH implements NanoSampler {
    public static final int TIME_CALL_NANO_TIME = 18;
    private final SortedMap<String, Histogram> additionHistograms = new ConcurrentSkipListMap<>();
    // wait time between invocations in nanoseconds
    private final long latencyBetweenTasks;
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
    private final AtomicLong sampleCount = new AtomicLong();
    //Use non-atomic when so thread synchronisation is necessary
    private boolean warmedUp;
    private volatile Thread testThread;

    /**
     * Creates a benchmark instance using the supplied options.  The provided
     * {@link JLBHOptions} defines how the benchmark will be executed including
     * run count, iteration count, warm-up iterations, throughput and jitter
     * recording settings.  If this convenience constructor is used the textual
     * benchmark output will be sent to {@code System.out}. Results are printed
     * once {@link #start()} completes.
     *
     * @param jlbhOptions options controlling the benchmark execution
     */
    public JLBH(@NotNull JLBHOptions jlbhOptions) {
        this(jlbhOptions, System.out, null);
    }

    /**
     * Use this constructor if you want to test the latencies in a more automated fashion. The
     * {@link JLBHResult} produced after {@link #start()} completes is passed to the supplied
     * {@code resultConsumer}.  If you need to access the result from another thread consider using
     * {@link JLBHResultConsumer#newThreadSafeInstance()} to obtain a suitable consumer.
     *
     * @param jlbhOptions    options that control how the benchmark is executed
     * @param printStream    stream used for textual output, e.g. {@link System#out}
     * @param resultConsumer consumer that receives the {@link JLBHResult} once the benchmark has
     *                       finished; may be {@code null} if no programmatic result is required
     */
    public JLBH(@NotNull JLBHOptions jlbhOptions, @NotNull PrintStream printStream, Consumer<JLBHResult> resultConsumer) {

        final String resourceTracing = System.getProperty("jvm.resource.tracing");

        if (resourceTracing != null && (resourceTracing.isEmpty() || Boolean.parseBoolean(resourceTracing))) {
            System.out.println("***** WARNING : JLBH can not be run if jvm.resource.tracing=" + resourceTracing + ", please remove all \"jvm.resource.tracing\" as this will corrupt your stats *****");
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
        long mod2 = 1000;
        while (mod2 <= jlbhOptions.iterations / 200) {
            mod2 *= 10;
        }
        this.mod = mod2;
    }

    static CharSequence padUntil(CharSequence cs, int length, char ch) {
        StringBuilder sb = new StringBuilder(cs);
        while (sb.length() < length)
            sb.append(ch);
        return sb;
    }

    /**
     * Add a named probe to measure a sub-stage of the benchmark.
     * <p>
     * A probe is sampled whenever the returned {@link NanoSampler}'s
     * {@code sampleNanos(long)} (or {@link #sample(long)}) method is invoked.
     * Samples gathered during the warmup phase are discarded when warmup
     * completes and each probe histogram is reset after every run
     * before collecting the next run's data.
     * Typical usage is to create probes in {@link JLBHTask#init(JLBH)} and
     * record durations inside {@link JLBHTask#run(long)}.
     *
     * @param name Name of probe
     * @return a {@code NanoSampler} to record timings for the named probe
     */
    public NanoSampler addProbe(String name) {
        return additionHistograms.computeIfAbsent(name, n -> createHistogram());
    }

    /**
     * Returns the percentile runs that have been recorded for any additional probes.
     *
     * @return a map of probe name to a list of percentile arrays for each run
     */
    @NotNull
    public Map<String, List<double[]>> additionalPercentileRuns() {
        return additionalPercentileRuns;
    }

    /**
     * Request the currently executing benchmark to stop.
     * <p>
     * This method sets an internal flag that causes the running loop in
     * {@link #start()} to exit. The thread executing the benchmark is also
     * interrupted, so any code waiting or blocking on that thread may fail with
     * {@link InterruptedException}. The interrupted status of the test thread is
     * cleared when {@link #start()} finishes.
     */
    public void abort() {
        abortTestRun.set(true);
        testThread.interrupt();
    }

    /**
     * Start benchmark.
     *
     * <p>The start method performs an initial warm up phase before collecting
     * any timings. {@link #warmup()} runs the configured number of warm-up
     * iterations. The thread then waits in {@link #waitForWarmupToComplete(long)}
     * until {@link #sample(long)} signals that the histograms have been reset
     * and the warm up is finished.</p>
     *
     * <p>Once warmed up, the benchmark executes the configured number of runs.
     * For each run the method loops over all iterations invoking
     * {@link JLBHTask#run(long)} at the calculated start time. After every run
     * {@link #endOfRun(int, long)} prints run statistics and resets the histograms
     * in preparation for the next run.
     * After the final run {@link #endOfAllRuns()} outputs the summary and calls
     * the result consumer.</p>
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
                                // account for jitter in Thread.sleep() and wait until a fixed point in time
                                startTimeNs = busyWaitUntil(startTimeNs);
                            }

                        } else {
                            if (latencyBetweenTasks > 2e6) {
                                long end = System.nanoTime() + latencyBetweenTasks;
                                Jvm.pause(latencyBetweenTasks / 1_000_000 - 1);
                                // account for jitter in Thread.sleep() and wait until a fixed point in time
                                startTimeNs = busyWaitUntil(startTimeNs);

                            } else {
                                startTimeNs += latencyBetweenTasks - 14;
                                long nowNS = System.nanoTime();
                                if (startTimeNs < nowNS + TIME_CALL_NANO_TIME) {
                                    startTimeNs = nowNS;
                                } else {
                                    // account for jitter in Thread.sleep() and wait until a fixed point in time
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
     * Spin until the provided time is reached.
     *
     * <p>We repeatedly invoke {@link System#nanoTime()} in a tight loop
     * and compare the result to {@code startTimeNs}. This busy wait avoids
     * a context switch so the benchmark can begin as close as possible to the
     * requested start time. The method returns the last value obtained from
     * {@code nanoTime} so the caller knows the exact timestamp at which the
     * wait completed.</p>
     *
     * @param startTimeNs the target time, in nanoseconds, to wait until
     * @return the actual timestamp returned from {@code nanoTime} once the
     *         wait is over
     */
    private static long busyWaitUntil(long startTimeNs) {
        long nanoTime;
        do {
            nanoTime = System.nanoTime();
        } while (startTimeNs > nanoTime);
        startTimeNs = nanoTime;
        return startTimeNs;
    }

    private void startTimeoutCheckerIfRequired() {
        if (jlbhOptions.timeout > 0) {
            Thread sampleTimeoutChecker = new Thread(this::checkSampleTimeout);
            sampleTimeoutChecker.setDaemon(true);
            sampleTimeoutChecker.start();
        }
    }

    private void waitForWarmupToComplete(long warmupStart) {
        while (!warmUpComplete.get()) {
            Jvm.pause(500);
        printStream.println("Complete: " + sampleCount.get());
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
     * Initialise the configured {@link JLBHTask} and start jitter monitoring.
     * <p>
     * The task's {@link JLBHTask#init(JLBH)} method is invoked giving it an
     * opportunity to create any additional probes via {@link #addProbe(String)}.
     * If {@link JLBHOptions#recordOSJitter} is enabled a background
     * {@link OSJitterMonitor} thread is started to record operating system
     * scheduling jitter.
     */
    private void initStartOSJitterMonitor() {
        jlbhOptions.jlbhTask.init(this);
        if (jlbhOptions.recordOSJitter) {
            osJitterMonitor.setDaemon(true);
            osJitterMonitor.start();
        }
    }

    private long warmup() {
        long warmupStart = System.currentTimeMillis();
        for (int i = 0; i < jlbhOptions.warmUpIterations; i++) {
            jlbhOptions.jlbhTask.run(System.nanoTime());
        }
        return warmupStart;
    }

    private void endOfAllRuns() {
        printPercentilesSummary("end to end", percentileRuns, printStream);
        if (additionalPercentileRuns.size() > 0) {
            additionalPercentileRuns.forEach((label, percentileRuns1) -> printPercentilesSummary(label, percentileRuns1, printStream));
        }

        consumeResults();

        jlbhOptions.jlbhTask.complete();
    }

    public List<double[]> percentileRuns() {
        return percentileRuns;
    }

    /**
     * Print statistics for a single run and reset state for the next run.
     *
     * <p>The method waits until all samples for the run have been recorded.
     * It then calculates the run duration and prints the end-to-end histogram
     * along with any additional probe histograms. OS jitter information is
     * included if recording is enabled.</p>
     *
     * <p>The percentile data from the end-to-end histogram is stored in
     * {@link #percentileRuns} so that a summary can be produced once all runs
     * complete. After invoking {@link JLBHTask#runComplete()}, all histograms
     * and counters are cleared ready for the next run.</p>
     *
     * @param run      index of the run to finish (zero based)
     * @param runStart wall clock time when the run started, used to measure the
     *                 total run duration
     */
    private void endOfRun(int run, long runStart) {
        while (!abortTestRun.get() && endToEndHistogram.totalCount() < jlbhOptions.iterations) {
            Thread.yield();
        }

        long totalRunTime = System.currentTimeMillis() - runStart;

        percentileRuns.add(endToEndHistogram.getPercentiles());

        printStream.println(padUntil("-------------------------------- BENCHMARK RESULTS (RUN " + (run + 1) + ") " + timeUnitToString(TimeUnit.MICROSECONDS) + " ----", 100, '-'));
        printStream.println("Run time: " + totalRunTime / 1000.0 + " s, distribution: " + latencyDistributor);
        printStream.println("Correcting for co-ordinated:" + jlbhOptions.accountForCoordinatedOmission);
        printStream.println("Target throughput:" + jlbhOptions.throughput + "/" + timeUnitToString(jlbhOptions.throughputTimeUnit) + " = 1 message every " + (latencyBetweenTasks / 1000) + "us");
        printStream.printf("%-48s", format("End to End: (%,d)", endToEndHistogram.totalCount()));
        printStream.println(endToEndHistogram.toMicrosFormat());

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
        if (jlbhOptions.recordOSJitter) {
            printStream.printf("%-48s", format("OS Jitter (%,d)", osJitterHistogram.totalCount()));
            printStream.println(osJitterHistogram.toMicrosFormat());
        }
        printStream.println(padUntil("----", 100, '-'));

        jlbhOptions.jlbhTask.runComplete();

        sampleCount.set(0);
        additionHistograms.values().forEach(Histogram::reset);
        endToEndHistogram.reset();
        osJitterMonitor.reset();
    }

    private void checkSampleTimeout() {
        long previousSampleCount = 0;
        long previousSampleTime = 0;

        while (true) {
            Jvm.pause(TimeUnit.SECONDS.toMillis(10));

            long current = sampleCount.get();
            if (previousSampleCount < current) {
                previousSampleCount = current;
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
     * Call this instead of {@link #start()} to run JLBH using an external
     * {@link EventLoop}. The warmup and benchmark handlers are registered with
     * the supplied event loop so that all work executes on that loop's thread.
     * The caller is expected to manage the lifecycle of the event loop itself.
     *
     * @param eventLoop the loop to attach the JLBH handlers to
     * @throws UnsupportedOperationException if coordinated omission accounting is disabled
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
     * Print a human readable summary of percentile data for a probe.
     * <p>
     * The supplied {@code appendable} receives a table showing the percentile
     * latencies for each run followed by the percentage variation of those
     * values. The first line contains a header in the following form
     * (padded to 100 characters):
     *
     * <pre>
     * -------------------------------- SUMMARY (label) us -------------------------
     * Percentile   run1         run2         run3      % Variation
     * 50.0:            8.07         8.07         6.10        17.69
     * ...
     * worst:          12.56        12.56        10.61        10.93
     * ----
     * </pre>
     *
     * Each percentile value is printed in microseconds with two decimal places.
     * The final column displays the percentage variation between the largest and
     * smallest values present in the row. The number of {@code run} columns is
     * determined by {@link JLBHOptions#runs}.
     *
     * @param label          descriptive name of the probe being summarised
     * @param percentileRuns list of percentile arrays for each run (values in
     *                       nanoseconds)
     * @param appendable     destination for the generated summary text
     * @throws IORuntimeException if writing to {@code appendable} fails
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
     * Append a printf style pattern for one percentile row to {@code sb}.
     *
     * <p>The pattern starts with the already formatted percentile label
     * supplied in {@code pr} and then adds a {@code %12.2f} placeholder for
     * each run followed by one additional {@code %12.2f} for the variance. The
     * row is terminated with {@code %n}. This pattern can later be passed to
     * {@link String#format(String, Object...)} or a {@code printf} method to
     * render the values.</p>
     *
     * @param sb   builder receiving the pattern
     * @param pr   formatted percentile label (e.g. {@code "99.9:"})
     * @param runs number of run value placeholders to append
     */
    private void addPrToPrint(@NotNull StringBuilder sb, String pr, int runs) {
        sb.append(pr);
        for (int i = 0; i < runs; i++) {
            sb.append("%12.2f ");
        }
        sb.append("%12.2f");
        sb.append("%n");
    }

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
     * Records a duration sample expressed in nanoseconds.
     *
     * @param durationNs duration of the event in nanoseconds
     */
    @Override
    public void sampleNanos(long durationNs) {
        sample(durationNs);
    }

    /**
     * Record a latency sample and manage warm-up state.
     *
     * <p>The first {@code jlbhOptions.warmUpIterations} calls are treated as the
     * warm-up phase. Samples taken during this period are added to the internal
     * histogram but are discarded once the warm-up is complete. When the warm-up
     * count is reached the histograms are cleared, {@code warmUpComplete} is set
     * and subsequent samples contribute to the benchmark results.</p>
     *
     * @param durationNs latency in nanoseconds
     */
    public void sample(long durationNs) {
        long current = sampleCount.incrementAndGet();
        if (current < jlbhOptions.warmUpIterations && !warmedUp) {
            endToEndHistogram.sample(durationNs);
            return;
        }
        if (current == jlbhOptions.warmUpIterations && !warmedUp) {
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
     * Create the histogram used for all probes in JLBH.
     * <p>
     * The parameters {@code 35}, {@code 8} and {@code 100} configure the
     * histogram to keep 35 bits of range with eight significant digits and
     * around one hundred buckets per decade. This provides sub-microsecond
     * precision over a very wide range of latencies.
     * </p>
     *
     * @return newly configured {@link Histogram}
     */
    @NotNull
    protected Histogram createHistogram() {
        return new Histogram(35, 8, 100);
    }

    /**
     * Background thread that measures scheduling jitter introduced by the
     * operating system. It repeatedly samples {@code System.nanoTime()} and
     * records any gap larger than {@link JLBHOptions#recordJitterGreaterThanNs}
     * into {@link #osJitterHistogram}. The monitor can optionally be bound to a
     * dedicated CPU when {@link JLBHOptions#jitterAffinity} is enabled and runs
     * until {@link #terminate()} is invoked.
     */
    private final class OSJitterMonitor extends Thread {
        final AtomicBoolean reset = new AtomicBoolean(false);
        final AtomicBoolean running = new AtomicBoolean(false);

        /**
         * Monitor loop that samples {@code System.nanoTime()} to detect scheduling
         * delays.
         * <p>
         * The thread first clears any inherited affinity and, when
         * {@link JLBHOptions#jitterAffinity} is enabled, locks itself to a core
         * using {@link AffinityLock}. It then repeatedly measures the gap between
         * successive {@code nanoTime()} calls, recording values that exceed the
         * configured {@link JLBHOptions#recordJitterGreaterThanNs} threshold into
         * {@link #osJitterHistogram}. The histogram may be cleared via
         * {@link #reset()} and the loop terminates when {@link #terminate()} sets
         * {@code running} to {@code false}. Approximately once a minute the
         * thread pauses briefly to avoid monopolising the CPU.
         */
        @Override
        public void run() {
            running.set(true);

            // make sure this thread is not bound by its parent.
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
         * Request that the jitter histogram be cleared and monitoring restarted
         * from the current time.
         * <p>
         * Calling this method only sets a flag; the histogram is actually
         * cleared by the monitor thread on its next iteration.
         * </p>
         */
        void reset() {
            reset.set(true);
        }

        /**
         * Stop the jitter monitoring thread. The thread will exit once the
         * current loop iteration completes.
         */
        void terminate() {
            running.set(false);
        }
    }

    /**
     * {@link EventHandler} used when {@link #eventLoopHandler(EventLoop)} is
     * called. It drives the benchmark from within the provided event loop,
     * invoking the configured {@link JLBHTask} at the configured rate and
     * managing progression through the iterations and runs. Once all runs are
     * complete it triggers result reporting and removes itself from the event
     * loop by throwing an {@link InvalidEventHandlerException}.
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

        private void resetTime() {
            runStart = System.currentTimeMillis();
            nextInvokeTime = System.nanoTime() + latencyBetweenTasks;
        }

        /**
         * Drive the benchmark state machine.
         * <p>
         * The handler has two states controlled by {@code waitingForEndOfRun}:
         * scheduling iterations and waiting for a run to finish.
         * </p>
         * <ul>
         *     <li><b>Scheduling iterations</b> – when not waiting for a run to
         *     finish the handler invokes the {@link JLBHTask} at
         *     {@code nextInvokeTime}. After each invocation counters are
         *     updated and once all iterations have been scheduled the handler
         *     switches to the waiting state.</li>
         *     <li><b>Waiting for completion</b> – when all iterations of the
         *     current run are scheduled the handler waits until the end to end
         *     histogram contains {@code jlbhOptions.iterations} samples. The
         *     run is then finalised and either the next run is started or, if
         *     the last run has completed, results are reported and the handler
         *     removes itself from the event loop by throwing an
         *     {@link InvalidEventHandlerException}.</li>
         * </ul>
         *
         * @return {@code true} if an iteration was executed, otherwise
         * {@code false}
         * @throws InvalidEventHandlerException if the benchmark has completed
         *                                      all runs and the handler should
         *                                      be removed from the loop
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
     * Handler executed before the benchmark runs to perform warm-up iterations.
     * <p>
     * Each call to {@link #action()} invokes the benchmark task once. When the
     * configured number of warm-up iterations has been reached the handler
     * throws {@link InvalidEventHandlerException#reusable()} which removes it
     * from the event loop and allows the main benchmark handler to start.
     */
    private final class WarmupHandler implements EventHandler {
        private int iteration;

        @Override
        public boolean action() throws InvalidEventHandlerException {
            if (iteration >= jlbhOptions.warmUpIterations)
                throw InvalidEventHandlerException.reusable();

            jlbhOptions.jlbhTask.run(System.nanoTime());
            ++iteration;
            return true;
        }

        @Override
        public void loopStarted() {
            // capture the thread that the warmup is executing on.  When JLBH
            // runs using an {@link EventLoop} this handler is invoked on the
            // loop's thread rather than the thread that created the JLBH
            // instance.  Storing it allows {@link #abort()} and interruption
            // checks to operate on the correct thread.
            testThread = Thread.currentThread();
        }
    }
}
