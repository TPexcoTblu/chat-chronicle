"""Check installable matrix artifacts without launching Minecraft. Python 3.9+."""
from pathlib import Path
import hashlib
import io
import json
import struct
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify():
    targets = json.loads((ROOT / 'gradle/targets.json').read_text())
    version = next(line.split('=', 1)[1].strip() for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('version='))
    results = []
    for game, target in targets.items():
        for loader in ('fabric', 'neoforge', 'quilt'):
            path = ROOT / f'build/releases/{game}/{loader}/chat-chronicle-{loader}-mc{game}-{version}.jar'
            with zipfile.ZipFile(path) as jar:
                assert jar.testzip() is None, path
                names = set(jar.namelist())
                assert len(names) == len(jar.namelist()), f'Duplicate entries: {path}'
                for required in ('LICENSE', 'NOTICE', 'assets/chatchronicle/icon.png',
                                 'assets/chatchronicle/lang/en_us.json', 'assets/chatchronicle/lang/ru_ru.json',
                                 'licenses/SQLITE-JDBC-LICENSE', 'licenses/RE2J-LICENSE',
                                 'dev/chatchronicle/core/ArchiveStore.class',
                                 'dev/chatchronicle/client/ChatChronicle.class'):
                    assert required in names, f'{path}: missing {required}'
                mixins = json.loads(jar.read('chatchronicle.mixins.json'))
                for mixin in ('ClientLifecycleMixin', 'ChatListenerMixin', 'ChatComponentMixin', 'ChatScreenMixin'):
                    assert mixin in mixins['client'], path
                assert mixins['compatibilityLevel'] == f'JAVA_{target["java"]}', path
                for name in names:
                    if name.endswith('.class'):
                        assert struct.unpack('>H', jar.read(name)[6:8])[0] <= target['java'] + 44, (path, name)
                if loader == 'neoforge':
                    meta = jar.read('META-INF/neoforge.mods.toml').decode()
                    assert f'version="{version}"' in meta
                    # Validate the fixed metadata schema emitted by our Gradle task.
                    deps = re.findall(r'\[\[dependencies\.chatchronicle\]\]\s*'
                                      r'modId="([^"]+)"\s*type="required"\s*versionRange="([^"]+)"\s*side="CLIENT"', meta)
                    assert dict(deps)['minecraft'] == f'[{game}]'
                    assert dict(deps)['neoforge'] == f'[{target["neoforge"]},)'
                    assert 'fabric.mod.json' not in names
                    assert 'refmap' not in mixins
                    nested = json.loads(jar.read('META-INF/jarjar/metadata.json'))
                    libraries = [item['path'] for item in nested['jars']]
                    assert 'META-INF/accesstransformer.cfg' in names
                else:
                    meta = json.loads(jar.read('fabric.mod.json'))
                    assert meta['version'] == version and meta['depends']['minecraft'] == game
                    assert ('quilt_loader' if loader == 'quilt' else 'fabricloader') in meta['depends']
                    assert 'fabric-api' not in meta['depends']
                    assert 'META-INF/neoforge.mods.toml' not in names
                    libraries = [item['file'] for item in meta['jars']]
                    assert 'chatchronicle.accesswidener' in names
                    if game == '1.21.11':
                        refmap = json.loads(jar.read(mixins['refmap']))
                        assert refmap['mappings']['dev/chatchronicle/client/mixin/ChatComponentMixin']
                assert any('sqlite-jdbc' in name for name in libraries), path
                assert any('re2j' in name for name in libraries), path
                for library in libraries:
                    with zipfile.ZipFile(io.BytesIO(jar.read(library))) as nested:
                        assert nested.testzip() is None, (path, library)
            results.append({'minecraft': game, 'loader': loader, 'java': target['java'],
                            'file': str(path.relative_to(ROOT)), 'bytes': path.stat().st_size,
                            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                            'packaging': 'passed', 'in_game': 'not_tested'})
    out = ROOT / 'build/distributions'
    out.mkdir(exist_ok=True, parents=True)
    (out / f'matrix-{version}.json').write_text(json.dumps(results, indent=2) + '\n')
    (out / f'SHA256SUMS-{version}.txt').write_text(''.join(f'{r["sha256"]}  {r["file"]}\n' for r in results))
    print(f'PASS: {len(results)} artifacts; metadata, bytecode, libraries, licenses and catalogs verified. No game launched.')


if __name__ == '__main__':
    verify()
