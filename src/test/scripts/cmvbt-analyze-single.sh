#!/bin/sh

TEST=$1
RESDIR=results/cmvbt-perf
SCRIPTDIR=`dirname $0`

DBS="cmvbt tsbt"
STAGES="initial updated updated-2 deleted"

for DB in $DBS ; do

SUMMARYFILE=$RESDIR/summary/$DB-$TEST.log
echo "Analyzing $TEST in $SCRIPTDIR to $SUMMARYFILE"
rm -f $SUMMARYFILE
echo "# Test $TEST for $DB" >>$SUMMARYFILE  

S=0

for STAGE in $STAGES ; do

FILE=$RESDIR/$DB-$STAGE-$TEST.log

# dos2unix $FILE >/dev/null 2>&1; 
cat $FILE | awk -f $SCRIPTDIR/cmvbt-analyze.awk | sed s/,/./g | xargs echo $S >>$SUMMARYFILE

S=`expr $S + 1`

done
done
