package fi.hut.cs.treelib.common;

import java.beans.PropertyEditorSupport;

/**
 * A simple editor class for providing support to Spring for converting
 * strings in the Spring configuration files into IntegerValues.
 * 
 * @author thaapasa
 */
public class IntegerValueEditor extends PropertyEditorSupport {

    @Override
    public String getAsText() {
        IntegerValue key = (IntegerValue) getValue();
        return key.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        setValue(IntegerValue.PROTOTYPE.parse(text));
    }

}
