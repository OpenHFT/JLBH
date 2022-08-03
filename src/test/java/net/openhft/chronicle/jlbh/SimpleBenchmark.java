/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
//                .accountForCoordinatedOmission(true)
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
//        long start = System.nanoTime();          // (1)
        long start = startTimeNS;                       // (2)
        LockSupport.parkNanos(1);

        final long delta = System.nanoTime() - start;
        jlbh.sample(delta);
    }
}