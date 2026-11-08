#!/usr/bin/env python3
"""Package the modified React Native bundle with Sommet v0.2.59's unchanged arm64 runtime."""
import argparse
import hashlib
import re
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED


def replace_fixed(data: bytes, old: bytes, new: bytes, expected: int) -> bytes:
    if len(old) != len(new) or data.count(old) != expected:
        raise ValueError("Unexpected binary structure for " + repr(old))
    return data.replace(old, new)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("official_apk", type=Path)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("unsigned_apk", type=Path)
    parser.add_argument('--manifest-apk', type=Path, required=True)
    parser.add_argument('--addon-dex', type=Path, required=True)
    parser.add_argument('--replacement-dex-dir', type=Path)
    parser.add_argument('--package-name', default='com.ambitsportlocal')
    args = parser.parse_args()
    with ZipFile(args.manifest_apk) as patched:
        manifest = patched.read('AndroidManifest.xml')
    expected_hash = Path(__file__).with_name("base.sha256").read_text().strip()
    if hashlib.file_digest(args.official_apk.open("rb"), "sha256").hexdigest() != expected_hash:
        raise ValueError("Expected the exact Sommet v0.2.59 base APK")
    old = "com.ambitsyncmodern"
    new = args.package_name
    suffixes = ("", ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", ".fileprovider",
                ".mlkitinitprovider", ".androidx-startup")
    with ZipFile(args.official_apk) as source, ZipFile(args.unsigned_apk, "w") as out:
        existing_bundle = source.read("assets/index.android.bundle")
        bundle = args.bundle.read_bytes()
        if existing_bundle[:12] != bundle[:12]:
            raise ValueError("Bundle does not match the original Hermes magic and bytecode version")
        for entry in source.infolist():
            name = entry.filename
            if re.fullmatch(r"META-INF/[^/]+\.(?:MF|SF|RSA|DSA|EC)", name, re.IGNORECASE):
                continue
            if name.startswith("lib/") and not name.startswith("lib/arm64-v8a/"):
                continue
            data = source.read(entry)
            if name == "AndroidManifest.xml":
                data = manifest
            elif name == "resources.arsc":
                data = replace_fixed(data, old.encode("utf-16le"), new.encode("utf-16le"), 1)
                data = replace_fixed(data, b"\x06\x06Sommet\0", b"\x06\x06Ambit3\0", 1)
            elif args.replacement_dex_dir and re.fullmatch(r'classes(\d*)\.dex', name):
                replacement = args.replacement_dex_dir / name
                if replacement.is_file():
                    data = replacement.read_bytes()
            elif name == "assets/index.android.bundle":
                data = bundle
            out.writestr(entry, data)
        dex_numbers = [int(m.group(1) or '1') for n in source.namelist() if (m := re.fullmatch(r'classes(\d*)\.dex', n))]
        out.writestr('classes%d.dex' % (max(dex_numbers) + 1), args.addon_dex.read_bytes(), compress_type=ZIP_DEFLATED)


if __name__ == "__main__":
    main()
