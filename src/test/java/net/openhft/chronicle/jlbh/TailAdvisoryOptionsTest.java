/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

public class TailAdvisoryOptionsTest {
    public static void main(String[] args) {
        JLBHOptions options = new JLBHOptions();
        System.out.println("DISABLED=" + options.disableTailLatencyAdvisory);
        assertOverride(options, true); assertOverride(options, false);
    }
    private static void assertOverride(JLBHOptions options, boolean disabled) {
        if (options.disableTailLatencyAdvisory(disabled) != options || options.disableTailLatencyAdvisory != disabled)
            throw new AssertionError("explicit override ignored");
    }
    @Test void defaultEnabledAndPropertyDisableWithExplicitOverrides() throws Exception {
        run("false", null); run("false", "false"); run("true", "true");
    }
    private void run(String expected, String property) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        if (property != null) command.add("-Djlbh.disableTailLatencyAdvisory=" + property);
        if (!System.getProperty("java.specification.version").startsWith("1.")) {
            command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
            command.add("--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED");
        }
        command.add("-cp"); command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(getClass().getName());
        Path log = Files.createTempFile("tail-advisory-options", ".log"); Process child = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            for (String key : Arrays.asList("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(key);
            child = builder.start(); assertTrue(child.waitFor(15, TimeUnit.SECONDS));
            String out = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue(), out); assertTrue(out.contains("DISABLED=" + expected), out);
        } finally {
            if (child != null && child.isAlive()) child.destroyForcibly().waitFor();
            Files.deleteIfExists(log);
        }
    }
}
