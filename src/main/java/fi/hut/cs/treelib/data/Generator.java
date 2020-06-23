package fi.hut.cs.treelib.data;

public interface Generator<K> {

    K generate();

    K getPrototype();

}
