package fi.hut.cs.treelib.test;

import java.io.File;
import java.util.EnumMap;
import java.util.Random;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.Transaction;
import fi.tuska.util.math.GaussianRandom;
import fi.tuska.util.math.UniformRandom;

public class Workload implements InitializingBean {

    private static final Logger log = Logger.getLogger(Workload.class);

    public static enum Type {
        KEY_QUERY, RANGE_QUERY, INSERT, DELETE
    };

    public static final Random RANDOM = new Random();

    private EnumMap<Type, Double> txTypeProbabilities = new EnumMap<Type, Double>(Type.class);
    private EnumMap<Type, Integer> txTypeLimits = new EnumMap<Type, Integer>(Type.class);
    private EnumMap<Type, Integer> txTypeAmounts = new EnumMap<Type, Integer>(Type.class);

    private double rangeSize = 0.01;
    private Random keyRandom;

    private boolean exactAmounts = false;

    private int numTransactions;
    private int txLength;

    public static enum Distribution {
        Uniform, Gaussian
    };

    private Distribution distribution;

    private File workloadDir;
    private String filename;

    private KeyBounds keyBounds;
    private TestState basedOnState;

    /**
     * 0 = only current, -x means to randomize a version between 0 and -x (+
     * current ver)
     */
    private int historicalVersions;

    public Workload() {
        for (Type type : Type.values()) {
            txTypeProbabilities.put(type, 0d);
        }
        setDistribution(Distribution.Uniform);
    }

    public void resetLimits() {
        for (Type type : Type.values()) {
            txTypeAmounts.put(type, 0);
            txTypeLimits.put(type, exactAmounts ? (int) Math.ceil(txTypeProbabilities.get(type)
                * numTransactions) : Integer.MAX_VALUE);

        }
    }

    public Type selectNextType(Database<?, ?, ?> db) {
        double val = RANDOM.nextDouble();

        // Find out maximum cumulative probabilities of selectable types
        double maxP = 0;
        for (Type type : Type.values()) {
            if (isSelectable(type, db))
                maxP += txTypeProbabilities.get(type);
        }
        // If there are less than 100% types selectable, scale down the random
        // selection
        if (maxP < 1)
            val *= maxP;

        for (Type type : Type.values()) {
            if (isSelectable(type, db)) {
                double prob = txTypeProbabilities.get(type);
                if (val < prob) {
                    // Select this type
                    txTypeAmounts.put(type, txTypeAmounts.get(type) + 1);
                    return type;
                } else {
                    val -= prob;
                }
            }
        }
        // Not reached
        assert false;
        return null;
    }

    private <K extends Key<K>, V extends PageValue<?>> boolean isSelectable(Type type,
        Database<K, V, ?> db) {
        if (type == Type.DELETE) {
            Transaction<K, V> tx = db.beginReadTransaction(db.getCommittedVersion());
            boolean empty = db.isEmpty(tx);
            tx.abort();
            if (empty)
                return false;
        }
        int curAmount = txTypeAmounts.get(type);
        return curAmount < txTypeLimits.get(type);
    }

    public double getRangeSize() {
        return rangeSize;
    }

    public <K extends Key<K>> K getRangeSize(K proto) {
        return keyBounds.getRangeSize(proto, rangeSize);
    }

    public void setRangeSize(double rangeSize) {
        this.rangeSize = rangeSize;
    }

    @Override
    public void afterPropertiesSet() {
        // Consistency checks
        double totalAmount = 0;
        for (Type type : Type.values()) {
            totalAmount += txTypeProbabilities.get(type);
        }

        if (totalAmount > 1) {
            // Scale the amounts down
            for (Type type : Type.values()) {
                txTypeProbabilities.put(type, txTypeProbabilities.get(type) / totalAmount);
            }
        }
        if (totalAmount < 1) {
            txTypeProbabilities.put(Type.KEY_QUERY, (1 - totalAmount));
            assert txTypeProbabilities.get(Type.KEY_QUERY) <= 1;
        }
        // Should be one now
        totalAmount = 0;
        for (Type type : Type.values()) {
            totalAmount += txTypeProbabilities.get(type);
        }

        resetLimits();

        if (log.isDebugEnabled())
            log.debug(String.format("Workload amounts: %.2f (%d/%d), %.2f (%d/%d), "
                + "%.2f (%d/%d), %.2f (%d/%d) = %.2f", txTypeProbabilities.get(Type.KEY_QUERY),
                txTypeAmounts.get(Type.KEY_QUERY), txTypeLimits.get(Type.KEY_QUERY),
                txTypeProbabilities.get(Type.RANGE_QUERY), txTypeAmounts.get(Type.RANGE_QUERY),
                txTypeLimits.get(Type.RANGE_QUERY), txTypeProbabilities.get(Type.INSERT),
                txTypeAmounts.get(Type.INSERT), txTypeLimits.get(Type.INSERT),
                txTypeProbabilities.get(Type.DELETE), txTypeAmounts.get(Type.DELETE),
                txTypeLimits.get(Type.DELETE), totalAmount));

        if (keyBounds == null)
            keyBounds = new KeyBounds();
    }

    public boolean isExactAmounts() {
        return exactAmounts;
    }

    public void setExactAmounts(boolean exactAmounts) {
        this.exactAmounts = exactAmounts;
    }

    public int getNumTransactions() {
        return numTransactions;
    }

    @Required
    public void setNumTransactions(int numTransactions) {
        this.numTransactions = numTransactions;
    }

    public File getWorkloadDir() {
        return workloadDir;
    }

    @Required
    public void setWorkloadDir(File workloadDir) {
        this.workloadDir = workloadDir;
        if (!workloadDir.exists()) {
            log.error("Workload directory does not exist");
        }
    }

    public String getFilename() {
        return filename;
    }

    @Required
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public File getWorkloadFile() {
        assert workloadDir.exists();
        return new File(workloadDir, filename);
    }

    public double getKeyQueryAmount() {
        return txTypeProbabilities.get(Type.KEY_QUERY);
    }

    public void setKeyQueryAmount(double keyQueryAmount) {
        txTypeProbabilities.put(Type.KEY_QUERY, keyQueryAmount);
    }

    public double getRangeQueryAmount() {
        return txTypeProbabilities.get(Type.RANGE_QUERY);
    }

    public void setRangeQueryAmount(double rangeQueryAmount) {
        txTypeProbabilities.put(Type.RANGE_QUERY, rangeQueryAmount);
    }

    public double getInsertAmount() {
        return txTypeProbabilities.get(Type.INSERT);
    }

    public void setInsertAmount(double insertAmount) {
        txTypeProbabilities.put(Type.INSERT, insertAmount);
    }

    public double getDeleteAmount() {
        return txTypeProbabilities.get(Type.DELETE);
    }

    public void setDeleteAmount(double deleteAmount) {
        txTypeProbabilities.put(Type.DELETE, deleteAmount);
    }

    public TestState getBasedOnState() {
        return basedOnState;
    }

    public void setBasedOnState(TestState basedOnState) {
        this.basedOnState = basedOnState;
    }

    public int getTransactionLength() {
        return txLength;
    }

    @Required
    public void setTransactionLength(int txLength) {
        this.txLength = txLength;
    }

    public void setTxLength(int txLength) {
        this.txLength = txLength;
    }

    public KeyBounds getKeyBounds() {
        return keyBounds;
    }

    public void setKeyBounds(KeyBounds keyBounds) {
        this.keyBounds = keyBounds;
    }

    public int getHistoricalVersions() {
        return historicalVersions;
    }

    public void setHistoricalVersions(int historicalVersions) {
        this.historicalVersions = historicalVersions;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public void setDistribution(Distribution distribution) {
        if (distribution.equals(Distribution.Uniform)) {
            this.keyRandom = new UniformRandom();
        } else if (distribution.equals(Distribution.Gaussian)) {
            this.keyRandom = new GaussianRandom();
        } else {
            throw new IllegalArgumentException("Unknown distribution " + distribution);
        }
        this.distribution = distribution;
    }

    public Random getKeyRandom() {
        return keyRandom;
    }

    @Override
    public String toString() {
        return String.format("Workload %s (%s)", filename, distribution.toString());
    }

}
