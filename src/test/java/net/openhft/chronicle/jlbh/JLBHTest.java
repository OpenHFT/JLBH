package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.OS;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.openhft.chronicle.jlbh.JLBHDeterministicFixtures.*;
import static net.openhft.chronicle.jlbh.JLBHResult.RunResult.Percentile.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class JLBHTest {

    @Test
    public void shouldWriteResultToTheOutputProvided() {
        Assume.assumeTrue(!OS.isMacOSX());

        // given
        final OutputStream outputStream = new ByteArrayOutputStream();
        final JLBH jlbh = new JLBH(options(), new PrintStream(outputStream), resultConsumer());

        // when
        jlbh.start();

        // then
        String result = outputStream.toString().replace("\r", "");
        assertThat(result, containsString("OS Jitter"));
        assertThat(result, containsString("Warm up complete (500 iterations took "));
        assertThat(result, containsString("Run time: "));
        assertEquals(withoutNonDeterministicFields(predictableTaskExpectedResult()), withoutNonDeterministicFields(result));
    }

    @Test
    /*
     * To understand the data, please go to JLBHDeterministicFixtures
     * and JLBHDeterministicFixtures::expectedOutput in particular
     */
    public void shouldProvideResultData() {
        Assume.assumeTrue(!OS.isMacOSX());
        // given
        final JLBHResultConsumer resultConsumer = resultConsumer();
        final JLBH jlbh = new JLBH(options(), printStream(), resultConsumer);

        // when
        jlbh.start();

        // then
        final JLBHResult.RunResult lastRunSummary = resultConsumer.get().endToEnd().summaryOfLastRun();
        assertEquals(6_106L, lastRunSummary.get50thPercentile().toNanos(), 20);
        assertEquals(9_708L, lastRunSummary.get90thPercentile().toNanos(), 20);
        assertEquals(10_516L, lastRunSummary.get99thPercentile().toNanos(), 20);
        assertEquals(10_604L, lastRunSummary.getWorst().toNanos(), 20);
        assertEquals(lastRunSummary.get50thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_50TH));
        assertEquals(lastRunSummary.get90thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_90TH));
        assertEquals(lastRunSummary.get99thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_99TH));
        assertEquals(lastRunSummary.get999thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_99_9TH));
        assertEquals(lastRunSummary.get9999thPercentile(), lastRunSummary.percentiles().get(PERCENTILE_99_99TH));
        assertNull(lastRunSummary.percentiles().get(PERCENTILE_99_999TH));
        assertEquals(lastRunSummary.getWorst(), lastRunSummary.percentiles().get(WORST));

        final List<JLBHResult.RunResult> summaryOfEachRun = resultConsumer.get().endToEnd().eachRunSummary();
        assertEquals(3, summaryOfEachRun.size());
        assertThat(summaryOfEachRun.get(0), not(equalTo(lastRunSummary)));
        assertThat(summaryOfEachRun.get(1), not(equalTo(lastRunSummary)));
        assertEquals(lastRunSummary, summaryOfEachRun.get(2));

        assertTrue(resultConsumer.get().probe("A").isPresent());
        assertTrue(resultConsumer.get().probe("B").isPresent());
        assertFalse(resultConsumer.get().probe("C").isPresent());

        final JLBHResult.RunResult probeALastRunSummary = resultConsumer.get().probe("A").get().summaryOfLastRun();
        assertEquals(5_106L, probeALastRunSummary.get50thPercentile().toNanos(), 20);
        assertEquals(8_708L, probeALastRunSummary.get90thPercentile().toNanos(), 30);
        assertEquals(9_516L, probeALastRunSummary.get99thPercentile().toNanos(), 30);
//        assertEquals(9_604L, probeALastRunSummary.get9999thPercentile().toNanos());
        assertEquals(9_604L, probeALastRunSummary.getWorst().toNanos(), 30);
        assertEquals(probeALastRunSummary.get50thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_50TH));
        assertEquals(probeALastRunSummary.get90thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_90TH));
        assertEquals(probeALastRunSummary.get99thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_99TH));
        assertEquals(probeALastRunSummary.get999thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_99_9TH));
        assertEquals(probeALastRunSummary.get9999thPercentile(), probeALastRunSummary.percentiles().get(PERCENTILE_99_99TH));
        assertNull(probeALastRunSummary.percentiles().get(PERCENTILE_99_999TH));
        assertEquals(probeALastRunSummary.getWorst(), probeALastRunSummary.percentiles().get(WORST));

        final List<JLBHResult.RunResult> summaryOfProbeAEachRun = resultConsumer.get().probe("A").get().eachRunSummary();
        assertEquals(3, summaryOfProbeAEachRun.size());
        assertThat(summaryOfProbeAEachRun.get(0), not(equalTo(probeALastRunSummary)));
        assertThat(summaryOfProbeAEachRun.get(1), not(equalTo(probeALastRunSummary)));
        assertEquals(probeALastRunSummary, summaryOfProbeAEachRun.get(2));
    }

    @Test
    public void shouldProvideResultDataEvenIfProbesDoNotProvideSameShapedData() {
        Assume.assumeTrue(!OS.isMacOSX());
        // given
        final JLBHResultConsumer resultConsumer = resultConsumer();
        JLBHOptions jlbhOptions = options().jlbhTask(new PredictableJLBHTaskDifferentShape()).iterations(ITERATIONS * 2);
        final JLBH jlbh = new JLBH(jlbhOptions, printStream(), resultConsumer);

        // when
        jlbh.start();

        // then
        final JLBHResult.RunResult probeALastRunSummary = resultConsumer.get().probe("A").get().summaryOfLastRun();
        assertEquals(5, probeALastRunSummary.percentiles().size());

        final JLBHResult.RunResult probeBLastRunSummary = resultConsumer.get().probe("B").get().summaryOfLastRun();
        assertEquals(4, probeBLastRunSummary.percentiles().size());
    }

    @Test
    public void teamCityHelper() {
        Assume.assumeTrue(!OS.isMacOSX());
        // given
        final JLBHResultConsumer resultConsumer = resultConsumer();
        JLBHOptions jlbhOptions = options().jlbhTask(new PredictableJLBHTaskDifferentShape()).iterations(ITERATIONS * 2);
        final JLBH jlbh = new JLBH(jlbhOptions, printStream(), resultConsumer);

        // when
        jlbh.start();

        // then
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final PrintStream printStream = new PrintStream(baos)) {
            TeamCityHelper.teamCityStatsLastRun("prefix", jlbh, jlbhOptions.iterations, printStream);
        }
        Assert.assertEquals("##teamcity[buildStatisticValue key='prefix.end-to-end.0.5' value='8.072']\n" +
                "##teamcity[buildStatisticValue key='prefix.end-to-end.0.9' value='11.664']\n" +
                "##teamcity[buildStatisticValue key='prefix.end-to-end.0.99' value='12.464']\n" +
                "##teamcity[buildStatisticValue key='prefix.end-to-end.0.997' value='12.528']\n" +
                "##teamcity[buildStatisticValue key='prefix.end-to-end.1.0' value='12.56']\n" +
                "##teamcity[buildStatisticValue key='prefix.A.0.5' value='7.064']\n" +
                "##teamcity[buildStatisticValue key='prefix.A.0.9' value='10.672']\n" +
                "##teamcity[buildStatisticValue key='prefix.A.0.99' value='11.472']\n" +
                "##teamcity[buildStatisticValue key='prefix.A.0.997' value='11.536']\n" +
                "##teamcity[buildStatisticValue key='prefix.A.1.0' value='11.568']\n" +
                "##teamcity[buildStatisticValue key='prefix.B.0.5' value='0.100125']\n" +
                "##teamcity[buildStatisticValue key='prefix.B.0.9' value='0.100125']\n" +
                "##teamcity[buildStatisticValue key='prefix.B.0.99' value='0.100125']\n" +
                "##teamcity[buildStatisticValue key='prefix.B.1.0' value='0.100125']\n", baos.toString().replace("\r", ""));
    }

    @Test
    public void shouldCallAllLifecycleMethods() {
        Assume.assumeTrue(!OS.isMacOSX());
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
        new JLBH(new JLBHOptions()
                .warmUpIterations(warmUpIterations)
                .iterations(iterations)
                .runs(runs)
                .jlbhTask(task)).start();
        assertEquals(1, initCount.get());
        assertEquals(warmUpIterations + (runs * iterations), runCount.get());
        assertEquals(1, warmedUpCount.get());
        assertEquals(warmUpIterations, warmedUpIterations.get());
        assertEquals(runs, runCompleteCount.get());
        assertEquals(
                Arrays.asList(
                        warmUpIterations + iterations,
                        warmUpIterations + 2 * iterations),
                runCompleteIterations);
        assertEquals(1, completeCount.get());
        assertEquals(warmUpIterations + (runs * iterations), completeIterations.get());
    }

    @NotNull
    private JLBHResultConsumer resultConsumer() {
        return JLBHResultConsumer.newThreadSafeInstance();
    }

    @NotNull
    private PrintStream printStream() {
        return new PrintStream(new ByteArrayOutputStream());
    }
}