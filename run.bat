@echo off

set TARGET_DIR=target\classes
set RESOURCE_PATH=src\main\resources

set CLASSPATH=lib\commons-logging.jar;lib\log4j-1.2.15.jar;lib\spring-beans.jar;lib\spring-context-support.jar;lib\spring-context.jar;lib\spring-core.jar

echo on

java -classpath %CLASSPATH%;%TARGET_DIR%;%RESOURCE_PATH% fi.hut.cs.treelib.gui.TreeVisualizer
