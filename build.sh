#!/bin/sh

SRC_PATH=src/main/java
TARGET_DIR=target/classes
JAVA_FILES=`find src/main/java -name *.java`

CLASSPATH=`find lib -name *.jar -printf "%p:" | sed "s/:$//"`

mkdir -p target/classes

PARAMS="-Xlint -target 1.6 -source 1.6 -sourcepath $SRC_PATH -d $TARGET_DIR -classpath $CLASSPATH $JAVA_FILES"

#echo javac $PARAMS
#echo $PARAMS | xargs javac
echo $CLASSPATH
