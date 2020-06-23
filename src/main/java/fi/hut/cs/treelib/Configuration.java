package fi.hut.cs.treelib;

import org.apache.log4j.Logger;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;

/**
 * General configuration settings for the application.
 * 
 * @author thaapasa
 */
public class Configuration {

    private static final Logger log = Logger.getLogger(Configuration.class);
    public static final String BEAN_NAME = "configuration";

    private static Configuration instance;

    /** Limit page sizes to a small value (for visualization). */
    private boolean limitPageSizes = false;
    /** The page size limit value for index pages (see above). */
    private int indexPageSizeLimit = 5;
    /** The page size limit value for leaf pages (see above). */
    private int leafPageSizeLimit = 5;
    /** String max length, when storing strings. */
    private int maxStringLength = 20;
    /** Use page type as name prefix (L/I) instead of plain P? */
    private boolean typedPageNames = true;
    /** Debug: Flush pages from page buffer after each action? */
    private boolean flushPagesAfterActions = false;
    /** Show statistics after running program execution? */
    private boolean showStatistics = true;
    /** Use a simple calculation to center pages in visualizer? */
    private boolean simplePageCentering = true;
    /** Do consistency checks? */
    private boolean checkConsistency = false;
    /** Use R*-style split in R-tree? */
    private boolean useRStarSplit = false;
    /**
     * Fill ratio to use when bulk-loading pages; from 0 to 1 (1 means that
     * pages will be full).
     */
    private double bulkLoadFillRatio = 0.9;
    /**
     * Items to skip when running operations from a key file. Null means no
     * skip.
     */
    private Long operationsSkip = null;
    /** Max operations to run from a key file. Null means no limit. */
    private Long operationsLimit = null;
    /**
     * If positive, will show a progress indication after this many steps has
     * been run.
     */
    private int showProgress = 0;
    /** The frequency f how often maintenance is run on the DB (after f TX). */
    private int maintenanceFrequency = 1;
    /**
     * True to show logical page contents; false to show actual stored
     * entries.
     */
    private boolean showLogicalPageContents = false;

    /** True to show entry values in leaf pages; false otherwise. */
    private boolean showLeafEntryValues = true;

    public Configuration() {
        if (instance == null) {
            log.debug("TreeLib configuration created.");
            instance = this;
        } else {
            throw new RuntimeException("Duplicate instance of TreeLib configuration created");
        }
    }

    public static Configuration instance() {
        if (instance == null) {
            log.warn("TreeLib configuration instance requested, but it has not been created. "
                + "Creating default configuration.");
            new Configuration();
        }
        assert instance != null;
        return instance;
    }

    public void readConfiguration(String paramLine) {
        if (paramLine == null || paramLine.equals(""))
            return;
        MutablePropertyValues pvs = new MutablePropertyValues();
        String[] params = paramLine.split(",");
        for (String param : params) {
            String[] kvl = param.split("=", 2);
            String key = kvl[0];
            String value = kvl[1];
            log.info("Setting configuration property " + key + " to " + value);
            pvs.addPropertyValue(key, value);
        }
        DataBinder binder = new DataBinder(this);
        binder.setIgnoreInvalidFields(false);
        binder.setIgnoreUnknownFields(false);
        log.debug("Binding property values");
        binder.bind(pvs);
        BindingResult res = binder.getBindingResult();
        assert !res.hasErrors();
    }

    public void readConfiguration(String[] paramSrc) {
        if (paramSrc == null || paramSrc.equals(""))
            return;
        for (String paramLine : paramSrc) {
            readConfiguration(paramLine);
        }
    }

    /**
     * @return the maximum amount of bytes available for storing string
     * values.
     */
    public int getMaxStringLength() {
        return maxStringLength;
    }

    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    public boolean isTypedPageNames() {
        return typedPageNames;
    }

    public void setTypedPageNames(boolean typedPageNames) {
        this.typedPageNames = typedPageNames;
    }

    public boolean isLimitPageSizes() {
        return limitPageSizes;
    }

    public void setLimitPageSizes(boolean limitPageSizes) {
        this.limitPageSizes = limitPageSizes;
    }

    public int getIndexPageSizeLimit() {
        return indexPageSizeLimit;
    }

    public void setIndexPageSizeLimit(int pageSizeLimit) {
        this.indexPageSizeLimit = pageSizeLimit;
    }

    public int getLeafPageSizeLimit() {
        return leafPageSizeLimit;
    }

    public void setLeafPageSizeLimit(int pageSizeLimit) {
        this.leafPageSizeLimit = pageSizeLimit;
    }

    public boolean isFlushPagesAfterActions() {
        return flushPagesAfterActions;
    }

    public void setFlushPagesAfterActions(boolean flushPagesAfterActions) {
        this.flushPagesAfterActions = flushPagesAfterActions;
    }

    public boolean isShowStatistics() {
        return showStatistics;
    }

    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
    }

    public int getShowProgress() {
        return showProgress;
    }

    public void setShowProgress(int showProgress) {
        this.showProgress = showProgress;
    }

    public boolean isSimplePageCentering() {
        return simplePageCentering;
    }

    public void setSimplePageCentering(boolean simplePageCentering) {
        this.simplePageCentering = simplePageCentering;
    }

    public boolean isCheckConsistency() {
        return checkConsistency;
    }

    public void setCheckConsistency(boolean checkConsistency) {
        this.checkConsistency = checkConsistency;
    }

    public boolean isUseRStarSplit() {
        return useRStarSplit;
    }

    public void setUseRStarSplit(boolean useRStarSplit) {
        this.useRStarSplit = useRStarSplit;
    }

    public double getBulkLoadFillRatio() {
        return bulkLoadFillRatio;
    }

    public void setBulkLoadFillRatio(double bulkLoadFillRatio) {
        this.bulkLoadFillRatio = bulkLoadFillRatio;
    }

    public Long getOperationsSkip() {
        return operationsSkip;
    }

    public void setOperationsSkip(Long operationsSkip) {
        this.operationsSkip = operationsSkip;
    }

    public Long getOperationsLimit() {
        return operationsLimit;
    }

    public void setOperationsLimit(Long operationsLimit) {
        this.operationsLimit = operationsLimit;
    }

    public void setMaintenanceFrequency(int maintenanceFrequency) {
        this.maintenanceFrequency = maintenanceFrequency;
    }

    public int getMaintenanceFrequency() {
        return maintenanceFrequency;
    }

    public boolean isShowLogicalPageContents() {
        return showLogicalPageContents;
    }

    public void setShowLogicalPageContents(boolean logicalPageContents) {
        this.showLogicalPageContents = logicalPageContents;
    }

    public boolean isShowLeafEntryValues() {
        return showLeafEntryValues;
    }

    public void setShowLeafEntryValues(boolean showLeafEntryValues) {
        this.showLeafEntryValues = showLeafEntryValues;
    }

}
