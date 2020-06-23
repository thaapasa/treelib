package fi.hut.cs.treelib.console;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Database;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.MDPage;
import fi.hut.cs.treelib.MDTree;
import fi.hut.cs.treelib.gui.GUIElements;
import fi.hut.cs.treelib.storage.PageBuffer;

public class PageDrawerGUI<K extends Key<K>> implements KeyListener {

    private static final Logger log = Logger.getLogger(PageDrawerGUI.class);

    private PageDrawer<K> drawer;
    private PageDrawerCanvas<K> canvas;
    private JFrame frame;

    private PageBuffer buffer;

    @SuppressWarnings("unchecked")
    public PageDrawerGUI(Database<K, ?, ?> database) {
        buffer = database.getDatabaseTree().getPageBuffer();

        MDTree<K, ?, ?> mdTree = (MDTree<K, ?, ?>) database.getDatabaseTree();

        this.drawer = new PageDrawer<K>(mdTree.getExtents(), mdTree.getPageBuffer());
        this.canvas = new PageDrawerCanvas<K>(drawer);
        this.frame = new JFrame("Page Drawer");
        Container main = this.frame.getContentPane();

        main.setLayout(new BorderLayout());
        main.add(canvas, BorderLayout.CENTER);

        this.frame.addKeyListener(this);

        this.frame.setVisible(true);
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
        final char c = Character.toLowerCase(e.getKeyChar());
        switch (c) {

        case 'q':
            hide();
            break;

        case 'p':
            addPage();
            break;

        case 'u':
            update();
            break;

        default:
            break;
        }
    }

    public void show() {
        frame.setVisible(true);
        frame.pack();
    }

    public void hide() {
        drawer.clear();
        canvas.update();
        frame.setVisible(false);
    }

    @SuppressWarnings("unchecked")
    private void addPage() {
        log.info("Operation: Add page to page drawer");
        MDPage<K, ?, ?> page = (MDPage<K, ?, ?>) GUIElements.selectPage(frame,
            "Select page to show", buffer, GUIElements.VISUALIZER_OWNER);

        if (page != null) {
            drawer.addPage(page);
            buffer.unfix(page, GUIElements.VISUALIZER_OWNER);
            update();
        }
    }

    private void update() {
        log.debug("Updating page drawer");
        canvas.update();
    }

}
