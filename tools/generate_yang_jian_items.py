"""Author the small pixel-art talisman, trial seal and mark icons used by P1."""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'


def png(path, size, paint):
    pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    def rect(x1, y1, x2, y2, color):
        for y in range(max(0, y1), min(size, y2 + 1)):
            for x in range(max(0, x1), min(size, x2 + 1)):
                pixels[y][x] = (*color, 255)

    paint(rect)
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


GOLD = (187, 152, 78)
LIGHT = (239, 216, 152)
INK = (39, 48, 62)
PAPER = (213, 228, 226)
RED = (150, 57, 55)


def talisman(r):
    r(8, 3, 23, 25, INK)
    r(9, 4, 22, 24, GOLD)
    r(11, 6, 20, 22, PAPER)
    r(8, 3, 23, 4, LIGHT)
    r(8, 23, 23, 25, GOLD)
    r(7, 2, 10, 5, GOLD)
    r(21, 2, 24, 5, GOLD)
    for x, y, w, h in [(15, 7, 2, 3), (13, 10, 6, 2), (12, 14, 8, 1), (15, 12, 2, 7), (13, 18, 2, 2), (18, 18, 2, 2)]:
        r(x, y, x + w - 1, y + h - 1, RED)
    r(14, 25, 17, 27, INK)
    r(15, 25, 16, 29, RED)
    r(12, 28, 14, 30, RED)
    r(17, 28, 19, 30, RED)
    r(11, 7, 11, 21, (170, 195, 200))


def seal(r):
    r(8, 4, 23, 27, INK)
    r(5, 8, 26, 23, INK)
    r(9, 5, 22, 26, GOLD)
    r(6, 9, 25, 22, GOLD)
    r(8, 10, 23, 21, (86, 106, 116))
    r(10, 8, 21, 23, (86, 106, 116))
    r(7, 9, 8, 20, LIGHT)
    r(10, 6, 21, 7, LIGHT)
    r(14, 10, 17, 21, LIGHT)
    r(12, 13, 19, 15, GOLD)
    r(11, 17, 12, 20, LIGHT)
    r(19, 17, 20, 20, LIGHT)
    r(15, 12, 16, 16, INK)
    r(15, 18, 16, 19, (220, 245, 245))


def mark(r):
    r(2, 2, 5, 6, GOLD)
    r(12, 2, 15, 6, GOLD)
    r(4, 5, 13, 12, GOLD)
    r(6, 12, 11, 15, GOLD)
    r(5, 6, 12, 11, INK)
    r(5, 7, 6, 8, LIGHT)
    r(11, 7, 12, 8, LIGHT)
    r(7, 11, 10, 12, LIGHT)
    r(8, 9, 9, 11, RED)


png(ASSETS / 'textures/item/cloud_talisman.png', 32, talisman)
png(ASSETS / 'textures/item/divine_sigil.png', 32, seal)
png(ASSETS / 'textures/mob_effect/roar_mark.png', 18, mark)
for name in ['cloud_talisman', 'divine_sigil']:
    path = ASSETS / f'models/item/{name}.json'
    path.write_text(json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'myriad_calamity:item/{name}'}}, indent=2) + '\n', encoding='utf8')
(ASSETS / 'models/item/yang_jian_spawn_egg.json').write_text('{"parent":"minecraft:item/template_spawn_egg"}\n', encoding='utf8')
recipe = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': [' F ', 'GPG', ' E '],
          'key': {'F': {'item': 'minecraft:feather'}, 'G': {'item': 'minecraft:gold_ingot'},
                  'P': {'item': 'minecraft:paper'}, 'E': {'item': 'minecraft:ender_pearl'}},
          'result': {'item': 'myriad_calamity:cloud_talisman', 'count': 1}}
(ROOT / 'src/main/resources/data/myriad_calamity/recipes/cloud_talisman.json').write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf8')
print('Generated Yang Jian item icons, mark icon, item models and talisman recipe.')
