/*
 * Copyright 2016-2022 chronicle.software
 *
 *       https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.OS;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static net.openhft.chronicle.jlbh.JLBHDeterministicFixtures.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class JLBHIntegrationTest {

    private PrintStream originalSystemOut;
    private PrintStream originalSystemErr;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @Before
    public void setUp() {
        rememberOriginalStdErrOut();
        Assume.assumeTrue(!OS.isMacOSX());
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @After
    public void tearDown() {
        resetSystemOut();
    }

    @Test
    public void shouldMeasureLatency() {
        // given
        redirectSystemOut();
        final JLBH jlbh = new JLBH(options());

        // when
        jlbh.start();

        // then
        String stdOut = outContent.toString();
        resetSystemOut();
        assertThat(stdOut, containsString("OS Jitter"));
        assertThat(stdOut, containsString("Warm up complete (500 iterations took "));
        assertThat(stdOut, containsString("Run time: "));
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