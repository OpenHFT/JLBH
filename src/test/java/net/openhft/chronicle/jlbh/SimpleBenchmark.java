/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import java.util.concurrent.locks.LockSupport;

public class SimpleBenchmark implements JLBHTask {

    private JLBH jlbh;

    public static void main(String[] args) {
        //Create the JLBH options you require for the benchmark
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(20_000)
                .iterations(1_000_000)
                .throughput(100_000)
                // enable this if you need to compensate for coordinated omission
                // .accountForCoordinatedOmission(true)
                .runs(2)
                .jlbhTask(new SimpleBenchmark());
        new JLBH(lth).start();
    }

    @Override
    public void init(JLBH jlbh) {
        this.jlbh = jlbh;
    }

    @Override
    public void run(long startTimeNS) {
        // Use System.nanoTime() here if you only want to include work done inside this method.
        // long start = System.nanoTime();          // (1)
        // (2)
        LockSupport.parkNanos(1);

        final long delta = System.nanoTime() - startTimeNS;
        jlbh.sample(delta);
    }
}
