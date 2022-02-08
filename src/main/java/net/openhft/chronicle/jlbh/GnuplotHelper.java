package net.openhft.chronicle.jlbh;

import net.openhft.chronicle.core.util.Histogram;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GnuplotHelper {
    // TODO: change colours to corporate palette
    private static final String[] colours = new String[]{"coral", "forest-green", "royalblue", "gold", "mediumpurple3", "dark-red", "gray"};

    /**
     * line for each run. Each probe shares same colour but uses different line type
     * prefix drives gnuplot filename (overload) and .png name
     */
    public static void printRuns(@NotNull String prefix, @NotNull JLBH jlbh, long iterations, @NotNull PrintStream printStream) {
        printRuns(prefix, jlbh, iterations, printStream, TimeUnit.MICROSECONDS, 1.0, false, false);
    }

    public static void printRuns(@NotNull String prefix, @NotNull JLBH jlbh, long iterations, @NotNull PrintStream printStream, TimeUnit timeUnit, double maxPercentile, boolean e2eOnly, boolean logscale) {
        final double convertSeed = 1_000_000;
        final double conversion = timeUnit.convert((long) convertSeed, TimeUnit.NANOSECONDS) / convertSeed;
        double[] percentages = Histogram.percentilesFor(iterations);
        String xtics = "";
        for (int i = 0; i < percentages.length; i++) {
            if (i > 0) xtics += ", ";
            if (percentages[i] <= maxPercentile)
                xtics += "\"" + percentages[i] + "\" " + i;
        }
        printStream.println("set terminal pngcairo size 1024,480");
        printStream.println("set output \"" + prefix + ".png\"");
        printStream.println("set title \"" + prefix + " - latency by percentile distribution\"");
        if (logscale) {
            printStream.println("set logscale y");
            // otherwise lowest values hug the bottom of the graph
            printStream.println("set yrange [0.01:]");
        }
        printStream.println("set ylabel \"latency " + timeUnit.name() + (logscale ? " log scale" : "") + "\"");
        printStream.println("set key outside");
        printStream.println("set style line 11 lc rgb '#808080' lt 1");
        printStream.println("set border 3 back ls 11");
        printStream.println("set style line 12 lc rgb '#808080' lt 0 lw 1");
        printStream.println("set grid back ls 12");
        printStream.println("set xtics (" + xtics + ")");
        printStream.println("\n$data << EOD");

        final int runs = jlbh.percentileRuns().size();
        for (int i = 0; i < percentages.length; i++) {
            if (percentages[i] <= maxPercentile) {
                printStream.print(i + " ");
                for (int r = 0; r < runs; r++) {
                    double[] values = jlbh.percentileRuns().get(r);
                    printStream.printf("%.2f ", values[i] * conversion);
                }
                if (!e2eOnly) {
                    for (List<double[]> list : jlbh.additionalPercentileRuns().values()) {
                        for (int r = 0; r < runs; r++) {
                            double[] values = fixup(list.get(r), percentages);
                            if (values[i] == -1)
                                printStream.print("- ");
                            else
                                printStream.printf("%.2f ", values[i] * conversion);
                        }
                    }
                }
                printStream.println();
            }
        }

        printStream.println("EOD");
        printStream.println("\nplot \\");
        int column = 2;
        for (int r = 0; r < runs; r++) {
            String colour = "lc rgb \"" + colours[r % colours.length] + "\"";
            printStream.println("\"$data\" using 1:" + column++ + " with lines lw " + ((runs - r) + 1) + " dt 1 " + colour + " title \"e2e run" + (r + 1) + "\",\\");
        }
        if (!e2eOnly) {
            final List<String> keys = new ArrayList<>(jlbh.additionalPercentileRuns().keySet());
            for (int k = 0; k < keys.size(); k++) {
                for (int r = 0; r < runs; r++) {
                    String colour = "lc rgb \"" + colours[r % colours.length] + "\"";
                    String eol = (r < runs - 1 || k < keys.size() - 1) ? ",\\" : "";
                    printStream.println("\"$data\" using 1:" + column++ + " with lines lw " + ((runs - r) + 1) + " dt " + (k + 2) + " " + colour + " title \"" + keys.get(k) + " run" + (r + 1) + "\"" + eol);
                }
            }
        }
    }

    // sometimes we don't have as many values as percentages
    // e.g. %ages 50 90 99 99.9 1
    //     values 1  2          3
    private static double[] fixup(double[] values, double[] percentages) {
        if (percentages.length > values.length) {
            final double[] rv = new double[percentages.length];
            int i = 0;
            for (; i < values.length - 1; i++) {
                rv[i] = values[i];
            }
            for (; i < percentages.length - 1; i++) {
                rv[i] = -1;
            }
            rv[i] = values[values.length - 1];
            return rv;
        } else {
            return values;
        }
    }
}
