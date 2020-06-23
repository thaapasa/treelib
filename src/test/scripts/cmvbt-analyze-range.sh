#!/bin/sh

RESDIR=results/cmvbt-perf
SCRIPTDIR=`dirname $0`

TESTS="1 5 10 20 50"
DBS="cmvbt tsbt"
STAGES="initial updated updated-2 deleted"

for DB in $DBS ; do
for T in $TESTS ; do

TEST="range-$T"
SUMMARYFILE=$RESDIR/summary/$DB-$TEST.log
echo "Analyzing range for $DB, $TEST in $SCRIPTDIR to $SUMMARYFILE"
rm -f $SUMMARYFILE

echo "# Test (range) for $DB, $TEST" >>$SUMMARYFILE  

S=0

for STAGE in $STAGES; do

FILE=$RESDIR/$DB-$STAGE-$TEST.log

# dos2unix $FILE >/dev/null 2>&1; 
cat $FILE | awk -f $SCRIPTDIR/cmvbt-analyze.awk | sed s/,/./g | xargs echo $S >>$SUMMARYFILE

S=`expr $S + 1`

done
done
done

