#!/bin/sh

RESDIR=results/cmvbt-perf
SCRIPTDIR=`dirname $0`

pushd .

cd $RESDIR/summary

for N in 0 10 20 50 100 ; do
for TXLEN in 5 100 ; do 
for DB in cmvbt-1 cmvbt-5 cmvbt-10 cmvbt-50 tsbt ; do


FILE=$DB-qu-$TXLEN-initial.log
cat $FILE | grep "^$N " | xargs echo "$DB $TXLEN" | sed "s/ / \& /g" | sed "s/$/\\\\\\\\/g" | tr "[:lower:]" "[:upper:]"

done
echo "\\hline"

done
done

popd
