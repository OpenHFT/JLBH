/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class OptionsPropertyTest {
    @Test void propertyDefaultsAndExplicitOverrides() throws Exception {
        run("DEFAULT=false,100,10000");
        run("DEFAULT=true,250,1234", "-Djlbh.disableGcAfterWarmup=true", "-Djlbh.pauseAfterWarmupMS=250", "-Djlbh.gcAfterWarmupTimeoutMS=1234");
        run("DEFAULT=false,0,10000", "-Djlbh.disableGcAfterWarmup=false", "-Djlbh.pauseAfterWarmupMS=0");
    }
    @Test void invalidDurationsRefuseConstruction() throws Exception {
        run("INVALID", "-Djlbh.pauseAfterWarmupMS=-1");
        run("INVALID", "-Djlbh.gcAfterWarmupTimeoutMS=0");
    }
    public static void main(String[] args) {
        try {
            JLBHOptions options = new JLBHOptions();
            System.out.println("DEFAULT=" + options.disableGcAfterWarmup + "," + options.pauseAfterWarmupMS + "," + options.gcAfterWarmupTimeoutMS);
            options.disableGcAfterWarmup(false).pauseAfterWarmupMS(5).gcAfterWarmupTimeoutMS(20);
            if (options.disableGcAfterWarmup || options.pauseAfterWarmupMS != 5 || options.gcAfterWarmupTimeoutMS != 20) throw new AssertionError("override");
        } catch (IllegalArgumentException expected) {
            System.out.println("INVALID");
            System.exit(2);
        }
    }
    private static String run(String expected, String... properties) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(java.util.Arrays.asList(properties));
        if (!System.getProperty("java.specification.version").startsWith("1.")) {
        command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        command.add("--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED");
        }
        command.add("-cp");
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(OptionsPropertyTest.class.getName());
        java.nio.file.Path log = java.nio.file.Files.createTempFile("jlbh-options", ".log");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        for (String key : new String[]{"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) builder.environment().remove(key);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            String output = new String(java.nio.file.Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.contains(expected), output);
            assertEquals(expected.equals("INVALID") ? 2 : 0, process.exitValue(), output);
            return output;
        } finally {
            process.destroyForcibly();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            java.nio.file.Files.deleteIfExists(log);
        }
    }
}
