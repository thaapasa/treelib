package fi.hut.cs.treelib.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;

import fi.hut.cs.treelib.Configuration;
import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MBR;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.action.Action;
import fi.hut.cs.treelib.action.ActionReader;
import fi.hut.cs.treelib.btree.BTreeDatabase;
import fi.hut.cs.treelib.btree.BTreeDatabaseTest;
import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.MDSMOPolicy;
import fi.hut.cs.treelib.common.NonThrashingSMOPolicy;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.console.TreeConsole;
import fi.hut.cs.treelib.controller.TreeCreator;
import fi.hut.cs.treelib.data.ActionExecutor;
import fi.hut.cs.treelib.mdtree.JTreeDatabase;
import fi.hut.cs.treelib.mdtree.JTreeDatabaseTest;
import fi.hut.cs.treelib.mvbt.MVBTDatabase;
import fi.hut.cs.treelib.mvbt.MVBTDatabaseTest;
import fi.hut.cs.treelib.mvbt.TMVBTDatabase;
import fi.hut.cs.treelib.mvbt.TMVBTDatabaseTest;
import fi.hut.cs.treelib.rtree.RTreeDatabase;
import fi.hut.cs.treelib.rtree.RTreeDatabaseTest;
import fi.hut.cs.treelib.stats.NoStatistics;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.StreamUtils;
import fi.tuska.util.iterator.Iterables;
import fi.tuska.util.iterator.ReaderIterator;

@SuppressWarnings("unused")
public class TreeTester {

    public static final IntegerKey keyProto = IntegerKey.PROTOTYPE;
    public static final StringValue valueProto = StringValue.PROTOTYPE;
    public static final FloatKey floatProto = FloatKey.PROTOTYPE;
    public static final MBR<FloatKey> MBRProto = new MBR<FloatKey>(2, floatProto, false);

    private static final int BTREE_PAGE_SIZE = BTreeDatabaseTest.PAGE_SIZE;
    private static final int MVBTREE_PAGE_SIZE = MVBTDatabaseTest.PAGE_SIZE;
    private static final int TMVBTREE_PAGE_SIZE = TMVBTDatabaseTest.PAGE_SIZE;
    private static final int JTREE_PAGE_SIZE = JTreeDatabaseTest.PAGE_SIZE;
    private static final int RTREE_PAGE_SIZE = RTreeDatabaseTest.PAGE_SIZE;

    public static final String[] OPERATIONS_1 = new String[] { "begin", "insert;1;1",
        "insert;2;2", "insert;10;10", "insert;15;15", "insert;3;3", "insert;4;4", "insert;7;7",
        "delete;15", "delete;10", "delete;7" };

    public static final String[] OPERATIONS_2 = new String[] { "begin", "insert;1;1",
        "insert;2;2", "insert;10;10", "insert;15;15", "insert;3;3", "insert;4;4", "insert;7;7",
        "commit", "begin", "delete;3", "delete;2" };

    public static final String[] INSERT_OPS;
    public static final String[] REVERSE_INSERT_OPS;

    public static final String[] INSERT_MBR_OPS;
    public static final String[] REVERSE_INSERT_MBR_OPS;

    public static final String[] FILLED_TREE_2 = new String[] { "b", "i;10;10", "i;5;5", "i;7;7",
        "i;15;15", "i;123;123", "i;3;3", "i;4;4", "i;18;18", "i;24;24", "i;25;25" };

    public static final int INSERT_DELETE_AMOUNT = 1000;

    private static final SMOPolicy SMO_POLICY = new NonThrashingSMOPolicy(0.2, 0.2);
    private static final SMOPolicy MD_SMO_POLICY = new MDSMOPolicy(0.25, 0.375);

    static {
        final int tlen = INSERT_DELETE_AMOUNT * 2 + 2;
        REVERSE_INSERT_OPS = new String[tlen];
        INSERT_OPS = new String[tlen];
        REVERSE_INSERT_MBR_OPS = new String[tlen];
        INSERT_MBR_OPS = new String[tlen];
        INSERT_OPS[0] = "b";
        REVERSE_INSERT_OPS[0] = "b";
        INSERT_MBR_OPS[0] = "b";
        REVERSE_INSERT_MBR_OPS[0] = "b";
        int c = 1;
        for (c = 1; c <= INSERT_DELETE_AMOUNT; c++) {
            INSERT_OPS[c] = "i;" + c;
            REVERSE_INSERT_OPS[c] = "i;" + (INSERT_DELETE_AMOUNT - c);
            INSERT_MBR_OPS[c] = "i;" + MBRProto.fromInt(c).toString();
            REVERSE_INSERT_MBR_OPS[c] = "i;"
                + MBRProto.fromInt(INSERT_DELETE_AMOUNT - c).toString();
        }
        for (c = 1; c <= INSERT_DELETE_AMOUNT; c++) {
            INSERT_OPS[c + INSERT_DELETE_AMOUNT] = "d;" + c;
            REVERSE_INSERT_OPS[c + INSERT_DELETE_AMOUNT] = "d;" + (INSERT_DELETE_AMOUNT - c);
            INSERT_MBR_OPS[c + INSERT_DELETE_AMOUNT] = "d;" + MBRProto.fromInt(c).toString();
            REVERSE_INSERT_MBR_OPS[c + INSERT_DELETE_AMOUNT] = "d;"
                + MBRProto.fromInt(INSERT_DELETE_AMOUNT - c).toString();
        }
        INSERT_OPS[tlen - 1] = "c";
        REVERSE_INSERT_OPS[tlen - 1] = "c";
        INSERT_MBR_OPS[tlen - 1] = "c";
        REVERSE_INSERT_MBR_OPS[tlen - 1] = "c";
    }

    protected static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> List<String> getOperationList(
        String operationFile, K keyProto, V valueProto) {
        InputStream str = TreeTester.class.getResourceAsStream(operationFile);
        assert str != null;
        List<String> lines = StreamUtils.readFromStream(str);
        StreamUtils.tryToClose(str);
        return lines;
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void testDBWithGUI(
        Database<K, V, P> db, K keyProto, String[] operations, int shift) throws IOException {
        testDBWithGUI(db, keyProto, CollectionUtils.toList(operations), shift);
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void testDBWithGUI(
        Database<K, V, P> db, K keyProto, List<String> operations, int shift) throws IOException {
        TreeCreator<K, V> creator = new TreeCreator<K, V>(db, operations, keyProto);
        creator.setInitialOperationCount(operations.size() + shift);
        TreeVisualizerGUI<K, V> gui = new TreeVisualizerGUI<K, V>(db, keyProto, creator,
            NoStatistics.instance());

        creator.initComponent();
        gui.start();
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void testDBNoGUI(
        Database<K, V, P> db, K keyProto, String[] operations, int shift) throws IOException {
        testDBNoGUI(db, keyProto, CollectionUtils.toList(operations), shift);
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void testDBNoGUI(
        Database<K, V, P> db, K keyProto, List<String> operations, int shift) throws IOException {
        TreeCreator<K, V> creator = new TreeCreator<K, V>(db, operations, keyProto);
        creator.setInitialOperationCount(operations.size() + shift);

        creator.initComponent();

        TreeConsole<K, V> console = new TreeConsole<K, V>();
        console.setDatabase(db);
        console.run();
    }

    public static void main(String[] args) throws IOException {
        Configuration c = Configuration.instance();
        // Configuration.instance().setCheckConsistency(true);

        c.setLeafPageSizeLimit(10);
        c.setIndexPageSizeLimit(10);
        c.setLimitPageSizes(true);
       
        TMVBTDatabase<IntegerKey, StringValue> tmvbt = new TMVBTDatabase<IntegerKey, StringValue>(
            32, SMO_POLICY, keyProto, valueProto, new MemoryPageStorage(TMVBTREE_PAGE_SIZE));

        MVBTDatabase<IntegerKey, StringValue> mvbt = new MVBTDatabase<IntegerKey, StringValue>(
            32, SMO_POLICY, keyProto, valueProto, new MemoryPageStorage(MVBTREE_PAGE_SIZE));

        BTreeDatabase<IntegerKey, StringValue> btdb = new BTreeDatabase<IntegerKey, StringValue>(
            32, SMO_POLICY, keyProto, valueProto, new MemoryPageStorage(BTREE_PAGE_SIZE));

        JTreeDatabase<FloatKey, StringValue> jtreedb = new JTreeDatabase<FloatKey, StringValue>(
            32, MD_SMO_POLICY, new MBR<FloatKey>(2, FloatKey.PROTOTYPE, false),
            StringValue.PROTOTYPE, new MemoryPageStorage(JTREE_PAGE_SIZE));

        RTreeDatabase<FloatKey, StringValue> rtreedb = new RTreeDatabase<FloatKey, StringValue>(
            32, MD_SMO_POLICY, new MBR<FloatKey>(2, FloatKey.PROTOTYPE, false),
            StringValue.PROTOTYPE, new MemoryPageStorage(RTREE_PAGE_SIZE));

        // testDBNoGUI(tmvbt, keyProto, INSERT_OPS, -109);
        testDBWithGUI(tmvbt, keyProto, getOperationList("/transaction-model.log", keyProto, valueProto), 0);

        // testDBWithGUI(tmvbt, keyProto,
        // TMVBTDatabaseTest.TMVBT_DECREASE_HEIGHT,
        // -100);
        // testDBWithGUI(btdb, keyProto, OPERATIONS_2, 0);
        // testDBWithGUI(jtreedb, MBRProto, INSERT_MBR_OPS, -117);
        // testDBWithGUI(jtreedb, MBRProto, AbstractMDDatabaseTest.OPERATIONS,
        // -36);
    }
}
