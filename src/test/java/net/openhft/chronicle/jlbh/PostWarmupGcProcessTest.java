/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(40)
class PostWarmupGcProcessTest {
    @ParameterizedTest
    @ValueSource(strings = {"-XX:+UseG1GC", "-XX:+UseSerialGC", "-XX:+UseParallelGC"})
    void waitsForRealGcBeforePauseAndMeasurement(String collector) throws Exception {
        for (boolean eventLoop : new boolean[]{false, true}) {
            String output = run(0, eventLoop, false, collector);
            assertTrue(output.indexOf("LAST_WARMUP_COMPLETED") < output.indexOf("Requesting post-warmup"), output);
            int completed = output.indexOf("Post-warmup explicit major GC completion observed");
            int pause = output.indexOf("Pausing after warmup for 100 ms");
            assertTrue(completed >= 0 && completed < pause, output);
            assertTrue(pause < output.indexOf("WARMED_UP_CALLBACK"), output);
            assertTrue(output.contains("MEASURED=20"), output);
            assertEquals(1, output.split("Requesting post-warmup", -1).length - 1, output);
        }
    }

    @Test
    void disabledExplicitGcRefusesMeasurementUnlessOptedOut() throws Exception {
        for (boolean eventLoop : new boolean[]{false, true}) {
            String failed = run(1, eventLoop, false, "-XX:+DisableExplicitGC");
            assertTrue(failed.contains("GC completion not observed"), failed);
            assertTrue(failed.contains("REFUSED measured=0 results=0 published=0"), failed);
            assertFalse(failed.contains("WARMED_UP_CALLBACK"), failed);
            assertFalse(failed.contains("Pausing after warmup"), failed);
            String optedOut = run(0, eventLoop, true, "-XX:+DisableExplicitGC");
            assertFalse(optedOut.contains("Requesting post-warmup"), optedOut);
            assertTrue(optedOut.contains("Pausing after warmup for 100 ms"), optedOut);
            assertTrue(optedOut.contains("MEASURED=20"), optedOut);
        }
    }

    @Test
    void concurrentG1PausesAreNotMistakenForCompletedMajorCollection() throws Exception {
        String failed = run(1, false, false, "-XX:+UseG1GC", "-XX:+ExplicitGCInvokesConcurrent");
        assertTrue(failed.contains("GC completion not observed"), failed);
        assertTrue(failed.contains("REFUSED measured=0 results=0 published=0"), failed);
    }

    private static String run(int expected, boolean eventLoop, boolean optOut, String... flags) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(Arrays.asList(flags));
        command.add("-Djvm.resource.tracing=false");
        if (!System.getProperty("java.specification.version").startsWith("1.")) {
            command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
            command.add("--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED");
        }
        command.add("-cp");
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(WarmupGcProcess.class.getName());
        command.add(Boolean.toString(eventLoop));
        command.add(Boolean.toString(optOut));
        command.add(Boolean.toString(expected != 0));
        Path log = Files.createTempFile("jlbh-gc-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "child timed out: " + log);
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertEquals(expected, process.exitValue(), output);
            return output;
        } finally {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            Files.deleteIfExists(log);
        }
    }
}
