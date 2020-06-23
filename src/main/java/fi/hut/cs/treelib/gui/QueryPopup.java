package fi.hut.cs.treelib.gui;

import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import org.apache.log4j.Logger;

import fi.tuska.util.StringParser;

public class QueryPopup implements ActionListener, KeyListener {

    private static final Logger log = Logger.getLogger(QueryPopup.class);

    private QueryLine[] queries;
    private JDialog dialog;
    private JButton okButton;
    private JButton cancelButton;
    private Boolean result;

    public QueryPopup(String title, JFrame parent, QueryLine... queries) {
        this.queries = queries;
        this.dialog = new JDialog(parent, title, true);
        this.result = null;
        createLayout();
    }

    private void createLayout() {
        Container main = dialog.getContentPane();
        dialog.addKeyListener(this);

        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        main.setLayout(layout);

        c.gridwidth = GridBagConstraints.REMAINDER;

        // Add query lines
        for (QueryLine query : queries) {
            Container cont = new Container();
            cont.setLayout(new FlowLayout());
            query.render(cont, this);

            main.add(cont);
            layout.addLayoutComponent(cont, c);
        }

        // Add OK button
        okButton = new JButton("OK");
        okButton.addActionListener(this);
        c.gridwidth = 1;
        main.add(okButton);
        layout.addLayoutComponent(okButton, c);
        okButton.addKeyListener(this);

        // Add cancel button
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        c.gridwidth = GridBagConstraints.REMAINDER;
        main.add(cancelButton);
        layout.addLayoutComponent(cancelButton, c);
        cancelButton.addKeyListener(this);

        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.pack();
    }

    public static class QueryLine {
        private String label;
        private JTextField input;
        private StringParser<? extends Object> format;

        public QueryLine(String label, StringParser<? extends Object> format) {
            this.label = label;
            this.input = new JTextField(20);
            this.format = format;
        }

        public <E> QueryLine(String label, E value, StringParser<E> format) {
            this.label = label;
            this.input = new JTextField(20);
            this.format = format;
            this.input.setText(format.write(value));
        }

        protected void render(Container container, KeyListener listener) {
            container.add(new JLabel(label));
            container.add(input);
            input.addKeyListener(listener);
        }

        public String getLabel() {
            return label;
        }

        public Object getValue() {
            String text = input.getText();
            return format.parse(text);
        }

        public boolean isValid() {
            String text = input.getText();
            return format.isValid(text);
        }
    }

    public List<Object> show() {
        dialog.setVisible(true);
        return result ? getAnswers() : null;
    }

    private List<Object> getAnswers() {
        List<Object> answers = new ArrayList<Object>(queries.length);
        for (QueryLine query : queries) {
            answers.add(query.getValue());
        }
        return answers;
    }

    private void okPressed() {
        log.debug("OK button pressed");
        // Check for validity
        for (QueryLine query : queries) {
            if (!query.isValid()) {
                JOptionPane.showMessageDialog(dialog, query.getLabel() + " is not valid");
                return;
            }
        }
        // Set result to break the wait loop
        result = Boolean.TRUE;
        dialog.setVisible(false);
    }

    private void cancelPressed() {
        log.debug("Cancel button pressed");
        // Set result to break the wait loop
        result = Boolean.FALSE;
        dialog.setVisible(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            okPressed();
        } else if (e.getSource() == cancelButton) {
            cancelPressed();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            cancelPressed();
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            okPressed();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}
