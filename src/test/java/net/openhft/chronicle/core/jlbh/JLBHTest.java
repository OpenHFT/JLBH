package net.openhft.chronicle.core.jlbh;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.openhft.chronicle.core.jlbh.JLBHDeterministicFixtures.*;
import static net.openhft.chronicle.core.jlbh.JLBHResult.RunResult.Percentile.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class JLBHTest {

    @Test
    public void shouldWriteResultToTheOutputProvided() {
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

    private Set<Duration> percentilesUniqueLatenciesIn(JLBHResult.RunResult summaryA) {
        return new HashSet<>(summaryA.percentiles().values());
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