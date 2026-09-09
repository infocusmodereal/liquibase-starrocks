#!/usr/bin/env bash
set -euo pipefail

workspace="${WORKSPACE_DIR:-/workspace}"
cd "$workspace"
starrocks_host="${STARROCKS_HOST:-starrocks}"
starrocks_port="${STARROCKS_PORT:-9030}"
cache_dir="${CACHE_DIR:-$workspace/integration/.cache}"
liquibase_version="${INTEGRATION_LIQUIBASE_VERSION:-4.23.0}"
mkdir -p "$cache_dir"

# Older Liquibase banners omit Java. Record the runtime used by its launcher.
java_binary="${JAVA_HOME:+$JAVA_HOME/bin/}java"
java_version="$("$java_binary" -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java.version = / { print $3; exit }')"
[[ -n "$java_version" ]]
echo "JAVA_VERSION=$java_version"

sql() {
  mysql --protocol=TCP -h "$starrocks_host" -P "$starrocks_port" -u root "$@"
}

ready=false
for _ in $(seq 1 150); do
  if sql -e 'SHOW BACKENDS' 2>/dev/null | awk -F '\t' '
    NR == 1 { for (i=1; i<=NF; i++) if ($i == "Alive") col=i }
    NR > 1 && col && $col == "true" { alive=1 }
    END { exit !alive }'; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo "StarRocks FE and BE did not become ready within 300 seconds." >&2
  exit 1
fi

actual_starrocks_version="$(sql -N -e 'SELECT current_version();')"
echo "STARROCKS_VERSION=$actual_starrocks_version"
if [[ -n "${INTEGRATION_STARROCKS_VERSION:-}" ]]; then
  [[ "${actual_starrocks_version%%-*}" == "$INTEGRATION_STARROCKS_VERSION" ]]
fi

# Require a fresh database: this harness must prove the initial migration ran.
if [[ "$(sql -N -e "SHOW DATABASES LIKE 'liquibase_test';")" == liquibase_test ]]; then
  echo "liquibase_test already exists. Use a fresh disposable Compose stack." >&2
  exit 1
fi
sql -e 'CREATE DATABASE liquibase_test;'

# .part files cannot be mistaken for a completed download on a later run.
download() {
  local url="$1" target="$2"
  if [[ ! -f "$target" ]]; then
    curl --fail --location --retry 3 "$url" -o "$target.part"
    mv "$target.part" "$target"
  fi
}

liquibase_zip="$cache_dir/liquibase-${liquibase_version}.zip"
download "https://github.com/liquibase/liquibase/releases/download/v${liquibase_version}/liquibase-${liquibase_version}.zip" "$liquibase_zip"
liquibase_dir="$(mktemp -d "$cache_dir/liquibase-run.XXXXXX")"
trap 'rm -rf "$liquibase_dir"' EXIT
unzip -q "$liquibase_zip" -d "$liquibase_dir"
chmod +x "$liquibase_dir/liquibase"

mysql_driver_version="${MYSQL_DRIVER_VERSION:-8.4.0}"
mysql_jar="$cache_dir/mysql-connector-j-${mysql_driver_version}.jar"
download "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/${mysql_driver_version}/mysql-connector-j-${mysql_driver_version}.jar" "$mysql_jar"
jar_path="${INTEGRATION_JAR:-$workspace/build/integration/liquibase-starrocks.jar}"
if [[ -n "${EXTENSION_VERSION:-}" ]]; then
  jar_path="$cache_dir/liquibase-starrocks-${EXTENSION_VERSION}.jar"
  download "https://repo.maven.apache.org/maven2/io/github/infocusmodereal/liquibase-starrocks/${EXTENSION_VERSION}/liquibase-starrocks-${EXTENSION_VERSION}.jar" "$jar_path"
fi
if [[ ! -f "$jar_path" ]]; then
  echo "Missing integration JAR: run ./scripts/dev prepareIntegrationJar first." >&2
  exit 1
fi
echo "CONNECTOR_SHA256=$(sha256sum "$jar_path" | cut -d " " -f1)"
cp "$jar_path" "$liquibase_dir/lib/"
cp "$mysql_jar" "$liquibase_dir/lib/"

run_liquibase() {
  local label="$1"
  shift
  "$liquibase_dir/liquibase" --log-level=info --defaultsFile=integration/liquibase.properties "$@" \
    2>&1 | tee "$cache_dir/${liquibase_version}-${label}.log"
  if grep -Ei 'Could not release lock|SEVERE|Failed to release change log lock' "$cache_dir/${liquibase_version}-${label}.log"; then
    echo "Unexpected lock failure in $label." >&2
    exit 1
  fi
}

assert_unlocked() {
  local state
  state="$(sql -D liquibase_test -N -e "SELECT CONCAT(LOCKED, '|', COALESCE(LOCKEDBY,'null'), '|', COALESCE(LOCKGRANTED,'null')) FROM DATABASECHANGELOGLOCK WHERE ID = 1;")"
  [[ "$state" == '0|null|null' ]] || {
    echo "Unexpected lock state: $state" >&2
    exit 1
  }
}

for run in first second third; do
  run_liquibase "$run" update
  assert_unlocked
  [[ "$(sql -D liquibase_test -N -e 'SELECT COUNT(*) FROM DATABASECHANGELOG;')" == 1 ]]
  [[ "$(sql -D liquibase_test -N -e "SHOW TABLES LIKE 'plugin_integration';")" == plugin_integration ]]
done

original_checksum="$(sql -D liquibase_test -N -e 'SELECT MD5SUM FROM DATABASECHANGELOG;')"
run_liquibase clear-checksums clear-checksums
[[ "$(sql -D liquibase_test -N -e 'SELECT COUNT(*) FROM DATABASECHANGELOG WHERE MD5SUM IS NULL;')" == 1 ]]
run_liquibase recalculate-checksums update
[[ "$(sql -D liquibase_test -N -e 'SELECT MD5SUM FROM DATABASECHANGELOG;')" == "$original_checksum" ]]
run_liquibase validate-checksums validate
assert_unlocked

# A new CLI process must still recover a lock abandoned by an earlier process.
sql -D liquibase_test -e "UPDATE DATABASECHANGELOGLOCK SET LOCKED = true, LOCKEDBY = 'abandoned-integration-process', LOCKGRANTED = NOW() WHERE ID = 1;"
[[ "$(sql -D liquibase_test -N -e 'SELECT LOCKED FROM DATABASECHANGELOGLOCK WHERE ID = 1;')" == 1 ]]
run_liquibase force-release release-locks
assert_unlocked
run_liquibase after-recovery update
assert_unlocked

echo "Integration passed: migration, idempotence, checksum recovery and abandoned-lock recovery ($liquibase_version)."

source integration/run-capabilities.sh
