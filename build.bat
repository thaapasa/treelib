@echo off

set SRC_PATH=src\main\java
set TARGET_DIR=target\classes
set JAVA_FILES=src\main\java\fi\hut\cs\treelib\*.java src\main\java\fi\hut\cs\treelib\gui\*.java src\main\java\fi\hut\cs\treelib\controller\*.java src\main\java\fi\hut\cs\treelib\btree\*.java src\main\java\fi\hut\cs\treelib\mvbt\*.java src\main\java\fi\hut\cs\treelib\tsbt\*.java

set CLASSPATH=lib\commons-logging.jar;lib\log4j-1.2.15.jar;lib\spring-beans.jar;lib\spring-context-support.jar;lib\spring-context.jar;lib\spring-core.jar;lib\general-utils.jar

mkdir target
mkdir target\classes
echo on

javac -sourcepath %SRC_PATH% -d %TARGET_DIR% -classpath %CLASSPATH% %JAVA_FILES%
