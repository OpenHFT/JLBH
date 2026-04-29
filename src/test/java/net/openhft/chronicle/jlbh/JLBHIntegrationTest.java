/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.OS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static net.openhft.chronicle.jlbh.JLBHDeterministicFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class JLBHIntegrationTest {

    private PrintStream originalSystemOut;
    private PrintStream originalSystemErr;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        rememberOriginalStdErrOut();
        assumeTrue(!OS.isMacOSX());
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() {
        resetSystemOut();
    }

    @Test
    void shouldMeasureLatency() {
        // given
        redirectSystemOut();
        final JLBH jlbh = new JLBH(options());

        // when
        jlbh.start();

        // then
        String stdOut = outContent.toString();
        resetSystemOut();
        assertTrue(stdOut.contains("OS Jitter"));
        assertTrue(stdOut.contains("Warm up complete (500 iterations took "));
        assertTrue(stdOut.contains("Run time: "));
        String actual = withoutNonDeterministicFields(stdOut);
        String expected = withoutNonDeterministicFields(predictableTaskExpectedResult());

        assertEquals(expected, actual);
    }

    private void redirectSystemOut() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
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
