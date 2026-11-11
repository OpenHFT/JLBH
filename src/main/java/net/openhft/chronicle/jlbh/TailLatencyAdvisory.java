/*
 * Copyright 2013-2025 chronicle.software; SPDX-License-Identifier: Apache-2.0
 */
package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.Histogram;
import java.io.PrintStream;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/** Descriptive tail checks on completed end-to-end samples; never a run-validity verdict. */
final class TailLatencyAdvisory {
    // Ascending numerator: equal qualifying ratios prefer the more extreme percentile.
    private static final double[] NUMERATORS = {0.99, 0.997, 0.999, 0.9997, 0.9999};
    private static final double[] DENOMINATORS = {0.50, 0.97, 0.99, 0.997, 0.999};
    private static final String[] LABELS = {"P99/P50", "P99.7/P97", "P99.9/P99", "P99.97/P99.7", "P99.99/P99.9"};
    private static final int[] THRESHOLDS = {5, 10, 10, 10, 10};

    private TailLatencyAdvisory() { }

    static void report(Histogram histogram, int run, PrintStream output) {
        long count = histogram.totalCount();
        Finding finding = largest(count, histogram::percentile);
        if (finding == null) return;
        output.printf(Locale.ROOT,
                "Tail-latency advisory: run=%d samples=%d %s=%s > %d "
                        + "(numerator=%s ns, denominator=%s ns); largest qualifying ratio. "
                        + "This tail discontinuity is probably worth triaging/investigating.%n",
                run, count, finding.label(), Double.toString(finding.ratio), THRESHOLDS[finding.rule],
                Double.toString(finding.numerator), Double.toString(finding.denominator));
    }

    static Finding largest(long count, DoubleUnaryOperator percentile) {
        if (count <= 0) return null;
        double worst = percentile.applyAsDouble(1.0);
        if (!Double.isFinite(worst) || worst <= 0) return null;
        Finding best = null;
        for (int rule = 0; rule < NUMERATORS.length; rule++) {
            double numerator = percentile.applyAsDouble(NUMERATORS[rule]);
            double denominator = percentile.applyAsDouble(DENOMINATORS[rule]);
            if (!Double.isFinite(numerator) || !Double.isFinite(denominator)
                    || numerator <= 0 || denominator <= 0 || numerator >= worst) continue;
            double ratio = numerator / denominator;
            if (Double.isFinite(ratio) && ratio > THRESHOLDS[rule]
                    && (best == null || ratio >= best.ratio))
                best = new Finding(rule, numerator, denominator, ratio);
        }
        return best;
    }

    static final class Finding {
        final int rule;
        final double numerator;
        final double denominator;
        final double ratio;
        private Finding(int rule, double numerator, double denominator, double ratio) {
            this.rule = rule;
            this.numerator = numerator;
            this.denominator = denominator;
            this.ratio = ratio;
        }
        String label() { return LABELS[rule]; }
    }
}
