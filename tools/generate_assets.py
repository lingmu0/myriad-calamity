"""Generate original pixel textures and JSON assets; standard library only."""
from pathlib import Path
import json, struct, zlib, math
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
NS='myriad_calamity'
def write_json(rel,obj):
    p=RES/rel; p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n','utf8')
def png(rel,w,h,pixels):
    def chunk(tag,data): return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
    raw=b''.join(b'\x00'+bytes(sum(pixels[y*w:(y+1)*w],[])) for y in range(h))
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
    p=RES/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(data)
def icon(kind):
    w=h=32; pixels=[[0,0,0,0] for _ in range(w*h)]
    def put(x,y,c):
        if 0<=x<w and 0<=y<h:pixels[y*w+x]=list(c)+[255]
    gold=(220,178,92); dark=(62,55,47); bright=(255,228,155); teal=(82,218,202)
    cx,cy=(15,10) if kind=='winding_key' else (16,16)
    for y in range(h):
        for x in range(w):
            dx=x-cx;dy=y-cy;r=math.hypot(dx,dy);angle=math.atan2(dy,dx)
            outer=8.5+(1.5 if math.cos(angle*8)>0.2 else 0)
            if 4.0<r<outer:put(x,y,bright if dx+dy<-3 else gold if dx+dy<8 else dark)
            if kind=='cogwork_heart' and r<4.5:put(x,y,teal if dx+dy<2 else (40,126,133))
    if kind=='winding_key':
        for y in range(18,29):
            for x in range(13,18): put(x,y,gold if x>13 else bright)
        for y in [22,23,27,28]:
            for x in range(18,23):put(x,y,gold)
    png(f'assets/{NS}/textures/item/{kind}.png',w,h,pixels)
for name in ['winding_key','cogwork_heart']:
    icon(name)
    write_json(f'assets/{NS}/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{NS}:item/{name}'}})
write_json(f'assets/{NS}/models/item/cogwork_dancer_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
zh={
 'entity.myriad_calamity.cogwork_blade':'机枢剑光',
 'itemGroup.myriad_calamity':'万象灾厄',
 'entity.myriad_calamity.cogwork_dancer':'机枢舞者',
 'item.myriad_calamity.winding_key':'机枢发条钥匙',
 'item.myriad_calamity.cogwork_heart':'机枢之心',
 'item.myriad_calamity.cogwork_dancer_spawn_egg':'机枢舞者刷怪蛋',
 'boss.myriad_calamity.phase1':'机枢舞者 · 第一乐章',
 'boss.myriad_calamity.phase2':'机枢舞者 · 加速轮舞',
 'boss.myriad_calamity.phase3':'机枢舞者 · 错拍终曲',
 'boss.myriad_calamity.phase4':'机枢舞者 · 孤独谢幕',
 'tooltip.myriad_calamity.winding_key':'右键地面，唤醒一对共享生命值的机枢舞者。',
 'tooltip.myriad_calamity.arena':'建议准备 29×29 的平坦场地，上方留空 8 格。钥匙可重复使用。',
 'message.myriad_calamity.peaceful':'和平难度无法唤醒机枢舞者。',
 'message.myriad_calamity.already_dancing':'附近已有机枢舞者在等待谢幕。',
 'message.myriad_calamity.no_room':'需要坚实地面、3 格净空，且相隔 4 格有第二个站位。',
 'message.myriad_calamity.summoned':'齿轮啮合，双人舞启幕。',
 'advancement.myriad_calamity.last_dance.title':'最后一支舞',
 'advancement.myriad_calamity.last_dance.description':'击败失去舞伴的机枢舞者，结束这场舞会。'
}
en={
 'entity.myriad_calamity.cogwork_blade':'Cogwork Blade',
 'itemGroup.myriad_calamity':'Myriad Calamity',
 'entity.myriad_calamity.cogwork_dancer':'Cogwork Dancer',
 'item.myriad_calamity.winding_key':'Cogwork Winding Key',
 'item.myriad_calamity.cogwork_heart':'Cogwork Heart',
 'item.myriad_calamity.cogwork_dancer_spawn_egg':'Cogwork Dancers Spawn Egg',
 'boss.myriad_calamity.phase1':'Cogwork Dancers · First Movement',
 'boss.myriad_calamity.phase2':'Cogwork Dancers · Accelerando',
 'boss.myriad_calamity.phase3':'Cogwork Dancers · Broken Cadence',
 'boss.myriad_calamity.phase4':'Cogwork Dancer · Lonely Curtain Call',
 'tooltip.myriad_calamity.winding_key':'Use on the ground to summon two dancers with one shared health pool.',
 'tooltip.myriad_calamity.arena':'Prepare a flat 29×29 arena with 8 blocks of headroom. Reusable.',
 'message.myriad_calamity.peaceful':'The dancers cannot awaken in Peaceful difficulty.',
 'message.myriad_calamity.already_dancing':'A nearby dance has not yet ended.',
 'message.myriad_calamity.no_room':'Requires solid ground, 3 blocks of clearance, and a second position 4 blocks away.',
 'message.myriad_calamity.summoned':'The gears engage. The duet begins.',
 'advancement.myriad_calamity.last_dance.title':'One Last Dance',
 'advancement.myriad_calamity.last_dance.description':'Defeat the surviving Cogwork Dancer and end the performance.'
}
for lang,values in [('zh_cn',zh),('en_us',en)]:write_json(f'assets/{NS}/lang/{lang}.json',values)
write_json(f'assets/{NS}/sounds.json',{f'music.cogwork_dancer_phase{phase}':{'sounds':[{'name':f'{NS}:music/cogwork_dancer_phase{phase}','stream':True}]} for phase in range(1,5)})
write_json(f'data/{NS}/recipe/winding_key.json',{
 'type':'minecraft:crafting_shaped','category':'equipment','pattern':[' G ','GAG',' RC'],
 'key':{'G':{'item':'minecraft:gold_ingot'},'A':{'item':'minecraft:amethyst_shard'},'R':{'item':'minecraft:redstone'},'C':{'item':'minecraft:copper_ingot'}},
 'result':{'id':f'{NS}:winding_key','count':1}})
solo_condition={'condition':'minecraft:entity_properties','entity':'this','predicate':{'nbt':'{Solo:1b}'}}
write_json(f'data/{NS}/loot_table/entities/cogwork_dancer.json',{
 'type':'minecraft:entity','pools':[
  {'rolls':1,'conditions':[solo_condition,{'condition':'minecraft:killed_by_player'}],
   'entries':[{'type':'minecraft:item','name':f'{NS}:cogwork_heart','functions':[{'function':'minecraft:set_count','count':2}]}]},
  {'rolls':1,'conditions':[solo_condition],
   'entries':[{'type':'minecraft:item','name':'minecraft:copper_ingot','functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':6,'max':12}}]}]}
 ]})
write_json(f'data/{NS}/advancement/last_dance.json',{
 'display':{'icon':{'id':f'{NS}:cogwork_heart'},'title':{'translate':'advancement.myriad_calamity.last_dance.title'},
 'description':{'translate':'advancement.myriad_calamity.last_dance.description'},'background':'minecraft:textures/block/deepslate_tiles.png','frame':'challenge','show_toast':True,'announce_to_chat':True},
 'criteria':{'solo_defeated':{'trigger':'minecraft:player_killed_entity','conditions':{'entity':{'type':f'{NS}:cogwork_dancer','nbt':'{Solo:1b}'}}}},
 'rewards':{'experience':150}})
import subprocess,sys
subprocess.run([sys.executable,str(ROOT/'tools/generate_theatre_assets.py')],check=True)
subprocess.run([sys.executable,str(ROOT/'tools/export_blockbench_assets.py')],check=True)
print('Generated item assets, theatre resources and imported the saved Blockbench model.')
