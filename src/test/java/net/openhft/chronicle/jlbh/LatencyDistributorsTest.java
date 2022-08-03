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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class LatencyDistributorsTest {

    final LatencyDistributor ld;

    public LatencyDistributorsTest(LatencyDistributor ld) {
        this.ld = ld;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.stream(LatencyDistributors.values())
                .map(x -> new Object[]{x})
                .collect(Collectors.toList());
    }

    @Test
    public void averageOk() {
        long base = 10_000; // e.g. 100_000/s
        long sum = 0;
        final int count = 100_000;
        for (int i = 0; i < count; i++)
            sum += ld.apply(base);
        assertEquals(count * base, sum, sum / 50.0);
    }
}