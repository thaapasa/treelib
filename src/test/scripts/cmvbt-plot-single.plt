set term post eps
set output 'FILE'

set title "" 
set xlabel "Database state"
set ylabel "OPTITLE / VALTARGET"
set yrange [0:]

set key left top

load "SCRIPTDIR/settings.plt"

set xtics ("initial" 0, "updated" 1, "updated-2" 2, "deleted" 3)
set xrange [-0.2:3.2]

plot "summary/cmvbt-TEST.log" using 1:OP title 'CMVBT' with linespoints ls 1, \
  "summary/tsbt-TEST.log" using 1:OP title 'TSB' with linespoints ls 5
