-- Statistics for cmvbt-1 test: history-range-initial
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('history-range-initial', '2009-10-20 12:18:23', '2009-10-20 12:18:23', 'cmvbt-1', 168, 4096, 100, 0.087, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-initial', 'cmvbt-1', '2009-10-20 12:18:23', 'go_new_transaction', 100, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-initial', 'cmvbt-1', '2009-10-20 12:18:23', 'action_range_query', 100, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-initial', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_fix', 2.76, 2, 3, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-initial', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_read', 0.07, 0, 1, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-initial', 'cmvbt-1', '2009-10-20 12:18:23', 'op_version_stable', 1, 1, 1, '0');

-- Statistics for cmvbt-1 test: history-range-updated
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('history-range-updated', '2009-10-20 12:18:23', '2009-10-20 12:18:23', 'cmvbt-1', 168, 4096, 100, 0.048, '1');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-updated', 'cmvbt-1', '2009-10-20 12:18:23', 'go_new_transaction', 100, '1');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-updated', 'cmvbt-1', '2009-10-20 12:18:23', 'action_range_query', 100, '1');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-updated', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_fix', 2.99, 2, 3, '1');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-updated', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_read', 0.12, 0, 1, '1');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-updated', 'cmvbt-1', '2009-10-20 12:18:23', 'op_version_stable', 1, 1, 1, '1');

-- Statistics for tsbt test: history-range-initial
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('history-range-initial', '2009-10-20 12:18:23', '2009-10-20 12:18:23', 'tsbt', 200, 4096, 100, 0.059, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-initial', 'tsbt', '2009-10-20 12:18:23', 'go_new_transaction', 100, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-initial', 'tsbt', '2009-10-20 12:18:23', 'action_range_query', 100, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-initial', 'tsbt', '2009-10-20 12:18:23', 'op_buffer_fix', 3, 2, 4, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-initial', 'tsbt', '2009-10-20 12:18:23', 'op_buffer_read', 0.04, 0, 1, '0');

-- Statistics for tsbt test: history-range-updated
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('history-range-updated', '2009-10-20 12:18:23', '2009-10-20 12:18:23', 'tsbt', 200, 4096, 100, 0.014, '1');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-updated', 'tsbt', '2009-10-20 12:18:23', 'go_new_transaction', 100, '1');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('history-range-updated', 'tsbt', '2009-10-20 12:18:23', 'action_range_query', 100, '1');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-updated', 'tsbt', '2009-10-20 12:18:23', 'op_buffer_fix', 3, 2, 4, '1');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('history-range-updated', 'tsbt', '2009-10-20 12:18:23', 'op_buffer_read', 0.08, 0, 1, '1');

-- Statistics for cmvbt-1 test: qu-initial-0
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-0', '2009-10-20 12:18:23', '2009-10-20 12:18:23', 'cmvbt-1', 168, 4096, 1000, 0.258, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-0', 'cmvbt-1', '2009-10-20 12:18:23', 'go_new_transaction', 100, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-0', 'cmvbt-1', '2009-10-20 12:18:23', 'action_query', 1000, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-1', '2009-10-20 12:18:23', 'op_query_found_object', 0.522, 0, 1, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_fix', 2, 2, 2, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_read', 0.004, 0, 1, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-1', '2009-10-20 12:18:23', 'op_version_stable', 1, 1, 1, '0');

-- Statistics for cmvbt-1 test: qu-initial-5
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-5', '2009-10-20 12:18:23', '2009-10-20 12:18:23', 'cmvbt-1', 168, 4096, 1000, 0.25, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'go_new_transaction', 100, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'go_maintenance_tx', 7, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'action_insert', 30, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'action_delete', 40, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'action_query', 930, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_query_found_object', 0.528, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_object_deleted', 0.04, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_object_inserted', 0.03, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_spacemap_allocate', 0.007, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_spacemap_free', 0.007, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_fix', 2.033, 1, 14, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_read', 0.004, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_write', 0.007, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_traverse_path', 0.091, 0, 4, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_backtrack_path', 0.019, 0, 3, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_version_stable', 0.93, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-1', '2009-10-20 12:18:23', 'op_maintenance_tx', 0.007, 0, 1, '5');

-- Statistics for cmvbt-1 test: qu-initial-50
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-50', '2009-10-20 12:18:23', '2009-10-20 12:18:24', 'cmvbt-1', 168, 4096, 1000, 0.252, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'go_new_transaction', 100, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'go_maintenance_tx', 41, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'go_page_split', 2, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'go_key_split', 1, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'go_version_split', 1, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'action_insert', 220, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'action_delete', 190, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'action_query', 590, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_query_found_object', 0.348, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_object_deleted', 0.19, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_object_inserted', 0.22, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_spacemap_allocate', 0.043, 0, 2, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_spacemap_free', 0.041, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_fix', 2.205, 1, 15, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_read', 0.004, 0, 4, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_buffer_write', 0.041, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_page_split', 0.002, 0, 2, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_version_split', 0.001, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_key_split', 0.001, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_traverse_path', 0.533, 0, 4, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_backtrack_path', 0.121, 0, 4, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_version_stable', 0.59, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-1', '2009-10-20 12:18:23', 'op_maintenance_tx', 0.041, 0, 1, '50');

-- Statistics for cmvbt-5 test: qu-initial-0
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-0', '2009-10-20 12:18:24', '2009-10-20 12:18:24', 'cmvbt-5', 168, 4096, 1000, 0.211, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-0', 'cmvbt-5', '2009-10-20 12:18:24', 'go_new_transaction', 100, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-0', 'cmvbt-5', '2009-10-20 12:18:24', 'action_query', 1000, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-5', '2009-10-20 12:18:24', 'op_query_found_object', 0.522, 0, 1, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_fix', 2, 2, 2, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_read', 0.004, 0, 1, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'cmvbt-5', '2009-10-20 12:18:24', 'op_version_stable', 1, 1, 1, '0');

-- Statistics for cmvbt-5 test: qu-initial-5
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-5', '2009-10-20 12:18:24', '2009-10-20 12:18:24', 'cmvbt-5', 168, 4096, 1000, 0.252, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'go_new_transaction', 100, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'go_maintenance_tx', 7, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'action_insert', 30, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'action_delete', 40, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'action_query', 930, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_query_found_object', 0.528, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_object_deleted', 0.04, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_object_inserted', 0.03, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_spacemap_allocate', 0.006, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_spacemap_free', 0.006, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_fix', 2.286, 1, 23, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_read', 0.004, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_write', 0.006, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_traverse_path', 0.091, 0, 7, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_backtrack_path', 0.019, 0, 5, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_version_stable', 0.8, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_version_transient', 0.13, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'cmvbt-5', '2009-10-20 12:18:24', 'op_maintenance_tx', 0.007, 0, 2, '5');

-- Statistics for cmvbt-5 test: qu-initial-50
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-50', '2009-10-20 12:18:24', '2009-10-20 12:18:25', 'cmvbt-5', 168, 4096, 1000, 0.317, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'go_new_transaction', 100, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'go_maintenance_tx', 41, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'go_page_split', 2, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'go_key_split', 1, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'go_version_split', 1, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'action_insert', 220, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'action_delete', 190, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'action_query', 590, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_query_found_object', 0.348, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_object_deleted', 0.19, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_object_inserted', 0.22, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_spacemap_allocate', 0.02, 0, 2, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_spacemap_free', 0.018, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_fix', 2.73, 1, 46, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_read', 0.004, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_buffer_write', 0.018, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_page_split', 0.002, 0, 2, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_version_split', 0.001, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_key_split', 0.001, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_traverse_path', 0.533, 0, 13, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_backtrack_path', 0.121, 0, 14, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_version_stable', 0.28, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_version_transient', 0.31, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'cmvbt-5', '2009-10-20 12:18:24', 'op_maintenance_tx', 0.041, 0, 4, '50');

-- Statistics for tsbt test: qu-initial-0
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-0', '2009-10-20 12:18:25', '2009-10-20 12:18:25', 'tsbt', 200, 4096, 1000, 0.228, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-0', 'tsbt', '2009-10-20 12:18:25', 'go_new_transaction', 100, '0');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-0', 'tsbt', '2009-10-20 12:18:25', 'action_query', 1000, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'tsbt', '2009-10-20 12:18:25', 'op_query_found_object', 0.522, 0, 1, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'tsbt', '2009-10-20 12:18:25', 'op_buffer_fix', 2.1, 2, 3, '0');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-0', 'tsbt', '2009-10-20 12:18:25', 'op_buffer_read', 0.004, 0, 1, '0');

-- Statistics for tsbt test: qu-initial-5
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-5', '2009-10-20 12:18:25', '2009-10-20 12:18:25', 'tsbt', 200, 4096, 1000, 0.277, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'go_new_transaction', 100, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'action_insert', 30, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'action_delete', 40, '5');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'action_query', 930, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'op_query_found_object', 0.528, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'op_object_deleted', 0.023, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'op_object_inserted', 0.03, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'op_buffer_fix', 2.121, 2, 5, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'op_buffer_read', 0.004, 0, 1, '5');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-5', 'tsbt', '2009-10-20 12:18:25', 'op_traverse_path', 0.084, 0, 2, '5');

-- Statistics for tsbt test: qu-initial-50
INSERT INTO test_info (test, start_time, end_time, db, buffer_size, page_size, ops, elapsed, extra) VALUES('qu-initial-50', '2009-10-20 12:18:25', '2009-10-20 12:18:25', 'tsbt', 200, 4096, 1000, 0.242, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'go_new_transaction', 100, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'go_page_split', 1, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'action_insert', 220, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'action_delete', 190, '50');
INSERT INTO test_ops (test, db, start_time, op, amount, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'action_query', 590, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_query_found_object', 0.348, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_object_deleted', 0.114, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_object_inserted', 0.22, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_spacemap_allocate', 0.001, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_buffer_fix', 2.224, 2, 6, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_buffer_read', 0.004, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_page_split', 0.001, 0, 1, '50');
INSERT INTO test_vals (test, db, start_time, op, avgval, minval, maxval, extra) VALUES ('qu-initial-50', 'tsbt', '2009-10-20 12:18:25', 'op_traverse_path', 0.492, 0, 3, '50');

