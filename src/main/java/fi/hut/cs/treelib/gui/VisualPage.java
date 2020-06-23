package fi.hut.cs.treelib.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;

import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.PageValue;

/**
 * Pages that implement this class define how they are drawn. Pages that do
 * not implement this interface are drawn using default settings.
 * 
 * @author thaapasa
 */
public interface VisualPage {

    /**
     * @return the size of the page.
     */
    Dimension getPageDrawSize(int version, int fontHeight, TreeDrawStyle scheme,
        FontMetrics metrics);

    /**
     * @return the text to print at the page
     */
    TextLine[] getPageText(int version, TreeDrawStyle scheme);

    /**
     * @return the text to print at the page
     */
    TextLine[] getPageName(int version, TreeDrawStyle scheme);

    /**
     * @return the page color at the given version; or null to use the default
     * page color
     */
    Color getPageColor(int version, TreeDrawStyle scheme);

    /**
     * @return the page parent link color at the given version; or null to use
     * the default color
     */
    Color getPageParentLinkColor(int version, TreeDrawStyle scheme);

    public class TextLine {
        public String text;
        public Color textColor;
        public KeyRange<?> keyRange;
        public PageValue<?> value;

        public TextLine(String text) {
            this.text = text;
        }

        public TextLine(String text, KeyRange<?> range, PageValue<?> value) {
            this.text = text;
            this.keyRange = range;
            this.value = value;
        }

        public TextLine(String text, Color textColor) {
            this.text = text;
            this.textColor = textColor;
        }

        public TextLine(String text, Color textColor, KeyRange<?> range, PageValue<?> value) {
            this.text = text;
            this.textColor = textColor;
            this.keyRange = range;
            this.value = value;
        }

        public static TextLine[] toTextLines(String[] text) {
            TextLine[] data = new TextLine[text.length];
            for (int i = 0; i < text.length; i++) {
                data[i] = new TextLine(text[i]);
            }
            return data;
        }

        public String getText() {
            return text;
        }

        public Color getTextColor() {
            return textColor;
        }

        public Color getTextColor(Color defaultColor) {
            return textColor != null ? textColor : defaultColor;
        }
    }

}
