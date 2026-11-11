/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** Requests once, waits for observed completion, and removes listeners on every exit. */
final class PostWarmupGc {
    private PostWarmupGc() {
    }

    static void await(PrintStream output, long timeoutMS) {
        List<NotificationEmitter> emitters = new ArrayList<>();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
            if (bean instanceof NotificationEmitter)
                emitters.add((NotificationEmitter) bean);
        await(output, timeoutMS, emitters, System::gc);
    }

    static void await(PrintStream output, long timeoutMS, List<NotificationEmitter> emitters, Runnable request) {
        if (Thread.currentThread().isInterrupted())
            throw new IllegalStateException("Interrupted before post-warmup GC");
        if (emitters.isEmpty())
            throw new IllegalStateException("GC notifications unavailable; use disableGcAfterWarmup(true) to opt out");
        GcCompletion completion = new GcCompletion();
        Map<String, Long> baseline = new HashMap<>();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof com.sun.management.GarbageCollectorMXBean) {
                com.sun.management.GcInfo last = ((com.sun.management.GarbageCollectorMXBean) bean).getLastGcInfo();
                baseline.put(bean.getName(), last == null ? 0L : last.getId());
            }
        }
        AtomicReference<WeakReference<Object>> marker = new AtomicReference<>();
        NotificationListener listener = (notification, handback) -> {
            if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())
                    && notification.getUserData() instanceof CompositeData) {
                GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(
                        (CompositeData) notification.getUserData());
                WeakReference<Object> reference = marker.get();
                Long previous = baseline.get(info.getGcName());
                completion.observe(info.getGcCause(), info.getGcAction(),
                        previous != null && info.getGcInfo().getId() > previous,
                        reference != null && reference.get() == null);
            }
        };
        List<NotificationEmitter> registered = new ArrayList<>();
        try {
            for (NotificationEmitter emitter : emitters) {
                emitter.addNotificationListener(listener, null, null);
                registered.add(emitter);
            }
            output.println("Requesting post-warmup System.gc() and waiting for completion");
            marker.set(new WeakReference<>(new Object()));
            request.run();
            completion.await(timeoutMS);
            output.println("Post-warmup explicit major GC completion observed");
        } finally {
            for (NotificationEmitter emitter : registered) {
                try {
                    emitter.removeNotificationListener(listener);
                } catch (ListenerNotFoundException alreadyRemoved) {
                    // No registration remains to release.
                }
            }
        }
    }
}
