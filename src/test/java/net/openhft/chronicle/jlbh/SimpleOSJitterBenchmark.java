/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.NanoSampler;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class SimpleOSJitterBenchmark implements JLBHTask {

    private JLBH jlbh;
    private NanoSampler myProbe;

    public static void main(String[] args) {
        //Create the JLBH options you require for the benchmark
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(20_000)
                .iterations(1_000_000)
                .throughput(100_000)
                .recordOSJitter(true)
                .runs(4)
                .jlbhTask(new SimpleOSJitterBenchmark());
        new JLBH(lth,System.out, jlbhResult -> {
            jlbhResult.osJitter().ifPresent(probeResult -> {
                JLBHResult.RunResult runResult = probeResult.summaryOfLastRun();
                System.out.println("runResult = " + runResult);
                List<JLBHResult.RunResult> runResults = probeResult.eachRunSummary();
                for (JLBHResult.RunResult result : runResults) {
                    System.out.println("eachRunSummary = " + result);
                }
            });

        }).start();
    }

    @Override
    public void init(JLBH jlbh) {
        this.jlbh = jlbh;
        myProbe = jlbh.addProbe("MyProbe");
    }

    @Override
    public void run(long startTimeNS) {
        // Use System.nanoTime() here if you only want to measure the local work.
        // long start = System.nanoTime();          // (1)
        // (2)
        LockSupport.parkNanos(1);

        final long delta = System.nanoTime() - startTimeNS;
        jlbh.sample(delta);
        myProbe.sampleNanos(delta);
    }
}
