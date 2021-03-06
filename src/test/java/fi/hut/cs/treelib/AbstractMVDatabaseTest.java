package fi.hut.cs.treelib;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import fi.hut.cs.treelib.action.ActionReader;
import fi.hut.cs.treelib.common.CounterCallback;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.data.ActionExecutor;
import fi.hut.cs.treelib.internal.InspectPageOperation;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.storage.MemoryPageStorage;
import fi.hut.cs.treelib.storage.PageStorage;
import fi.hut.cs.treelib.util.KeyRangePredicate;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;
import fi.tuska.util.iterator.ReaderIterator;

public abstract class AbstractMVDatabaseTest<K extends Key<K>, P extends Page<K, StringValue>>
    extends AbstractDatabaseTest<K, P> {

    public static boolean RUN_LARGE_MVDATA_TEST = false;
    private static final boolean DISABLE_LONG_TX_LOGGING = true;

    protected AbstractMVDatabaseTest(K keyPrototype) {
        super(keyPrototype);
    }

    protected MVKeyRange<K> getKeyRange(String k1, String k2, int v1, int v2) {
        return new MVKeyRange<K>(keyProto.parse(k1), keyProto.parse(k2), v1, v2);
    }

    @Override
    protected abstract MVDatabase<K, StringValue, P> createDatabase();

    @Override
    protected abstract MVDatabase<K, StringValue, P> createDatabase(PageStorage storage);

    public void testDBLoad() {
        showTestName();
        PageStorage st = new MemoryPageStorage(350);
        MVDatabase<K, StringValue, P> db = createDatabase(st);
        Transaction<K, StringValue> tx = db.beginTransaction();
        for (int i = 1; i < 50; i++) {
            insert(tx, String.valueOf(i));
        }
        tx.commit();
        int ver1 = tx.getCommitVersion();

        tx = db.beginTransaction();
        for (int i = 50; i < 100; i++) {
            insert(tx, String.valueOf(i));
        }
        tx.commit();
        int ver2 = tx.getCommitVersion();

        for (int v = 0; v < 20; v++) {
            tx = db.beginTransaction();
            insert(tx, String.valueOf(200 + v));
            tx.commit();
        }

        db.flush();

        OwnerImpl.resetID(0);

        db = createDatabase(st);
        tx = db.beginTransaction();
        for (int i = 100; i < 150; i++) {
            insert(tx, String.valueOf(i));
        }
        tx.commit();

        int ver3 = tx.getCommitVersion();

        assertLT(ver1, ver2);
        assertLT(ver2, ver3);

        tx = db.beginReadTransaction(ver1);
        assertTrue(tx.contains(k(1)));
        assertTrue(tx.contains(k(10)));
        assertTrue(tx.contains(k(49)));
        assertFalse(tx.contains(k(50)));
        assertFalse(tx.contains(k(60)));
        assertFalse(tx.contains(k(99)));
        assertFalse(tx.contains(k(100)));
        assertFalse(tx.contains(k(110)));
        tx.commit();

        tx = db.beginReadTransaction(ver2);
        assertTrue(tx.contains(k(1)));
        assertTrue(tx.contains(k(10)));
        assertTrue(tx.contains(k(49)));
        assertTrue(tx.contains(k(50)));
        assertTrue(tx.contains(k(60)));
        assertTrue(tx.contains(k(99)));
        assertFalse(tx.contains(k(100)));
        assertFalse(tx.contains(k(110)));
        tx.commit();

        tx = db.beginReadTransaction(ver3);
        assertTrue(tx.contains(k(1)));
        assertTrue(tx.contains(k(10)));
        assertTrue(tx.contains(k(49)));
        assertTrue(tx.contains(k(50)));
        assertTrue(tx.contains(k(60)));
        assertTrue(tx.contains(k(99)));
        assertTrue(tx.contains(k(100)));
        assertTrue(tx.contains(k(110)));
        tx.commit();

    }

    public void testRangeQuery() {
        showTestName();
        if (!isTransactionSupported())
            return;

        MVDatabase<K, StringValue, P> db = createDatabase();
        Transaction<K, StringValue> tx = db.beginTransaction();
        insert(tx, "4");
        insert(tx, "2");
        insert(tx, "5");
        insert(tx, "1");
        insert(tx, "3");
        tx.commit();
        db.requestMaintenance();

        tx = db.beginTransaction();
        delete(tx, "2");
        insert(tx, "6");
        delete(tx, "3");
        tx.commit();
        int comVer = tx.getCommitVersion();

        tx = db.beginReadTransaction(comVer);
        List<Pair<K, StringValue>> list = tx.getRange(new KeyRangeImpl<K>(keyProto.parse("1"),
            keyProto.parse("7")));
        List<K> keyList = CollectionUtils.getPairFirstList(list);
        assertTrue(keyList.toString(), keyList.contains(keyProto.parse("1")));
        assertTrue(keyList.toString(), keyList.contains(keyProto.parse("4")));
        assertTrue(keyList.toString(), keyList.contains(keyProto.parse("5")));
        assertTrue(keyList.toString(), keyList.contains(keyProto.parse("6")));
        assertFalse(keyList.toString(), keyList.contains(keyProto.parse("2")));
        assertFalse(keyList.toString(), keyList.contains(keyProto.parse("3")));
    }

    public void testTXLogGeneration() {
        showTestName();
        PageStorage storage = new MemoryPageStorage(500);
        MVDatabase<K, StringValue, P> db = createDatabase(storage);
        execute(db, TX_GENERATE_LOG);
        int comVer = db.getCommittedVersion();
        log.debug("Querying committed version " + comVer);
        checkDBAliveCount(db, comVer, 576);
        assertEquals(comVer, db.getCommittedVersion());

        db.flush();
        db = createDatabase(storage);

        assertEquals(comVer, db.getCommittedVersion());
        log.info("Querying DB count for version " + comVer);

        checkDBAliveCount(db, comVer, 576);
        if (isTransactionSupported()) {
            // MVBT does not support transactions so these tests would not
            // pass
            checkDBAliveCount(db, comVer - 1, 571);
            checkDBAliveCount(db, comVer - 2, 572);
        }

        Transaction<K, StringValue> tx = db.beginReadTransaction(comVer);
        CounterCallback<Pair<K, StringValue>> count = new CounterCallback<Pair<K, StringValue>>();
        tx.getRange(KeyRangeImpl.getKeyRange(db.getKeyPrototype()).getEntireKeyRange(), count);
        assertEquals(576, count.getCount());
    }

    private void checkContents(Transaction<K, StringValue> tx, String... values) {
        List<Pair<K, StringValue>> inDB = tx.getRange(keyProto.getEntireRange());
        List<String> valueList = Arrays.asList(values);

        assertEquals(inDB + " size != " + valueList, valueList.size(), inDB.size());

        for (Pair<K, StringValue> entry : inDB) {
            assertTrue(entry + " not in " + valueList, valueList.contains(entry.getSecond()
                .toString()));
        }
    }

    public void testOverlappingTransactions() {
        showTestName();
        if (!isTransactionSupported())
            return;

        MVDatabase<K, StringValue, P> db = createDatabase();

        Transaction<K, StringValue> tx2 = db.beginTransaction();
        insert(tx2, "2");
        insert(tx2, "3");

        if (!isOverlappingTransactionSupported()) {
            try {
                db.beginTransaction();
                fail("Starting of second overlapping transaction did not fail");
            } catch (Exception e) {
                // OK: This action should fail
                // Return, because we cannot continue
                return;
            }
        }
        Transaction<K, StringValue> tx1 = db.beginTransaction();
        insert(tx1, "1");
        insert(tx1, "6");

        tx1.commit();

        Transaction<K, StringValue> tx3 = db.beginReadTransaction(tx1.getCommitVersion());
        checkContents(tx3, "1", "6");

        tx2.commit();
        checkContents(tx3, "1", "6");

        assertTrue(tx2.getCommitVersion() + " < " + tx1.getCommitVersion(), tx2
            .getCommitVersion() > tx1.getCommitVersion());
        Transaction<K, StringValue> tx4 = db.beginReadTransaction(tx2.getCommitVersion());
        checkContents(tx3, "1", "6");
        checkContents(tx4, "1", "2", "3", "6");

        db.requestMaintenance();
        checkContents(tx3, "1", "6");
        checkContents(tx4, "1", "2", "3", "6");
    }

    public void testInsertAndMix() {
        showTestName();
        PageStorage storage = new MemoryPageStorage(2048);
        runTestLog("/transaction-insert-mix.log", storage);
    }

    public void testTXLogGeneration2() {
        showTestName();
        if (!RUN_LARGE_MVDATA_TEST)
            return;
        PageStorage storage = new MemoryPageStorage(4096);
        runTestLog("/transaction-int-tx.log", storage);

        MVDatabase<K, StringValue, P> db = createDatabase(storage);

        InspectPageOperation<K, StringValue> op = new InspectPageOperation<K, StringValue>(db);
        db.traversePages(new KeyRangePredicate<K>(), op, TEST_OWNER);
        System.out.println(op.getSummary());

        assertEquals(4871, op.getAliveEntryCount());
        if (isTransactionSupported()) {
            assertEquals(9797, op.getTotalEntryCount());
        }

        int comVer = db.getCommittedVersion();
        checkDBAliveCount(db, comVer, 4871);
    }

    private void runTestLog(String testLogName, PageStorage storage) {
        MVDatabase<K, StringValue, P> db = createDatabase(storage);
        
        Logger mainLog = Logger.getLogger("fi.hut.cs.treelib");
        Level orgLevel = mainLog.getLevel();
        try {
            if (DISABLE_LONG_TX_LOGGING)
                mainLog.setLevel(Level.WARN);

            InputStream str = getClass().getResourceAsStream(testLogName);
            assertNotNull(str);
            ActionReader<K, StringValue> reader = new ActionReader<K, StringValue>(keyProto,
                StringValue.PROTOTYPE);

            ActionExecutor<K, StringValue> exec = new ActionExecutor<K, StringValue>(db);
            exec.execute(reader.iterator(new ReaderIterator(str)), null);
            mainLog.setLevel(orgLevel);
            db.flush();
        } finally {
            mainLog.setLevel(orgLevel);
        }
    }

    protected boolean isTransactionSupported() {
        return true;
    }

    protected boolean isOverlappingTransactionSupported() {
        return true;
    }

    protected void checkDBAliveCount(MVDatabase<K, StringValue, P> db, int version,
        int expectedCount) {
        Transaction<K, StringValue> tx = db.beginReadTransaction(version);

        CounterCallback<Pair<K, StringValue>> counter = new CounterCallback<Pair<K, StringValue>>();
        tx.getRange(KeyRangeImpl.getKeyRange(db.getKeyPrototype()).getEntireKeyRange(), counter);
        tx.commit();

        assertEquals(expectedCount, counter.getCount());
    }

    /**
     * Creates 576 alive entries at the end
     */
    public static final String[] TX_GENERATE_LOG = { "b", "i;4874188;1", "i;29429489;2",
        "i;88514625;3", "i;99058927;4", "i;49968820;5", "i;96389166;6", "d;4874188",
        "i;81155413;7", "d;29429489", "i;60853574;8", "d;60853574", "c", "b", "i;8105077;9",
        "i;61987528;10", "i;25878327;11", "i;21191438;12", "i;94786777;13", "i;2629734;14",
        "d;61987528", "i;56488067;15", "d;49968820", "d;88514625", "i;17658434;16",
        "i;87252072;17", "d;25878327", "i;99854036;18", "i;18421498;19", "i;1245796;20", "c",
        "b", "i;46620446;21", "i;93916652;22", "i;38673733;23", "d;21191438", "i;41285737;24",
        "i;3578845;25", "i;8606635;26", "i;72276860;27", "i;33939553;28", "i;25546355;29",
        "i;98373286;30", "i;2290167;31", "i;11594180;32", "i;39532029;33", "i;85632594;34",
        "i;54967782;35", "i;99247642;36", "c", "b", "i;2395634;37", "i;81880781;38",
        "i;50054584;39", "d;93916652", "i;84093218;40", "i;81257250;41", "i;62122419;42",
        "i;37813614;43", "i;55876096;44", "c", "b", "i;54594009;45", "i;56026952;46",
        "i;46151099;47", "i;27605306;48", "i;36184840;49", "i;20486631;50", "i;47239882;51", "c",
        "b", "d;56488067", "d;39532029", "i;58710143;52", "i;44237114;53", "d;27605306",
        "i;6688758;54", "d;20486631", "d;81257250", "d;87252072", "i;73985970;55",
        "i;76269739;56", "d;11594180", "d;99247642", "i;10962134;57", "d;38673733", "d;58710143",
        "i;42497780;58", "i;33618159;59", "i;48896729;60", "i;30935341;61", "i;47464001;62",
        "i;98399311;63", "d;81880781", "i;42883494;64", "c", "b", "d;72276860", "d;18421498",
        "d;6688758", "i;51380867;65", "d;2629734", "i;50011366;66", "d;76269739", "d;25546355",
        "i;52351056;67", "d;54967782", "d;85632594", "i;23120975;68", "d;84093218", "c", "b",
        "i;43178775;69", "d;52351056", "i;94868992;70", "i;76170670;71", "d;30935341",
        "d;56026952", "d;50054584", "i;85451515;72", "d;37813614", "i;72954141;73", "d;48896729",
        "d;23120975", "d;55876096", "i;96302158;74", "d;2290167", "d;54594009", "c", "b",
        "i;75049757;75", "i;68493912;76", "d;3578845", "d;17658434", "i;25773683;77",
        "i;49558178;78", "i;13652445;79", "i;99984996;80", "i;72918095;81", "d;51380867",
        "i;19447292;82", "i;77429419;83", "d;13652445", "i;802740;84", "i;10796843;85",
        "i;92623690;86", "i;91786779;87", "d;2395634", "c", "b", "d;68493912", "i;79598417;88",
        "d;62122419", "i;59295315;89", "i;60418551;90", "i;70224937;91", "d;85451515",
        "i;53798835;92", "d;50011366", "i;80273302;93", "i;85286609;94", "i;79524054;95",
        "i;10497765;96", "i;66356975;97", "i;64013564;98", "i;43554417;99", "d;8606635",
        "i;50923047;100", "d;50923047", "i;94565788;101", "i;94732273;102", "i;73872565;103",
        "c", "b", "i;11268072;104", "d;11268072", "d;19447292", "i;31446365;105", "d;8105077",
        "i;89808591;106", "i;3141895;107", "d;3141895", "i;86878453;108", "i;60836462;109",
        "i;86464845;110", "i;71092441;111", "i;17606669;112", "d;64013564", "i;45818637;113",
        "i;88988188;114", "i;75598149;115", "i;94526810;116", "c", "b", "d;36184840",
        "i;64724692;117", "d;91786779", "i;64504032;118", "i;61775752;119", "i;47945987;120",
        "d;17606669", "c", "b", "d;81155413", "i;18356914;121", "i;2005650;122",
        "i;88509008;123", "d;53798835", "d;49558178", "i;55017197;124", "d;55017197",
        "i;96184126;125", "i;23780923;126", "d;71092441", "i;79969898;127", "i;14737286;128",
        "i;23601744;129", "d;47945987", "i;95838357;130", "i;92424629;131", "i;90723834;132",
        "i;91341797;133", "d;44237114", "c", "b", "d;18356914", "i;17191548;134", "d;96389166",
        "d;66356975", "i;95988123;135", "i;44113090;136", "d;46620446", "i;72967286;137",
        "i;90891671;138", "i;94143815;139", "d;17191548", "i;56025626;140", "d;33939553",
        "i;55077046;141", "i;72941548;142", "i;37876799;143", "i;78973101;144", "i;35993180;145",
        "d;70224937", "i;98159725;146", "i;55605033;147", "c", "b", "i;37437455;148",
        "i;3786121;149", "i;11524542;150", "i;51787207;151", "i;12741223;152", "d;94868992",
        "i;16433211;153", "i;46859155;154", "d;47464001", "d;64724692", "i;9757867;155",
        "d;12741223", "d;61775752", "i;60048206;156", "i;70512508;157", "i;52796943;158",
        "d;85286609", "c", "b", "d;25773683", "d;92623690", "i;92064042;159", "d;23780923",
        "d;37876799", "d;60048206", "i;44878739;160", "i;89220271;161", "i;45062918;162",
        "i;72494219;163", "i;86780041;164", "i;45220215;165", "d;96302158", "d;70512508",
        "i;54020053;166", "i;92650886;167", "d;89808591", "d;73985970", "i;77768996;168",
        "d;42497780", "d;76170670", "i;99175164;169", "d;33618159", "c", "b", "i;90442760;170",
        "i;64850556;171", "i;88701531;172", "i;71364619;173", "d;41285737", "i;50839886;174",
        "d;10497765", "d;80273302", "d;2005650", "d;11524542", "c", "b", "i;91850491;175",
        "d;37437455", "i;50044483;176", "i;56797581;177", "i;23659078;178", "d;16433211",
        "i;55433729;179", "i;99868643;180", "i;8753289;181", "i;61469576;182", "i;62948984;183",
        "c", "b", "d;14737286", "i;15815825;184", "i;33366765;185", "i;24960715;186",
        "d;89220271", "i;77296941;187", "d;77429419", "d;56797581", "i;56532730;188",
        "i;94685096;189", "i;61068978;190", "d;90723834", "d;35993180", "d;10962134",
        "i;26416951;191", "i;11371554;192", "i;95775278;193", "i;86673703;194", "i;81101524;195",
        "i;7510088;196", "d;33366765", "i;92747209;197", "c", "b", "d;15815825", "i;1625998;198",
        "d;64850556", "i;28907414;199", "i;11718933;200", "i;96218639;201", "i;49981336;202",
        "i;7795867;203", "d;42883494", "d;50839886", "i;40810741;204", "i;71868160;205",
        "i;37208202;206", "d;1625998", "d;11718933", "i;49344632;207", "i;76949222;208",
        "d;31446365", "i;21466806;209", "d;24960715", "i;61729144;210", "d;75598149",
        "i;44986831;211", "c", "b", "i;29780301;212", "i;61548716;213", "i;67007928;214",
        "i;27683885;215", "d;10796843", "i;196975;216", "d;81101524", "d;46151099",
        "i;55858211;217", "i;4382802;218", "d;11371554", "i;11092411;219", "d;88701531",
        "d;40810741", "i;29606080;220", "i;95104374;221", "i;1001920;222", "i;55753756;223",
        "i;57214431;224", "i;95808485;225", "c", "b", "i;94152234;226", "i;52627364;227",
        "i;70974672;228", "i;51009966;229", "i;47213482;230", "i;99506020;231", "i;81408375;232",
        "i;43069828;233", "d;81408375", "d;86878453", "i;55486299;234", "d;47239882",
        "d;92064042", "i;4953102;235", "d;11092411", "d;70974672", "i;90760603;236",
        "d;52796943", "i;88924730;237", "c", "b", "i;95925175;238", "i;43101544;239",
        "i;13698263;240", "i;81012136;241", "d;81012136", "i;7268609;242", "d;13698263",
        "i;41832773;243", "i;2978936;244", "i;40477816;245", "i;60177151;246", "i;46117991;247",
        "c", "b", "i;817391;248", "d;92747209", "i;77121755;249", "i;35482289;250", "d;96218639",
        "i;86666181;251", "i;49750467;252", "d;37208202", "d;54020053", "i;60291161;253",
        "i;341778;254", "i;71575906;255", "i;72248750;256", "i;70804722;257", "i;43174805;258",
        "i;69609537;259", "i;51723095;260", "i;32327632;261", "i;45748416;262", "i;95133541;263",
        "c", "b", "i;89011973;264", "i;13269121;265", "i;21279803;266", "i;82549721;267",
        "d;82549721", "i;62475720;268", "d;8753289", "d;4953102", "c", "b", "i;15341021;269",
        "i;74544591;270", "i;28679701;271", "i;59858610;272", "i;24209438;273", "i;3496204;274",
        "i;10497919;275", "i;51544833;276", "d;75049757", "i;99333419;277", "i;20347466;278",
        "c", "b", "i;35299010;279", "d;52627364", "d;72967286", "d;89011973", "d;79969898",
        "i;59582077;280", "c", "b", "i;91601208;281", "i;59879575;282", "i;71168445;283",
        "i;29743173;284", "i;68769706;285", "i;26041125;286", "d;47213482", "i;92870283;287",
        "i;41264980;288", "i;43279606;289", "c", "b", "i;41562264;290", "d;69609537",
        "i;18385301;291", "i;15791705;292", "i;19833714;293", "d;77768996", "d;46859155",
        "i;47088749;294", "i;188767;295", "i;83217158;296", "d;68769706", "i;71765967;297",
        "i;52980987;298", "d;61548716", "d;13269121", "i;24691368;299", "d;32327632",
        "i;31195656;300", "i;60868703;301", "i;28903879;302", "i;80238627;303", "d;72954141",
        "i;99807958;304", "c", "b", "i;99623736;305", "d;31195656", "i;59679472;306",
        "i;14074491;307", "i;68516339;308", "i;58788723;309", "i;59662727;310", "i;62343588;311",
        "d;29606080", "d;23601744", "i;48785413;312", "c", "b", "i;58772328;313", "d;96184126",
        "d;80238627", "i;1188095;314", "i;28738013;315", "i;40881986;316", "i;37029961;317",
        "i;65515669;318", "i;70211354;319", "i;41034108;320", "i;42610;321", "i;73348172;322",
        "c", "b", "i;65603952;323", "i;54425504;324", "i;39663679;325", "i;22632051;326",
        "d;68516339", "i;43720068;327", "i;89797359;328", "i;11786375;329", "i;47356426;330",
        "i;56623731;331", "c", "b", "d;15791705", "i;74084158;332", "i;41640135;333",
        "i;76104266;334", "i;5220265;335", "i;76459836;336", "c", "b", "i;41348103;337",
        "i;84483342;338", "d;94152234", "i;2069647;339", "i;47256828;340", "c", "b",
        "i;97299876;341", "i;80907786;342", "i;31732507;343", "i;55974646;344", "i;26456982;345",
        "i;41773497;346", "i;75804697;347", "i;49755040;348", "d;26041125", "c", "b",
        "i;53340533;349", "i;85092519;350", "i;22453139;351", "i;61961709;352", "i;99644702;353",
        "i;87082801;354", "i;77578177;355", "i;11910118;356", "d;55858211", "i;52071950;357",
        "i;5976738;358", "c", "b", "d;88509008", "i;20705870;359", "i;92700024;360",
        "i;87888536;361", "i;10050009;362", "i;78422122;363", "i;39947085;364", "i;66948671;365",
        "d;10050009", "d;95988123", "i;18824827;366", "i;12167156;367", "i;8364707;368",
        "d;5220265", "d;67007928", "d;15341021", "i;87400710;369", "i;45581264;370",
        "i;7390609;371", "d;91850491", "i;55861188;372", "c", "b", "i;72770326;373",
        "i;65500985;374", "i;47485213;375", "d;66948671", "i;51682465;376", "d;8364707",
        "d;40477816", "i;86065778;377", "c", "b", "i;7482396;378", "i;19245633;379", "d;4382802",
        "i;55174704;380", "i;1894354;381", "d;56532730", "i;80878640;382", "i;44308099;383",
        "d;37029961", "i;56131927;384", "i;70880690;385", "d;78973101", "i;53856758;386",
        "d;61961709", "i;56403276;387", "i;55562891;388", "i;98281597;389", "i;85448229;390",
        "c", "b", "i;51528828;391", "d;74544591", "i;22686512;392", "i;38768452;393",
        "d;7795867", "c", "b", "i;94177817;394", "d;41348103", "i;39194646;395",
        "i;77745069;396", "d;92870283", "i;85004744;397", "i;29057443;398", "i;66636266;399",
        "d;41773497", "i;65851613;400", "i;79434413;401", "c", "b", "i;95276352;402",
        "i;82384889;403", "i;27672392;404", "i;86981804;405", "d;92700024", "d;44878739",
        "d;3786121", "i;65050821;406", "i;72183113;407", "i;26467715;408", "i;93187104;409",
        "i;60133967;410", "d;91601208", "d;7510088", "i;18199355;411", "i;95487771;412",
        "d;66636266", "i;38306481;413", "i;98694644;414", "i;55203976;415", "d;55077046",
        "i;84798987;416", "c", "b", "i;14586207;417", "i;74052101;418", "i;31575279;419",
        "i;19938925;420", "d;29780301", "i;91091984;421", "d;12167156", "d;50044483",
        "i;75031723;422", "d;57214431", "d;35299010", "i;50708388;423", "i;41506750;424", "c",
        "b", "i;69824727;425", "i;24184600;426", "i;74493754;427", "i;62293561;428",
        "i;90476264;429", "i;46436709;430", "d;71868160", "c", "b", "d;2069647",
        "i;20354051;431", "i;1490482;432", "i;15040937;433", "i;33347036;434", "d;24209438",
        "i;1191913;435", "i;67815293;436", "d;83217158", "i;95315671;437", "d;70211354",
        "d;15040937", "i;93519189;438", "d;93187104", "c", "b", "i;40354379;439", "d;65851613",
        "i;75607724;440", "i;25826250;441", "d;5976738", "c", "b", "d;79598417", "d;56623731",
        "i;84774884;442", "d;3496204", "d;7482396", "c", "b", "i;60062514;443", "d;62948984",
        "i;96323065;444", "d;21279803", "i;2165861;445", "i;14623826;446", "d;19938925",
        "d;62475720", "i;3024587;447", "i;71041389;448", "i;19769075;449", "i;65377066;450",
        "i;39222303;451", "c", "b", "i;20370584;452", "d;87888536", "i;79325432;453",
        "i;31506579;454", "d;22632051", "d;46436709", "i;16486926;455", "c", "b",
        "i;24529391;456", "i;13302593;457", "d;3024587", "i;2344695;458", "d;2978936",
        "i;50755310;459", "i;93917980;460", "i;11494768;461", "i;91152603;462", "i;58961325;463",
        "c", "b", "i;48139783;464", "i;14212450;465", "d;93917980", "i;37624768;466",
        "i;91075015;467", "d;54425504", "d;80907786", "i;77859171;468", "c", "b",
        "i;89259310;469", "d;50755310", "i;55801216;470", "i;25996334;471", "i;46955038;472",
        "i;74248151;473", "i;1286276;474", "i;84117454;475", "i;65389335;476", "i;11413880;477",
        "i;21088943;478", "i;22297403;479", "i;17465780;480", "i;64877424;481", "i;22822823;482",
        "d;2344695", "i;78882928;483", "i;77839358;484", "i;56761771;485", "i;98567809;486",
        "i;12550022;487", "d;95487771", "i;20934554;488", "c", "b", "i;46146422;489",
        "i;24772084;490", "d;92650886", "i;93794867;491", "i;23117508;492", "i;98883808;493",
        "i;2063103;494", "i;12108993;495", "i;3405103;496", "i;64843512;497", "i;73464245;498",
        "d;84798987", "i;75003816;499", "i;81755598;500", "c", "b", "d;92424629",
        "i;22639307;501", "i;24974248;502", "d;94177817", "i;52464211;503", "i;66732881;504",
        "d;22297403", "d;74248151", "i;39008994;505", "d;21088943", "d;79524054", "d;72183113",
        "i;81177831;506", "i;29701398;507", "i;69977971;508", "i;87495244;509", "i;20234872;510",
        "i;82561325;511", "i;94980124;512", "c", "b", "i;9965183;513", "d;31732507",
        "i;20500255;514", "i;5249198;515", "i;98509523;516", "i;56679240;517", "d;66732881",
        "i;47048579;518", "i;40885842;519", "i;87811578;520", "i;31152751;521", "d;99058927",
        "i;81629285;522", "c", "b", "i;46377628;523", "i;36696772;524", "i;41784127;525",
        "i;14293849;526", "i;91936123;527", "i;50416247;528", "d;40354379", "d;7390609",
        "d;78882928", "i;45206353;529", "i;7659794;530", "d;86065778", "d;21466806",
        "i;79869994;531", "i;11557643;532", "i;54599058;533", "d;9965183", "i;60921717;534",
        "i;80988068;535", "d;1286276", "i;94989688;536", "i;41586194;537", "i;28240785;538", "c",
        "b", "d;60418551", "i;4294013;539", "i;20945924;540", "i;71847760;541", "i;89250407;542",
        "i;8586098;543", "i;29337452;544", "i;11231158;545", "i;47649206;546", "i;90188018;547",
        "d;24772084", "i;28869461;548", "i;16138244;549", "i;96634980;550", "d;79869994",
        "i;56691363;551", "d;77859171", "c", "b", "d;79434413", "d;8586098", "d;45220215",
        "i;68273126;552", "i;36882639;553", "i;69161887;554", "i;18568319;555", "i;7628086;556",
        "d;49755040", "i;69608217;557", "d;75003816", "i;58517075;558", "i;51891211;559",
        "i;51721081;560", "i;30242121;561", "d;76459836", "i;78740490;562", "c", "b",
        "i;11697111;563", "i;47839027;564", "d;78740490", "i;79599597;565", "i;9706902;566",
        "i;20932017;567", "i;34366321;568", "d;99868643", "d;58961325", "d;5249198",
        "i;43450518;569", "d;11910118", "i;73997609;570", "d;4294013", "d;96323065",
        "i;10411744;571", "d;44308099", "i;6171066;572", "i;94732376;573", "d;24974248",
        "i;28055466;574", "i;62793183;575", "i;70633453;576", "c", "b", "i;33131165;577",
        "i;56092787;578", "i;36749854;579", "i;65338781;580", "i;53082607;581", "i;18576319;582",
        "i;2413996;583", "i;11518853;584", "d;41034108", "i;24467196;585", "i;76744021;586",
        "i;19498540;587", "d;68273126", "i;25300564;588", "i;25311483;589", "i;84672740;590",
        "i;79929995;591", "d;69977971", "i;79294152;592", "i;17826700;593", "c", "b",
        "i;88280423;594", "i;66938606;595", "i;7956652;596", "i;49743460;597", "d;49750467",
        "i;44352760;598", "i;43191481;599", "d;76744021", "i;5012059;600", "i;45163965;601",
        "i;45369128;602", "i;95314069;603", "i;34078625;604", "c", "b", "i;31902637;605",
        "i;33046685;606", "i;18623669;607", "i;12725078;608", "i;53627095;609", "i;73010180;610",
        "d;82561325", "i;84156407;611", "i;4598111;612", "i;41676673;613", "i;31921013;614",
        "d;87811578", "d;3405103", "d;23659078", "c", "b", "i;89664990;615", "d;99175164",
        "d;39008994", "i;12287538;616", "i;89401720;617", "d;31921013", "i;45606591;618",
        "i;2142105;619", "i;38896447;620", "i;60662570;621", "i;39484657;622", "i;63031758;623",
        "i;92759188;624", "d;92759188", "d;81755598", "c", "b", "i;74618796;625", "d;82384889",
        "i;6711774;626", "i;27796011;627", "i;81517795;628", "i;40771719;629", "d;23117508",
        "d;7659794", "i;16459441;630", "i;67939172;631", "i;61671155;632", "i;93738007;633",
        "i;81850226;634", "d;40885842", "c", "b", "d;38768452", "i;25295315;635", "d;59679472",
        "i;73507302;636", "i;52648962;637", "c", "b", "i;16288303;638", "i;41778493;639",
        "d;12287538", "i;81998743;640", "i;5871395;641", "i;29478465;642", "i;44543995;643",
        "i;77213625;644", "i;97689696;645", "d;62343588", "d;44352760", "d;16486926",
        "i;11457539;646", "i;68882600;647", "d;33347036", "i;7600326;648", "i;73937972;649",
        "i;18922728;650", "i;17829233;651", "d;95925175", "d;91936123", "i;87447578;652", "c",
        "b", "i;23438306;653", "d;41832773", "i;91267057;654", "i;92284280;655", "d;95838357",
        "d;80988068", "d;22822823", "i;83619982;656", "d;2413996", "d;45369128",
        "i;63084618;657", "i;3840864;658", "i;45312153;659", "d;10497919", "d;35482289",
        "i;24123314;660", "d;18385301", "i;23441011;661", "i;46811220;662", "i;91068937;663",
        "d;39222303", "i;43710216;664", "d;84483342", "d;84156407", "c", "b", "i;15742084;665",
        "i;44084948;666", "i;5076176;667", "i;94638893;668", "d;62293561", "i;21548387;669",
        "i;26577768;670", "i;79999463;671", "d;48139783", "i;91914207;672", "i;40723217;673",
        "i;48511816;674", "i;15028981;675", "d;29478465", "i;11256123;676", "i;45024270;677",
        "i;23917241;678", "d;5076176", "c", "b", "i;20079096;679", "i;90769889;680",
        "i;77006425;681", "i;51168655;682", "d;25996334", "i;35989318;683", "d;99854036",
        "d;54599058", "i;23938245;684", "i;37758561;685", "i;96873069;686", "i;40049716;687",
        "d;17465780", "i;67895359;688", "i;64783221;689", "c", "b", "i;47421785;690",
        "i;90716653;691", "d;88988188", "d;1894354", "i;61430469;692", "c", "b", "i;3265682;693",
        "i;11561060;694", "i;58373027;695", "i;72034224;696", "d;40881986", "i;56286228;697",
        "i;51474771;698", "i;73042550;699", "i;37823267;700", "i;7636399;701", "d;17829233",
        "i;16743499;702", "i;99535374;703", "d;31902637", "d;76104266", "i;46642915;704", "c",
        "b", "i;99960869;705", "d;77296941", "i;56324349;706", "d;24529391", "d;48785413",
        "i;43667546;707", "d;79999463", "i;53235405;708", "d;7956652", "i;34902308;709",
        "d;6171066", "d;70880690", "i;90299594;710", "d;56761771", "d;91341797",
        "i;60920505;711", "i;90609230;712", "i;32081462;713", "i;89388915;714", "i;75197012;715",
        "d;83619982", "i;26288349;716", "d;91914207", "d;18922728", "c", "b", "i;60118625;717",
        "i;89348752;718", "i;90375154;719", "i;82474576;720", "i;3114143;721", "i;257982;722",
        "d;69161887", "i;28574396;723", "d;73348172", "i;94303029;724", "i;49730289;725",
        "i;76941703;726", "d;25311483", "i;39018089;727", "i;54007754;728", "d;18824827",
        "i;74456163;729", "d;72034224", "i;2884033;730", "i;13121289;731", "c", "b",
        "i;33375134;732", "i;97294694;733", "d;51009966", "d;87495244", "i;49722736;734",
        "d;56691363", "i;27164401;735", "i;76364185;736", "i;13342208;737", "i;73833499;738",
        "d;74084158", "d;65050821", "i;47744696;739", "i;27690624;740", "d;78422122",
        "i;8894144;741", "d;64504032", "d;41784127", "i;36531057;742", "d;16743499",
        "d;39018089", "c", "b", "d;63084618", "i;27827147;743", "i;62738745;744",
        "i;60369200;745", "i;5395379;746", "i;64067694;747", "d;95104374", "i;19230356;748",
        "i;65408553;749", "i;25563459;750", "i;65874437;751", "i;24779119;752", "i;43383504;753",
        "c", "b", "i;30144740;754", "i;95758722;755", "i;81129770;756", "i;31802882;757",
        "d;60369200", "i;12913806;758", "i;13944552;759", "c", "b", "d;43554417",
        "i;30022658;760", "d;92284280", "i;9444679;761", "i;70160263;762", "i;87686899;763",
        "d;16459441", "i;17321831;764", "i;34399647;765", "i;42082158;766", "d;46955038",
        "i;73375914;767", "d;13342208", "d;39947085", "d;81850226", "i;17724439;768",
        "i;62954625;769", "i;46101658;770", "d;16288303", "i;20073379;771", "i;52737346;772",
        "i;52423264;773", "i;87549830;774", "c", "b", "i;74808907;775", "i;84934819;776",
        "i;94348440;777", "d;14074491", "d;95315671", "d;5012059", "i;59552818;778",
        "i;89034936;779", "i;21433007;780", "i;59228698;781", "d;56131927", "i;87099608;782",
        "i;45153251;783", "i;89556893;784", "i;49054253;785", "i;76383676;786", "i;87339402;787",
        "i;64831318;788", "i;81905486;789", "i;20038362;790", "d;70160263", "i;72771447;791",
        "c", "b", "d;77839358", "i;50167237;792", "i;89617860;793", "i;82628093;794",
        "i;56469870;795", "i;80653719;796", "d;43450518", "i;1851558;797", "i;90340043;798",
        "i;1148991;799", "d;54007754", "i;9630954;800", "i;38898176;801", "d;60291161",
        "i;9769699;802", "d;48511816", "i;80248614;803", "d;4598111", "c", "b", "d;59228698",
        "i;98245036;804", "d;7636399", "i;83481016;805", "d;22686512", "d;24184600", "c", "b",
        "d;5871395", "i;69242822;806", "i;10608334;807", "d;37823267", "d;59879575",
        "i;80007892;808", "i;99460782;809", "i;37750769;810", "i;44860540;811", "i;91186476;812",
        "i;96053981;813", "i;23332133;814", "d;29057443", "d;52648962", "i;85056426;815",
        "i;13186942;816", "i;22470988;817", "i;17456109;818", "i;75297496;819", "i;71971314;820",
        "d;26577768", "i;80581755;821", "i;67303048;822", "c", "b", "i;86838710;823",
        "d;91267057", "d;12108993", "i;99910951;824", "i;91551034;825", "i;18448284;826",
        "i;36118655;827", "i;81596996;828", "i;43825111;829", "i;25995105;830", "c", "b",
        "i;72657878;831", "i;75911273;832", "d;6711774", "i;88621068;833", "d;14623826",
        "d;89797359", "i;73372782;834", "c", "b", "i;40060210;835", "d;53082607",
        "i;8763473;836", "i;94174209;837", "d;56679240", "i;99645405;838", "i;32899126;839",
        "i;47685306;840", "i;80617160;841", "i;83182579;842", "i;95240547;843", "c", "b",
        "i;86729432;844", "i;50756415;845", "i;10948929;846", "i;84332414;847", "i;54554007;848",
        "i;32200606;849", "i;91167953;850", "i;51593223;851", "d;47839027", "i;42774416;852",
        "i;13629386;853", "d;56469870", "c", "b", "d;38306481", "i;29511557;854",
        "i;70920536;855", "d;91551034", "i;27857396;856", "i;14182000;857", "d;81998743",
        "i;37543019;858", "d;32200606", "i;81758684;859", "i;51004997;860", "d;42610",
        "d;41640135", "i;413178;861", "i;46859180;862", "d;56403276", "i;79626553;863",
        "i;42586783;864", "d;53856758", "c", "b", "i;48129459;865", "i;47409998;866",
        "i;23265656;867", "i;68580827;868", "i;20439367;869", "i;83849971;870", "i;83795493;871",
        "i;57494076;872", "i;69398735;873", "i;5281330;874", "i;27403381;875", "i;18982566;876",
        "i;33312715;877", "i;36525861;878", "d;56025626", "i;90841485;879", "i;82684246;880",
        "i;42131738;881", "c", "b", "i;67329070;882", "d;61729144", "d;39484657", "d;3114143",
        "i;44797021;883", "i;32909569;884", "d;16138244", "i;61900112;885", "d;15028981",
        "i;61093058;886", "i;29799019;887", "i;74460703;888", "d;54554007", "i;93242336;889",
        "i;99205815;890", "i;69705879;891", "i;87498963;892", "d;64067694", "c", "b",
        "i;74419422;893", "i;11653847;894", "i;79857430;895", "i;90310430;896", "i;91861321;897",
        "i;2228969;898", "d;3840864", "i;34519578;899", "i;23341034;900", "i;41322297;901", "c",
        "b", "d;45818637", "d;61900112", "i;99681392;902", "i;81948416;903", "i;38636431;904",
        "i;30433466;905", "d;51593223", "i;79708180;906", "d;27403381", "d;72494219",
        "d;80007892", "c", "b", "i;37817729;907", "i;14348674;908", "i;57819256;909",
        "i;52676531;910", "i;91639928;911", "d;77745069", "d;93794867", "i;25227388;912",
        "i;59955169;913", "i;56344370;914", "i;41574153;915", "c", "b", "i;5082822;916",
        "d;13944552", "d;31575279", "i;86957165;917", "i;2356210;918", "i;80927018;919",
        "i;10362033;920", "d;91861321", "i;66850910;921", "i;99755397;922", "i;50742434;923",
        "i;4363186;924", "i;70118330;925", "i;1202013;926", "i;79053068;927", "i;64031020;928",
        "i;72159251;929", "d;59662727", "c", "b", "i;42491505;930", "i;85800090;931",
        "i;7207521;932", "i;1355039;933", "i;44824662;934", "i;33786313;935", "i;33679716;936",
        "d;11786375", "i;64254801;937", "i;28055595;938", "i;19643919;939", "i;8598210;940",
        "i;75183883;941", "i;40342589;942", "d;77006425", "d;65603952", "d;82628093",
        "i;42347140;943", "c", "b", "i;15525787;944", "d;9769699", "d;31152751",
        "i;67487920;945", "i;59539281;946", "i;91028116;947", "i;68253074;948", "i;34981084;949",
        "i;61794207;950", "i;31492885;951", "i;25391081;952", "i;63724995;953", "d;49743460",
        "d;65515669", "i;33403631;954", "d;72657878", "i;62463844;955", "i;20680554;956",
        "i;97929143;957", "d;24467196", "i;70485521;958", "i;92845228;959", "c", "b",
        "i;47458399;960", "i;19118914;961", "i;22772905;962", "i;8634621;963", "i;9881764;964",
        "d;85448229", "i;31578935;965", "d;23441011", "d;86464845", "i;53360453;966",
        "d;88621068", "i;35647144;967", "c", "b", "d;71168445", "i;1637917;968", "i;4700813;969",
        "i;42300646;970", "i;2232883;971", "i;7580628;972", "i;76338812;973", "i;63084175;974",
        "i;50813960;975", "i;7099178;976", "i;17108755;977", "i;91490964;978", "i;15394130;979",
        "i;84349766;980", "i;30470464;981", "i;12396096;982", "i;39388189;983", "i;86935946;984",
        "c", "b", "i;66269028;985", "d;41264980", "i;71348738;986", "i;40434896;987",
        "d;76949222", "i;99080332;988", "d;59582077", "i;1838636;989", "c", "b", "d;81948416",
        "i;35665283;990", "i;11636555;991", "i;48238970;992", "i;58349081;993", "i;54174618;994",
        "i;46606838;995", "i;44448135;996", "i;90278681;997", "i;69022433;998", "i;47335592;999",
        "d;71041389", "i;66888356;1000", "c", "b", "i;50858864;1001", "d;40060210",
        "i;24473635;1002", "d;49054253", "d;73042550", "c", "b", "d;41506750", "i;14726427;1003",
        "d;14726427", "d;77578177", "d;66269028", "d;5395379", "i;69254210;1004", "d;21433007",
        "i;34405884;1005", "d;37817729", "i;6831668;1006", "i;33424625;1007", "i;95650626;1008",
        "i;50523763;1009", "i;36133821;1010", "i;76237768;1011", "i;68598220;1012",
        "i;40980864;1013", "i;1455441;1014", "i;13877364;1015", "d;5281330", "i;25413048;1016",
        "d;90891671", "c" };
}
