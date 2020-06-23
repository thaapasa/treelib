#!/bin/sh

DB=$1
STATE=$2

if [ x$1 = x ] || [ x$2 = x ] ; then
  echo Usage: $0 database-config state
  exit 
fi

CONFIGFILE=target/classes/$1

if [ ! -r $CONFIGFILE ] ; then
  echo Supplied DB configuration file $CONFIGFILE not found
  exit 
fi 


DBFILE=`grep -o "\"[^\"]*[.]db\"" $CONFIGFILE | sed 's/"//g'`

echo Inspecting DB $DB state $STATE, db stored in file $DBFILE

rm $DBFILE
cp $DBFILE.$STATE $DBFILE

LIBJARS=`find lib/*.jar -printf ":%p"`

java -ea -cp target/classes$LIBJARS fi.hut.cs.treelib.console.TreeConsole $DB


  