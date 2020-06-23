#!/bin/sh

STAGE=$1
TXLEN=$2
RESDIR=results/cmvbt-perf
SCRIPTDIR=`dirname $0`

TESTS="0 10 20 50 100"
DBS="cmvbt-1 cmvbt-5 cmvbt-10 cmvbt-50 tsbt"

for DB in $DBS ; do

SUMMARYFILE=$RESDIR/summary/$DB-qu-$TXLEN-$STAGE.log
echo "Analyzing query-update for $DB ($TXLEN, $STAGE) in $SCRIPTDIR to $SUMMARYFILE"
rm -f $SUMMARYFILE

echo "# Test (query-update) for $DB ($STAGE)" >>$SUMMARYFILE  

for T in $TESTS ; do

FILE=$RESDIR/$DB-$STAGE-qu-$TXLEN-$T.log

# dos2unix $FILE >/dev/null 2>&1; 
cat $FILE | awk -f $SCRIPTDIR/cmvbt-analyze.awk | sed s/,/./g | xargs echo $T >>$SUMMARYFILE

done
done

