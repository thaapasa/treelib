package fi.hut.cs.treelib.gui;

import java.awt.Font;
import java.awt.FontMetrics;

public class DummyFontMetrics extends FontMetrics {

    private static final long serialVersionUID = -4049993565994818863L;

    public DummyFontMetrics() {
        super(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
    }

    @Override
    public int stringWidth(String str) {
        return 25;
    }

}
