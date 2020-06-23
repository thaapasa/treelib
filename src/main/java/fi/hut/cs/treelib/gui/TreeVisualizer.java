package fi.hut.cs.treelib.gui;

import org.apache.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TreeVisualizer {

    private static final Logger log = Logger.getLogger(TreeVisualizer.class);

    public static final String[] CONFIG_PATHS = new String[] { "classpath:visualizer.xml" };

    public static void main(String[] args) {
        log.info("Initializing application context");
        new ClassPathXmlApplicationContext(CONFIG_PATHS);
    }

}
