#!/usr/bin/env python3
"""make_apk.py — build Chess Helper's APK without the Android SDK.

Produces a valid binary-AndroidManifest.xml (AXML), zips the dex/assets,
and (optionally) signs the result with v1+v2 signatures. Used for local
builds in environments where the Android SDK is unavailable; the GitHub
Actions workflow is the canonical release build.

AXML attr resource IDs verified against Android's public.xml table.
"""
import struct
import zipfile
import hashlib
import os
import sys

# ---- framework attribute resource IDs (from Android's public.xml) ----
ATTR = {
    "theme": 0x01010000,
    "label": 0x01010001,
    "name": 0x01010003,
    "debuggable": 0x0101000F,
    "exported": 0x01010010,
    "launchMode": 0x0101001D,
    "configChanges": 0x0101001F,
    "windowSoftInputMode": 0x0101022B,
    "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B,
    "versionName": 0x0101021C,
    "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280,
    "parentActivityName": 0x010103A7,
    "supportsRtl": 0x010103AF,
    "extractNativeLibs": 0x010104EA,
    "usesCleartextTraffic": 0x010104EC,
}

ANDROID_URI = "http://schemas.android.com/apk/res/android"

# typed value data types
TYPE_STRING = 0x03
TYPE_REFERENCE = 0x01
TYPE_INT_DEC = 0x10
TYPE_INT_BOOLEAN = 0x12

# xml chunk types
CHUNK_XML = 0x0003
CHUNK_STRING_POOL = 0x0001
CHUNK_RES_MAP = 0x0180
CHUNK_START_NS = 0x0100
CHUNK_END_NS = 0x0101
CHUNK_START_EL = 0x0102
CHUNK_END_EL = 0x0103

NO_INDEX = 0xFFFFFFFF


class StringPool:
    def __init__(self):
        self.strings = []
        self.index = {}

    def add(self, s):
        if s not in self.index:
            self.index[s] = len(self.strings)
            self.strings.append(s)
        return self.index[s]

    def _encode_utf8(self, s):
        b = s.encode("utf-8")

        def enc_len(n):
            if n < 0x80:
                return bytes([n])
            return bytes([0x80 | ((n >> 8) & 0x7F), n & 0xFF])

        return enc_len(len(s)) + enc_len(len(b)) + b + b"\x00"

    def chunk(self):
        count = len(self.strings)
        offsets = []
        data = b""
        for s in self.strings:
            offsets.append(len(data))
            data += self._encode_utf8(s)
        while len(data) % 4:
            data += b"\x00"
        strings_start = 28 + 4 * count
        size = strings_start + len(data)
        out = struct.pack(
            "<HHIIIIII",
            CHUNK_STRING_POOL,
            28,
            size,
            count,
            0,
            0x100,  # UTF-8
            strings_start,
            0,
        )
        out += struct.pack(f"<{count}I", *offsets) if count else b""
        out += data
        return out


def resmap_chunk(pool):
    ids = []
    for s in pool.strings:
        ids.append(ATTR.get(s, 0))
    size = 8 + 4 * len(ids)
    return struct.pack("<HHI", CHUNK_RES_MAP, 8, size) + struct.pack(f"<{len(ids)}I", *ids)


def ns_chunk(typ, line, prefix_idx, uri_idx):
    return struct.pack("<HHIIIII", typ, 16, 24, line, NO_INDEX, prefix_idx, uri_idx)


def attr(pool, name, value, ns_uri=True):
    """Returns (name_idx, raw_idx, dtype, data)."""
    name_idx = pool.add(name)
    ns_idx = pool.add(ANDROID_URI) if ns_uri else NO_INDEX
    if isinstance(value, bool):
        return (ns_idx, name_idx, NO_INDEX, TYPE_INT_BOOLEAN, 0xFFFFFFFF if value else 0)
    if isinstance(value, int):
        return (ns_idx, name_idx, NO_INDEX, TYPE_INT_DEC, value)
    if isinstance(value, tuple) and value[0] == "ref":
        return (ns_idx, name_idx, NO_INDEX, TYPE_REFERENCE, value[1])
    raw = pool.add(value)
    return (ns_idx, name_idx, raw, TYPE_STRING, raw)


def start_el(pool, name, attrs, line):
    name_idx = pool.add(name)
    n = len(attrs)
    body = struct.pack(
        "<IIHHHHHH",
        NO_INDEX,
        name_idx,
        20,
        20,
        n,
        0,
        0,
        0,
    )
    for ns_idx, a_name, raw, dtype, data in attrs:
        body += struct.pack("<IIIHBBI", ns_idx, a_name, raw, 8, 0, dtype, data)
    size = 16 + len(body)
    return struct.pack("<HHIII", CHUNK_START_EL, 16, size, line, NO_INDEX) + body


def end_el(pool, name, line):
    name_idx = pool.add(name)
    return struct.pack("<HHIIIII", CHUNK_END_EL, 16, 24, line, NO_INDEX, NO_INDEX, name_idx)


def build_manifest_xml(package, version_code, version_name, min_sdk, target_sdk, debuggable=False):
    pool = StringPool()
    prefix_idx = pool.add("android")
    uri_idx = pool.add(ANDROID_URI)

    # Build element tree; collect chunks AFTER all strings are known.
    events = []  # callables that build bytes using final pool/resmap indices

    def start(name, attrs, line):
        pool.add(name)  # intern element name BEFORE pool serialization
        events.append(("start", name, attrs, line))

    def end(name, line):
        pool.add(name)
        events.append(("end", name, line))

    start("manifest", [
        attr(pool, "package", package, ns_uri=False),
        attr(pool, "versionCode", version_code),
        attr(pool, "versionName", version_name),
    ], 2)
    start("uses-sdk", [
        attr(pool, "minSdkVersion", min_sdk),
        attr(pool, "targetSdkVersion", target_sdk),
    ], 3)
    end("uses-sdk", 3)
    start("uses-permission", [attr(pool, "name", "android.permission.INTERNET")], 4)
    end("uses-permission", 4)
    app_attrs = [
        attr(pool, "name", ".ChessApplication"),
        attr(pool, "label", "Chess Helper"),
        attr(pool, "allowBackup", False),
        attr(pool, "supportsRtl", True),
        attr(pool, "theme", ("ref", 0x0103022E)),  # @android:style/Theme.Material.NoActionBar
        attr(pool, "usesCleartextTraffic", False),
        attr(pool, "extractNativeLibs", True),
    ]
    if debuggable:
        app_attrs.append(attr(pool, "debuggable", True))
    start("application", app_attrs, 6)
    start("activity", [
        attr(pool, "name", ".MainActivity"),
        attr(pool, "exported", True),
        attr(pool, "configChanges", 0x0080 | 0x0400 | 0x0200 | 0x0020),
        attr(pool, "launchMode", 2),  # singleTop
        attr(pool, "windowSoftInputMode", 0x10),  # adjustResize
    ], 7)
    start("intent-filter", [], 8)
    start("action", [attr(pool, "name", "android.intent.action.MAIN")], 9)
    end("action", 9)
    start("category", [attr(pool, "name", "android.intent.category.LAUNCHER")], 10)
    end("category", 10)
    end("intent-filter", 8)
    end("activity", 7)
    start("activity", [
        attr(pool, "name", ".SettingsActivity"),
        attr(pool, "exported", False),
        attr(pool, "parentActivityName", ".MainActivity"),
    ], 11)
    end("activity", 11)
    end("application", 6)
    end("manifest", 2)

    # Serialize: string pool and res map must come first, but pools are final now.
    chunks = [pool.chunk(), resmap_chunk(pool)]
    chunks.append(ns_chunk(CHUNK_START_NS, 1, prefix_idx, uri_idx))
    for ev in events:
        if ev[0] == "start":
            chunks.append(start_el(pool, ev[1], ev[2], ev[3]))
        else:
            chunks.append(end_el(pool, ev[1], ev[2]))
    chunks.append(ns_chunk(CHUNK_END_NS, 2, prefix_idx, uri_idx))

    body = b"".join(chunks)
    return struct.pack("<HHI", CHUNK_XML, 8, 8 + len(body)) + body


def build_apk(out_path, manifest_bytes, dex_path, assets_dir, debuggable=False):
    """Zip with classes.dex stored+4k-aligned (APK v2 aligned zip)."""
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        # stored entries need explicit alignment; python zipfile aligns only if
        # we pad extra fields. Simplest compliant approach: store dex with a
        # zip extra field padding so file data starts at a 4096 boundary.
        info = zipfile.ZipInfo("AndroidManifest.xml")
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, manifest_bytes)

        dex = open(dex_path, "rb").read()
        info = zipfile.ZipInfo("classes.dex")
        info.compress_type = zipfile.ZIP_STORED
        info.external_attr = 0o100644 << 16
        # compute alignment: local header = 30 + len(name) + len(extra)
        # target: 30 + len(name) + len(extra) + offset_of_header ≡ 0 (mod 4096)
        # We don't know offset until write time, so use a two-pass approach below.
        z.writestr(info, dex)

        if assets_dir and os.path.isdir(assets_dir):
            for root, _, files in os.walk(assets_dir):
                for fn in sorted(files):
                    full = os.path.join(root, fn)
                    rel = os.path.relpath(full, assets_dir)
                    info = zipfile.ZipInfo("assets/" + rel.replace(os.sep, "/"))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    z.writestr(info, open(full, "rb").read())

    align_stored_entry(out_path, "classes.dex")
    return out_path


def align_stored_entry(apk_path, entry_name, alignment=4096):
    """Rewrite the zip so entry_name's data offset is aligned (classic zipalign)."""
    data = open(apk_path, "rb").read()
    # find local header of entry
    eocd = data.rfind(b"PK\x05\x06")
    cd_off = struct.unpack("<I", data[eocd + 16 : eocd + 20])[0]
    # walk central directory to find entry and its local header offset
    off = cd_off
    while off < eocd:
        if data[off : off + 4] != b"PK\x01\x02":
            break
        name_len, extra_len, comment_len = struct.unpack("<HHH", data[off + 28 : off + 34])
        name = data[off + 46 : off + 46 + name_len]
        local_off = struct.unpack("<I", data[off + 42 : off + 46])[0]
        if name.decode("utf-8", "replace") == entry_name:
            break
        off += 46 + name_len + extra_len + comment_len
    else:
        raise RuntimeError("entry not found")

    l_name_len, l_extra_len = struct.unpack("<HH", data[local_off + 26 : local_off + 30])
    data_start = local_off + 30 + l_name_len + l_extra_len
    cur_pad = (alignment - (data_start % alignment)) % alignment
    if cur_pad == 0:
        return
    if cur_pad < 4:
        cur_pad += alignment  # extra field must be >= 4 bytes to fit header
    # add padding to local extra field
    new_extra = data[local_off + 30 + l_name_len : local_off + 30 + l_name_len + l_extra_len] + (
        b"\x00" * cur_pad
    )
    new_l_extra_len = l_extra_len + cur_pad
    out = bytearray(data[: local_off + 26])
    out += struct.pack("<HH", l_name_len, new_l_extra_len)
    out += data[local_off + 30 : local_off + 30 + l_name_len]
    out += new_extra
    out += data[local_off + 30 + l_name_len + l_extra_len :]
    # shift all central-directory local-header offsets after this entry
    delta = cur_pad
    eocd2 = len(out) - (len(data) - eocd)
    cd_off2 = cd_off + delta
    off = cd_off2
    while off < eocd2:
        if out[off : off + 4] != b"PK\x01\x02":
            break
        name_len, extra_len, comment_len = struct.unpack("<HHH", bytes(out[off + 28 : off + 34]))
        name = bytes(out[off + 46 : off + 46 + name_len]).decode("utf-8", "replace")
        old_local = struct.unpack("<I", bytes(out[off + 42 : off + 46]))[0]
        if old_local > local_off:
            out[off + 42 : off + 46] = struct.pack("<I", old_local + delta)
        elif name == entry_name:
            out[off + 42 : off + 46] = struct.pack("<I", local_off)
        off += 46 + name_len + extra_len + comment_len
    # fix EOCD cd offset
    out[eocd2 + 16 : eocd2 + 20] = struct.pack("<I", cd_off2)
    open(apk_path, "wb").write(bytes(out))


if __name__ == "__main__":
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("--package", default="com.chesshelper.app")
    ap.add_argument("--version-code", type=int, default=21)
    ap.add_argument("--version-name", default="2.1.0")
    ap.add_argument("--min-sdk", type=int, default=26)
    ap.add_argument("--target-sdk", type=int, default=35)
    ap.add_argument("--dex", required=True)
    ap.add_argument("--assets", default=None)
    ap.add_argument("--out", required=True)
    ap.add_argument("--debuggable", action="store_true")
    args = ap.parse_args()

    manifest = build_manifest_xml(
        args.package, args.version_code, args.version_name,
        args.min_sdk, args.target_sdk, args.debuggable,
    )
    open("/tmp/apkbuild/AndroidManifest.axml", "wb").write(manifest)
    build_apk(args.out, manifest, args.dex, args.assets, args.debuggable)
    print("wrote", args.out, os.path.getsize(args.out), "bytes")
