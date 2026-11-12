/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-process behaviour of the opt-in host lock ({@code -Djlbh.hostLock=true}), using child JVMs that run
 * {@link HostLockChild}. Each child uses a private lock file, so this test never touches the host's real lock.
 */
class JLBHHostLockProcessTest {
    private static final Pattern INIT = Pattern.compile("INIT pid=(\\d+) at=(\\d+)");
    private static final Pattern COMPLETE = Pattern.compile("COMPLETE at=(\\d+)");

    @TempDir
    Path folder;
    private final List<Child> children = new ArrayList<>();

    @AfterEach
    void destroyChildren() throws InterruptedException {
        for (Child child : children)
            child.process.destroyForcibly();
        // @TempDir cleanup must not race a still-running holder or its output reader.
        for (Child child : children)
            child.waitFor(10);
    }

    @Test
    @Timeout(60)
    void cleanupWaitsForTheHolderAndOutputReader() throws Exception {
        Child holder = start(folder.resolve("host.lock").toFile(), 60_000, 60);
        assertNotNull(holder.awaitLine(INIT, 30), holder.output());
        destroyChildren();
        assertFalse(holder.process.isAlive());
        assertFalse(holder.reader.isAlive());
        try (java.util.stream.Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files::iterator)
                Files.delete(file);
        }
        Files.delete(folder);
    }

    @Test
    @Timeout(120)
    void twoJvmsSerialiseTheirRuns() throws Exception {
        File lockFile = Files.createFile(folder.resolve("host.lock")).toFile();
        Child a = start(lockFile, 1_500, 60);
        Child b = start(lockFile, 1_500, 60);

        assertEquals(0, a.waitFor(60), a.output());
        assertEquals(0, b.waitFor(60), b.output());
        long[] runA = interval(a);
        long[] runB = interval(b);
        long[] first = runA[0] <= runB[0] ? runA : runB;
        long[] second = runA[0] <= runB[0] ? runB : runA;
        assertTrue(second[0] >= first[1], "runs overlapped: first init..complete " + first[0] + ".." + first[1]
                + ", second init " + second[0] + "\n--- a ---\n" + a.output() + "\n--- b ---\n" + b.output());
    }

    @Test
    @Timeout(120)
    void killedHolderReleasesTheLock() throws Exception {
        File lockFile = Files.createFile(folder.resolve("host.lock")).toFile();
        Child holder = start(lockFile, 60_000, 60);
        assertNotNull(holder.awaitLine(INIT, 30), holder.output());

        Child waiter = start(lockFile, 0, 60);
        assertNull(waiter.awaitLine(INIT, 3), "the waiter started while the holder was running\n" + waiter.output());

        holder.process.destroyForcibly();
        assertTrue(holder.process.waitFor(10, TimeUnit.SECONDS));
        assertNotNull(waiter.awaitLine(INIT, 15), "the waiter did not start after the holder was killed\n" + waiter.output());
        assertEquals(0, waiter.waitFor(30), waiter.output());
    }

    @Test
    @Timeout(120)
    void timeoutNamesTheHolder() throws Exception {
        File lockFile = Files.createFile(folder.resolve("host.lock")).toFile();
        Child holder = start(lockFile, 60_000, 60);
        Matcher init = holder.awaitLine(INIT, 30);
        assertNotNull(init, holder.output());
        String holderPid = init.group(1);

        // the holder record is a separate file, readable without taking the lock
        String record = new String(Files.readAllBytes(new File(lockFile + ".holder").toPath()), StandardCharsets.UTF_8);
        assertTrue(record.contains("pid=" + holderPid + " "), record);
        assertTrue(record.contains(HostLockChild.class.getName()), record);

        Child waiter = start(lockFile, 0, 2);
        int exit = waiter.waitFor(30);
        String output = waiter.output();
        assertNotEquals(0, exit, "the waiter ran instead of timing out\n" + output);
        assertTrue(output.contains("JLBH: waiting for host lock " + lockFile), output);
        assertTrue(output.contains("JLBH: timed out after "), output);
        assertTrue(output.contains("held by pid=" + holderPid + " "), output);
        assertFalse(INIT.matcher(output).find(), output);
    }

    private static long[] interval(Child child) {
        Matcher init = INIT.matcher(child.output());
        Matcher complete = COMPLETE.matcher(child.output());
        assertTrue(init.find(), child.output());
        assertTrue(complete.find(), child.output());
        return new long[]{Long.parseLong(init.group(2)), Long.parseLong(complete.group(1))};
    }

    private Child start(File lockFile, long holdMillis, long timeoutSecs) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
            if (argument.startsWith("--add-"))
                command.add(argument);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("-Djlbh.hostLock=true");
        command.add("-Djlbh.hostLock.file=" + lockFile);
        command.add("-Djlbh.hostLock.timeoutSecs=" + timeoutSecs);
        command.add("-Djlbh.hostLock.reportSecs=1");
        command.add(HostLockChild.class.getName());
        command.add(Long.toString(holdMillis));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        Child child = new Child(process);
        children.add(child);
        return child;
    }

    private static final class Child {
        final Process process;
        final StringBuffer output = new StringBuffer();
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        final Thread reader;

        Child(Process process) {
            this.process = process;
            reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = in.readLine()) != null; ) {
                        output.append(line).append('\n');
                        lines.add(line);
                    }
                } catch (IOException ignored) {
                    // the process was destroyed
                }
            }, "host-lock-child-output");
            reader.setDaemon(true);
            reader.start();
        }

        Matcher awaitLine(Pattern pattern, long seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            for (long remaining; (remaining = deadline - System.nanoTime()) > 0; ) {
                String line = lines.poll(remaining, TimeUnit.NANOSECONDS);
                if (line == null)
                    break;
                Matcher matcher = pattern.matcher(line);
                if (matcher.find())
                    return matcher;
            }
            return null;
        }

        int waitFor(long seconds) throws InterruptedException {
            assertTrue(process.waitFor(seconds, TimeUnit.SECONDS), "child did not exit\n" + output);
            reader.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(reader.isAlive(), "child output reader did not stop\n" + output);
            return process.exitValue();
        }

        String output() {
            return output.toString();
        }
    }
}
