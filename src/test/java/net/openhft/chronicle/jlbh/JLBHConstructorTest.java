/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class JLBHConstructorTest {
    private static final String RESOURCE_TRACING = "jvm.resource.tracing";
    private String originalResourceTracing;

    @BeforeEach
    void rememberProperty() {
        originalResourceTracing = System.getProperty(RESOURCE_TRACING);
        // Initialise Core before changing the property it also reads at startup.
        Jvm.isResourceTracing();
    }

    @AfterEach
    void restoreProperty() {
        setResourceTracing(originalResourceTracing);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "true", "TRUE", "not-a-boolean"})
    void shouldRejectIntrusiveTracingWithoutTerminatingTheHost(String value) throws Exception {
        setResourceTracing(value);
        JLBHOptions options = new JLBHOptions().jlbhTask(new JLBHTask() {
            @Override
            public void init(JLBH jlbh) {
                fail("Construction must not start the task");
            }

            @Override
            public void run(long startTimeNS) {
                fail("Construction must not run the task");
            }
        });
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream(), false, "UTF-8")) {
            for (Supplier<JLBH> constructor : Arrays.<Supplier<JLBH>>asList(
                    () -> new JLBH(options), () -> new JLBH(options, output, null))) {
                if (value != null && (value.isEmpty() || Boolean.parseBoolean(value))) {
                    IllegalStateException failure = assertThrows(IllegalStateException.class, constructor::get);
                    assertTrue(failure.getMessage().contains(RESOURCE_TRACING + "=" + value));
                } else {
                    assertNotNull(assertDoesNotThrow(constructor::get));
                }
            }
        }
    }

    private static void setResourceTracing(String value) {
        if (value == null)
            System.clearProperty(RESOURCE_TRACING);
        else
            System.setProperty(RESOURCE_TRACING, value);
    }
}
