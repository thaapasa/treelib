BEGIN {
  buffix = 0;
  bufread = 0;
  bufwrite = 0;
  time = 0;
}

/database:(.*)/ {
  split($1, a, ":");
  dbid = a[2];
}

/op_buffer_fix/ {
  split($2, a, ":");
  buffix = a[2];
}

/op_buffer_read/ {
  split($2, a, ":");
  bufread = a[2];
}

/op_buffer_write/ {
  split($2, a, ":");
  bufwrite = a[2];
}

/time-measurement:/ {
  split($1, a, ":");
  time = a[2];
}

END {
   print buffix " " bufread " " bufwrite " " time;
}
