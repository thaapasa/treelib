BEGIN {
  db = "";
  test = "";
  extra = "";
  start = "";
  end = "";
  time = 0;
  comments = 0;
}

/---/ {
  split($2, arr, ";");
  test = arr[2];
  extra = arr[3];
  print "-- Test: " test " (" extra ")";
}

/database:/ {
  split($1, arr, ":");
  database = arr[2];
  if (comments) print "-- Using database: " database; 
}

/start/ {
  split($1, arr, ":");
  start = arr[2] " " $2;
  if (comments) print "-- Start time: " start; 
}

/end/ {
  split($1, arr, ":");
  end = arr[2] " " $2;
  if (comments) print "-- End time: " start; 
}

/time-measurement:/ {
  split($1, arr, ":");
  time = sprintf("%.6f", arr[2]);
  if (comments) print "-- Using time measurement: " time;
  
  print "INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('" test "', '" start "', '" end "', '" database "', 0, 0, 0, " time ", '" extra "');";
}

/op_/ {
  op = $1;
  split($2, arr, ":");
  avg = sprintf("%.6f", arr[2]);
  split($3, arr, ":");
  min = sprintf("%.6f", arr[2]);
  split($4, arr, ":");
  max = sprintf("%.6f", arr[2]);
  print "INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('" test "', '" database "', '" start "', '" op "', " avg ", " min ", " max ", '" extra "');"; 
}