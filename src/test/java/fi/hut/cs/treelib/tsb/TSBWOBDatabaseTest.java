package fi.hut.cs.treelib.tsb;

import fi.hut.cs.treelib.tsb.TSBOperations.SplitPolicy;

public class TSBWOBDatabaseTest extends AbstractTSBDatabaseTest {

    public TSBWOBDatabaseTest() {
        super(SplitPolicy.WOB, false);
    }

}
