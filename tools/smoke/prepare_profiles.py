"""Create isolated Prism 26.3 smoke profiles; never copy accounts or user chat."""
import json
import os
import shutil
from pathlib import Path

project = Path(__file__).resolve().parents[2]
instances = Path(os.environ['APPDATA']) / 'PrismLauncher/instances'
world = instances / '26.3/minecraft/saves/Chat Chronicle Test'
targets = {
    'fabric': ('net.fabricmc.fabric-loader', '0.20.1'),
    'neoforge': ('net.neoforged', '26.3.0.101'),
    'quilt': ('org.quiltmc.quilt-loader', '0.31.0-beta.5'),
}
for loader, (uid, version) in targets.items():
    profile = instances / ('CC-Smoke-26.3-' + loader)
    if profile.exists():
        raise SystemExit('Refusing to overwrite existing profile: ' + str(profile))
    game = profile / 'minecraft'
    (game / 'mods').mkdir(parents=True)
    components = [{'uid': 'net.minecraft', 'version': '26.3', 'important': True}]
    if loader in ('fabric', 'quilt'):
        components.append({'uid': 'net.fabricmc.intermediary', 'version': '26.3', 'dependencyOnly': True})
    components.append({'uid': uid, 'version': version})
    (profile / 'mmc-pack.json').write_text(json.dumps({'formatVersion': 1, 'components': components}, indent=2))
    (profile / 'instance.cfg').write_text('''[General]
InstanceType=OneSix
name=CC Smoke 26.3 ''' + loader + '''
iconKey=default
OverrideJavaLocation=true
JavaPath=C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin/javaw.exe
AutomaticJava=false
OverrideMemory=true
MinMemAlloc=512
MaxMemAlloc=2048
OverrideWindow=true
MinecraftWinWidth=1000
MinecraftWinHeight=650
LaunchMaximized=false
OverrideCommands=true
PreLaunchCommand=
PostExitCommand=
WrapperCommand=
''')
    shutil.copytree(world, game / 'saves/Chat Chronicle Smoke', ignore=shutil.ignore_patterns('session.lock'))
    release = project / f'build/releases/26.3/{loader}/chat-chronicle-{loader}-mc26.3-0.2.0.jar'
    shutil.copy2(release, game / 'mods' / release.name)
    shutil.copy2(project / '.tools/smoke/harness/chat-chronicle-smoke-harness.jar', game / 'mods')
    (game / 'chatchronicle-smoke-authorized').write_text('Isolated Chat Chronicle acceptance test\n')
    (game / 'options.txt').write_text('enableVsync:false\nmaxFps:60\nrenderDistance:2\nsimulationDistance:5\n')
    print(profile)
