/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.OS;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A run-scoped lease on a host-wide lock, so that benchmark runs on one host do not overlap.
 * <p>
 * The lease is not owned by a thread. It is acquired once and may be released exactly once by any thread;
 * further calls to {@link #close()} are ignored. This allows {@link JLBH#eventLoopHandler} to acquire the
 * lease on the caller's thread and release it on the event loop thread.
 * <p>
 * Exclusion has two layers:
 * <ul>
 *     <li>within this JVM, a fair {@link Semaphore} with one permit. A nested or concurrent acquire in the same
 *     JVM waits for the current lease, as a second JVM would; there is no re-entry.</li>
 *     <li>across processes, an exclusive {@link FileChannel#tryLock(long, long, boolean)} on one byte at offset
 *     {@code 1L << 62} of the lock file. On Linux this is an fcntl record lock, which the OS drops when the holder
 *     exits or is killed, so a crashed holder cannot leave a stale lock. It does not interact with flock(2) locks
 *     such as flock(1); a script can join it with {@code fcntl.lockf(fd, fcntl.LOCK_EX, 1, 1 << 62)}.</li>
 * </ul>
 * The holder writes a one-line description of itself to a sidecar file ({@code <lock file>.holder}) after acquiring
 * and truncates it before releasing. Waiters read the sidecar without locking. The lock file itself is never opened
 * by readers, because closing any descriptor of an fcntl-locked file drops that process's lock. The sidecar has a
 * predictable name in a shared directory, so it is never opened through a symbolic link, and at most
 * {@value #MAX_RECORD_BYTES} bytes of it are read.
 * <p>
 * The lock file is {@code -Djlbh.hostLock.file}, else the environment variable {@code JLBH_HOST_LOCK_FILE}, else
 * {@code /tmp/jlbh-host.lock}. {@code java.io.tmpdir} is deliberately not used on Linux or macOS because builds
 * commonly redirect it per project, which would split the lock. On Windows the default is under
 * {@code java.io.tmpdir}.
 */
public final class JLBHHostLock implements AutoCloseable {
    /** Enables the lock for {@link JLBH} runs; off by default. */
    public static final String ENABLED_PROPERTY = "jlbh.hostLock";
    public static final String FILE_PROPERTY = "jlbh.hostLock.file";
    public static final String FILE_ENV = "JLBH_HOST_LOCK_FILE";
    /** Seconds to wait before failing; 0 fails immediately, a negative value waits indefinitely. Default 3600. */
    public static final String TIMEOUT_SECS_PROPERTY = "jlbh.hostLock.timeoutSecs";
    /** Seconds between "waiting" messages. Default 30. */
    public static final String REPORT_SECS_PROPERTY = "jlbh.hostLock.reportSecs";
    static final long LOCK_POSITION = 1L << 62;
    static final int MAX_RECORD_BYTES = 4096;
    private static final Semaphore IN_JVM = new Semaphore(1, true);
    private static volatile String inJvmHolder;

    private final Path lockFile;
    private final Path holderFile;
    private final FileChannel channel;
    private final FileLock fileLock;
    private final long waitedMillis;
    private final AtomicBoolean released = new AtomicBoolean();

    private JLBHHostLock(Path lockFile, Path holderFile, FileChannel channel, FileLock fileLock, long waitedMillis) {
        this.lockFile = lockFile;
        this.holderFile = holderFile;
        this.channel = channel;
        this.fileLock = fileLock;
        this.waitedMillis = waitedMillis;
    }

    /**
     * @return the lock file named by {@code -Djlbh.hostLock.file}, {@code JLBH_HOST_LOCK_FILE} or the default.
     */
    @NotNull
    public static Path defaultLockFile() {
        String file = System.getProperty(FILE_PROPERTY);
        if (file == null || file.isEmpty())
            file = System.getenv(FILE_ENV);
        if (file != null && !file.isEmpty())
            return Paths.get(file);
        if (OS.isWindows())
            return Paths.get(System.getProperty("java.io.tmpdir"), "jlbh-host.lock");
        return Paths.get("/tmp", "jlbh-host.lock");
    }

    /**
     * @return {@code -Djlbh.hostLock.timeoutSecs} in milliseconds, or -1 to wait indefinitely.
     */
    public static long defaultTimeoutMillis() {
        long secs = Long.getLong(TIMEOUT_SECS_PROPERTY, 3600L);
        return secs < 0 ? -1 : TimeUnit.SECONDS.toMillis(secs);
    }

    /**
     * Acquire the lease on {@link #defaultLockFile()}, reporting every {@code -Djlbh.hostLock.reportSecs} while
     * waiting.
     *
     * @see #acquire(Path, String, long, long, PrintStream)
     */
    @NotNull
    public static JLBHHostLock acquire(String description, long timeoutMillis, @NotNull PrintStream log) {
        long reportMillis = TimeUnit.SECONDS.toMillis(Math.max(1L, Long.getLong(REPORT_SECS_PROPERTY, 30L)));
        return acquire(defaultLockFile(), description, timeoutMillis, reportMillis, log);
    }

    /**
     * Wait for and acquire the host lease.
     *
     * @param lockFile      the shared lock file; created world read/writable if absent
     * @param description   recorded in the holder file and shown to waiters
     * @param timeoutMillis maximum wait; 0 fails at once if the lock is held, a negative value waits indefinitely
     * @param reportMillis  interval between "waiting" messages naming the holder
     * @param log           receives the waiting and acquired messages
     * @return the lease, which must be closed once the run ends
     * @throws IllegalStateException if the timeout expires (the message names the holder), the thread is
     *                               interrupted, or the lock file cannot be opened
     */
    @NotNull
    public static JLBHHostLock acquire(@NotNull Path lockFile, String description, long timeoutMillis,
                                       long reportMillis, @NotNull PrintStream log) {
        final long startNs = System.nanoTime();
        final long timeoutNs = timeoutMillis < 0 ? Long.MAX_VALUE : TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        final long reportNs = TimeUnit.MILLISECONDS.toNanos(Math.max(1L, reportMillis));
        final Path holderFile = holderFile(lockFile);
        // all times below are elapsed nanoseconds since startNs, which avoids System.nanoTime() overflow
        long nextReportNs = reportNs;

        try {
            while (true) {
                long elapsedNs = System.nanoTime() - startNs;
                if (IN_JVM.tryAcquire(Math.max(0L, Math.min(timeoutNs, nextReportNs) - elapsedNs), TimeUnit.NANOSECONDS))
                    break;
                elapsedNs = System.nanoTime() - startNs;
                String holder = "in this JVM by " + inJvmHolder;
                if (elapsedNs >= timeoutNs)
                    throw timedOut(lockFile, holder, startNs, timeoutMillis);
                if (elapsedNs >= nextReportNs) {
                    log.println(waiting(lockFile, holder, startNs, timeoutMillis));
                    nextReportNs = elapsedNs + reportNs;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JLBH: interrupted waiting for host lock " + lockFile, e);
        }

        inJvmHolder = description;
        FileChannel channel = null;
        try {
            channel = openShared(lockFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
            long sleepMs = 100;
            FileLock fileLock;
            while ((fileLock = channel.tryLock(LOCK_POSITION, 1, false)) == null) {
                long elapsedNs = System.nanoTime() - startNs;
                if (elapsedNs >= timeoutNs)
                    throw timedOut(lockFile, "by " + readHolder(holderFile), startNs, timeoutMillis);
                if (elapsedNs >= nextReportNs) {
                    log.println(waiting(lockFile, "by " + readHolder(holderFile), startNs, timeoutMillis));
                    nextReportNs = elapsedNs + reportNs;
                }
                long untilNextCheckMs = TimeUnit.NANOSECONDS.toMillis(Math.min(timeoutNs, nextReportNs) - elapsedNs);
                Thread.sleep(Math.max(1L, Math.min(sleepMs, untilNextCheckMs)));
                sleepMs = Math.min(1000L, sleepMs * 2);
            }
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            writeHolder(holderFile, description, log);
            log.println("JLBH: host lock " + lockFile + " acquired after " + waitedMillis / 1000.0 + " s");
            return new JLBHHostLock(lockFile, holderFile, channel, fileLock, waitedMillis);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseInJvm(channel);
            throw new IllegalStateException("JLBH: interrupted waiting for host lock " + lockFile, e);

        } catch (IOException e) {
            releaseInJvm(channel);
            throw new IllegalStateException("JLBH: cannot lock host lock file " + lockFile
                    + "; set -D" + FILE_PROPERTY + " to a shared writable path", e);

        } catch (RuntimeException | Error e) {
            releaseInJvm(channel);
            throw e;
        }
    }

    static Path holderFile(Path lockFile) {
        return Paths.get(lockFile + ".holder");
    }

    /**
     * @return the holder record, or a placeholder when it is absent or empty
     */
    @NotNull
    public static String readHolder(@NotNull Path holderFile) {
        // bounded and without following a symbolic link: anyone who can write the shared directory controls this file
        try (FileChannel in = FileChannel.open(holderFile, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer bytes = ByteBuffer.allocate(MAX_RECORD_BYTES);
            while (bytes.hasRemaining() && in.read(bytes) >= 0) {
                // keep reading until full or end of file
            }
            String record = new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8).trim();
            if (!record.isEmpty())
                return record;
        } catch (IOException ignored) {
            // fall through
        }
        return "an unknown holder (no record in " + holderFile + ")";
    }

    private static void releaseInJvm(FileChannel channel) {
        try {
            if (channel != null)
                channel.close();
        } catch (IOException ignored) {
            // nothing more to release
        } finally {
            inJvmHolder = null;
            IN_JVM.release();
        }
    }

    private static IllegalStateException timedOut(Path lockFile, String holder, long startNs, long timeoutMillis) {
        return new IllegalStateException("JLBH: timed out after " + elapsedSecs(startNs) + " s waiting for host lock "
                + lockFile + " held " + holder + "; stop that run or raise -D" + TIMEOUT_SECS_PROPERTY
                + " (timeout " + timeoutMillis + " ms)");
    }

    private static String waiting(Path lockFile, String holder, long startNs, long timeoutMillis) {
        return "JLBH: waiting for host lock " + lockFile + " held " + holder + "; waited " + elapsedSecs(startNs)
                + " s of " + (timeoutMillis < 0 ? "unlimited" : timeoutMillis / 1000 + " s");
    }

    private static long elapsedSecs(long startNs) {
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNs);
    }

    private static void writeHolder(Path holderFile, String description, PrintStream log) {
        String command = System.getProperty("sun.java.command", "");
        if (command.length() > 200)
            command = command.substring(0, 200);
        String record = "pid=" + OS.getProcessId() + " user=" + OS.getUserName() + " host=" + OS.getHostName()
                + " started=" + Instant.now() + " run=" + description + " cmd=" + command + "\n";
        try (FileChannel holder = openShared(holderFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer bytes = ByteBuffer.wrap(record.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining())
                holder.write(bytes);
        } catch (IOException e) {
            log.println("JLBH: host lock held, but the holder record " + holderFile + " could not be written: " + e);
        }
    }

    /**
     * Open an existing file without O_CREAT, so a file another user created in sticky /tmp can be opened under
     * fs.protected_regular; otherwise create it world read/writable so other users can share it.
     */
    private static FileChannel openShared(Path file, OpenOption... options) throws IOException {
        try {
            return FileChannel.open(file, options);
        } catch (NoSuchFileException absent) {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null)
                Files.createDirectories(parent);
            FileChannel created;
            try {
                created = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException race) {
                return FileChannel.open(file, options);
            }
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-rw-"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // not a POSIX file system, or not ours to change
            }
            return created;
        }
    }

    @NotNull
    public Path lockFile() {
        return lockFile;
    }

    /**
     * @return how long {@link #acquire} waited for the lease, in milliseconds
     */
    public long waitedMillis() {
        return waitedMillis;
    }

    public boolean isReleased() {
        return released.get();
    }

    /**
     * Release the lease. Any thread may call this; only the first call has an effect.
     * The holder record is cleared before the file lock is released, so it never describes a later holder.
     */
    @Override
    public void close() {
        if (!released.compareAndSet(false, true))
            return;
        try {
            FileChannel.open(holderFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS).close();
        } catch (IOException ignored) {
            // a missing or unwritable record does not prevent release
        }
        try {
            fileLock.release();
        } catch (IOException ignored) {
            // closing the channel also releases the lock
        } finally {
            releaseInJvm(channel);
        }
    }

    @Override
    public String toString() {
        return "JLBHHostLock{lockFile=" + lockFile + ", waitedMillis=" + waitedMillis + ", released=" + released.get() + '}';
    }
}
