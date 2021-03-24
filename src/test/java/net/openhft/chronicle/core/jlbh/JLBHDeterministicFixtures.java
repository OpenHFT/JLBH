package net.openhft.chronicle.core.jlbh;

import net.openhft.chronicle.core.util.NanoSampler;

public class JLBHDeterministicFixtures {

    static final int WARM_UP_ITERATIONS = 500;
    static final int ITERATIONS = 9_000;
    static final int THROUGHPUT = 1_000_000;
    static final int RUNS = 3;
    private final static String expectedOutput = "Warm up complete ...\n" +
            "-------------------------------- BENCHMARK RESULTS (RUN 1) --------------------------------------------------------\n" +
            "Run time: ...s, distribution: NORMAL\n" +
            "Correcting for co-ordinated:true\n" +
            "Target throughput:1000000/s = 1 message every 1us\n" +
            "End to End: (9,000)                             50/90 99/99.9 99.99 - worst was 8.08 / 11.68  12.45 / 12.58  12.58 - 12.58\n" +
            "A (9,001)                                       50/90 99/99.9 99.99 - worst was 7.06 / 10.66  11.49 / 11.55  11.55 - 11.55\n" +
            "B (9,001)                                       50/90 99/99.9 99.99 - worst was 0.100 / 0.100  0.100 / 0.100  0.100 - 0.100\n" +
            "OS Jitter ...\n" +
            "-------------------------------------------------------------------------------------------------------------------\n" +
            "-------------------------------- BENCHMARK RESULTS (RUN 2) --------------------------------------------------------\n" +
            "Run time: ...s, distribution: NORMAL\n" +
            "Correcting for co-ordinated:true\n" +
            "Target throughput:1000000/s = 1 message every 1us\n" +
            "End to End: (9,000)                             50/90 99/99.9 99.99 - worst was 8.08 / 11.68  12.45 / 12.58  12.58 - 12.58\n" +
            "A (9,000)                                       50/90 99/99.9 99.99 - worst was 7.06 / 10.66  11.49 / 11.55  11.55 - 11.55\n" +
            "B (9,000)                                       50/90 99/99.9 99.99 - worst was 0.100 / 0.100  0.100 / 0.100  0.100 - 0.100\n" +
            "OS Jitter ...\n" +
            "-------------------------------------------------------------------------------------------------------------------\n" +
            "-------------------------------- BENCHMARK RESULTS (RUN 3) --------------------------------------------------------\n" +
            "Run time: ...s, distribution: NORMAL\n" +
            "Correcting for co-ordinated:true\n" +
            "Target throughput:1000000/s = 1 message every 1us\n" +
            "End to End: (9,000)                             50/90 99/99.9 99.99 - worst was 6.10 / 9.70  10.53 / 10.59  10.59 - 10.59\n" +
            "A (9,000)                                       50/90 99/99.9 99.99 - worst was 5.10 / 8.74  9.50 / 9.57  9.63 - 9.63\n" +
            "B (9,000)                                       50/90 99/99.9 99.99 - worst was 0.100 / 0.100  0.100 / 0.100  0.100 - 0.100\n" +
            "OS Jitter ...\n" +
            "-------------------------------------------------------------------------------------------------------------------\n" +
            "-------------------------------- SUMMARY (end to end) -----------------------------------------------------------\n" +
            "Percentile   run1         run2         run3      % Variation\n" +
            "50:             8.08         8.08         6.10        17.83\n" +
            "90:            11.68        11.68         9.70        12.00\n" +
            "99:            12.45        12.45        10.53        10.84\n" +
            "worst:         12.58        12.58        10.59        11.10\n" +
            "-------------------------------------------------------------------------------------------------------------------\n" +
            "-------------------------------- SUMMARY (A) -----------------------------------------------------------\n" +
            "Percentile   run1         run2         run3      % Variation\n" +
            "50:             7.06         7.06         5.10        20.32\n" +
            "90:            10.66        10.66         8.74        12.78\n" +
            "99:            11.49        11.49         9.50        12.22\n" +
            "worst:         11.55        11.55         9.63        11.73\n" +
            "-------------------------------------------------------------------------------------------------------------------\n" +
            "-------------------------------- SUMMARY (B) -----------------------------------------------------------\n" +
            "Percentile   run1         run2         run3      % Variation\n" +
            "50:             0.10         0.10         0.10         0.00\n" +
            "90:             0.10         0.10         0.10         0.00\n" +
            "99:             0.10         0.10         0.10         0.00\n" +
            "worst:          0.10         0.10         0.10         0.00\n" +
            "-------------------------------------------------------------------------------------------------------------------\n";

    static JLBHOptions options() {
        return new JLBHOptions()
                .warmUpIterations(WARM_UP_ITERATIONS)
                .iterations(ITERATIONS)
                .throughput(THROUGHPUT).accountForCoordinatedOmission(true)
                .runs(RUNS).accountForCoordinatedOmission(true)
                .jlbhTask(new PredictableJLBHTask());
    }

    static String predictableTaskExpectedResult() {
        return expectedOutput;
    }

    static String withoutNonDeterministicFields(String content) {
        return content
                .replaceAll("Warm up complete \\(\\d+ iterations took .+s\\)", "Warm up complete ...")
                .replaceAll("OS Jitter .+", "OS Jitter ...")
                .replaceAll("Run time: .+s,", "Run time: ...s,")
                .replaceAll("\r", "");
    }

    static class PredictableJLBHTask implements JLBHTask {

        protected int nanoTime = 1_000_000;
        private int latency;
        private JLBH lth;
        private NanoSampler additionalSamplerA;
        private NanoSampler additionalSamplerB;

        @Override
        public void run(long startTimeNS) {
            latency = 1000 + (++this.nanoTime % 11567);
            lth.sample(latency);
            additionalSamplerA.sampleNanos(latency - 1000);
            if (sampleB())
                additionalSamplerB.sampleNanos(100);
        }

        @Override
        public void init(JLBH lth) {
            this.lth = lth;
            this.additionalSamplerA = lth.addProbe("A");
            this.additionalSamplerB = lth.addProbe("B");
        }

        @Override
        public void complete() {
        }

        protected boolean sampleB() {
            return true;
        }
    }

    static class PredictableJLBHTaskDifferentShape extends PredictableJLBHTask {
        @Override
        protected boolean sampleB() {
            return nanoTime % 10 == 0;
        }
    }

    static class FixedLatencyJLBHTask implements JLBHTask {

        private final int latency;
        private JLBH lth;
        private NanoSampler additionalSamplerA;
        private NanoSampler additionalSamplerB;

        public FixedLatencyJLBHTask(int latency) {
            this.latency = latency;
        }

        @Override
        public void run(long startTimeNS) {
            lth.sample(latency);
            additionalSamplerA.sampleNanos(latency);
            additionalSamplerB.sampleNanos(latency);
        }

        @Override
        public void init(JLBH lth) {
            this.lth = lth;
            this.additionalSamplerA = lth.addProbe("A");
            this.additionalSamplerB = lth.addProbe("B");
        }

        @Override
        public void complete() {
        }
    }
}
