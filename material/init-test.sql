CREATE TABLE test_info (
  id            INTEGER NOT NULL 
                PRIMARY KEY GENERATED ALWAYS AS IDENTITY 
                (START WITH 1, INCREMENT BY 1),
  test          VARCHAR(64) NOT NULL,
  db            VARCHAR(32) NOT NULL,
  start_time    TIMESTAMP NOT NULL,
  end_time      TIMESTAMP NOT NULL,
  buffer_size   INTEGER NOT NULL,
  page_size     INTEGER NOT NULL,
  ops           INTEGER NOT NULL,
  elapsed       FLOAT NOT NULL,
  extra         VARCHAR(30) NOT NULL DEFAULT ''
  );

CREATE TABLE test_ops (
  id            INTEGER NOT NULL 
                PRIMARY KEY GENERATED ALWAYS AS IDENTITY 
                (START WITH 1, INCREMENT BY 1),
  test          VARCHAR(64) NOT NULL,
  db            VARCHAR(32) NOT NULL,
  start_time    TIMESTAMP NOT NULL,
  op            VARCHAR(64) NOT NULL,
  amount        INTEGER NOT NULL,
  extra         VARCHAR(30) NOT NULL DEFAULT ''
  );

CREATE TABLE test_vals (
  id            INTEGER NOT NULL 
                PRIMARY KEY GENERATED ALWAYS AS IDENTITY 
                (START WITH 1, INCREMENT BY 1),
  test          VARCHAR(64) NOT NULL,
  db            VARCHAR(32) NOT NULL,
  start_time    TIMESTAMP NOT NULL,
  op            VARCHAR(64) NOT NULL,
  avgval        FLOAT NOT NULL,
  deviation     FLOAT NOT NULL DEFAULT 0,
  minval        FLOAT NOT NULL,
  maxval        FLOAT NOT NULL,
  extra         VARCHAR(30) NOT NULL DEFAULT ''
  );

