#!/bin/sh

TARGET_DIR=target/classes
RESOURCE_PATH=src/main/resources

CLASSPATH=`find lib -name *.jar -printf "%p:" | sed "s/:$//"`


echo java -classpath $CLASSPATH:$TARGET_DIR:$RESOURCE_PATH fi.hut.cs.treelib.gui.TreeVisualizer
java -classpath $CLASSPATH:$TARGET_DIR:$RESOURCE_PATH fi.hut.cs.treelib.gui.TreeVisualizer
