/*
 * Copyright 2013-2026 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import org.jetbrains.annotations.NotNull;

/**
 * <p>A minimal command line example demonstrating how to configure and invoke the
 * Java Latency Benchmark Harness (JLBH). The benchmark performs a {@code Math.sin}
 * calculation on each iteration and after {@code 160_000} calls deliberately
 * pauses for 100&nbsp;ms to create a visible latency spike. The {@link JLBHOptions}
 * used in {@link #main(String[])} run two iterations with a target throughput of
 * 500&nbsp;000 operations per second and account for coordinated omission.</p>
 *
 * <p>Run the class from your IDE or via Maven:</p>
 *
 * <pre>{@code
 * mvn -q test-compile exec:java \
 *   -Dexec.classpathScope=test \
 *   -Dexec.mainClass=net.openhft.chronicle.jlbh.ExampleJLBHMain
 * }</pre>
 *
 * <p>The harness will print percentile summaries for each run to {@code System.out}.</p>
 */
public class ExampleJLBHMain implements JLBHTask {
    private int count = 0;
    private double sin;
    //private NanoSampler nanoSamplerSin;
    //private NanoSampler nanoSamplerWait;
    private JLBH lth;

    public static void main(String[] args) {
        @NotNull JLBHOptions jlbhOptions = new JLBHOptions()
                .warmUpIterations(Jvm.compileThreshold() * 2)
                .iterations(10_000_001)
                .throughput(500_000).accountForCoordinatedOmission(true)
                .runs(2).accountForCoordinatedOmission(true)
                .jlbhTask(new ExampleJLBHMain());
        new JLBH(jlbhOptions).start();
    }

    @Override
    public void run(long startTimeNS) {
        count++;
        if (count == 160_000) {
            System.out.println("PAUSE");
            //long now = System.nanoTime();
            Jvm.pause(100);
            //nanoSamplerWait.sampleNanos(System.nanoTime()-now);
        }

        long now = System.nanoTime();
        sin = Math.sin(count);
        //nanoSamplerSin.sampleNanos(System.nanoTime()-now);

        lth.sample(System.nanoTime() - startTimeNS);
    }

    @Override
    public void init(JLBH lth) {

        this.lth = lth;
        //nanoSamplerSin = lth.addProbe("sin");
        //nanoSamplerWait = lth.addProbe("busyWait");
    }

    @Override
    public void complete() {
    }
}
