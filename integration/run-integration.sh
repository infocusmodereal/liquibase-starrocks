#!/usr/bin/env bash
set -euo pipefail

workspace="${WORKSPACE_DIR:-/workspace}"
cd "$workspace"

if [ ! -x ./gradlew ]; then
  chmod +x ./gradlew
fi

starrocks_host="${STARROCKS_HOST:-starrocks}"
starrocks_port="${STARROCKS_PORT:-9030}"

echo "Waiting for StarRocks to accept connections..."
for _ in $(seq 1 60); do
  if mysql --protocol=TCP -h "$starrocks_host" -P "$starrocks_port" -u root -e "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! mysql --protocol=TCP -h "$starrocks_host" -P "$starrocks_port" -u root -e "SELECT 1" >/dev/null 2>&1; then
  echo "StarRocks did not become ready in time."
  exit 1
fi

mysql --protocol=TCP -h "$starrocks_host" -P "$starrocks_port" -u root \
  -e "CREATE DATABASE IF NOT EXISTS liquibase_test;"

./gradlew --no-daemon shadowJar

jar_path="$(find build/libs -maxdepth 1 -type f -name "liquibase-starrocks-*.jar" \
  ! -name "*-sources.jar" ! -name "*-javadoc.jar" | head -n 1)"

if [ -z "$jar_path" ]; then
  echo "Plugin jar not found in build/libs."
  exit 1
fi

cache_dir="${CACHE_DIR:-$workspace/integration/.cache}"
mkdir -p "$cache_dir"

liquibase_version="${INTEGRATION_LIQUIBASE_VERSION:-4.23.0}"
liquibase_zip="$cache_dir/liquibase-${liquibase_version}.zip"
if [ ! -f "$liquibase_zip" ]; then
  curl -L \
    "https://github.com/liquibase/liquibase/releases/download/v${liquibase_version}/liquibase-${liquibase_version}.zip" \
    -o "$liquibase_zip"
fi

liquibase_dir="$cache_dir/liquibase"
rm -rf "$liquibase_dir"
mkdir -p "$liquibase_dir"
unzip -q "$liquibase_zip" -d "$liquibase_dir"
chmod +x "$liquibase_dir/liquibase"

mysql_driver_version="${MYSQL_DRIVER_VERSION:-8.4.0}"
mysql_jar="$cache_dir/mysql-connector-j-${mysql_driver_version}.jar"
if [ ! -f "$mysql_jar" ]; then
  curl -L \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${mysql_driver_version}/mysql-connector-j-${mysql_driver_version}.jar" \
    -o "$mysql_jar"
fi

cp "$jar_path" "$liquibase_dir/lib/"
cp "$mysql_jar" "$liquibase_dir/lib/"

first_run_log="$cache_dir/liquibase-first-run.log"
second_run_log="$cache_dir/liquibase-second-run.log"

"$liquibase_dir/liquibase" --defaultsFile=integration/liquibase.properties update \
  2>&1 | tee "$first_run_log"

table_name="plugin_integration"
table_found="$(mysql --protocol=TCP -h "$starrocks_host" -P "$starrocks_port" -u root \
  -D liquibase_test -N -e "SHOW TABLES LIKE '${table_name}';" || true)"

if [ "$table_found" != "$table_name" ]; then
  echo "Integration test failed: table '${table_name}' not found."
  exit 1
fi

# Second (idempotent) run — triggers AbstractUpdateCommandStep's fast-check path, which
# skips acquireLock() but still calls releaseLock() in its finally block. Without the
# hasChangeLogLock() gate in StarRocksLockService, that fires the extension's
# WHERE ID = 1 AND LOCKED = true release UPDATE against a row that already has LOCKED=false,
# matches 0 rows, and StandardLockService.releaseLock throws LockException — logged SEVERE.
"$liquibase_dir/liquibase" --defaultsFile=integration/liquibase.properties update \
  2>&1 | tee "$second_run_log"

if grep -q "Could not release lock" "$second_run_log"; then
  echo "Integration test failed: 'Could not release lock' appeared in the second-run log."
  echo "This is the regression the hasChangeLogLock() gate in StarRocksLockService prevents."
  exit 1
fi

if grep -qi "SEVERE" "$second_run_log"; then
  echo "Integration test failed: unexpected SEVERE log line on an up-to-date second run."
  grep -i "SEVERE" "$second_run_log" | head -5
  exit 1
fi

# The DATABASECHANGELOGLOCK row must end in LOCKED = 0 with no LOCKEDBY / LOCKGRANTED set —
# the lock state stayed consistent even though acquireLock was skipped on the second run.
lock_state="$(mysql --protocol=TCP -h "$starrocks_host" -P "$starrocks_port" -u root \
  -D liquibase_test -N -e \
  "SELECT CONCAT(LOCKED, '|', COALESCE(LOCKEDBY,'null'), '|', COALESCE(LOCKGRANTED,'null')) \
   FROM DATABASECHANGELOGLOCK WHERE ID = 1;" || true)"

if [ "$lock_state" != "0|null|null" ]; then
  echo "Integration test failed: DATABASECHANGELOGLOCK ended in unexpected state '$lock_state'."
  echo "Expected '0|null|null' (LOCKED=0, LOCKEDBY=null, LOCKGRANTED=null)."
  exit 1
fi

echo "Integration test passed."
