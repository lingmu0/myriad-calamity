"""Paint the mod's item sprites at Minecraft's native 16-pixel resolution."""
import struct
import zlib
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/myriad_calamity'

KEY = (
    '................',
    '..........ooo...',
    '.........ohhgo..',
    '........ohgGggo.',
    '........og..Ggo.',
    '........og..sgo.',
    '.......ohgGgso..',
    '......ohggsso...',
    '.....ohgsoo.....',
    '....ohgso.......',
    '...ohgso........',
    '..ohgsohgo......',
    '..ogggggso......',
    '...oggso........',
    '....ooo.........',
    '................',
)
HEART = (
    '................',
    '.......oo.......',
    '....oooHgoo.....',
    '...ohgHhgggo....',
    '..ohgoscgosso...',
    '..ohgoJjjohso...',
    '.ohhscJcjosgso..',
    '.ohgscCjJcsgso..',
    '..oghoJjjohso...',
    '..oghocjcohso...',
    '...oghsosgso....',
    '...ooghggsoo....',
    '......ogso......',
    '.......oo.......',
    '................',
    '................',
)
TALISMAN = (
    '................',
    '...oooooooooo...',
    '..oHHHhhhgggso..',
    '...oppppppsso...',
    '...opprpppsso...',
    '...oprrrppsso...',
    '...opprpppsso...',
    '...orrrrppsso...',
    '...opprpppsso...',
    '...oprprppsso...',
    '...oppppppsso...',
    '...ohggggggso...',
    '....oooroo......',
    '......rrr.......',
    '.....rr.rr......',
    '................',
)
SIGIL = (
    '................',
    '................',
    '.....oooooo.....',
    '....oHHhhgso....',
    '...ohgsssGgso...',
    '..ohgsspssGgso..',
    '..ohsspppssgso..',
    '..ohspspspsgso..',
    '..ogspcpcpsgso..',
    '..ogscpcpcsgso..',
    '...ogsscsgsso...',
    '....ogggggso....',
    '.....oooooo.....',
    '................',
    '................',
    '................',
)
EGG = (
    '................',
    '.........ooo....',
    '........oHHgo...',
    '.......oHjjcgo..',
    '......oHjGcjsgo.',
    '.....oHjjpGjsgo.',
    '....oHjcpGcjsgo.',
    '...oHjGjccjssgo.',
    '...oHjpGjcjsgso.',
    '..oHjcjGjjsgso..',
    '..oHjjjjsgsso...',
    '..ogjsjsggso....',
    '...ogsggsoo.....',
    '....ogsoo.......',
    '.....oo.........',
    '................',
)

METAL = {
    'o': '362821', 's': '765035', 'g': 'bf8849', 'h': 'e2b667',
    'H': 'ffdc91', 'G': 'd59c4f',
}
COG = {**METAL, 'o': '302725', 'c': '347b83', 'j': '41b5a5', 'J': '79e4cf', 'C': 'c2ffe7'}
PAPER = {**METAL, 'o': '442d29', 's': 'a9885c', 'p': 'f6e8bf', 'r': '982d35'}
SEAL = {**METAL, 'o': '303744', 's': '465c6f', 'p': 'c9eaff', 'c': '75a5ba'}
EGG_DANCER = {'o': '1a2430', 'g': '61737a', 'H': '9eb5bd', 'j': '4a606d',
              'c': '344655', 's': '2a3445', 'p': 'ffdf8b', 'G': 'd8a958'}
EGG_YANG_JIAN = {'o': '20202a', 'g': '6e6c7b', 'H': 'a4a8b7', 'j': '42465c',
                 'c': '303746', 's': '2c2533', 'p': 'f2d6aa', 'G': 'b79864'}
EGG_DRAGON = {'o': '263e3d', 'g': 'a8c6be', 'H': 'e6ecea', 'j': '8bab9e',
              'c': '6d8e82', 's': '557367', 'p': 'a4e7ce', 'G': '72c6a2'}
SPRITES = {
    'divine_dragon_spawn_egg': (EGG, EGG_DRAGON),
    'winding_key': (KEY, METAL), 'cogwork_heart': (HEART, COG),
    'cloud_talisman': (TALISMAN, PAPER), 'divine_sigil': (SIGIL, SEAL),
    'cogwork_dancer_spawn_egg': (EGG, EGG_DANCER), 'yang_jian_spawn_egg': (EGG, EGG_YANG_JIAN),
}


def write_sprite(name, assets=ASSETS):
    rows, colors = SPRITES[name]
    if len(rows) != 16 or any(len(row) != 16 for row in rows):
        raise ValueError(f'{name} must have exactly 16 rows of 16 pixels')
    palette = {key: bytes.fromhex(rgb) + b'\xff' for key, rgb in colors.items()}
    palette['.'] = b'\0' * 4
    pixels = b''.join(b'\0' + b''.join(palette[key] for key in row) for row in rows)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(pixels, 9)) + chunk(b'IEND', b'')
    (assets / 'textures/item' / f'{name}.png').write_bytes(png)


if __name__ == '__main__':
    for sprite in SPRITES:
        # Older versions use Minecraft's built-in, two-color egg sprites.
        if sprite.endswith('_spawn_egg') and not (ASSETS / 'items' / f'{sprite}.json').is_file():
            continue
        write_sprite(sprite)
        print(f'{sprite}: 16 x 16')
