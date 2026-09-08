#!/usr/bin/env python3
"""Verify the staged signatures and hashes using the pinned Gradle distribution's PGP libraries."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--key', type=Path, required=True, help='Public key or local signing keyring; only public packets are used')
parser.add_argument('--directory', type=Path, required=True)
args = parser.parse_args()
wrapper = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
version = re.search(r'gradle-([0-9.]+)-bin.zip', wrapper).group(1)
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
homes = list((cache / 'wrapper/dists' / f'gradle-{version}-bin').glob(f'*/gradle-{version}/lib/plugins'))
assert len(homes) == 1, 'Run the pinned Gradle wrapper first'
classpath = os.pathsep.join(str(p) for p in homes[0].glob('bc*-*.jar'))
assert classpath, 'Missing PGP verification libraries'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
subprocess.run([java, '-cp', classpath, str(root / 'scripts/VerifySignatures.java'), str(args.key), str(args.directory)], check=True)
for artifact in args.directory.iterdir():
    if artifact.suffix not in ('.jar', '.pom'):
        continue
    for extension, algorithm in [('md5', 'md5'), ('sha1', 'sha1'), ('sha256', 'sha256'), ('sha512', 'sha512')]:
        expected = artifact.with_name(artifact.name + '.' + extension).read_text().strip()
        assert hashlib.new(algorithm, artifact.read_bytes()).hexdigest() == expected, artifact.name
print('All staged signatures and checksums verified.')
