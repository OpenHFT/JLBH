/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;

/**
 * A minimal JLBH run used as a child JVM by {@link JLBHHostLockProcessTest}.
 * It prints {@code INIT pid=<pid> at=<epoch ms>} from {@link JLBHTask#init(JLBH)}, pauses for the number of
 * milliseconds given as the first argument, and prints {@code COMPLETE at=<epoch ms>} from
 * {@link JLBHTask#complete()}. The host lock is configured by system properties only.
 */
public final class HostLockChild {
    private HostLockChild() {
    }

    public static void main(String[] args) {
        final long holdMillis = Long.parseLong(args[0]);
        JLBHOptions options = new JLBHOptions()
                .warmUpIterations(1_000)
                .iterations(1_000)
                .throughput(100_000)
                .runs(1)
                .recordOSJitter(false)
                .acquireLock(() -> null)
                .jlbhTask(new JLBHTask() {
                    private JLBH jlbh;

                    @Override
                    public void init(JLBH jlbh) {
                        this.jlbh = jlbh;
                        System.out.println("INIT pid=" + OS.getProcessId() + " at=" + System.currentTimeMillis());
                        System.out.flush();
                        Jvm.pause(holdMillis);
                    }

                    @Override
                    public void run(long startTimeNS) {
                        jlbh.sample(System.nanoTime() - startTimeNS);
                    }

                    @Override
                    public void complete() {
                        System.out.println("COMPLETE at=" + System.currentTimeMillis());
                        System.out.flush();
                    }
                });
        new JLBH(options, System.out, null).start();
        System.exit(0);
    }
}
