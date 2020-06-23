#!/bin/sh

STAGE=$1
OP=$2
OPTITLE=$3
VALTARGET=$4
RESDIR=results/cmvbt-perf
SCRIPTDIR=`dirname $0`

pushd .

TEMPFILE=_current.plt

SUFFIX="`echo "$OPTITLE" | sed s/\ *\(.*\)\ *//g | sed s/\ //g`"

echo "Creating images at $STAGE, $OP: $OPTITLE; using suffix $SUFFIX"
echo "Script dir is $SCRIPTDIR"

cd $RESDIR

SDIR=../../$SCRIPTDIR

for TXLEN in 5 100 ; do 

EPSFILE="summary/qu-$STAGE-$TXLEN-$SUFFIX.eps"
echo "Creating image for query-update ($TXLEN, $STAGE), $OP ($OPTITLE), file $EPSFILE"
cat $SDIR/cmvbt-plot-qu.plt | sed "s!SCRIPTDIR!$SDIR!" | sed "s!VALTARGET!$VALTARGET!" | sed "s!TXLEN!$TXLEN!" | sed "s!FILE!$EPSFILE!" | sed "s/STAGE/$STAGE/g" | sed "s/OPTITLE/$OPTITLE/g" | sed "s/OP/$OP/g" >$TEMPFILE
gnuplot $TEMPFILE
epstopdf $EPSFILE

done

if [ "$STAGE" = "initial" ] ; then
  echo "Creating image for range, $OP ($OPTITLE)" 
  for TEST in 1 5 10 20 50 ; do
    EPSFILE="summary/range-$TEST-$SUFFIX.eps"
    echo "Creating range image for $TEST, $OP ($OPTITLE), file $EPSFILE"
#  for TEST in 1 ; do 
    cat $SDIR/cmvbt-plot-range.plt | sed "s!SCRIPTDIR!$SDIR!" | sed "s!VALTARGET!$VALTARGET!" | sed "s!FILE!$EPSFILE!" | sed "s/OPTITLE/$OPTITLE/g" | sed "s/OP/$OP/g" | sed "s/TEST/$TEST/g" >$TEMPFILE  
    gnuplot $TEMPFILE
    epstopdf $EPSFILE
  done

  for TEST in history history-range ; do
    EPSFILE="summary/$TEST-$SUFFIX.eps" 
    echo "Creating image for $TEST, $OP ($OPTITLE), file $EPSFILE"
    cat $SDIR/cmvbt-plot-single.plt | sed "s!SCRIPTDIR!$SDIR!" | sed "s!VALTARGET!$VALTARGET!" | sed "s!FILE!$EPSFILE!" | sed "s/OPTITLE/$OPTITLE/g" | sed "s/OP/$OP/g" | sed "s/TEST/$TEST/g" >$TEMPFILE  
    gnuplot $TEMPFILE
    epstopdf $EPSFILE 
  done
fi

rm $TEMPFILE

popd
