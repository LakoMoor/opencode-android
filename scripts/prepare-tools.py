#!/usr/bin/env python3
"""Bundle the Linux dev tools (git, python3, curl, ripgrep, ssh...) as a pre-extracted overlay so the APK works offline and first launch needs no dpkg.

Resolves dependencies against Ubuntu 24.04 (noble) arm64 indexes, skipping what ubuntu-base already ships,
verifies every SHA-256 from the index and unpacks the .debs and packs them into assets/runtime/tools-overlay.tgz.
"""
import gzip, hashlib, pathlib, re, shutil, subprocess, tarfile, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / 'runtime' / 'downloads'
ASSETS = ROOT / 'app/src/main/assets/runtime'
MIRROR = 'https://ports.ubuntu.com/ubuntu-ports'
WANT = ['ca-certificates', 'git', 'curl', 'ripgrep', 'openssh-client', 'python3', 'unzip', 'libstdc++6', 'libatomic1', 'libgomp1', 'libssl3t64']
POCKETS = ['noble', 'noble-updates', 'noble-security']
COMPONENTS = ['main', 'universe']
LLAMA_TAG = 'b12017'
LLAMA_SHA256 = '676e8f99db67fbecd7854b948ed50d0bf5d7db463613fc8fcd8f4491a7709bec'
LLAMA_URL = f'https://github.com/ggml-org/llama.cpp/releases/download/{LLAMA_TAG}/llama-{LLAMA_TAG}-bin-ubuntu-arm64.tar.gz'

def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as r: return r.read()

def parse(text):
    for block in text.split('\n\n'):
        fields, key = {}, None
        for line in block.splitlines():
            if line[:1] in ' \t' and key: fields[key] += '\n' + line
            elif ':' in line: key, value = line.split(':', 1); fields[key] = value.strip()
        if 'Package' in fields: yield fields

def index():
    packages, provides = {}, {}
    for pocket in POCKETS:
        for comp in COMPONENTS:
            data = gzip.decompress(fetch(f'{MIRROR}/dists/{pocket}/{comp}/binary-arm64/Packages.gz')).decode()
            for p in parse(data): packages[p['Package']] = p  # later pockets win
    for p in packages.values():
        for item in p.get('Provides', '').split(','):
            name = item.strip().split(' ')[0]
            if name: provides.setdefault(name, p['Package'])
    return packages, provides

def installed_base():
    with tarfile.open(CACHE / 'ubuntu-base-24.04.5-base-arm64.tar.gz') as tar:
        member = next(m for m in tar if m.name.lstrip('./') == 'var/lib/dpkg/status')
        status = tar.extractfile(member).read().decode()
    names = set()
    for p in parse(status):
        names.add(p['Package'])
        for item in p.get('Provides', '').split(','): names.add(item.strip().split(' ')[0])
    return names

def main():
    packages, provides = index(); have = installed_base(); chosen = {}; queue = list(WANT)
    while queue:
        name = queue.pop()
        if name in have or name in chosen: continue
        name = name if name in packages else provides.get(name, name)
        if name in have or name in chosen: continue
        if name not in packages: raise SystemExit('Unresolvable package: ' + name)
        chosen[name] = packages[name]
        for group in re.split(r',\s*', packages[name].get('Depends', '') + ',' + packages[name].get('Pre-Depends', '')):
            alts = [re.sub(r'[ (:].*', '', a.strip()) for a in group.split('|') if a.strip()]
            if not alts or any(a in have for a in alts): continue
            queue.append(next((a for a in alts if a in packages or a in provides), alts[0]))
    CACHE.mkdir(parents=True, exist_ok=True); ASSETS.mkdir(parents=True, exist_ok=True)
    stage = CACHE / 'tools-overlay'
    if stage.exists(): shutil.rmtree(stage)
    stage.mkdir()
    for name in sorted(chosen):
        p = chosen[name]; dest = CACHE / pathlib.Path(p['Filename']).name
        if not dest.exists() or hashlib.sha256(dest.read_bytes()).hexdigest() != p['SHA256']:
            print('Downloading', p['Filename'], flush=True)
            dest.write_bytes(fetch(f"{MIRROR}/{p['Filename']}"))
            if hashlib.sha256(dest.read_bytes()).hexdigest() != p['SHA256']: raise SystemExit('Checksum mismatch: ' + name)
        raw = dest.read_bytes(); offset = 8; data = None
        while offset + 60 <= len(raw):
            size = int(raw[offset+48:offset+58]); member = raw[offset:offset+16].decode().strip().rstrip('/')
            if member.startswith('data.tar.'): data = raw[offset+60:offset+60+size]; ext = member.rsplit('.', 1)[1]; break
            offset += 60 + size + size % 2
        if data is None: raise SystemExit('No data archive in ' + name)
        subprocess.run(['tar', {'xz':'-J','gz':'-z','zst':'--zstd','bz2':'-j'}[ext], '-xf', '-', '-C', str(stage)], input=data, check=True)
    # Pre-extracted overlay: the phone just unpacks it, no dpkg/PRoot work at first launch.
    for junk in ('usr/share/doc', 'usr/share/man', 'usr/share/locale', 'usr/share/info', 'usr/share/lintian', 'var'):
        shutil.rmtree(stage / junk, ignore_errors=True)
    # llama.cpp (official glibc arm64 build): only the server and its libraries go into /opt/llama.
    llama = CACHE / f'llama-{LLAMA_TAG}-ubuntu-arm64.tar.gz'
    if not llama.exists() or hashlib.sha256(llama.read_bytes()).hexdigest() != LLAMA_SHA256:
        print('Downloading llama.cpp', LLAMA_TAG, flush=True); llama.write_bytes(fetch(LLAMA_URL))
        if hashlib.sha256(llama.read_bytes()).hexdigest() != LLAMA_SHA256: raise SystemExit('Checksum mismatch: llama.cpp')
    unpack = CACHE / 'llama-unpacked'
    if unpack.exists(): shutil.rmtree(unpack)
    unpack.mkdir(); subprocess.run(['tar', '-xzf', str(llama), '-C', str(unpack)], check=True)
    src = next(unpack.iterdir()); dst = stage / 'opt/llama'; dst.mkdir(parents=True)
    for f in src.iterdir():
        if f.name == 'LICENSE' or f.name == 'llama-server' or (f.name.startswith('lib') and '.so' in f.name and ('impl' not in f.name or f.name.startswith(('libllama-server-impl','libllama-fit-params-impl'))) and 'rpc' not in f.name):
            shutil.copy2(f, dst / f.name, follow_symlinks=False)
    certs = sorted((stage / 'usr/share/ca-certificates').rglob('*.crt'))
    (stage / 'etc/ssl/certs').mkdir(parents=True, exist_ok=True)
    (stage / 'etc/ssl/certs/ca-certificates.crt').write_bytes(b''.join(c.read_bytes() for c in certs))
    out = ASSETS / 'tools-overlay.tgz'
    subprocess.run(['tar', '-czf', str(out), '-C', str(stage), '.'], check=True, env={'COPYFILE_DISABLE': '1', 'PATH': '/usr/bin:/bin'})
    (ASSETS / 'tools-debs.tgz').unlink(missing_ok=True)
    print(f'Bundled {len(chosen)} packages, {len(certs)} CA certs, {out.stat().st_size // 1048576} MiB')

if __name__ == '__main__': main()
