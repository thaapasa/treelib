package fi.hut.cs.treelib.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.filechooser.FileNameExtensionFilter;

import fi.tuska.util.file.FileNameUtils;

/**
 * GUI utilities.
 * 
 * @author thaapasa
 */
public class GUIUtils {

    public static File currentDir = new File(".");

    private GUIUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Shows a log file chooser.
     * 
     * @param parent the parent component
     * @return the selected log file; or null, if file selection aborted.
     */
    public static File chooseLogFile(Component parent) {
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Operation log files", "log");
        return chooseFile(filter, parent);
    }

    /**
     * Shows a PNG image file chooser.
     * 
     * @param parent the parent component
     * @return the selected PNG image file; or null, if file selection was
     * aborted.
     */
    public static File chooseImageFile(Component parent) {
        FileNameExtensionFilter filter = new FileNameExtensionFilter("PNG files", "png");
        return chooseFile(filter, parent);
    }

    /**
     * Shows a file chooser. Checks that the file name contains an extension
     * of the specified type.
     * 
     * @param filter the file name filter to use
     * @param parent the parent component
     * @return the selected file; or null, if the file selection was aborted.
     */
    public static File chooseFile(FileNameExtensionFilter filter, Component parent) {
        JFileChooser chooser = new JFileChooser(currentDir);

        chooser.setFileFilter(filter);
        int returnVal = chooser.showSaveDialog(parent);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            currentDir = file.getParentFile();
            return FileNameUtils.forceExtension(file, filter.getExtensions()[0]);
        }
        return null;
    }

    /**
     * Shows a modal text dialog containing the given text lines and an OK
     * button for closing it.
     * 
     * @param title the dialog title
     * @param text the text to show
     * @param owner the owner of the dialog
     */
    public static void showTextDialog(String title, String[] text, Frame owner) {
        final JDialog dialog = new JDialog(owner, title, true);
        Container outer = dialog.getContentPane();
        outer.setLayout(new FlowLayout());
        Container main = new Container();
        outer.add(main);
        GridBagLayout gb = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        main.setLayout(gb);
        // Text lines
        for (String line : text) {
            JLabel label = new JLabel(line);
            main.add(label);
            gb.addLayoutComponent(label, c);
        }
        // OK button
        JButton ok = new JButton(new AbstractAction("OK") {
            private static final long serialVersionUID = -5932658639440083444L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(false);
            }
        });
        main.add(ok);
        c.anchor = GridBagConstraints.CENTER;
        gb.addLayoutComponent(ok, c);
        dialog.pack();
        dialog.setVisible(true);
    }
}
