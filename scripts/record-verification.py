#!/usr/bin/env python3
"""Record the exact tested bytes and environment after the scenario suite succeeds."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
for flag in ('liquibase', 'starrocks', 'java', 'architecture', 'image', 'jar', 'log', 'output', 'evidence'):
    parser.add_argument('--' + flag, required=True)
parser.add_argument('--connector-version', default='0.3.0-SNAPSHOT')
parser.add_argument('--artifact-kind', default='source-build')
args = parser.parse_args()
log = Path(args.log).read_text()
assert 'ALL SCENARIOS PASSED (migration-capabilities-v2)' in log
assert 'STARROCKS_VERSION=' + args.starrocks in log
jar_sha = hashlib.sha256(Path(args.jar).read_bytes()).hexdigest()
assert 'CONNECTOR_SHA256=' + jar_sha in log
image = json.loads(subprocess.check_output(['docker', 'image', 'inspect', args.image]))[0]
record = {
    'connectorVersion': args.connector_version,
    'artifactKind': args.artifact_kind,
    'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'jarSha256': hashlib.sha256(Path(args.jar).read_bytes()).hexdigest(),
    'liquibase': args.liquibase, 'starrocks': args.starrocks,
    'java': int(args.java), 'jdbcDriver': 'com.mysql:mysql-connector-j:8.4.0',
    'os': 'linux', 'architecture': args.architecture, 'topology': 'allin1-single-node',
    'image': args.image, 'imageDigest': image['RepoDigests'][0],
    'scenarioSet': 'migration-capabilities-v2', 'result': 'passed',
    'validatedAt': datetime.now(timezone.utc).isoformat(), 'evidence': args.evidence,
}
Path(args.output).write_text(json.dumps(record, indent=2) + '\n')
print('Recorded exact artifact and image provenance.')
