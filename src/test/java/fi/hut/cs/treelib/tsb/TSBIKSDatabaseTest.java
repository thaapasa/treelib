package fi.hut.cs.treelib.tsb;

import fi.hut.cs.treelib.tsb.TSBOperations.SplitPolicy;

public class TSBIKSDatabaseTest extends AbstractTSBDatabaseTest {

    public TSBIKSDatabaseTest() {
        super(SplitPolicy.IKS, false);
    }

}
