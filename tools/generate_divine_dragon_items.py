"""Generate crisp 16x16 pixel-art item icons for Divine Dragon items:
  - everblossom_branch.png (entry talisman: ancient cherry branch with blooming pink sakura & pale jade aura)
  - divine_dragon_tears.png (boss drop: luminous golden-jade teardrop pearl with soft sakura halo)
"""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/myriad_calamity/textures/item'


def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw.extend((r, g, b, a))

    def chunk(tag, data):
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', crc)

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def paint_grid(rows, palette):
    return [[palette[ch] for ch in row] for row in rows]


def main():
    branch_pal = {
        '.': (0, 0, 0, 0),
        'g': (146, 224, 198, 95),   # soft jade spirit aura
        'b': (92, 66, 58, 255),     # dark cherry bark
        'w': (136, 102, 88, 255),   # warm cherry wood highlight
        'p': (236, 146, 178, 255),  # sakura petal mid
        'P': (255, 198, 218, 255),  # sakura petal bright
        'c': (255, 240, 216, 255),  # blossom stamen highlight
        's': (184, 96, 128, 255),   # deep sakura shadow
        'j': (138, 232, 198, 255),  # jade dew bead
    }
    branch_rows = [
        '..........gPpg..',
        '.........gPcpPg.',
        '.....gPp..pPsp..',
        '....gPcpP..bwg..',
        '.....pPsp.bwb...',
        '....g.bw.bwb.gPp',
        '.....bwb.bw.gPcp',
        '......bwbw...pPs',
        '.......bw..j.bw.',
        '......bwb..bwb..',
        '.....bwb..bwb...',
        '....bwb..bwg....',
        '...bwb..g.......',
        '..bwb...........',
        '.bwb............',
        '..b.............',
    ]
    write_png(OUT / 'everblossom_branch.png', 16, 16, paint_grid(branch_rows, branch_pal))

    tears_pal = {
        '.': (0, 0, 0, 0),
        'a': (176, 244, 222, 75),   # faint jade-moon halo
        'k': (255, 196, 220, 125),  # soft sakura shimmer
        'o': (86, 148, 134, 255),   # outer rim jade-teal
        'm': (138, 214, 192, 255),  # mid crystalline jade
        'l': (198, 248, 234, 255),  # bright pearl aqua
        'W': (255, 255, 250, 255),  # radiant core white
        'G': (255, 234, 156, 255),  # golden divine tear spark
        'g': (238, 194, 108, 255),  # warm gold depth
        's': (62, 104, 100, 255),   # bottom deep tear shadow
    }
    tears_rows = [
        '.......aa.......',
        '......aola......',
        '.....aommoa.....',
        '.....omllmo.....',
        '....aomWWmoa....',
        '....omWlWlmo....',
        '...komWlGlmok...',
        '...omlWGGGlmo...',
        '...omlWGGglmo...',
        '...ommlgglmmo...',
        '...sommllmmos...',
        '....sommmmos....',
        '.....soooos.....',
        '......kssk......',
        '.......aa.......',
        '................',
    ]
    write_png(OUT / 'divine_dragon_tears.png', 16, 16, paint_grid(tears_rows, tears_pal))
    print('Generated everblossom_branch.png and divine_dragon_tears.png')


if __name__ == '__main__':
    main()
