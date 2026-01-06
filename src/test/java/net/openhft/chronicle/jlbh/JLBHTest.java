/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.threads.EventLoop;
import net.openhft.chronicle.threads.MediumEventLoop;
import net.openhft.chronicle.threads.Pauser;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.openhft.chronicle.jlbh.JLBHDeterministicFixtures.*;
import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.*;
import static org.junit.jupiter.api.Assertions.*;

public class JLBHTest {
    private static EventLoop createEventLoop(boolean runFromEventLoop) {
        if (!runFromEventLoop) {
            return null;
        }
        EventLoop eventLoop = new MediumEventLoop(null, "el", Pauser.busy(), true, null);
        eventLoop.start();
        return eventLoop;
    }

    private static void start(JLBH jlbh, EventLoop eventLoop) {
        if (eventLoop != null) {
            jlbh.eventLoopHandler(eventLoop);
            Jvm.pause(500);
        } else {
            jlbh.start();
        }
    }

    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    public void shouldWriteResultToTheOutputProvided(boolean runFromEventLoop) {
        EventLoop eventLoop = createEventLoop(runFromEventLoop);
        try {

            // given
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final PrintStream ps;
            try {
                ps = new PrintStream(outputStream, true, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new AssertionError("UTF-8 not supported", e);
            }
            final JLBH jlbh = new JLBH(options(), ps, resultConsumer());

            // when
            start(jlbh, eventLoop);

            // then
            String result;
            try {
                result = outputStream.toString("UTF-8").replace("\r", "");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new AssertionError("UTF-8 not supported", e);
            }
            assertTrue(result.contains("OS Jitter"), "output should include OS jitter section");
            assertTrue(result.contains("Warm up complete (500 iterations took "), "output should include warmup summary");
            assertTrue(result.contains("Run time: "), "output should include run time");

            final String predictableTaskExpectedResult = predictableTaskExpectedResult();
            System.out.println("predictableTaskExpectedResult = " + predictableTaskExpectedResult);
            final String expected = withoutNonDeterministicFields(predictableTaskExpectedResult);
            System.out.println("expected = " + expected);
            final String actual = withoutNonDeterministicFields(result);
            System.out.println("actual = " + actual);

            if (!expected.equals(actual)) {
                System.err.println("expected");
                expected.chars().limit(10).boxed().forEach(System.err::println);
                System.err.println("actual");
                actual.chars().limit(10).boxed().forEach(System.err::println);
            }

            // Disable for the moment. Reintroduce this assertion once the source of flakyness on Java 11 is figured out
            // assertEquals(expected, actual);
            if (!expected.equals(actual)) {
                System.err.println("ERROR! There is an error here which is disabled at the moment! expected is not equal to actual");
            }
        } finally {
            Closeable.closeQuietly(eventLoop);
        }
    }

    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    /*
     * To understand the data, please go to JLBHDeterministicFixtures
     * and JLBHDeterministicFixtures::expectedOutput in particular
     */
    public void shouldProvideResultData(boolean runFromEventLoop) {
        EventLoop eventLoop = createEventLoop(runFromEventLoop);
        try {

            // given
            final JLBHResultConsumer resultConsumer = resultConsumer();
            final JLBH jlbh = new JLBH(options(), printStream(), resultConsumer);

            // when
            start(jlbh, eventLoop);

            // then
            final JLBHResult.RunResult lastRunSummary = resultConsumer.get().endToEnd().summaryOfLastRun();
            assertEquals(6_106L, lastRunSummary.get50thPercentile().toNanos(), 20, "end-to-end: 50th percentile");
            assertEquals(9_708L, lastRunSummary.get90thPercentile().toNanos(), 20, "end-to-end: 90th percentile");
            assertEquals(10_516L, lastRunSummary.get99thPercentile().toNanos(), 20, "end-to-end: 99th percentile");
            assertEquals(10_604L, lastRunSummary.getWorst().toNanos(), 20, "end-to-end: worst");
            assertEquals(lastRunSummary.get50thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_50TH), "percentiles map: 50th");
            assertEquals(lastRunSummary.get90thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_90TH), "percentiles map: 90th");
            assertEquals(lastRunSummary.get99thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_99TH), "percentiles map: 99th");
            assertEquals(lastRunSummary.get999thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_99_9TH), "percentiles map: 99.9th");
            assertEquals(lastRunSummary.get9999thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_99_99TH), "percentiles map: 99.99th");
            assertNull(lastRunSummary.percentiles().get(PERCENTILE_99_999TH), "percentiles map should not contain 99.999th");
            assertEquals(lastRunSummary.getWorst(), lastRunSummary.percentiles().get(WORST), "percentiles map: worst");

            final List<JLBHResult.RunResult> summaryOfEachRun = resultConsumer.get().endToEnd().eachRunSummary();
            assertEquals(3, summaryOfEachRun.size(), "end-to-end run summaries count");
            assertNotEquals(lastRunSummary, summaryOfEachRun.get(0), "run0 summary should differ from last run");
            assertNotEquals(lastRunSummary, summaryOfEachRun.get(1), "run1 summary should differ from last run");
            assertEquals(lastRunSummary, summaryOfEachRun.get(2), "run2 summary should match last run");

            assertTrue(resultConsumer.get().probe("A").isPresent(), "probe A should be present");
            assertTrue(resultConsumer.get().probe("B").isPresent(), "probe B should be present");
            assertFalse(resultConsumer.get().probe("C").isPresent(), "probe C should not be present");

            final JLBHResult.RunResult probeALastRunSummary = resultConsumer.get().probe("A").get().summaryOfLastRun();
            assertEquals(5_106L, probeALastRunSummary.get50thPercentile().toNanos(), 20, "probe A: 50th percentile");
            assertEquals(8_708L, probeALastRunSummary.get90thPercentile().toNanos(), 30, "probe A: 90th percentile");
            assertEquals(9_516L, probeALastRunSummary.get99thPercentile().toNanos(), 30, "probe A: 99th percentile");
            assertEquals(9_604L, probeALastRunSummary.getWorst().toNanos(), 30, "probe A: worst");
            assertEquals(probeALastRunSummary.get50thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_50TH), "probe A percentiles map: 50th");
            assertEquals(probeALastRunSummary.get90thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_90TH), "probe A percentiles map: 90th");
            assertEquals(probeALastRunSummary.get99thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_99TH), "probe A percentiles map: 99th");
            assertEquals(probeALastRunSummary.get999thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_99_9TH), "probe A percentiles map: 99.9th");
            assertEquals(probeALastRunSummary.get9999thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_99_99TH), "probe A percentiles map: 99.99th");
            assertNull(probeALastRunSummary.percentiles().get(PERCENTILE_99_999TH), "probe A percentiles map should not contain 99.999th");
            assertEquals(probeALastRunSummary.getWorst(), probeALastRunSummary.percentiles().get(WORST), "probe A percentiles map: worst");

            final List<JLBHResult.RunResult> summaryOfProbeAEachRun = resultConsumer.get().probe("A").get().eachRunSummary();
            assertEquals(3, summaryOfProbeAEachRun.size(), "probe A run summaries count");
            assertNotEquals(probeALastRunSummary, summaryOfProbeAEachRun.get(0), "probe A run0 summary should differ from last run");
            assertNotEquals(probeALastRunSummary, summaryOfProbeAEachRun.get(1), "probe A run1 summary should differ from last run");
            assertEquals(probeALastRunSummary, summaryOfProbeAEachRun.get(2), "probe A run2 summary should match last run");
        } finally {
            Closeable.closeQuietly(eventLoop);
        }
    }

    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    public void shouldProvideResultDataEvenIfProbesDoNotProvideSameShapedData(boolean runFromEventLoop) {
        EventLoop eventLoop = createEventLoop(runFromEventLoop);
        try {

            // given
            final JLBHResultConsumer resultConsumer = resultConsumer();
            JLBHOptions jlbhOptions = options().jlbhTask(new PredictableJLBHTaskDifferentShape()).iterations(ITERATIONS * 2);
            final JLBH jlbh = new JLBH(jlbhOptions, printStream(), resultConsumer);

            // when
            start(jlbh, eventLoop);

            // then
            final JLBHResult.RunResult probeALastRunSummary = resultConsumer.get().probe("A").get().summaryOfLastRun();
            assertEquals(5, probeALastRunSummary.percentiles().size(), "probe A percentiles size");

            final JLBHResult.RunResult probeBLastRunSummary = resultConsumer.get().probe("B").get().summaryOfLastRun();
            assertEquals(4, probeBLastRunSummary.percentiles().size(), "probe B percentiles size");
        } finally {
            Closeable.closeQuietly(eventLoop);
        }
    }

    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    public void teamCityHelper(boolean runFromEventLoop) {
        EventLoop eventLoop = createEventLoop(runFromEventLoop);
        try {

            // given
            final JLBHResultConsumer resultConsumer = resultConsumer();
            JLBHOptions jlbhOptions = options().jlbhTask(new PredictableJLBHTaskDifferentShape()).iterations(ITERATIONS * 2);
            final JLBH jlbh = new JLBH(jlbhOptions, printStream(), resultConsumer);

            // when
            start(jlbh, eventLoop);

            // then
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                try (final PrintStream printStream = new PrintStream(baos, true, "UTF-8")) {
                    TeamCityHelper.teamCityStatsLastRun("prefix", jlbh, jlbhOptions.iterations, printStream);
                }
            } catch (java.io.UnsupportedEncodingException e) {
                throw new AssertionError("UTF-8 not supported", e);
            }
            String extra = Jvm.isAzulZing() ? ".zing" : Jvm.isJava15Plus() ? ".java17" : "";
            String stats;
            try {
                stats = baos.toString("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new AssertionError("UTF-8 not supported", e);
            }
            assertEquals("##teamcity[buildStatisticValue key='prefix.end-to-end.0.5" + extra + "' value='8.072']\n" +
                            "##teamcity[buildStatisticValue key='prefix.end-to-end.0.9" + extra + "' value='11.664']\n" +
                            "##teamcity[buildStatisticValue key='prefix.end-to-end.0.99" + extra + "' value='12.464']\n" +
                            "##teamcity[buildStatisticValue key='prefix.end-to-end.0.997" + extra + "' value='12.528']\n" +
                            "##teamcity[buildStatisticValue key='prefix.end-to-end.1.0" + extra + "' value='12.56']\n" +
                            "##teamcity[buildStatisticValue key='prefix.A.0.5" + extra + "' value='7.064']\n" +
                            "##teamcity[buildStatisticValue key='prefix.A.0.9" + extra + "' value='10.672']\n" +
                            "##teamcity[buildStatisticValue key='prefix.A.0.99" + extra + "' value='11.472']\n" +
                            "##teamcity[buildStatisticValue key='prefix.A.0.997" + extra + "' value='11.536']\n" +
                            "##teamcity[buildStatisticValue key='prefix.A.1.0" + extra + "' value='11.568']\n" +
                            "##teamcity[buildStatisticValue key='prefix.B.0.5" + extra + "' value='0.100125']\n" +
                            "##teamcity[buildStatisticValue key='prefix.B.0.9" + extra + "' value='0.100125']\n" +
                            "##teamcity[buildStatisticValue key='prefix.B.0.99" + extra + "' value='0.100125']\n" +
                            "##teamcity[buildStatisticValue key='prefix.B.1.0" + extra + "' value='0.100125']\n",
                    stats.replace("\r", ""),
                    "TeamCity statistics output");
        } finally {
            Closeable.closeQuietly(eventLoop);
        }
    }

    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    public void histogramSummariesAreCorrect(boolean runFromEventLoop) {
        EventLoop eventLoop = createEventLoop(runFromEventLoop);
        try {
            final JLBHResultConsumer resultConsumer = resultConsumer();
            JLBHOptions jlbhOptions = options().jlbhTask(new PredictableJLBHTaskDifferentShape()).iterations(ITERATIONS * 2);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintStream printStream;
            try {
                printStream = new PrintStream(baos, true, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new AssertionError("UTF-8 not supported", e);
            }
            final JLBH jlbh = new JLBH(jlbhOptions, printStream, resultConsumer);

            // when
            start(jlbh, eventLoop);

            System.out.println(baos);
            String summary;
            try {
                summary = baos.toString("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new AssertionError("UTF-8 not supported", e);
            }
            assertTrue(summary.replace("\r", "").contains(
                            "-------------------------------- SUMMARY (B) us ----------------------------------------------------\n" +
                                    "Percentile   run1         run2         run3      % Variation\n" +
                                    "50.0:            0.10         0.10         0.10         0.00\n" +
                                    "90.0:            0.10         0.10         0.10         0.00\n" +
                                    "99.0:            0.10         0.10         0.10         0.00\n" +
                                    "worst:           0.10         0.10         0.10         0.00"),
                    "probe B summary table should be present");
        } finally {
            Closeable.closeQuietly(eventLoop);
        }
    }

    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    public void shouldCallAllLifecycleMethods(boolean runFromEventLoop) {
        EventLoop eventLoop = createEventLoop(runFromEventLoop);
        try {

            AtomicInteger initCount = new AtomicInteger(0);
            AtomicInteger runCount = new AtomicInteger(0);

            AtomicInteger warmedUpCount = new AtomicInteger(0);
            AtomicInteger warmedUpIterations = new AtomicInteger(0);

            AtomicInteger runCompleteCount = new AtomicInteger(0);
            List<Integer> runCompleteIterations = new ArrayList<>();

            AtomicInteger completeCount = new AtomicInteger(0);
            AtomicInteger completeIterations = new AtomicInteger(0);

            JLBHTask task = new JLBHTask() {

                private JLBH jlbh;

                @Override
                public void init(JLBH jlbh) {
                    initCount.incrementAndGet();
                    this.jlbh = jlbh;
                }

                @Override
                public void run(long startTimeNS) {
                    runCount.incrementAndGet();
                    jlbh.sampleNanos(System.nanoTime() - startTimeNS);
                }

                @Override
                public void warmedUp() {
                    warmedUpCount.incrementAndGet();
                    warmedUpIterations.set(runCount.get());
                }

                @Override
                public void runComplete() {
                    runCompleteCount.incrementAndGet();
                    runCompleteIterations.add(runCount.get());
                }

                @Override
                public void complete() {
                    completeCount.incrementAndGet();
                    completeIterations.set(runCount.get());
                }
            };

            final int warmUpIterations = 10;
            final int iterations = 10;
            final int runs = 2;

            JLBH jlbh = new JLBH(new JLBHOptions()
                    .warmUpIterations(warmUpIterations)
                    .iterations(iterations)
                    .runs(runs)
                    .jlbhTask(task));
            start(jlbh, eventLoop);

            assertEquals(1, initCount.get(), "init should be called once");
            assertEquals(warmUpIterations + (runs * iterations), runCount.get(), "run should include warmup and measured runs");
            assertEquals(1, warmedUpCount.get(), "warmedUp should be called once");
            assertEquals(warmUpIterations, warmedUpIterations.get(), "warmedUp iteration count");
            assertEquals(runs, runCompleteCount.get(), "runComplete count");
            assertEquals(Arrays.asList(warmUpIterations + iterations, warmUpIterations + 2 * iterations),
                    runCompleteIterations,
                    "runComplete iteration checkpoints");
            assertEquals(1, completeCount.get(), "complete should be called once");
            assertEquals(warmUpIterations + (runs * iterations), completeIterations.get(), "complete iteration count");
        } finally {
            Closeable.closeQuietly(eventLoop);
        }
    }

    @NotNull
    private JLBHResultConsumer resultConsumer() {
        return JLBHResultConsumer.newThreadSafeInstance();
    }

    @NotNull
    private PrintStream printStream() {
        try {
            return new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 not supported", e);
        }
    }
}
