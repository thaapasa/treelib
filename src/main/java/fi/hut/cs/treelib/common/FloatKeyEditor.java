package fi.hut.cs.treelib.common;

import java.beans.PropertyEditorSupport;

/**
 * A simple editor class for providing support to Spring for converting
 * strings in the Spring configuration files into FloatKeys.
 * 
 * @author thaapasa
 */
public class FloatKeyEditor extends PropertyEditorSupport {

    @Override
    public String getAsText() {
        FloatKey key = (FloatKey) getValue();
        return key.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        setValue(FloatKey.PROTOTYPE.parse(text));
    }

}
