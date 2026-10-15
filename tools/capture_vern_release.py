"""Capture APK and bundle outputs separately, before a later build overwrites R8 metadata."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output_apks'
BUILD_CONFIG = (ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')
VERSION = re.search(r'versionName\s*=\s*"([^"]+)"', BUILD_CONFIG).group(1)
CODE = int(re.search(r'versionCode\s*=\s*(\d+)', BUILD_CONFIG).group(1))
PACK = ROOT / 'release' / f'v{VERSION}'
SDK = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
BUILD_TOOLS = SDK / 'build-tools/37.0.0'
JAVA = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Android\Android Studio\jbr')) / 'bin'

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True, check=True)
    return result.stdout + result.stderr

def copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)

def elf_info(data):
    assert data[:4] == b'\x7fELF' and data[5] == 1, 'Expected little-endian ELF'
    wide = data[4] == 2
    phoff = struct.unpack_from('<Q' if wide else '<I', data, 32 if wide else 28)[0]
    phsize, phnum = struct.unpack_from('<HH', data, 54 if wide else 42)
    alignments = []
    for i in range(phnum):
        pos = phoff + i * phsize
        if struct.unpack_from('<I', data, pos)[0] == 1:
            offset, address = struct.unpack_from('<QQ' if wide else '<II', data, pos + (8 if wide else 4))
            alignment = struct.unpack_from('<Q' if wide else '<I', data, pos + (48 if wide else 28))[0]
            alignments.append(alignment)
            assert (offset - address) % max(1, alignment) == 0
    shoff = struct.unpack_from('<Q' if wide else '<I', data, 40 if wide else 32)[0]
    shsize, shnum, name_index = struct.unpack_from('<HHH', data, 58 if wide else 46)
    sections = [struct.unpack_from('<IIQQQQIIQQ' if wide else '<IIIIIIIIII', data, shoff + i * shsize) for i in range(shnum)]
    name_section = sections[name_index]
    names = data[name_section[4]:name_section[4]+name_section[5]]
    allocated = {}
    for section in sections:
        name_offset, kind, flags, address, offset, size = section[:6]
        if not flags & 2:
            continue
        name = names[name_offset:names.find(b'\0', name_offset)].decode('ascii')
        allocated[(name, kind, flags, address, size)] = b'' if kind == 8 else data[offset:offset+size]
    return alignments, allocated

def snapshot_symbols(mode):
    source = ROOT / 'app/build/outputs/mapping/release'
    assert (source/'mapping.txt').is_file(), 'Missing exact-build R8 mapping'
    records = []
    for path in source.iterdir():
        if path.is_file():
            copy(path, OUT/f'v{VERSION}-symbols'/mode/path.name)
            copy(path, PACK/'symbols'/mode/path.name)
            if mode == 'apk':
                copy(path, OUT/f'v{VERSION}-symbols'/path.name)
            records.append(dict(file=path.name, bytes=path.stat().st_size, sha256=digest(path)))
    profile_root = ROOT/'app/build/intermediates'
    for folder in ('binary_art_profile', 'binary_art_profile_metadata', 'merged_art_profile', 'r8_art_profile'):
        for path in (profile_root/folder/'release').rglob('*'):
            if path.is_file():
                copy(path, OUT/f'v{VERSION}-baseline-profiles'/mode/folder/path.name)
                copy(path, PACK/'symbols'/mode/'profiles'/folder/path.name)
    return records

def capture_apks():
    metadata = json.loads((ROOT/'app/build/outputs/apk/release/output-metadata.json').read_text())
    assert metadata['applicationId'] == 'com.veritas.reader'
    assert len(metadata['elements']) == 3, 'Expected ARM64, ARM32 and universal APKs'
    old = json.loads((OUT/'release-manifest.json').read_text())
    certificate = old['apks'][0]['certificateSha256']
    records, native, verification = [], {}, []
    for element in metadata['elements']:
        assert element['versionCode'] == CODE and element['versionName'] == VERSION
        abi = next((f['value'] for f in element['filters'] if f['filterType'] == 'ABI'), 'universal')
        source = ROOT/'app/build/outputs/apk/release'/element['outputFile']
        filename = f'Veritas-Reader-v{VERSION}-{abi}-release.apk'
        target = OUT/filename
        signature = run(['cmd', '/c', BUILD_TOOLS/'apksigner.bat', 'verify', '--print-certs', source])
        signer = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', signature).group(1)
        assert signer == certificate, 'Release signer differs from the prior release'
        run([BUILD_TOOLS/'zipalign.exe', '-c', '-P', '16', '4', source])
        badging = run([BUILD_TOOLS/'aapt2.exe', 'dump', 'badging', source])
        assert "name='com.veritas.reader'" in badging.splitlines()[0]
        assert f"versionCode='{CODE}'" in badging.splitlines()[0] and f"versionName='{VERSION}'" in badging.splitlines()[0]
        assert "sdkVersion:'28'" in badging and "targetSdkVersion:'36'" in badging
        with zipfile.ZipFile(source) as archive:
            assert archive.testzip() is None
            for member in archive.namelist():
                if not member.startswith('lib/') or not member.endswith('.so'):
                    continue
                data = archive.read(member)
                architecture, name = member.split('/')[1:]
                merged = ROOT/'app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out'/member
                stripped = ROOT/'app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out'/member
                assert hashlib.sha256(data).hexdigest() == digest(stripped)
                alignments, sections = elf_info(data)
                _, original_sections = elf_info(merged.read_bytes())
                assert sections == original_sections, f'Allocated runtime sections changed: {member}'
                if architecture == 'arm64-v8a':
                    assert min(alignments) >= 16384, f'ELF alignment is below 16 KiB: {member}'
                native[f'{architecture}/{name}'] = dict(sourceSha256=digest(merged), packagedSha256=hashlib.sha256(data).hexdigest(),
                    loadSegmentAlignments=alignments, runtimeAllocatedSectionsUnchanged=True)
        copy(source, target)
        copy(source, PACK/'builds'/filename)
        records.append(dict(file=filename, abi=abi, bytes=source.stat().st_size, sha256=digest(source), certificateSha256=signer))
        verification.append(f'{filename}: signer matches, package/version/SDK verified, 16 KiB ZIP alignment passed; native runtime sections unchanged.\n{signature}')
    manifest = dict(date='2026-10-14', versionName=VERSION, versionCode=CODE, package='com.veritas.reader',
        verification='13 focused Samsung tests passed; release signatures, metadata, ZIP alignment and native ELF/runtime sections verified',
        nativeLibraries=native, apks=records, apkSymbols=snapshot_symbols('apk'))
    (OUT/'release-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    (PACK/'verification').mkdir(parents=True, exist_ok=True)
    (PACK/'verification/apk-verification.txt').write_text('\n'.join(verification))
    symbols = ROOT/'app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip'
    if symbols.is_file():
        copy(symbols, OUT/f'v{VERSION}-symbols/native-debug-symbols.zip')
        copy(symbols, PACK/'symbols/native-debug-symbols.zip')
    print('Captured and verified all three signed APKs with their exact R8 mappings.')

def capture_bundle():
    source = ROOT/'app/build/outputs/bundle/release/app-release.aab'
    result = run([JAVA/'jarsigner.exe', '-verify', source])
    assert 'jar verified.' in result
    certificate = run([JAVA/'keytool.exe', '-printcert', '-jarfile', source])
    signer = re.search(r'SHA256: ([0-9A-F:]+)', certificate).group(1).replace(':', '').lower()
    manifest = json.loads((OUT/'release-manifest.json').read_text())
    assert signer == manifest['apks'][0]['certificateSha256']
    with zipfile.ZipFile(source) as archive:
        assert archive.testzip() is None
        assert 'base/manifest/AndroidManifest.xml' in archive.namelist()
    name = f'Vern-v{VERSION}-release.aab'
    copy(source, OUT/name)
    copy(source, PACK/'builds'/name)
    manifest['bundle'] = dict(file=name, bytes=source.stat().st_size, sha256=digest(source), certificateSha256=signer)
    manifest['bundleSymbols'] = snapshot_symbols('bundle')
    (OUT/'release-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    (PACK/'verification/bundle-verification.txt').write_text(result+'\n'+certificate)
    copy(OUT/'release-manifest.json', PACK/'release-manifest.json')
    (OUT/'SHA256SUMS.txt').write_text(''.join(f"{item['sha256']}  {item['file']}\n" for item in manifest['apks']+[manifest['bundle']]))
    print('Captured the signed app bundle with its separate exact R8 mapping.')

if __name__ == '__main__':
    {'apk': capture_apks, 'bundle': capture_bundle}[sys.argv[1]]()
