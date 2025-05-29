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

import net.openhft.chronicle.core.Jvm;
import org.jetbrains.annotations.NotNull;

public class ExampleJLBHMain implements JLBHTask {
    int count = 0;
    double sin;
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
