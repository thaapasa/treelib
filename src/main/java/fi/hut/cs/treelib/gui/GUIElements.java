package fi.hut.cs.treelib.gui;

import java.util.List;

import javax.swing.JFrame;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.common.OwnerImpl;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.StoredPage;

public class GUIElements {

    private static final Logger log = Logger.getLogger(GUIElements.class);

    public static final Owner VISUALIZER_OWNER = new OwnerImpl("visualizer");

    public static PageID selectPageID(JFrame frame, String title) {
        QueryPopup query = new QueryPopup(title, frame, new QueryPopup.QueryLine("Page ID",
            PageID.PROTOTYPE));

        List<Object> values = query.show();
        if (values == null)
            return null;
        PageID pageID = (PageID) values.get(0);
        return pageID;
    }

    /**
     * @return the fixed page. Remember to release the page!
     */
    public static StoredPage selectPage(JFrame frame, String title, PageBuffer buffer, Owner owner) {
        PageID pageID = selectPageID(frame, title);
        if (pageID == null)
            return null;

        StoredPage page = buffer.fixPage(pageID, owner);
        if (page != null) {
            return page;
        }

        log.info("No page with given ID found");
        return null;
    }
}
