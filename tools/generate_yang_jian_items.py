"""Author the small pixel-art talisman, trial seal and mark icons used by P1."""
import json
import struct
import zlib
from pathlib import Path
from generate_item_sprites import write_sprite

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
RED = (150, 57, 55)


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


write_sprite('cloud_talisman', ASSETS)
write_sprite('divine_sigil', ASSETS)
png(ASSETS / 'textures/mob_effect/roar_mark.png', 18, mark)
for name in ['cloud_talisman', 'divine_sigil']:
    path = ASSETS / f'models/item/{name}.json'
    path.write_text(json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'myriad_calamity:item/{name}'}}, indent=2) + '\n', encoding='utf8')
(ASSETS / 'models/item/yang_jian_spawn_egg.json').write_text('{"parent":"minecraft:item/template_spawn_egg"}\n', encoding='utf8')
recipe = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': [' F ', 'GPG', ' E '],
          'key': {'F': {'item': 'minecraft:feather'}, 'G': {'item': 'minecraft:gold_ingot'},
                  'P': {'item': 'minecraft:paper'}, 'E': {'item': 'minecraft:ender_pearl'}},
          'result': {'id': 'myriad_calamity:cloud_talisman', 'count': 1}}
(ROOT / 'src/main/resources/data/myriad_calamity/recipe/cloud_talisman.json').write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf8')
print('Generated Yang Jian item icons, mark icon, item models and talisman recipe.')
