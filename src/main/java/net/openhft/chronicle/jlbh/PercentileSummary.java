package net.openhft.chronicle.jlbh;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Calculates values for the run summary.
 * <p>
 * Handle runs with differing number of samples (and hence, percentiles) correctly
 */
public final class PercentileSummary {

    private final boolean skipFirst;
    private final List<double[]> percentileRuns;
    private final double[] percentiles;

    /**
     * Constructor
     *
     * @param skipFirst      Whether to skip the value from the first run when calculating variance
     * @param percentileRuns The values for the individual runs
     * @param percentiles    The percentiles to render (some may not be present in some runs)
     */
    public PercentileSummary(boolean skipFirst,
                             @NotNull List<double[]> percentileRuns,
                             double[] percentiles) {
        this.skipFirst = skipFirst;
        this.percentileRuns = percentileRuns;
        this.percentiles = percentiles;
    }

    /**
     * Print out the summary (for debugging)
     * <p>
     * percentiles down the Y axis, runs along the X axis, variance in last column
     */
    public void printSummary() {
        System.out.print("    %ile    ");
        for (int i = 0; i < percentileRuns.size(); i++) {
            System.out.printf("%10d", i);
        }
        System.out.println("    var   ");
        forEachRow((percentile, values, variance) -> {
            System.out.printf("%10f: ", percentile);
            for (double value : values) {
                System.out.printf("%10f", value);
            }
            System.out.printf("%10f%n", variance);
        });
    }

    /**
     * Calculate the variance between runs for a percentile
     *
     * @param percentileIndex The index of the percentile for which to calculate the variance
     * @return The variance for that percentile
     */
    public double calculateVariance(int percentileIndex) {
        double maxValue = Double.MIN_VALUE;
        double minValue = Double.MAX_VALUE;
        for (int j = 0; j < percentileRuns.size(); j++) {
            double cellValue = getPercentileForRun(percentileIndex, j);
            if ((skipFirst && j == 0) || cellValue == Double.POSITIVE_INFINITY) {
                continue;
            }
            if (cellValue > maxValue) {
                maxValue = cellValue;
            }
            if (cellValue < minValue) {
                minValue = cellValue;
            }
        }
        return 100 * (maxValue - minValue) / (maxValue + minValue / 2);
    }

    /**
     * Get the percentile measurement for a specific run
     *
     * @param percentileIndex The index of the percentile to retrieve
     * @param runIndex        The index of the run to retrieve
     * @return The latency for the percentile, or {@link Double#POSITIVE_INFINITY} if there are not enough samples for that
     */
    public double getPercentileForRun(int percentileIndex, int runIndex) {
        final double[] percentileRun = percentileRuns.get(runIndex);
        if (percentileIndex == percentiles.length - 1) {
            return percentileRun[percentileRun.length - 1] / 1e3;
        } else if (percentileRun.length - 1 <= percentileIndex) {
            return Double.POSITIVE_INFINITY;
        } else {
            return percentileRun[percentileIndex] / 1e3;
        }
    }

    /**
     * Execute a callback for every percentile row that contains at least one value
     *
     * @param consumer A {@link RowConsumer} to process the rows
     */
    public void forEachRow(RowConsumer consumer) {
        for (int i = 0; i < percentiles.length; i++) {
            final double[] rowValues = new double[percentileRuns.size()];
            boolean rowHasValues = false;
            for (int j = 0; j < percentileRuns.size(); j++) {
                final double percentileForRun = getPercentileForRun(i, j);
                rowValues[j] = percentileForRun;
                rowHasValues = rowHasValues || !Double.isInfinite(percentileForRun);
            }
            if (rowHasValues) {
                consumer.consume(percentiles[i], rowValues, calculateVariance(i));
            }
        }
    }

    /**
     * Consumer of summary rows
     */
    public interface RowConsumer {

        /**
         * Consume a row of the summary
         *
         * @param percentile The percentile for the row
         * @param values     The values for the respective runs
         * @param variance   The variance for the values present in the row
         */
        void consume(double percentile, double[] values, double variance);
    }
}
