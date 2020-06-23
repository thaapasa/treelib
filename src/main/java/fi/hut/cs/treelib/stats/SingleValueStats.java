package fi.hut.cs.treelib.stats;

public class SingleValueStats {

    private double min;
    private double max;
    private double cumulative = 0;
    private long count = 0;

    public SingleValueStats() {
    }

    public void log(double stat) {
        if (count < 1) {
            // First value that is logged
            min = stat;
            max = stat;
        } else {
            if (stat < min)
                min = stat;
            if (stat > max)
                max = stat;
        }
        cumulative += stat;
        count++;
    }

    public boolean isInitialized() {
        return count > 0;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getAverage() {
        return isInitialized() ? cumulative / count : 0;
    }

    public long getCount() {
        return count;
    }

}
