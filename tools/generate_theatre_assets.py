"""Resource definitions for the naturally generated Cogwork Theatre and its summoning altar."""
from pathlib import Path
import json,struct,zlib,math
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources';NS='myriad_calamity'
def write(rel,obj):
 p=RES/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n','utf8')
write(f'data/{NS}/worldgen/structure/cogwork_theatre.json',{
 'type':f'{NS}:cogwork_theatre','biomes':f'#{NS}:has_structure/cogwork_theatre','step':'surface_structures',
 'spawn_overrides':{'monster':{'bounding_box':'piece','spawns':[]}},'terrain_adaptation':'beard_thin'})
write(f'data/{NS}/worldgen/structure_set/cogwork_theatre.json',{
 'structures':[{'structure':f'{NS}:cogwork_theatre','weight':1}],
 'placement':{'type':'minecraft:random_spread','spacing':36,'separation':12,'salt':19470351}})
write(f'data/{NS}/tags/worldgen/biome/has_structure/cogwork_theatre.json',{
 'replace':False,'values':['minecraft:plains','minecraft:sunflower_plains','minecraft:forest','minecraft:birch_forest','minecraft:taiga','minecraft:savanna','minecraft:meadow']})
write(f'assets/{NS}/blockstates/dance_altar.json',{'variants':{'':{'model':f'{NS}:block/dance_altar'}}})
write(f'assets/{NS}/models/block/dance_altar.json',{'parent':'minecraft:block/cube','textures':{
 'particle':f'{NS}:block/dance_altar_side','down':'minecraft:block/deepslate_tiles','up':f'{NS}:block/dance_altar_top',
 'north':f'{NS}:block/dance_altar_side','south':f'{NS}:block/dance_altar_side','east':f'{NS}:block/dance_altar_side','west':f'{NS}:block/dance_altar_side'}})
def png(name,pixels):
 def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 raw=b''.join(b'\0'+bytes(sum(pixels[y*32:(y+1)*32],[])) for y in range(32))
 data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
 p=RES/f'assets/{NS}/textures/block/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
for top in [True,False]:
 pixels=[]
 for y in range(32):
  for x in range(32):
   c=(40,48,56);edge=min(x,y,31-x,31-y);noise=(x*13+y*7)%7-3
   if edge<2:c=(198,148,70)
   if top:
    if max(abs(x-15.5),abs(y-15.5)) in [9.5,10.5]:c=(215,169,88)
    if abs(x-15.5)<=1.5 and 11<=y<=22:c=(114,237,222)
    if 11<=x<=20 and 9<=y<=14:c=(114,237,222)
    if 14<=x<=17 and 10<=y<=12:c=(28,53,61)
    if (x in [5,6,25,26] and y in [5,6,25,26]):c=(225,213,175)
   else:
    if y in [5,6,24,25]:c=(200,150,76)
    if 10<=y<=20 and x%8 in [2,3]:c=(20,30,35)
    if 12<=y<=18 and x in [14,15,16,17]:c=(94,204,194)
   pixels.append([max(0,min(255,v+noise)) for v in c]+[255])
 png('dance_altar_top' if top else 'dance_altar_side',pixels)
for lang in ['zh_cn','en_us']:
 p=RES/f'assets/{NS}/lang/{lang}.json';d=json.loads(p.read_text('utf8'))
 if lang=='zh_cn':d.update({
  'block.myriad_calamity.dance_altar':'机枢舞台核心',
  'tooltip.myriad_calamity.winding_key':'右键机枢舞厅中央核心，消耗 1 把钥匙召唤双人 Boss。',
  'tooltip.myriad_calamity.arena':'寻找主世界的机枢舞厅。召唤失败不消耗钥匙。',
  'message.myriad_calamity.only_altar':'钥匙只能用于机枢舞厅中央的舞台核心。',
  'message.myriad_calamity.need_key':'手持机枢发条钥匙，右键核心开启舞会。',
  'message.myriad_calamity.incomplete_theatre':'舞厅的八枚磁石共鸣印记不完整，或所在区块尚未加载。',
  'message.myriad_calamity.no_room':'核心左右各 4 格的舞者站位需要坚实地面和 4 格净空。'})
 else:d.update({
  'block.myriad_calamity.dance_altar':'Cogwork Stage Core',
  'tooltip.myriad_calamity.winding_key':'Use on the Theatre stage core. Consumes one key to summon both dancers.',
  'tooltip.myriad_calamity.arena':'Find a Cogwork Theatre in the Overworld. Failed summons consume nothing.',
  'message.myriad_calamity.only_altar':'The key only works on the central core of a Cogwork Theatre.',
  'message.myriad_calamity.need_key':'Use a Cogwork Winding Key on the core to begin the performance.',
  'message.myriad_calamity.incomplete_theatre':'The eight lodestone resonance seals are incomplete or their chunks are unloaded.',
  'message.myriad_calamity.no_room':'Both positions, four blocks left and right, need solid ground and four blocks of clearance.'})
 write(f'assets/{NS}/lang/{lang}.json',d)
print('Theatre worldgen, altar resources and updated summoning text generated.')
