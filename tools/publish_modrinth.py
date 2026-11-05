"""Preview or publish the verified Chat Chronicle matrix. Python 3.9+."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / '.modrinth.local.json'
API = 'https://api.modrinth.com/v2'
LOADERS = ('fabric', 'neoforge', 'quilt')


class PublishError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(route, token, body=None, content_type=None):
    headers = {'User-Agent': 'TPexcoTblu/ChatChronicle (matrix publisher)',
               'Accept': 'application/json'}
    if token:
        headers['Authorization'] = token
    if content_type:
        headers['Content-Type'] = content_type
    try:
        with build_opener(NoRedirect()).open(Request(API + route, data=body, headers=headers), timeout=120) as reply:
            return json.load(reply)
    except HTTPError as exc:
        raise PublishError(f'Modrinth returned HTTP {exc.code} for {route}.') from None
    except (URLError, TimeoutError):
        raise PublishError(f'Connection failed for {route}. Check the project before retrying.') from None


def release_plan(channel):
    properties = (ROOT / 'gradle.properties').read_text(encoding='utf-8')
    version = re.search(r'^version=([\w.+-]+)$', properties, re.MULTILINE)
    if version is None:
        raise PublishError('Expected a version in gradle.properties.')
    version = version[1]
    targets = json.loads((ROOT / 'gradle/targets.json').read_text(encoding='utf-8'))
    expected = {(game, loader) for game in targets for loader in LOADERS}
    manifest = ROOT / f'build/distributions/matrix-{version}.json'
    if not manifest.is_file():
        raise PublishError('Build the full matrix and run tools/verify_matrix.py first.')
    plan = []
    seen = set()
    for row in json.loads(manifest.read_text(encoding='utf-8')):
        game, loader = row['minecraft'], row['loader']
        key = (game, loader)
        if key not in expected or key in seen or row['packaging'] != 'passed':
            raise PublishError(f'Unexpected, duplicate or unverified matrix entry: {game} / {loader}.')
        if not re.fullmatch(r'\d+(?:\.\d+)+', game):
            raise PublishError(f'Unsupported Minecraft version format: {game}.')
        seen.add(key)
        filename = f'chat-chronicle-{loader}-mc{game}-{version}.jar'
        relative = f'build/releases/{game}/{loader}/{filename}'
        path = ROOT / relative
        if row['file'].replace('\\', '/') != relative or path.resolve() != path:
            raise PublishError(f'Unexpected path for {game} / {loader}.')
        data = path.read_bytes()
        if len(data) != row['bytes'] or hashlib.sha256(data).hexdigest() != row['sha256']:
            raise PublishError(f'JAR differs from the verified matrix: {filename}.')
        suffix = '.' if '+' in version else '+'
        plan.append({
            'path': path, 'sha512': hashlib.sha512(data).hexdigest(),
            'mod_version': version,
            'version_number': f'{version}{suffix}mc{game}.{loader}',
            'name': f'Chat Chronicle {version} · Minecraft {game} · {loader.capitalize()}',
            'game_versions': [game], 'loaders': [loader], 'version_type': channel,
        })
    if seen != expected:
        raise PublishError(f'Missing builds: {", ".join(f"{g}/{l}" for g, l in sorted(expected - seen))}.')
    return plan


def existing_match(item, versions):
    matches = []
    for remote in versions:
        files = remote['files']
        same_file = any(f['filename'] == item['path'].name or f['hashes'].get('sha512') == item['sha512']
                        for f in files)
        same_version = remote['version_number'] == item['version_number']
        manual_version = (remote['version_number'] == item['mod_version']
                          and item['game_versions'][0] in remote['game_versions']
                          and item['loaders'][0] in remote['loaders'])
        if not (same_version or manual_version or same_file):
            continue
        primary = next((f for f in files if f['primary']), files[0] if files else {})
        if (primary.get('hashes', {}).get('sha512') != item['sha512']
                or remote['game_versions'] != item['game_versions'] or remote['loaders'] != item['loaders']
                or remote['version_type'] != item['version_type'] or remote['status'] != 'listed'):
            raise PublishError(f'Conflict for {item["version_number"]} with Modrinth version {remote["id"]}.')
        matches.append(remote)
    if len(matches) > 1:
        raise PublishError(f'Multiple existing uploads for {item["version_number"]}.')
    return matches[0] if matches else None


def upload(item, project_id, token, changelog):
    data = item['path'].read_bytes()
    if hashlib.sha512(data).hexdigest() != item['sha512']:
        raise PublishError(f'File changed after preflight: {item["path"].name}.')
    meta = {key: item[key] for key in ('version_number', 'name', 'game_versions', 'loaders', 'version_type')}
    meta.update(project_id=project_id, changelog=changelog, featured=False, dependencies=[],
                status='listed', environment='client_only', file_parts=['jar'], primary_file='jar')
    boundary = uuid4().hex
    body = b''.join((
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'.encode(),
        json.dumps(meta).encode(),
        f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="jar"; filename="{item["path"].name}"\r\n'
        'Content-Type: application/java-archive\r\n\r\n'.encode(),
        data,
        f'\r\n--{boundary}--\r\n'.encode(),
    ))
    remote = request('/version', token, body, f'multipart/form-data; boundary={boundary}')
    if remote['project_id'] != project_id or existing_match(item, [remote]) is None:
        raise PublishError(f'Unexpected response for {item["version_number"]}. Check Modrinth before retrying.')
    return remote['id']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--check-remote', action='store_true', help='compare with Modrinth without uploading')
    mode.add_argument('--publish', action='store_true', help='upload missing versions as listed versions')
    parser.add_argument('--channel', choices=('alpha', 'beta', 'release'), default='beta')
    parser.add_argument('--changelog', type=Path, help='Markdown release notes for this version')
    parser.add_argument('--source-url', help='HTTPS link to the matching source archive or tagged source')
    args = parser.parse_args()
    plan = release_plan(args.channel)
    if not (args.check_remote or args.publish):
        for item in plan:
            print(f'{item["version_number"]} [{args.channel}] {item["path"].relative_to(ROOT)}')
        print(f'Offline preview: {len(plan)} verified JARs.')
        return

    if not CONFIG.is_file():
        raise PublishError('Create .modrinth.local.json in the project root. See RELEASING.md.')
    config = json.loads(CONFIG.read_text(encoding='utf-8-sig'))
    project_id, slug = config['project_id'], config['expected_slug']
    if not re.fullmatch(r'[A-Za-z0-9]{8}', project_id) or not re.fullmatch(r'[a-z0-9_-]+', slug):
        raise PublishError('Set the Modrinth project ID and expected slug in .modrinth.local.json.')
    token = (os.environ.get('MODRINTH_TOKEN') or config.get('token', '')).strip()
    if token and not re.fullmatch(r'mrp_[A-Za-z0-9]+', token):
        raise PublishError('Expected a Modrinth personal access token.')
    changelog = None
    if args.publish:
        if not token or not args.changelog or not args.source_url:
            parser.error('--publish requires a token, --changelog and --source-url')
        source = urlsplit(args.source_url)
        if source.scheme != 'https' or not source.hostname or source.username or source.password or re.search(r'\s|[()]', args.source_url):
            parser.error('--source-url must be an HTTPS URL without credentials, spaces or parentheses')
        notes = args.changelog.read_text(encoding='utf-8-sig').strip()
        if not notes:
            parser.error('--changelog is empty')
        changelog = f'{notes}\n\n[Corresponding source code]({args.source_url})'
    project = request(f'/project/{project_id}', token)
    if project['id'] != project_id or project['slug'] != slug:
        raise PublishError('The configured project ID and slug do not match on Modrinth.')
    print(f'Project: {slug} ({project_id}), channel: {args.channel}')
    versions = request(f'/project/{project_id}/version?include_changelog=false', token)
    pending = []
    for item in plan:
        match = existing_match(item, versions)
        if match:
            print(f'SKIP {item["version_number"]}: {match["id"]}')
        else:
            pending.append(item)
            print(f'ADD  {item["version_number"]}')
    print(f'{len(pending)} pending, {len(plan) - len(pending)} already listed.')
    if args.publish:
        for item in pending:
            remote_id = upload(item, project_id, token, changelog)
            print(f'PUBLISHED {item["version_number"]}: https://modrinth.com/mod/{slug}/version/{remote_id}', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (PublishError, OSError, ValueError, KeyError, TypeError) as exc:
        # Avoid printing configuration, response bodies or authentication headers.
        message = str(exc) if isinstance(exc, (PublishError, OSError)) else 'Invalid configuration, matrix or API response.'
        print(f'Error: {message}', file=sys.stderr)
        sys.exit(1)
