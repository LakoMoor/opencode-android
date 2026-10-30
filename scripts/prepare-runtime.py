#!/usr/bin/env python3
"""Fetch pinned upstream runtime assets. SHA-256 is checked before extraction."""
import hashlib, json, pathlib, shutil, subprocess, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / 'runtime' / 'downloads'
ASSETS = ROOT / 'app/src/main/assets/runtime'
LIBS = ROOT / 'app/src/main/jniLibs/arm64-v8a'
for path in (CACHE, ASSETS, LIBS): path.mkdir(parents=True, exist_ok=True)
lock = json.loads((ROOT / 'runtime/lock.json').read_text())
for item in lock['artifacts']:
    dest = CACHE / item['name']
    if not dest.exists() or hashlib.sha256(dest.read_bytes()).hexdigest() != item['sha256']:
        print('Downloading', item['url'], flush=True)
        temp = dest.with_suffix('.part')
        urllib.request.urlretrieve(item['url'], temp)
        if hashlib.sha256(temp.read_bytes()).hexdigest() != item['sha256']: raise RuntimeError('Checksum mismatch: '+item['name'])
        temp.replace(dest)
    if item['kind'] == 'asset': shutil.copyfile(dest, ASSETS / item['name'].replace('.tar.gz', '.tgz'))
    if item['kind'] == 'deb':
        # Parse the ar container; tar handles its compressed data member.
        raw = dest.read_bytes(); offset = 8; archive = None
        while offset + 60 <= len(raw):
            header = raw[offset:offset+60]; size = int(header[48:58]); name = header[:16].decode().strip().rstrip('/')
            payload = raw[offset+60:offset+60+size]
            if name.startswith('data.tar.'): archive = payload; ext = name.rsplit('.', 1)[1]; break
            offset += 60 + size + size % 2
        if archive is None: raise RuntimeError('Missing deb data archive')
        stage = CACHE / item['name'].replace('.deb',''); stage.mkdir(exist_ok=True)
        subprocess.run(['tar',{'xz':'-J','gz':'-z','zst':'--zstd','bz2':'-j'}[ext],'-xf','-','-C',str(stage)],input=archive,check=True)
        prefix = stage / 'data/data/com.termux/files/usr'
        for source, target in item['files'].items(): shutil.copyfile(prefix / source, LIBS / target)
(ASSETS/'lock.json').write_text(json.dumps(lock,indent=2)+'\n')
subprocess.run(['python3','-I',str(ROOT/'scripts/prepare-tools.py')],check=True)
print('Runtime assets ready', flush=True)
