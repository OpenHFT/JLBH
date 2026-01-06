/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.OS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static net.openhft.chronicle.jlbh.JLBHDeterministicFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JLBHIntegrationTest {

    private PrintStream originalSystemOut;
    private PrintStream originalSystemErr;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        rememberOriginalStdErrOut();
        assumeTrue(!OS.isMacOSX(), "not supported on macOS");
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() {
        resetSystemOut();
    }

    @Test
    @DisplayName("Measures latency output and matches deterministic fixture")
    void shouldMeasureLatency() {
        // given
        redirectSystemOut();
        final JLBH jlbh = new JLBH(options());

        // when
        jlbh.start();

        // then
        String stdOut;
        try {
            stdOut = outContent.toString("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 not supported in captured output", e);
        }
        resetSystemOut();
        assertTrue(stdOut.contains("OS Jitter"),
                stdOut + " should contain OS Jitter section");
        assertTrue(stdOut.contains("Warm up complete (500 iterations took "),
                stdOut + " should contain warmup summary");
        assertTrue(stdOut.contains("Run time: "),
                stdOut + " should contain run time");
        String actual = withoutNonDeterministicFields(stdOut);
        String expected = withoutNonDeterministicFields(predictableTaskExpectedResult());

        assertEquals(expected, actual, "normalised output should match fixture");
    }

    private void redirectSystemOut() {
        try {
            System.setOut(new PrintStream(outContent, true, "UTF-8"));
            System.setErr(new PrintStream(errContent, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 not supported while redirecting standard streams", e);
        }
    }

    private void resetSystemOut() {
        System.setOut(originalSystemOut);
        System.setErr(originalSystemErr);
    }

    private void rememberOriginalStdErrOut() {
        originalSystemOut = System.out;
        originalSystemErr = System.err;
    }
}
