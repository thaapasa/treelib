package fi.hut.cs.treelib.internal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.propertyeditors.CustomDateEditor;

import fi.hut.cs.treelib.common.FloatKey;
import fi.hut.cs.treelib.common.FloatKeyEditor;
import fi.hut.cs.treelib.common.IntegerKey;
import fi.hut.cs.treelib.common.IntegerKeyEditor;
import fi.hut.cs.treelib.common.IntegerValue;
import fi.hut.cs.treelib.common.IntegerValueEditor;
import fi.tuska.util.file.CustomFileEditor;

public class TreelibPropertyEditorRegistrar implements PropertyEditorRegistrar {

    @Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {
        registry.registerCustomEditor(Date.class, new CustomDateEditor(new SimpleDateFormat(
            "dd.MM.yyyy"), false));
        registry.registerCustomEditor(FloatKey.class, new FloatKeyEditor());
        registry.registerCustomEditor(IntegerKey.class, new IntegerKeyEditor());
        registry.registerCustomEditor(IntegerValue.class, new IntegerValueEditor());
        registry.registerCustomEditor(File.class, new CustomFileEditor());
    }

}
