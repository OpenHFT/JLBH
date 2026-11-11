/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Contains a stuck benchmark even if its scheduling thread ignores interruption. */
class JLBHProgressOutputTest {
    @ParameterizedTest(name = "event loop {0}")
    @ValueSource(booleans = {false, true})
    void progressLinesGoToTheConfiguredPrintStream(boolean eventLoop) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!argument.startsWith("-javaagent:") && !argument.startsWith("-agentlib:"))
                command.add(argument);
        }
        command.add("-cp");
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(ProgressOutputProcess.class.getName());
        command.add(Boolean.toString(eventLoop));
        Path log = Files.createTempFile("jlbh-progress-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            boolean exited = process.waitFor(30, TimeUnit.SECONDS);
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertTrue(exited, "Progress child timed out: " + output);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("progress routing verified"), output);
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Progress child did not stop");
            Files.deleteIfExists(log);
        }
    }
}
