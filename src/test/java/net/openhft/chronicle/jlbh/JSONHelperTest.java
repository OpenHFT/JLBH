package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.NanoSampler;
import org.junit.Test;

import java.util.Stack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class JSONHelperTest {

    @Test
    public void jsonOutputIsWellFormed() {
        final int iterations = 1_000;
        final JLBH jlbh = new JLBH(new JLBHOptions()
                .iterations(iterations)
                .jlbhTask(new DummyTest())
        );
        jlbh.start();
        final String jsonSummary = JSONHelper.generateJSONSummary("Dummy\n\nTest", jlbh, iterations);
        System.out.println("Got summary: " + jsonSummary);
        crudelyValidate(jsonSummary);
    }

    /**
     * Crudely validate some JSON, just checks that strings, objects and arrays are well-formed
     *
     * @param jsonString The JSON string to validate
     */
    private void crudelyValidate(String jsonString) {
        Stack<Character> context = new Stack<>();
        boolean inString = false;
        boolean skipNext = false;
        for (int i = 0; i < jsonString.length(); i++) {
            final char c = jsonString.charAt(i);
            if (inString) {
                if (skipNext) {
                    skipNext = false;
                    continue;
                }
                switch (c) {
                    case '\\':
                        skipNext = true;
                        break;
                    case '"':
                        inString = false;
                        break;
                }
            } else {
                switch (c) {
                    case '{':
                    case '[':
                        context.push(c);
                        break;
                    case '}':
                        assertEquals("Unexpected end of object at position " + i, '{', context.pop().charValue());
                        break;
                    case ']':
                        assertEquals("Unexpected end of array at position " + i, '[', context.pop().charValue());
                        break;
                    case '"':
                        inString = true;
                }
            }
        }
        assertEquals("Unbalanced object or array brackets", 0, context.size());
        assertFalse("Non-terminated string", inString);
        assertFalse("Invalid escape sequence", skipNext);
    }

    private static class DummyTest implements JLBHTask {

        private static final int NUM_VALUES = 100;
        private JLBH jlbh;
        private int[] values = new int[NUM_VALUES];
        private NanoSampler sampler1;
        private NanoSampler sampler2;
        private NanoSampler sampler3;

        @Override
        public void init(JLBH jlbh) {
            this.jlbh = jlbh;
            sampler1 = jlbh.addProbe("bad\nname");
            sampler2 = jlbh.addProbe("anotherbad\\\"name");
            sampler3 = jlbh.addProbe("\"tough\"name");
        }

        @Override
        public void run(long startTimeNS) {
            Jvm.nanoPause();
            jlbh.sampleNanos(System.nanoTime() - startTimeNS);
            sampler1.sampleNanos(System.nanoTime() - startTimeNS);
            sampler2.sampleNanos(System.nanoTime() - startTimeNS);
            sampler3.sampleNanos(System.nanoTime() - startTimeNS);
        }
    }
}