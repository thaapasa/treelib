package fi.hut.cs.treelib.gui;

import java.awt.Dimension;
import java.awt.Window;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.log4j.Logger;
import org.apache.xmlgraphics.java2d.ps.EPSDocumentGraphics2D;

import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.util.GUIUtils;

public class GUIOperations {

    private static final Logger log = Logger.getLogger(GUIOperations.class);

    public static void saveImage(Window parent, RenderedImage image) {
        log.info("Operation: Save image");
        File file = GUIUtils.chooseImageFile(parent);

        if (file != null) {
            log.info(String.format("Saving tree image to file %s", file.getAbsolutePath()));
            try {
                if (ImageIO.write(image, "png", file)) {
                    log.info("Image saved");
                } else {
                    log.warn("Could not save image");
                }
            } catch (IOException e) {
                log.warn("Could not save image: " + e.getMessage(), e);
            }
        }
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void saveEPS(
        Window parent, TreeDrawer<K, V> drawer) {
        log.info("Operation: Save EPS");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("EPS files", "eps");
        File file = GUIUtils.chooseFile(filter, parent);

        if (file != null) {
            log.info(String.format("Saving tree EPS to file %s", file.getAbsolutePath()));
            try {
                EPSDocumentGraphics2D g2d = new EPSDocumentGraphics2D(false);
                g2d.setGraphicContext(new org.apache.xmlgraphics.java2d.GraphicContext());

                drawer.update();
                Dimension size = drawer.getSize();

                FileOutputStream fos = new FileOutputStream(file);
                // Set up the document size
                g2d.setupDocument(fos, (int) size.getWidth(), (int) size.getHeight());
                drawer.drawTree(g2d);

                // Wrap up and finalize the EPS file
                g2d.finish();

                fos.flush();
                fos.close();
            } catch (IOException e) {
                log.warn("Could not save EPS: " + e.getMessage(), e);
            }
        }
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void saveTikZ(
        Window parent, TreeDrawer<K, V> drawer) {
        log.info("Operation: Save TikZ");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("TikZ TeX files", "tex");
        File file = GUIUtils.chooseFile(filter, parent);

        if (file != null) {
            log.info(String.format("Saving tree TikZ to file %s", file.getAbsolutePath()));
            try {

                FileOutputStream fos = new FileOutputStream(file);
                TreeDrawTikZ<K, V> tikzDrawer = new TreeDrawTikZ<K, V>(parent.getGraphics(),
                    drawer.getScheme(), drawer.getVersion(), fos);

                drawer.setFontMetrics(tikzDrawer.getFontMetrics());
                drawer.update();
                drawer.drawTree(tikzDrawer);

                tikzDrawer.close();

                fos.flush();
                fos.close();
            } catch (IOException e) {
                log.warn("Could not save TikZ: " + e.getMessage(), e);
            }
        }
    }
    

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void saveTikZModel(
        Window parent, TreeDrawer<K, V> drawer) {
        log.info("Operation: Save TikZ model");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("TikZ TeX files", "tex");
        File file = GUIUtils.chooseFile(filter, parent);

        if (file != null) {
            log.info(String.format("Saving TikZ tree model to file %s", file.getAbsolutePath()));
            try {

                FileOutputStream fos = new FileOutputStream(file);
                TreeDrawTikZModel<K, V> modelDrawer = new TreeDrawTikZModel<K, V>(parent.getGraphics(),
                    fos);

                drawer.update();
                drawer.drawTree(modelDrawer);
                modelDrawer.close();

                fos.flush();
                fos.close();
            } catch (IOException e) {
                log.warn("Could not TikZ tree model: " + e.getMessage(), e);
            }
        }
    }

    public static <K extends Key<K>, V extends PageValue<?>, P extends Page<K, V>> void saveSketchModel(
        Window parent, TreeDrawer<K, V> drawer) {
        log.info("Operation: Save Sketch model");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Sketch definition files", "sketch");
        File file = GUIUtils.chooseFile(filter, parent);

        if (file != null) {
            log.info(String.format("Saving Sketch tree model to file %s", file.getAbsolutePath()));
            try {

                FileOutputStream fos = new FileOutputStream(file);
                TreeDrawSketchModel<K, V> modelDrawer = new TreeDrawSketchModel<K, V>(parent.getGraphics(),
                    fos);

                drawer.update();
                drawer.drawTree(modelDrawer);
                modelDrawer.close();

                fos.flush();
                fos.close();
            } catch (IOException e) {
                log.warn("Could not TikZ tree model: " + e.getMessage(), e);
            }
        }
    }
}
