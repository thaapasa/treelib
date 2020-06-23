package fi.hut.cs.treelib.common;

import java.beans.PropertyEditorSupport;

/**
 * A simple editor class for providing support to Spring for converting
 * strings in the Spring configuration files into IntegerKeys.
 * 
 * @author thaapasa
 */
public class IntegerKeyEditor extends PropertyEditorSupport {

    @Override
    public String getAsText() {
        IntegerKey key = (IntegerKey) getValue();
        return key.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        setValue(IntegerKey.PROTOTYPE.parse(text));
    }

}
