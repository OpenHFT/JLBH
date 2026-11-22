/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.threads.EventLoop;
import net.openhft.chronicle.threads.MediumEventLoop;
import net.openhft.chronicle.threads.Pauser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * In-JVM behaviour of {@link JLBHHostLock} and its opt-in use by {@link JLBH}. Every test uses a private lock file,
 * named through a system property.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class JLBHHostLockTest {
    @TempDir
    Path folder;
    private final ByteArrayOutputStream log = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(log, true);
    private EventLoop eventLoop;

    @AfterEach
    void after() {
        System.clearProperty(JLBHHostLock.FILE_PROPERTY);
        Closeable.closeQuietly(eventLoop);
    }

    private Path lockFile() {
        return folder.resolve("host.lock");
    }

    private JLBHHostLock acquire(String description, long timeoutMillis) {
        return JLBHHostLock.acquire(lockFile(), description, timeoutMillis, 100, out);
    }

    /** Proves the lock is free, without waiting. */
    private void assertFree() {
        acquire("probe", 0).close();
    }

    @Test
    @Timeout(30)
    void secondAcquireInTheSameJvmWaitsForRelease() throws Exception {
        JLBHHostLock first = acquire("first", 0);
        CompletableFuture<JLBHHostLock> second = CompletableFuture.supplyAsync(() -> acquire("second", 20_000));
        try {
            second.get(500, TimeUnit.MILLISECONDS);
            fail("the second lease was granted while the first was held");
        } catch (TimeoutException expected) {
            // still waiting
        }
        first.close();
        second.get(10, TimeUnit.SECONDS).close();
        assertTrue(log.toString().contains("held in this JVM by first"), log.toString());
    }

    @Test
    @Timeout(30)
    void anotherThreadMayReleaseTheLeaseExactlyOnce() throws Exception {
        JLBHHostLock lease = acquire("run", 0);
        Thread releaser = new Thread(lease::close);
        releaser.start();
        releaser.join();
        assertTrue(lease.isReleased());
        lease.close(); // a second release must not add a permit

        JLBHHostLock next = acquire("next", 0);
        try {
            acquire("third", 0).close();
            fail("a repeated release let two leases be held at once");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("held in this JVM by next"), expected.getMessage());
        } finally {
            next.close();
        }
        assertFree();
    }

    @Test
    @Timeout(30)
    void timeoutNamesTheHolderAndReportsWhileWaiting() {
        JLBHHostLock holder = acquire("the holder", 0);
        try {
            acquire("waiter", 500).close();
            fail("expected a timeout");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("timed out"), expected.getMessage());
            assertTrue(expected.getMessage().contains("held in this JVM by the holder"), expected.getMessage());
            assertTrue(expected.getMessage().contains(JLBHHostLock.TIMEOUT_SECS_PROPERTY), expected.getMessage());
        } finally {
            holder.close();
        }
        assertTrue(log.toString().contains("JLBH: waiting for host lock " + lockFile() + " held in this JVM by the holder"), log.toString());
        assertFree();
    }

    @Test
    @Timeout(30)
    void holderRecordIsWrittenAfterAcquireAndClearedOnRelease() {
        Path holderFile = JLBHHostLock.holderFile(lockFile());
        try (JLBHHostLock lease = acquire("recorded run", 0)) {
            String record = JLBHHostLock.readHolder(holderFile);
            assertTrue(record.startsWith("pid=" + OS.getProcessId() + " "), record);
            assertTrue(record.contains(" run=recorded run "), record);
            assertEquals(lockFile(), lease.lockFile());
        }
        assertTrue(JLBHHostLock.readHolder(holderFile).contains("unknown holder"), JLBHHostLock.readHolder(holderFile));
    }

    @Test
    @Timeout(60)
    void jlbhIsUnchangedAndNeverTouchesTheLockFileByDefault() {
        System.setProperty(JLBHHostLock.FILE_PROPERTY, lockFile().toString());
        JLBHOptions options = options(new SamplingTask());
        assertFalse(options.hostLock);
        new JLBH(options, out, null).start();

        assertFalse(Files.exists(lockFile()));
        assertFalse(Files.exists(JLBHHostLock.holderFile(lockFile())));
        assertFalse(log.toString().contains("host lock"), log.toString());
    }

    @Test
    @Timeout(60)
    void jlbhHoldsTheLockForTheRunWhenEnabled() {
        System.setProperty(JLBHHostLock.FILE_PROPERTY, lockFile().toString());
        AtomicReference<String> recordDuringRun = new AtomicReference<>();
        SamplingTask task = new SamplingTask() {
            @Override
            public void init(JLBH jlbh) {
                super.init(jlbh);
                recordDuringRun.set(JLBHHostLock.readHolder(JLBHHostLock.holderFile(lockFile())));
            }
        };
        new JLBH(options(task).hostLock(true), out, null).start();

        assertTrue(recordDuringRun.get().contains("run=JLBH "), recordDuringRun.get());
        assertTrue(log.toString().contains("JLBH: host lock " + lockFile() + " acquired after "), log.toString());
        assertFree();
    }

    @Test
    @Timeout(60)
    void jlbhReleasesTheLockWhenInitThrows() {
        System.setProperty(JLBHHostLock.FILE_PROPERTY, lockFile().toString());
        SamplingTask task = new SamplingTask() {
            @Override
            public void init(JLBH jlbh) {
                throw new IllegalArgumentException("init failed");
            }
        };
        try {
            new JLBH(options(task).hostLock(true), out, null).start();
            fail("expected init to fail");
        } catch (IllegalArgumentException expected) {
            assertEquals("init failed", expected.getMessage());
        }
        assertFree();
    }

    @Test
    @Timeout(60)
    void jlbhFailsWithTheHolderNamedWhenTheLockIsBusy() {
        System.setProperty(JLBHHostLock.FILE_PROPERTY, lockFile().toString());
        JLBHHostLock other = acquire("another benchmark", 0);
        try {
            new JLBH(options(new SamplingTask()).hostLock(true).hostLockTimeoutMillis(0), out, null).start();
            fail("expected the busy lock to fail the run");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("held in this JVM by another benchmark"), expected.getMessage());
        } finally {
            other.close();
        }
        assertFree();
    }

    @Test
    @Timeout(60)
    void eventLoopRunReleasesTheLockOnTheLoopThread() throws InterruptedException, ExecutionException {
        System.setProperty(JLBHHostLock.FILE_PROPERTY, lockFile().toString());
        eventLoop = new MediumEventLoop(null, "host-lock-el", Pauser.busy(), true, null);
        eventLoop.start();
        CompletableFuture<Thread> completedOn = new CompletableFuture<>();
        AtomicReference<String> recordAtComplete = new AtomicReference<>();
        SamplingTask task = new SamplingTask() {
            @Override
            public void complete() {
                recordAtComplete.set(JLBHHostLock.readHolder(JLBHHostLock.holderFile(lockFile())));
                completedOn.complete(Thread.currentThread());
            }
        };
        new JLBH(options(task).hostLock(true), out, null).eventLoopHandler(eventLoop);
        // the lease is handed to the loop, not released when eventLoopHandler returns
        String recordAfterHandOver = JLBHHostLock.readHolder(JLBHHostLock.holderFile(lockFile()));

        Thread loopThread;
        try {
            loopThread = completedOn.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError(log.toString(), e);
        }
        assertNotSame(Thread.currentThread(), loopThread);
        assertTrue(recordAfterHandOver.contains("run=JLBH "), recordAfterHandOver);
        assertTrue(recordAtComplete.get().contains("run=JLBH "), recordAtComplete.get());
        // the lease is released just after complete() on the loop thread
        JLBHHostLock next = acquire("after event loop run", 10_000);
        next.close();
    }

    @Test
    @Timeout(60)
    void closingTheEventLoopEarlyReleasesTheLock() {
        System.setProperty(JLBHHostLock.FILE_PROPERTY, lockFile().toString());
        eventLoop = new MediumEventLoop(null, "host-lock-el", Pauser.busy(), true, null);
        eventLoop.start();
        JLBHOptions options = options(new SamplingTask()).hostLock(true).iterations(1_000_000_000L).throughput(1_000);
        new JLBH(options, out, null).eventLoopHandler(eventLoop);
        String recordWhileRunning = JLBHHostLock.readHolder(JLBHHostLock.holderFile(lockFile()));
        assertTrue(recordWhileRunning.contains("run=JLBH "), recordWhileRunning);
        eventLoop.close();
        acquire("after closed loop", 10_000).close();
    }

    @Test
    @Timeout(30)
    void holderRecordIsNeverWrittenOrReadThroughASymbolicLink() throws Exception {
        Path victim = folder.resolve("victim.txt");
        Files.write(victim, "unrelated content\n".getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(JLBHHostLock.holderFile(lockFile()), victim);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here: " + e);
        }
        try (JLBHHostLock lease = acquire("linked run", 0)) {
            assertTrue(log.toString().contains("could not be written"), log.toString());
            assertTrue(JLBHHostLock.readHolder(JLBHHostLock.holderFile(lockFile())).contains("unknown holder"));
            assertFalse(lease.isReleased());
        }
        assertEquals("unrelated content\n", new String(Files.readAllBytes(victim), StandardCharsets.UTF_8));
        assertFree();
    }

    private static JLBHOptions options(JLBHTask task) {
        return new JLBHOptions()
                .warmUpIterations(1_000)
                .iterations(1_000)
                .throughput(100_000)
                .runs(1)
                .recordOSJitter(false)
                .acquireLock(() -> null)
                .jlbhTask(task);
    }

    private static class SamplingTask implements JLBHTask {
        private JLBH jlbh;

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
        }

        @Override
        public void run(long startTimeNS) {
            jlbh.sample(System.nanoTime() - startTimeNS);
        }
    }
}
