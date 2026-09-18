"""Procedural 64px Minecraft atlas: charcoal fur, gold harness, amber eyes and ivory fangs."""
from pathlib import Path
import struct
import zlib

ROOT=Path(__file__).resolve().parents[1]
def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)

pixels=[]
for y in range(64):
    row=bytearray([0])
    for x in range(64):
        noise=((x*37+y*67+x*y*13)%9)-4
        c=(28+noise,31+noise,38+noise)
        if x>=40 and y<29:
            n=((x*5+y*3)%7)*3
            c=(130+n,101+n,57+n//2)
        if 55<=x and 29<=y<33:c=(246,179,48)
        if 55<=x and 34<=y<39:c=(222,214,187)
        row.extend((*c,255))
    pixels.append(bytes(row))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))
png+=chunk(b'IDAT',zlib.compress(b''.join(pixels),9))+chunk(b'IEND',b'')
target=ROOT/'src/main/resources/assets/myriad_calamity/textures/entity/celestial_hound.png'
target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(png)
print(target)
