set term post eps
set output 'FILE'

set title "" 
set xlabel "Updates (%)"
set ylabel "OPTITLE / VALTARGET"
set yrange [0:]

set key right bottom

load "SCRIPTDIR/settings.plt"

plot "summary/cmvbt-1-qu-TXLEN-STAGE.log" using 1:OP title 'CMVBT, m=1' with linespoints ls 1, \
  "summary/cmvbt-5-qu-TXLEN-STAGE.log" using 1:OP title 'CMVBT, m=5' with linespoints ls 2, \
  "summary/cmvbt-10-qu-TXLEN-STAGE.log" using 1:OP title 'CMVBT, m=10' with linespoints ls 3, \
  "summary/cmvbt-50-qu-TXLEN-STAGE.log" using 1:OP title 'CMVBT, m=50' with linespoints ls 4, \
  "summary/tsbt-qu-TXLEN-STAGE.log" using 1:OP title 'TSB' with linespoints ls 5
