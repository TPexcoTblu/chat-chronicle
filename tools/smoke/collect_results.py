"""Collect local runtime evidence, asserting results and tested release hashes."""
import hashlib
import json
import os
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[2]
profiles = Path(os.environ['APPDATA']) / 'PrismLauncher/instances'
output = root / '.tools/smoke/results'
matrix_path = root / 'build/distributions/matrix-0.2.0.json'
matrix = json.loads(matrix_path.read_text())
for loader in ('fabric', 'neoforge', 'quilt'):
    game = profiles / f'CC-Smoke-26.3-{loader}/minecraft'
    destination = output / loader
    destination.mkdir(parents=True, exist_ok=True)
    for name in ('smoke-first.json', 'smoke-reopen.json'):
        result = json.loads((game / name).read_text())
        assert result['status'] == 'passed', (loader, name, result)
        shutil.copy2(game / name, destination / name)
    log = game / 'logs/latest.log'
    assert 'Invalid atlas id' not in log.read_text(encoding='utf-8')
    shutil.copy2(log, destination / 'reopen.log')
    crashes = game / 'crash-reports'
    if crashes.exists():
        for crash in crashes.glob('*.txt'):
            shutil.copy2(crash, destination / crash.name)
    entry = next(row for row in matrix if row['minecraft'] == '26.3' and row['loader'] == loader)
    jar = game / 'mods' / Path(entry['file']).name
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == entry['sha256'], (loader, 'tested artifact hash mismatch')
    (destination / 'tested-sha256.txt').write_text(digest + '  ' + jar.name + '\n')
    entry['in_game'] = 'automated_client_smoke_passed'
    entry['runtime_report'] = 'tools/smoke/REPORT.md'
    print(loader + ': first/reopen passed; release hash matches')
matrix_path.write_text(json.dumps(matrix, indent=2) + '\n')
