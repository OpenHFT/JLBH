package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.Histogram;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A helper for generating JSON representations of JLBH benchmark runs
 */
public class JSONHelper {

    /**
     * Generate a JSON summary of the benchmark runs
     *
     * @param benchmarkName The name of the benchmark (included in the JSON)
     * @param jlbh          The completed benchmark
     * @param iterations    The number of runs the benchmark ran for
     * @return A JSON String representing a summary of the completed benchmark
     */
    public static String generateJSONSummary(String benchmarkName, JLBH jlbh, int iterations) {
        return new JSONSummary(benchmarkName, jlbh, iterations, RunSummarizers.TAKE_LAST_RUN).toString();
    }

    /**
     * This could be done a bit easier with Wire, but then we'd need to add a dependency on Wire.
     * <p>
     * As long as it doesn't get any more complicated than this, I think it's fine to avoid that.
     */
    public static final class JSONSummary {
        private final String benchmarkName;
        private final double[] percentiles;
        private final Map<String, List<Double>> percentileRuns;
        private final RunSummarizer runSummarizer;

        public JSONSummary(String benchmarkName, JLBH jlbh, int iterations, RunSummarizer runSummarizer) {
            this.benchmarkName = benchmarkName;
            this.runSummarizer = runSummarizer;
            percentiles = Histogram.percentilesFor(iterations);
            percentileRuns = summarisePercentileRuns(jlbh);
        }

        private Map<String, List<Double>> summarisePercentileRuns(JLBH jlbh) {
            Map<String, List<Double>> percentileRuns = new HashMap<>();
            addRunSummary(percentileRuns, "end-to-end", jlbh.percentileRuns());
            jlbh.additionalPercentileRuns().forEach(
                    (name, values) -> addRunSummary(percentileRuns, name, values)
            );
            return percentileRuns;
        }

        private void addRunSummary(Map<String, List<Double>> percentileRunsMap, String name, List<double[]> percentileRuns) {
            if (percentileRuns.isEmpty()) {
                Jvm.warn().on(JSONSummary.class, "No percentile runs for " + name + ", skipping");
                return;
            }
            percentileRunsMap.put(name, runSummarizer.summarizeRuns(percentileRuns));
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{")
                    .append("\"benchmarkName\": \"").append(escapeQuotes(benchmarkName)).append("\", ")
                    .append("\"percentiles\": [").append(Arrays.stream(percentiles).mapToObj(Double::toString).collect(Collectors.joining(", "))).append("], ")
                    .append("\"percentileRuns\": {");
            percentileRuns.forEach((name, values) -> builder.append("\"").append(escapeQuotes(name)).append("\": ")
                    .append(values.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"))).append(", "));
            if (!percentileRuns.isEmpty()) {
                builder.setLength(builder.length() - 2);
            }
            builder.append("}}");
            return builder.toString();
        }

        /**
         * I'm sure this isn't exhaustive, but it should cover most nastiness
         *
         * @param input The string to be written as a double-quoted JSON string
         * @return The string with any double quotes escaped
         */
        private String escapeQuotes(String input) {
            return input.replaceAll("\\\\", "\\\\\\\\") // escape characters
                    .replaceAll("\"", "\\\\\"") // double quotes
                    .replaceAll("\n", "\\\\n"); // newlines
        }
    }

    public interface RunSummarizer {
        /**
         * Summarize the runs for a single benchmark into a single set of percentiles
         *
         * @param runs The results from all the runs
         * @return A single list of percentiles
         */
        List<Double> summarizeRuns(List<double[]> runs);
    }

    public enum RunSummarizers implements RunSummarizer {
        /**
         * This one just takes the last run as the summary
         */
        TAKE_LAST_RUN {
            @Override
            public List<Double> summarizeRuns(List<double[]> runs) {
                return Arrays.stream(runs.get(runs.size() - 1)).boxed().collect(Collectors.toList());
            }
        }
    }
}
