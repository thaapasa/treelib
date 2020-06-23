package fi.hut.cs.treelib.gui;

import java.awt.Color;
import java.awt.Font;

public abstract class TreeDrawStyle {

    private Color pageBorderColor = new Color(0, 0, 0);
    private Color pageBGColor = new Color(255, 255, 255);
    private Color pageTextColor = new Color(0, 0, 0);
    private Color pageParentLinkColor = new Color(0, 0, 0);
    private Color pagePeerLinkColor = new Color(0, 0, 0);

    private Color deadPageBgColor = new Color(255, 255, 255);
    private Color deadPageParentLinkColor = new Color(0, 0, 0);
    private Color deadPageTextColor = new Color(0, 0, 0);

    private Color bgColor = new Color(255, 255, 255);
    private Color titleColor = new Color(0, 0, 0);

    private Font font = new Font("sans-serif", 0, 10);

    private boolean showTexts = true;

    private TreeDrawStyle() {
    }

    public static final TreeDrawStyle BLACK_AND_WHITE = new TreeDrawStyle() {
        // As-is
    };

    public static final TreeDrawStyle SCREEN_ACTIVE_TX = new TreeDrawStyle() {
        {
            // Constructor
            setPageBorderColor(new Color(0, 32, 0));
            setPagePeerLinkColor(new Color(196, 196, 196));
            setBgColor(new Color(255, 255, 255));
            setTitleColor(new Color(0, 0, 0));

            setPageTextColor(new Color(0, 0, 64));
            setDeadPageTextColor(new Color(196, 128, 128));

            setPageBGColor(new Color(218, 255, 218));
            setDeadPageBgColor(new Color(196, 196, 196));

            setPageParentLinkColor(new Color(0, 0, 64));
            setDeadPageParentLinkColor(new Color(196, 197, 255));
        }
    };

    public static final TreeDrawStyle SCREEN_NO_TX = new TreeDrawStyle() {
        {
            // Constructor
            setPageBorderColor(new Color(0, 32, 0));
            setPagePeerLinkColor(new Color(196, 196, 196));
            setBgColor(new Color(255, 255, 255));
            setTitleColor(new Color(0, 0, 0));

            setPageTextColor(new Color(0, 0, 64));
            setDeadPageTextColor(new Color(196, 128, 128));

            setPageBGColor(new Color(230, 240, 230));
            setDeadPageBgColor(new Color(196, 196, 196));

            setPageParentLinkColor(new Color(0, 0, 64));
            setDeadPageParentLinkColor(new Color(196, 197, 255));
        }
    };

    public static final TreeDrawStyle EPS = new TreeDrawStyle() {
        {
            // Constructor
            setDeadPageBgColor(new Color(196, 196, 196));
            setShowTexts(false);
        }
    };

    public Color getPageBorderColor() {
        return pageBorderColor;
    }

    public void setPageBorderColor(Color pageBorderColor) {
        this.pageBorderColor = pageBorderColor;
    }

    public Color getPageBGColor() {
        return pageBGColor;
    }

    public void setPageBGColor(Color pageBGColor) {
        this.pageBGColor = pageBGColor;
    }

    public Color getPageTextColor() {
        return pageTextColor;
    }

    public void setPageTextColor(Color pageTextColor) {
        this.pageTextColor = pageTextColor;
    }

    public Color getPageParentLinkColor() {
        return pageParentLinkColor;
    }

    public void setPageParentLinkColor(Color pageParentLinkColor) {
        this.pageParentLinkColor = pageParentLinkColor;
    }

    public Color getBgColor() {
        return bgColor;
    }

    public void setBgColor(Color bgColor) {
        this.bgColor = bgColor;
    }

    public Color getTitleColor() {
        return titleColor;
    }

    public void setTitleColor(Color titleColor) {
        this.titleColor = titleColor;
    }

    public Color getPagePeerLinkColor() {
        return pagePeerLinkColor;
    }

    public void setPagePeerLinkColor(Color pagePeerLinkColor) {
        this.pagePeerLinkColor = pagePeerLinkColor;
    }

    public Color getDeadPageParentLinkColor() {
        return deadPageParentLinkColor;
    }

    public void setDeadPageParentLinkColor(Color deadPageParentLinkColor) {
        this.deadPageParentLinkColor = deadPageParentLinkColor;
    }

    public Color getDeadPageBgColor() {
        return deadPageBgColor;
    }

    public void setDeadPageBgColor(Color deadPageBgColor) {
        this.deadPageBgColor = deadPageBgColor;
    }

    public Color getDeadPageTextColor() {
        return deadPageTextColor;
    }

    public void setDeadPageTextColor(Color deadPageTextColor) {
        this.deadPageTextColor = deadPageTextColor;
    }

    public boolean isShowTexts() {
        return showTexts;
    }

    public void setShowTexts(boolean showTexts) {
        this.showTexts = showTexts;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public int getFontHeight() {
        return (int) (font.getSize() * 1.3);
    }
}
