package fi.hut.cs.treelib;

import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.MDSMOPolicy;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.common.StringValue;
import fi.hut.cs.treelib.storage.PageStorage;

public abstract class AbstractMDDatabaseTest<P extends MDPage<FloatKey, StringValue, ?>> extends
    AbstractDatabaseTest<MBR<FloatKey>, P> {

    protected static final MBR<FloatKey> KEY_PROTO = new MBR<FloatKey>(2, FloatKey.PROTOTYPE,
        false);

    protected static final SMOPolicy MD_SMO_POLICY = new MDSMOPolicy(0.25, 0.375);

    public static final String[] OPERATIONS = new String[] { "b", "i;{(4.0,23.0),(7.0,26.0)};1",
        "i;{(11.0,29.0),(14.0,32.0)};3", "i;{(11.0,25.0),(14.0,28.0)};5",
        "i;{(16.0,10.0),(19.0,34.0)};7", "i;{(10.0,16.0),(18.0,22.0)};9",
        "i;{(22.0,17.0),(24.0,32.0)};11", "i;{(20.0,23.0),(23.0,27.0)};13",
        "i;{(1.0,2.0),(5.0,6.0)};15", "i;{(6.0,4.0),(15.0,14.0)};17",
        "i;{(26.0,10.0),(36.0,16.0)};19", "i;{(28.0,3.0),(32.0,19.0)};21",
        "i;{(30.0,8.0),(35.0,13.0)};23", "i;{(28.0,22.0),(34.0,26.0)};25", "c", "b",
        "i;{(17.0,45.0),(27.0,55.0)};27", "i;{(38.0,9.0),(47.0,19.0)};29",
        "i;{(30.0,16.0),(39.0,26.0)};31", "i;{(33.0,31.0),(43.0,41.0)};33",
        "i;{(37.0,29.0),(47.0,39.0)};35", "i;{(39.0,38.0),(49.0,48.0)};37",
        "i;{(83.0,16.0),(92.0,26.0)};39", "i;{(7.0,16.0),(17.0,26.0)};41",
        "i;{(88.0,82.0),(98.0,92.0)};43", "i;{(4.0,80.0),(14.0,90.0)};45",
        "i;{(33.0,44.0),(42.0,54.0)};47", "i;{(53.0,47.0),(62.0,57.0)};49",
        "i;{(26.0,25.0),(36.0,35.0)};51", "i;{(27.0,41.0),(37.0,51.0)};53",
        "i;{(63.0,50.0),(73.0,60.0)};55", "i;{(43.0,66.0),(53.0,76.0)};57",
        "i;{(27.0,25.0),(37.0,35.0)};59", "i;{(65.0,3.0),(75.0,13.0)};61",
        "i;{(48.0,65.0),(58.0,75.0)};63", "i;{(31.0,64.0),(41.0,73.0)};65",
        "i;{(31.0,56.0),(41.0,66.0)};67", "i;{(83.0,58.0),(92.0,68.0)};69",
        "i;{(19.0,66.0),(28.0,76.0)};71", "i;{(12.0,28.0),(22.0,38.0)};73",
        "i;{(60.0,43.0),(70.0,53.0)};75", "i;{(68.0,47.0),(78.0,57.0)};77",
        "i;{(3.0,57.0),(13.0,67.0)};79", "i;{(44.0,28.0),(54.0,38.0)};81",
        "i;{(81.0,87.0),(91.0,96.0)};83", "i;{(63.0,75.0),(73.0,85.0)};85",
        "i;{(59.0,8.0),(69.0,18.0)};87" };

    public static final String[] PARENT_SHRINK_OPS = new String[] { "b",
        "i;{(4.0,23.0):(7.0,26.0)};1", "i;{(11.0,29.0):(14.0,32.0)};3",
        "i;{(11.0,25.0):(14.0,28.0)};5", "i;{(16.0,10.0):(19.0,34.0)};7",
        "i;{(10.0,16.0):(18.0,22.0)};9", "i;{(22.0,17.0):(24.0,32.0)};11",
        "i;{(20.0,23.0):(23.0,27.0)};13", "i;{(1.0,2.0):(5.0,6.0)};15",
        "i;{(6.0,4.0):(15.0,14.0)};17", "i;{(26.0,10.0):(36.0,16.0)};19",
        "i;{(28.0,3.0):(32.0,19.0)};21", "i;{(30.0,8.0):(35.0,13.0)};23",
        "i;{(28.0,22.0):(34.0,26.0)};25", "c", "b", "i;{(17.0,45.0):(27.0,55.0)};27",
        "i;{(38.0,9.0):(47.0,19.0)};29", "i;{(30.0,16.0):(39.0,26.0)};31",
        "i;{(33.0,31.0):(43.0,41.0)};33", "i;{(37.0,29.0):(47.0,39.0)};35",
        "d;{(1.0,2.0):(5.0,6.0)}", "d;{(4.0,23.0):(7.0,26.0)}" };

    protected AbstractMDDatabaseTest() {
        super(KEY_PROTO);
    }

    @Override
    protected abstract MDDatabase<FloatKey, StringValue, P> createDatabase();

    @Override
    protected abstract MDDatabase<FloatKey, StringValue, P> createDatabase(PageStorage storage);

    public void testOperations() {
        MDDatabase<FloatKey, StringValue, P> db = createDatabase();
        execute(db, OPERATIONS);

        MDTree<FloatKey, StringValue, ?> tree = db.getDatabaseTree();
        assertTrue(tree.contains(KEY_PROTO.parse("1 2 5 6"), dummyTX));
        assertEquals("15", tree.get(KEY_PROTO.parse("1 2 5 6"), dummyTX).getValue());
        assertFalse(tree.contains(KEY_PROTO.parse("1 2 6 6"), dummyTX));
        assertNull(tree.get(KEY_PROTO.parse("1 2 6 6"), dummyTX));

        assertTrue(tree.contains(KEY_PROTO.parse("(12.0,28.0):(22.0,38.0)"), dummyTX));
        assertEquals("73", tree.get(KEY_PROTO.parse("(12.0,28.0):(22.0,38.0)"), dummyTX)
            .getValue());
        assertTrue(tree.contains(KEY_PROTO.parse("(11.0,29.0):(14.0,32.0)"), dummyTX));
        assertEquals("3", tree.get(KEY_PROTO.parse("(11.0,29.0):(14.0,32.0)"), dummyTX)
            .getValue());
    }

    @Override
    public MBR<FloatKey> parse(String value) {
        FloatKey x1 = keyProto.getLow(0).parse(value);
        FloatKey x2 = x1.fromInt(x1.toInt() + 3);
        FloatKey y1 = x1.fromInt(x1.toInt() + 2);
        FloatKey y2 = x1.fromInt(x1.toInt() + 4);
        MBR<FloatKey> key = new MBR<FloatKey>(new Coordinate<FloatKey>(x1, x2),
            new Coordinate<FloatKey>(y1, y2), false);
        return key;
    }

    public MBR<FloatKey> getMBR(float x1, float x2, float y1, float y2) {
        return new MBR<FloatKey>(new FloatKey(x1), new FloatKey(x2), new FloatKey(y1),
            new FloatKey(y2));
    }

    @Override
    protected MBR<FloatKey> k(int key) {
        return KEY_PROTO.parse("0 0 0 " + key);
    }

    @Override
    public final void testGetRange() {
        // Skipped
        log.info("testGetRange() skipped for MD trees");
    }

    @Override
    public final void testGetRangeExtensive() {
        // Skipped
        log.info("testGetRangeExtensive() skipped for MD trees");
    }
}
