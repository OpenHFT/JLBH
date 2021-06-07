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
        assertEquals(count * base, sum, sum / 100.0);
    }
}