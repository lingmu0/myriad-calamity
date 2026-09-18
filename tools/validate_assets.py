"""Static asset/package checks. Does not start Minecraft."""
from pathlib import Path
import json,re,struct,zlib,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
NS='myriad_calamity'
checks=0
def check(ok,message):
    global checks
    checks+=1
    if not ok:raise AssertionError(message)
def read_png(p):
    data=p.read_bytes();check(data[:8]==b'\x89PNG\r\n\x1a\n',f'PNG signature: {p}')
    at=8;stream=b'';width=height=0
    while at<len(data):
        n=struct.unpack('>I',data[at:at+4])[0];tag=data[at+4:at+8];block=data[at+8:at+8+n]
        crc=struct.unpack('>I',data[at+8+n:at+12+n])[0]
        check(zlib.crc32(tag+block)&0xffffffff==crc,f'PNG checksum: {p}')
        if tag==b'IHDR':
            width,height,depth,kind,_,_,_=struct.unpack('>IIBBBBB',block)
            check(depth==8 and kind==6,f'RGBA PNG: {p}')
        if tag==b'IDAT':stream+=block
        at+=12+n
    raw=zlib.decompress(stream)
    check(len(raw)==height*(1+width*4),f'PNG scanline size: {p}')
    return width,height
jsons={str(p.relative_to(RES)).replace('\\','/'):json.loads(p.read_text('utf8')) for p in RES.rglob('*.json')}
langs=[jsons[f'assets/{NS}/lang/{lang}.json'] for lang in ['zh_cn','en_us']]
check(langs[0].keys()==langs[1].keys(),'Language keys must match')
for phase in range(1,5):check(f'boss.{NS}.phase{phase}' in langs[0],f'Phase {phase} label')
for name in ['winding_key','cogwork_heart','cogwork_dancer_spawn_egg','cloud_talisman','divine_sigil','yang_jian_spawn_egg']:
    model=jsons[f'assets/{NS}/models/item/{name}.json']
    check(f'item.{NS}.{name}' in langs[0],f'Item name: {name}')
    for texture in model.get('textures',{}).values():
        namespace,relative=texture.split(':')
        if namespace==NS:check((RES/f'assets/{NS}/textures/{relative}.png').exists(),f'Model texture: {texture}')
for png in RES.rglob('*.png'):
    dimensions=read_png(png)
    expected = {'yang_jian.png': (2048,2048), 'celestial_hound.png': (64,64), 'roar_mark.png': (18,18)}
    check(dimensions==expected.get(png.name, (256,256) if png.parent.name=='entity' else (32,32)),f'Texture dimensions: {png}')
recipe=jsons[f'data/{NS}/recipe/winding_key.json']
check(recipe['result']['item']==f'{NS}:winding_key','1.20.1 recipe item id')
check(set(''.join(recipe['pattern']))-{' '}==recipe['key'].keys(),'Recipe key coverage')
loot=jsons[f'data/{NS}/loot_table/entities/cogwork_dancer.json']
check(all(any(c.get('predicate',{}).get('nbt')=='{Solo:1b}' for c in pool['conditions']) for pool in loot['pools']),'Only the final survivor drops loot')
adv=jsons[f'data/{NS}/advancement/last_dance.json']
check(adv['criteria']['solo_defeated']['conditions']['entity']['nbt']=='{Solo:1b}','Advancement requires solo survivor')
for old in ['recipes','loot_tables','advancements']:check(not (RES/f'data/{NS}/{old}').exists(),f'No legacy folder {old}')
for p in (ROOT/'src/main/java').rglob('*.java'):
    s=p.read_text('utf8')
    if '/client/' not in p.as_posix():check('net.minecraft.client' not in s,f'No client import in server class {p.name}')
    for key in re.findall(r'Component.translatable\("([^"+]+)"\)',s):
        if not key.endswith('phase'):check(key in langs[0],f'Translation: {key}')
structure=jsons[f'data/{NS}/worldgen/structure/cogwork_theatre.json']
check(structure['type']==f'{NS}:cogwork_theatre','Registered structure type')
placement=jsons[f'data/{NS}/worldgen/structure_set/cogwork_theatre.json']['placement']
check(placement['spacing']>placement['separation']>0,'Valid random-spread placement')
check((RES/f'assets/{NS}/blockstates/dance_altar.json').exists(),'Altar blockstate packaged')
default_config=RES/'defaultconfigs/myriad_calamity-common.toml'
check(default_config.exists(),'Default common config packaged')
config_text=default_config.read_text('utf8')
check('[cogworkDancer]' in config_text and 'health = 480.0' in config_text and 'damage = 9.0' in config_text,
    'Default Cogwork Dancer config values live in their own section')
check('phase2Guard = 220.0' in config_text,'P2 defense configuration packaged')
check('phase3Guard = 260.0' in config_text,'P3 defense configuration packaged')
check('phase3HealthFloor = 0.4' in config_text,'P3 entry health configuration packaged')
check('showAttackIndicators = false' in config_text,'Attack-range indicator switch packaged (default off)')
for key in ['yang_jian_p1','yang_jian_p2','yang_jian_transition','yang_jian_p2_clear',
            'yang_jian_p3','yang_jian_eye_open','yang_jian_judgement','yang_jian_defeated']:
    check(f'boss.{NS}.{key}' in langs[0],f'Yang Jian stage label: {key}')
sound_def=jsons[f'assets/{NS}/sounds.json']
for phase in range(1,5):
    key=f'music.cogwork_dancer_phase{phase}'
    check(key in sound_def and len(sound_def[key]['sounds'])==1,'Phase music sound definition: '+key)
    sound=sound_def[key]['sounds'][0]
    check(sound['name']==f'{NS}:music/cogwork_dancer_phase{phase}' and sound.get('stream') is True,'Streamed phase music: '+key)
    ogg=RES/f'assets/{NS}/sounds/music/cogwork_dancer_phase{phase}.ogg'
    check(ogg.exists() and ogg.stat().st_size>1024 and ogg.read_bytes()[:4]==b'OggS','OGG phase music: '+key)
for key,name in [('music.yang_jian_bgm_intro','yang_jian_bgm_intro'),('music.yang_jian_bgm_main','yang_jian_bgm_main')]:
    check(key in sound_def and len(sound_def[key]['sounds'])==1,'Yang Jian music sound definition: '+key)
    sound=sound_def[key]['sounds'][0]
    check(sound['name']==f'{NS}:music/{name}' and sound.get('stream') is True,'Streamed Yang Jian music: '+key)
    ogg=RES/f'assets/{NS}/sounds/music/{name}.ogg'
    check(ogg.exists() and ogg.stat().st_size>1024 and ogg.read_bytes()[:4]==b'OggS','OGG Yang Jian music: '+key)
for ref in jsons[f'assets/{NS}/models/block/dance_altar.json']['textures'].values():
 if ref.startswith(NS+':'):check((RES/f'assets/{NS}/textures/{ref.split(":",1)[1]}.png').exists(),'Altar texture exists')
mesh=jsons[f'assets/{NS}/mesh/cogwork_dancer.json']
clips=jsons[f'assets/{NS}/animations/cogwork_dancer.json']['clips']
names=set();face_count=0
import math
def inspect_bone(bone):
 global face_count
 check(bone['name'] not in names,'Unique bone name');names.add(bone['name'])
 for face in bone['faces']:
  check(len(face['v'])==4 and len(face['n'])==3,'Valid render quad')
  check(all(math.isfinite(v) for point in face['v'] for v in point),'Finite geometry and UVs')
  check(all(0<=point[3]<=1 and 0<=point[4]<=1 for point in face['v']),'UV within atlas')
  check(abs(sum(v*v for v in face['n'])-1)<1e-4,'Normalized face normal')
  face_count+=1
 for child in bone['children']:inspect_bone(child)
for bone in mesh['bones']:inspect_bone(bone)
required={'idle','solo_idle','dash_windup','dash','slam_windup','slam','duet_windup','duet','rewind','failed_duet','death','barrage_windup','barrage_dash','barrage_recover'}
check(required==clips.keys(),'All gameplay animations exported')
check({'chest','abdomen','pelvis','neck','crown','left_elbow','right_wrist','left_knee','right_ankle','left_tail_2'}.issubset(names),'Articulated rig joints exported')
for clip,duration in {'dash':1.8,'slam':2.1,'duet':3.9,'barrage_dash':1,'barrage_recover':1.2}.items():check(abs(clips[clip]['length']-duration)<1e-5,'Attack and clip duration agree: '+clip)
for name,clip in clips.items():
 check(clip['length']>0,'Positive animation length')
 for bone,channels in clip['bones'].items():
  check(bone in names,'Animation targets existing bone')
  for keys in channels.values():
   check(max(keys[i+1][0]-keys[i][0] for i in range(len(keys)-1))<1/60+1e-6,'60 Hz spline sampling')
   check(all(keys[i][0]<=keys[i+1][0] for i in range(len(keys)-1)),'Ordered keyframe times')
   check(all(0<=key[0]<=clip['length'] and len(key)==4 and all(math.isfinite(v) for v in key) for key in keys),'Valid keyframes')
source=ROOT/'modeling/cogwork_dancer.bbmodel'
manifest=json.loads((ROOT/'modeling/mcp-export-manifest.json').read_text('utf8'))
check(manifest['source_sha256']==hashlib.sha256(source.read_bytes()).hexdigest(),'Assets match the actual Blockbench export')
check(manifest['cubes']>=200 and manifest['bones']>=35 and manifest['rendered_faces']==face_count,'Cuboid detail and geometry count retained')
mod_bus_events={'EntityRenderersEvent','RegisterDimensionSpecialEffectsEvent','FMLClientSetupEvent','FMLCommonSetupEvent',
                'RegisterKeyMappingsEvent','RegisterParticleProvidersEvent','ModelEvent','RegisterClientReloadListenersEvent',
                'RegisterClientExtensionsEvent','RegisterNamedRenderTypesEvent','RegisterTextureAtlasSpriteLoadersEvent'}
forge_bus_events={'TickEvent','RenderLevelStageEvent','ViewportEvent','PlayerInteractEvent','BlockEvent','LivingDeathEvent',
                  'ProjectileImpactEvent','EntityJoinLevelEvent','EntityTravelToDimensionEvent','ExplosionEvent','PlayerEvent',
                  'ServerTickEvent','ClientTickEvent','ServerStartingEvent','ServerStoppedEvent','RecipesUpdatedEvent'}
subscriber=re.compile(r'@Mod\.EventBusSubscriber\((?P<body>[^)]*)\)')
handler=re.compile(r'@SubscribeEvent\s+public\s+static\s+\w+\s+\w+\(\s*(?:final\s+)?([A-Za-z0-9_.]+)\s+\w+\s*\)')
for path in (ROOT/'src/main/java').rglob('*.java'):
    source=path.read_text('utf8')
    annotation=subscriber.search(source)
    if not annotation:continue
    on_mod_bus='Bus.MOD' in annotation.group('body')
    for event in handler.findall(source):
        root=event.split('.')[0]
        if root in mod_bus_events:
            check(on_mod_bus,f'{path.name} registers the mod-bus event {event} on the Forge bus')
        elif root in forge_bus_events:
            check(not on_mod_bus,f'{path.name} handles the Forge event {event} on the mod bus')
    if on_mod_bus:check('value=Dist.CLIENT' in annotation.group('body') or 'value = Dist.CLIENT' in annotation.group('body'),
        f'{path.name} restricts its mod-bus client registrations to the client distribution')
registered=set(re.findall(r'ENTITIES\.register\("([a-z_]+)"',(ROOT/'src/main/java/net/xuwu/myriadcalamity/MyriadCalamity.java').read_text('utf8')))
rendered=set(re.findall(r'registerEntityRenderer\(MyriadCalamity\.([A-Z_]+)\.get\(\)',
    (ROOT/'src/main/java/net/xuwu/myriadcalamity/client/ClientModBus.java').read_text('utf8')))
fields=dict(re.findall(r'RegistryObject<EntityType<[^>]+>>\s*([A-Z_]+)\s*=\s*ENTITIES\.register\("([a-z_]+)"',
    (ROOT/'src/main/java/net/xuwu/myriadcalamity/MyriadCalamity.java').read_text('utf8')))
check(registered==set(fields.values()),'Every entity type is declared once')
check({fields[name] for name in rendered}==registered,f'Every entity type has a client renderer: {sorted(registered-{fields[n] for n in rendered})}')
version=re.search(r'^mod_version=(.+)$',(ROOT/'gradle.properties').read_text('utf8'),re.M).group(1).strip()
jars=list((ROOT/'build/libs').glob(f'{NS}-{version}.jar'))
check(len(jars)==1,'Exactly one distributable JAR')
with zipfile.ZipFile(jars[0]) as jar:
    names=set(jar.namelist())
    for p in RES.rglob('*'):
        if p.is_file():check(p.relative_to(RES).as_posix() in names and jar.read(p.relative_to(RES).as_posix())==p.read_bytes(),f'Current packaged resource {p.name}')
    for name in ['MyriadCalamity','entity/CogworkDancer','entity/CombatMath','client/ClientEvents','client/CogworkDancerModel','client/CogworkDancerRenderer','item/WindingKeyItem','client/AnimationTrack','client/AnimationPlayback','client/CogworkTelegraph','entity/CogworkBlade','client/CogworkBladeRenderer','block/DanceAltarBlock','world/ModStructures','world/CogworkTheatreStructure','world/CogworkTheatrePiece','world/DanceEncounters','world/TheatreLayout','config/MyriadConfig']:
        entry=f'net/xuwu/myriadcalamity/{name}.class'
        check(entry in names,f'Packaged class {name}')
        check(struct.unpack('>H',jar.read(entry)[6:8])[0]==61,f'Java 17 class {name}')
    for name in ['entity/YangJian','entity/YangJianSkill','entity/YangJianTransition','entity/YangJianDefense','entity/YangJianDefenseEvents','entity/CelestialHound','entity/TriPointedBlade',
                 'entity/DivineFlyingSword','entity/LightningTrail','entity/YangJianEffects',
                 'entity/YangJianHazard','entity/YangJianHazardMath','entity/YangJianPhaseThree','entity/YangJianFootwork','client/YangJianHazardRenderer',
                 'client/DivineFlyingSwordRenderer','client/LightningTrailRenderer','client/YangJianWeapons','client/YangJianCombatEffects','client/YangJianTransitionEffects',
                 'effect/RoarMarkEffect','world/CloudArena','world/CloudArenaData','item/CloudTalismanItem',
                 'client/YangJianModel','client/YangJianRenderer','client/YangJianTelegraph',
                 'client/CelestialHoundRenderer','client/CelestialHoundModel','client/TriPointedBladeRenderer','client/CloudRealmEffects']:
        entry=f'net/xuwu/myriadcalamity/{name}.class'
        check(entry in names,f'Packaged Yang Jian class {name}')
        check(struct.unpack('>H',jar.read(entry)[6:8])[0]==61,f'Java 17 Yang Jian class {name}')
    metadata=jar.read('META-INF/mods.toml').decode('utf8')
    check('${' not in metadata,'Expanded metadata')
    check(re.search(r'\bversion\s*=\s*"'+re.escape(version)+r'"',metadata),'JAR metadata matches release version')
    check('万象灾厄' in metadata,'Chinese display name retained in UTF-8')
    check(not any('demo1_21_1' in n for n in names) and 'demo.mixins.json' not in names,'No template remnants in JAR')
print(f'Assets/package: {checks} checks passed; {len(jsons)} JSON resources.')
print('JAR SHA256:',hashlib.sha256(jars[0].read_bytes()).hexdigest())
