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

import static java.util.concurrent.TimeUnit.SECONDS;

public class NothingBenchmark implements JLBHTask {
    private JLBH jlbh;

    public static void main(String[] args) {
        int iterations = 10; // 10, 50, 100
        JLBHOptions lth = new JLBHOptions()
                .warmUpIterations(iterations)
                .iterations(iterations)
                .throughput(5, SECONDS)
                .runs(3)
                .recordOSJitter(true).accountForCoordinatedOmission(true)
                .jlbhTask(new NothingBenchmark());
        new JLBH(lth).start();
    }

    @Override
    public void run(long startTimeNS) {
        jlbh.sample(System.nanoTime() - startTimeNS);
    }

    @Override
    public void init(JLBH jlbh) {
        this.jlbh = jlbh;
    }
}