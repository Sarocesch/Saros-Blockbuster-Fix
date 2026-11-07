"""Setzt die Mod-Version in der fertigen Jar (Blockbuster.class + mcmod.info).

Forge vergleicht beim Join nur diese Version. Bleibt sie gleich, kann ein Client
mit neuer Jar auf einen Server mit alter Jar - und Fixes, die auf dem Server
laufen, fehlen dort unbemerkt. Deshalb bei jedem Release hochzaehlen.

Aufruf: python set_version.py <jar> <version>
"""
import json
import struct
import sys
import zipfile
import os

CLASS = "mchorse/blockbuster/Blockbuster.class"


def patch_class(data, new):
    """Ersetzt den Utf8-Eintrag der @Mod-Version (2.7 oder schon gesetzt)."""
    count = struct.unpack(">H", data[8:10])[0]
    pos = 10
    out = bytearray(data[:10])
    index = 1
    found = 0

    while index < count:
        tag = data[pos]

        if tag == 1:
            length = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            text = data[pos + 3:pos + 3 + length].decode("utf-8", "replace")

            if text == "2.7" or text.startswith("2.7-20"):
                encoded = new.encode("utf-8")
                out += bytes([1]) + struct.pack(">H", len(encoded)) + encoded
                found += 1
            else:
                out += data[pos:pos + 3 + length]

            pos += 3 + length
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 18: 5}[tag]
            out += data[pos:pos + size]
            pos += size

            if tag in (5, 6):
                index += 1

        index += 1

    if found != 1:
        raise SystemExit("Versionseintrag nicht eindeutig gefunden (%d)" % found)

    return bytes(out + data[pos:])


def main():
    jar, version = sys.argv[1], sys.argv[2]
    tmp = jar + ".tmp"

    with zipfile.ZipFile(jar) as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)

            if item.filename == CLASS:
                data = patch_class(data, version)
            elif item.filename == "mcmod.info":
                info = json.loads(data.decode("utf-8"))
                info[0]["version"] = version + "-1.12.2"
                data = json.dumps(info, indent=4, ensure_ascii=False).encode("utf-8")

            dst.writestr(item, data)

    os.replace(tmp, jar)
    print("Version gesetzt:", version)


if __name__ == "__main__":
    main()
