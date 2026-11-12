/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.threads.MediumEventLoop;
import net.openhft.chronicle.threads.Pauser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JLBHZeroWarmupTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "0,true", "1,true"})
    void validSamplingTaskCompletesInBothModes(int warmup, boolean eventLoop) throws Exception {
        Path output = temporary.resolve("child.log");
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
            if (!argument.startsWith("-agentlib:jdwp"))
                command.add(argument);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Probe.class.getName());
        command.add(Integer.toString(warmup));
        command.add(Boolean.toString(eventLoop));
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean finished;
        try {
            finished = child.waitFor(5, TimeUnit.SECONDS);
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS), "owned child did not stop");
            }
        }
        String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertTrue(finished, text);
        assertEquals(0, child.exitValue(), text);
        assertTrue(text.contains("PROBE_PASS"), text);
    }

    @Test
    void rejectsNegativeWarmupBeforeStarting() {
        JLBHOptions options = new JLBHOptions();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> options.warmUpIterations(-1));
        assertTrue(failure.getMessage().contains("warmUpIterations"));
    }

    public static final class Probe implements JLBHTask {
        private JLBH jlbh;
        private int calls;
        private int warmedCalls = -1;
        private int warmedNotifications;
        private int runs;
        private final CountDownLatch complete = new CountDownLatch(1);

        public void init(JLBH jlbh) { this.jlbh = jlbh; }
        public void run(long start) { calls++; jlbh.sample(100); }
        public void warmedUp() { warmedCalls = calls; warmedNotifications++; }
        public void runComplete() { runs++; }
        public void complete() { complete.countDown(); }

        public static void main(String[] args) throws Exception {
            int warmup = Integer.parseInt(args[0]);
            Probe task = new Probe();
            JLBH harness = new JLBH(new JLBHOptions().warmUpIterations(warmup)
                    .iterations(2).runs(1).throughput(100_000).recordOSJitter(false)
                    .acquireLock(() -> null).timeout(0).jlbhTask(task));
            if (Boolean.parseBoolean(args[1])) {
                try (MediumEventLoop loop = new MediumEventLoop(null, "zero-warmup-probe", Pauser.busy(), true, null)) {
                    loop.start();
                    harness.eventLoopHandler(loop);
                    assertTrue(task.complete.await(2, TimeUnit.SECONDS), "completion missing");
                }
            } else {
                harness.start();
            }
            assertEquals(warmup + 2, task.calls);
            assertEquals(warmup, task.warmedCalls);
            assertEquals(1, task.warmedNotifications);
            assertEquals(1, task.runs);
            assertEquals(0, task.complete.getCount());
            assertEquals(1, harness.percentileRuns().size());
            System.out.println("PROBE_PASS warmup=" + warmup + " eventLoop=" + args[1]
                    + " calls=" + task.calls + " warmedCalls=" + task.warmedCalls);
        }
    }
}
