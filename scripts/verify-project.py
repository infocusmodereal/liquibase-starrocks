#!/usr/bin/env python3
"""Validate compatibility inputs, local documentation links and the consumer JAR."""
import argparse
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def verify(jar):
    candidates = json.loads((ROOT / 'compatibility/candidates.json').read_text())['include']
    tuples = set()
    for row in candidates:
        key = tuple(row[k] for k in ('liquibase', 'starrocks', 'java', 'architecture'))
        assert key not in tuples, f'Duplicate candidate: {key}'
        tuples.add(key)
        for key in ('liquibase', 'starrocks'):
            assert re.fullmatch(r'\d+\.\d+\.\d+', row[key]), row
    registry = json.loads((ROOT / 'compatibility/releases.json').read_text())
    for row in registry['records']:
        assert row['scenarioSet'] in registry['scenarios']
        assert row['result'] in ('passed', 'failed', 'blocked', 'untested')
        assert re.fullmatch(r'[0-9a-f]{40}', row['sourceCommit'])
        assert row['evidence'].startswith('https://')
    for path in list(ROOT.glob('*.md')) + list((ROOT / 'docs').glob('*.md')):
        if path.name == 'ACTION_PLAN.md':
            continue  # Local, untracked working notes.
        for target in re.findall(r'\]\(([^)]+)\)', path.read_text()):
            if re.match(r'[a-z]+:', target) or target.startswith('#'):
                continue
            assert (path.parent / target.split('#')[0]).exists(), f'{path.name}: {target}'
    if jar:
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
            assert 'kotlin/Unit.class' in names
            assert not any(n.startswith('liquibase/') and not n.startswith('liquibase/ext/') and n.endswith('.class') for n in names)
            assert not any(n.startswith('com/mysql/') for n in names)
            assert not any(n.endswith(('gradle.properties', 'secret-key.asc')) for n in names)
            for service in ('Database', 'LockService', 'SqlGenerator', 'LiquibaseDataType', 'Change', 'SnapshotGenerator', 'AutoloadedConfigurations'):
                matches = [n for n in names if n.startswith('META-INF/services/liquibase.') and n.endswith('.' + service)]
                assert len(matches) == 1, service
                for provider in archive.read(matches[0]).decode().splitlines():
                    if provider and not provider.startswith('#'):
                        assert provider.replace('.', '/') + '.class' in names, provider
    print('Compatibility inputs, documentation links and requested artifact checks passed.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    verify(args.jar)
