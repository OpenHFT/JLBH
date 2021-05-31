package net.openhft.chronicle.core.jlbh;

import java.util.ArrayList;
import java.util.List;
import net.openhft.chronicle.core.util.Histogram;
import org.jetbrains.annotations.NotNull;

public class TSHistogram {

    @NotNull
    private List<Histogram> endToEndHistograms = new ArrayList<>();

    @NotNull
    private ThreadLocal<Histogram> endToEndHistogram = new ThreadLocal<Histogram>() {
        @Override
        protected Histogram initialValue() {
            final Histogram histogram = new Histogram();
            synchronized (endToEndHistograms) {
                endToEndHistograms.add(histogram);
            }
            return histogram;
        }
    };


    public long totalCount() {
        long totalCount = 0;
        synchronized (endToEndHistograms) {
            for (Histogram histogram : endToEndHistograms) {
                totalCount = totalCount + histogram.totalCount();
            }
        }
        return totalCount;
    }

    public Histogram snapshot() {
        final Histogram snapshot = new Histogram();
        synchronized (endToEndHistograms) {
            for (Histogram histogram : endToEndHistograms) {
                snapshot.add(histogram);
            }
        }
        return snapshot;
    }

    public void sample(long nanoTime) {
        endToEndHistogram.get().sample(nanoTime);
    }

    public void reset() {
        synchronized (endToEndHistograms) {
            for (Histogram histogram : endToEndHistograms) {
                histogram.reset();
            }
        }
    }
}
