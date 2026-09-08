CREATE TABLE harness_test.harness_table (id INT NOT NULL, name VARCHAR(42) NULL) ENGINE = OLAP PRIMARY KEY (id) DISTRIBUTED BY HASH (id) BUCKETS 1 PROPERTIES ("replication_num"="1")
