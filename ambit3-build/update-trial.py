"""Replace the trial APK's Hermes bundle and addon DEX, and advance version 272 to 273."""
import argparse
import struct
from pathlib import Path
from zipfile import ZipFile

p = argparse.ArgumentParser(description=__doc__)
for name in ('base', 'bundle', 'dex', 'output'):
    p.add_argument(name, type=Path)
args = p.parse_args()

def bump_manifest(data):
    out = bytearray(data)
    pos = struct.unpack_from('<H', data, 2)[0]
    ids = ()
    found = 0
    while pos < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, pos)
        if size < 8 or pos + size > len(data):
            raise ValueError('Invalid manifest chunk')
        if kind == 0x0180:
            ids = struct.unpack_from('<' + 'I' * ((size - header) // 4), data, pos + header)
        if kind == 0x0102:
            extension = pos + header
            start, width, count = struct.unpack_from('<HHH', data, extension + 8)
            for i in range(count):
                attr = extension + start + i * width
                name = struct.unpack_from('<I', data, attr + 4)[0]
                if name < len(ids) and ids[name] == 0x0101021b:
                    if struct.unpack_from('<I', data, attr + 16)[0] != 272:
                        raise ValueError('Expected version 272')
                    struct.pack_into('<I', out, attr + 16, 273)
                    found += 1
        pos += size
    if found != 1:
        raise ValueError('Missing or duplicate versionCode')
    for encoding in ('utf-8', 'utf-16le'):
        before, after = ('0.2.72'.encode(encoding), '0.2.73'.encode(encoding))
        if out.count(before) == 1:
            return out.replace(before, after)
    raise ValueError('Expected unique version name 0.2.72')

with ZipFile(args.base) as base, ZipFile(args.output, 'w') as out:
    bundle_name = 'assets/index.android.bundle'
    bundle = args.bundle.read_bytes()
    if bundle[:12] != base.read(bundle_name)[:12]:
        raise ValueError('Incompatible Hermes bytecode')
    for name in ('AndroidManifest.xml', bundle_name, 'classes4.dex'):
        if name not in base.namelist():
            raise ValueError(f'Missing {name}')
    overrides = {'AndroidManifest.xml': bump_manifest(base.read('AndroidManifest.xml')),
                 bundle_name: bundle, 'classes4.dex': args.dex.read_bytes()}
    for entry in base.infolist():
        if entry.filename.startswith('META-INF/') and entry.filename.upper().endswith(('.RSA', '.SF', '.DSA', '.EC', '/MANIFEST.MF')):
            continue
        out.writestr(entry, overrides[entry.filename] if entry.filename in overrides else base.read(entry))
