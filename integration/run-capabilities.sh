#!/usr/bin/env bash
# Sourced by run-integration.sh after its baseline. Uses the same isolated CLI/JAR.
expect_failure() {
  local label="$1" expected="$2"
  shift 2
  if "$liquibase_dir/liquibase" --log-level=info --defaultsFile=integration/liquibase.properties "$@" > "$cache_dir/${liquibase_version}-${label}.log" 2>&1; then
    echo "Expected failure: $label" >&2
    exit 1
  fi
  grep -F "$expected" "$cache_dir/${liquibase_version}-${label}.log" > /dev/null
}

expect_failure modify-type 'StarRocks modifyDataType is not supported' --changelog-file=integration/changelogs/modify-type.yaml update
[[ "$(sql -D liquibase_test -N -e "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID='modify-type-must-fail';")" == 0 ]]
[[ "$(sql -D liquibase_test -N -e "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE TABLE_SCHEMA='liquibase_test' AND TABLE_NAME='plugin_integration' AND COLUMN_NAME='name';")" == 255 ]]
assert_unlocked
expect_failure failed-sql 'deliberately_missing_column' --changelog-file=integration/changelogs/failure.yaml update
[[ "$(sql -D liquibase_test -N -e "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID='failed-migration';")" == 0 ]]
assert_unlocked

run_liquibase status status
run_liquibase history history
run_liquibase list-locks list-locks
run_liquibase tag tag --tag="release'check"
[[ "$(sql -D liquibase_test -N -e "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE TAG='release''check';")" == 1 ]]
run_liquibase native-preview --changelog-file=integration/changelogs/native.yaml update-sql
[[ -z "$(sql -D liquibase_test -N -e "SHOW TABLES LIKE 'native_table';")" ]]
[[ "$(sql -D liquibase_test -N -e 'SELECT COUNT(*) FROM DATABASECHANGELOG;')" == 1 ]]
run_liquibase native-validate --changelog-file=integration/changelogs/native.yaml validate
run_liquibase native --changelog-file=integration/changelogs/native.yaml update
[[ "$(sql -D liquibase_test -N -e "SHOW TABLES LIKE 'native_table';")" == native_table ]]
run_liquibase native-repeat --changelog-file=integration/changelogs/native.yaml update
run_liquibase native-clear clear-checksums
run_liquibase native-recalculate --changelog-file=integration/changelogs/native.yaml update
run_liquibase native-rollback --changelog-file=integration/changelogs/native.yaml rollback-count --count=1
[[ -z "$(sql -D liquibase_test -N -e "SHOW TABLES LIKE 'native_table';")" ]]
[[ "$(sql -D liquibase_test -N -e 'SELECT COUNT(*) FROM DATABASECHANGELOG;')" == 1 ]]
assert_unlocked

# Snapshot and diff are probes: capabilities are assessed from their actual output.
run_liquibase snapshot snapshot --snapshot-format=json
run_liquibase diff diff --reference-url="jdbc:mysql://$starrocks_host:$starrocks_port/liquibase_test" --reference-username=root
generated_dir="$(mktemp -d integration/.cache/generated.XXXXXX)"
run_liquibase generate-changelog generate-changelog --changelog-file="$generated_dir/changelog.yaml"

sql -e 'CREATE DATABASE `meta-db`;'
metadata_args=(--liquibase-catalog-name=meta-db --database-changelog-table-name=change-log --database-changelog-lock-table-name=change-lock)
run_liquibase metadata "${metadata_args[@]}" --changelog-file=integration/changelogs/native.yaml update
run_liquibase metadata-repeat "${metadata_args[@]}" --changelog-file=integration/changelogs/native.yaml update
run_liquibase metadata-tag "${metadata_args[@]}" tag --tag=metadata
run_liquibase metadata-clear "${metadata_args[@]}" clear-checksums
run_liquibase metadata-recalculate "${metadata_args[@]}" --changelog-file=integration/changelogs/native.yaml update
run_liquibase metadata-rollback "${metadata_args[@]}" --changelog-file=integration/changelogs/native.yaml rollback-count --count=1
[[ "$(sql -D meta-db -N -e 'SELECT COUNT(*) FROM `change-log`;')" == 0 ]]
[[ "$(sql -D meta-db -N -e 'SELECT LOCKED FROM `change-lock` WHERE ID=1;')" == 0 ]]

# Separate processes, including a race on an entirely new metadata database.
sql -e 'CREATE DATABASE race_test;'
for process in 1 2; do
  (run_liquibase "concurrent-$process" --url="jdbc:mysql://$starrocks_host:$starrocks_port/race_test" --changelog-file=integration/changelogs/concurrent.yaml update) > "$cache_dir/concurrent-$process.out" 2>&1 &
  if [[ "$process" == 1 ]]; then pid1=$!; else pid2=$!; fi
done
wait "$pid1"
wait "$pid2"
[[ "$(sql -D race_test -N -e 'SELECT COUNT(*) FROM race_events;')" == 1 ]]
[[ "$(sql -D race_test -N -e 'SELECT COUNT(*) FROM DATABASECHANGELOG;')" == 1 ]]
[[ "$(sql -D race_test -N -e 'SELECT COUNT(*) FROM DATABASECHANGELOGLOCK WHERE ID=1 AND LOCKED=0;')" == 1 ]]
echo 'Capability suite passed: validation, preview, commands, native table, rollback, metadata location and concurrent initialization.'

sql -D liquibase_test -e "CREATE TABLE flag_events (id INT) DUPLICATE KEY(id) DISTRIBUTED BY HASH(id) BUCKETS 1 PROPERTIES ('replication_num'='1');"
run_liquibase filtered --changelog-file=integration/changelogs/flags.yaml update --contexts=prod --label-filter=integration -DviewValue=1
[[ "$(sql -D liquibase_test -N -e 'SELECT COUNT(*) FROM flag_events;')" == 0 ]]
run_liquibase flags-first --changelog-file=integration/changelogs/flags.yaml update --contexts=qa --label-filter=integration -DviewValue=1
run_liquibase flags-second --changelog-file=integration/changelogs/flags.yaml update --contexts=qa --label-filter=integration -DviewValue=2
[[ "$(sql -D liquibase_test -N -e 'SELECT COUNT(*) FROM flag_events;')" == 2 ]]
[[ "$(sql -D liquibase_test -N -e 'SELECT value FROM flag_view;')" == 2 ]]
run_liquibase flags-validate --changelog-file=integration/changelogs/flags.yaml validate -DviewValue=2

# Explicit recovery is idempotent, including an already unlocked table.
run_liquibase already-unlocked release-locks
sql -D liquibase_test -e "UPDATE DATABASECHANGELOGLOCK SET LOCKED=1, LOCKEDBY='timeout-owner', LOCKGRANTED=NOW() WHERE ID=1;"
expect_failure timeout 'Could not acquire change log lock' --changelog-lock-wait-time-in-minutes=0 --changelog-lock-poll-rate=1 --changelog-file=integration/changelogs/native.yaml update
[[ "$(sql -D liquibase_test -N -e 'SELECT LOCKED FROM DATABASECHANGELOGLOCK WHERE ID=1;')" == 1 ]]
run_liquibase timeout-recover release-locks
assert_unlocked
sql -e 'CREATE DATABASE tag_test;'
run_liquibase tag-ties-update --url="jdbc:mysql://$starrocks_host:$starrocks_port/tag_test" --changelog-file=integration/changelogs/tag-ties.yaml update
sql -D tag_test -e 'UPDATE DATABASECHANGELOG SET DATEEXECUTED=NOW(), ORDEREXECUTED=1 WHERE ID IS NOT NULL;'
run_liquibase tag-ties --url="jdbc:mysql://$starrocks_host:$starrocks_port/tag_test" tag --tag=single-row
[[ "$(sql -D tag_test -N -e "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE TAG='single-row';")" == 1 ]]
run_liquibase add-column --changelog-file=integration/changelogs/add-column.yaml update
column_exists() {
  sql -N -e "SELECT COUNT(*) FROM information_schema.columns WHERE TABLE_SCHEMA='liquibase_test' AND TABLE_NAME='plugin_integration' AND COLUMN_NAME='$1';"
}
for _ in $(seq 1 60); do
  [[ "$(column_exists added_column)" == 1 ]] && break
  sleep 1
done
[[ "$(column_exists added_column)" == 1 ]]
if [[ "$actual_starrocks_version" == 3.1.* ]]; then
  expect_failure rename-unsupported 'RENAME' --changelog-file=integration/changelogs/rename-column.yaml update
else
  run_liquibase rename-column --changelog-file=integration/changelogs/rename-column.yaml update
  [[ "$(column_exists renamed_column)" == 1 ]]
  run_liquibase rename-rollback --changelog-file=integration/changelogs/rename-column.yaml rollback-count --count=1
  [[ "$(column_exists added_column)" == 1 ]]
fi
run_liquibase drop-column --changelog-file=integration/changelogs/add-column.yaml rollback-count --count=1
for _ in $(seq 1 60); do
  [[ "$(column_exists added_column)" == 0 ]] && break
  sleep 1
done
[[ "$(column_exists added_column)" == 0 ]]
assert_unlocked
echo 'ALL SCENARIOS PASSED (migration-capabilities-v2)'
