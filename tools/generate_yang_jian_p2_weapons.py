"""Layered cuboid P2 weapons using the approved Yang Jian material atlas.

Meshes are local to the existing hand's weapon pivot, with blades along +Y in
Blockbench / -Y in the runtime. This generator never changes the approved body.
"""
from pathlib import Path
import copy
import json
import math
import uuid
from export_yang_jian_assets import cube_faces, convert, subtract

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/myriad_calamity'
BASE=json.loads((ROOT/'modeling/yang_jian_v3.bbmodel').read_text('utf8'))
BY_NAME={e['name']:e for e in BASE['elements']}
MATERIALS={
    'dark':'yj3_weapon_grip_inner',
    'iron':'yj3_weapon_lance_main',
    'silver':'yj3_weapon_lance_straight_edge_-1',
    'gold':'yj3_weapon_grip_low_base',
    'pale_gold':'yj3_weapon_grip_low_upper',
    'black':'yj3_weapon_lance_groove_-1_-1',
    'energy':'yj3_weapon_lance_straight_edge_1',
}
PARTS={'axe':[],'sword':[],'whip':[]}


def box(weapon,name,center,size,material='iron',rotation=(0,0,0)):
    assert all(s>0 for s in size)
    source=BY_NAME[MATERIALS[material]]
    e={'type':'cube','name':'p2_'+weapon+'_'+name,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'myriad:p2:'+weapon+':'+name)),
       'from':[center[i]-size[i]/2 for i in range(3)],'to':[center[i]+size[i]/2 for i in range(3)],
       'origin':list(center),'rotation':list(rotation),'faces':copy.deepcopy(source['faces']),
       'box_uv':False,'visibility':True,'p2_material':material}
    PARTS[weapon].append(e)


def rod(weapon,name,start,end,width,depth,material='gold'):
    dx,dy=end[0]-start[0],end[1]-start[1]
    length=math.hypot(dx,dy)
    box(weapon,name,[(start[0]+end[0])/2,(start[1]+end[1])/2,start[2]],
        [width,length,depth],material,[0,0,-math.degrees(math.atan2(dx,dy))])


def rings(weapon,levels,radius=1.4):
    for i,y in enumerate(levels):
        box(weapon,f'ring_{i}',[0,y,0],[radius*2,.75,radius*2],'gold')
        box(weapon,f'ring_lip_{i}',[0,y+.37,0],[radius*2+.25,.18,radius*2+.25],'pale_gold')


def build():
    # Long iron haft with sectional leather wraps and square bronze ferrules.
    box('axe','haft',[0,9,0],[1.85,57,1.85],'dark')
    for i,y in enumerate(range(-16,31,3)):
        box('axe',f'haft_wrap_{i}',[0,y,0],[2.05,.7,2.05],'black')
        for s in (-1,1):box('axe',f'haft_stitch_{i}_{s}',[s*1.06,y,.05],[.22,.58,1.5],'gold')
    rings('axe',[-19,-17,-7,5,19,24,36],1.4)
    box('axe','pommel',[0,-20,0],[3.3,2.5,3.3],'iron')
    for z in (-1,1):
        box('axe',f'pommel_glyph_{z}',[0,-20,z*1.7],[1.3,1.7,.3],'gold')
    box('axe','head_core',[0,33,0],[5.3,17,5.5],'iron')
    box('axe','head_band',[0,33,0],[6.2,6,6.0],'gold')
    # Broad twin blades are built from stepped steel columns, with a sharp silver
    # rim, dark inset and a raised face on both sides; no thin floating planes.
    for side in (-1,1):
        for i in range(8):
            x=side*(3.3+i*1.65)
            height=9.5+math.sin(i/7*math.pi)*6.5
            y=33.3+(i-3)*.65
            box('axe',f'blade_{side}_{i}',[x,y,0],[1.85,height,3.2],'iron')
            box('axe',f'cutting_edge_{side}_{i}',[x,y+height/2-.37,0],[1.9,.9,3.45],'silver',[0,0,side*8])
            box('axe',f'lower_edge_{side}_{i}',[x,y-height/2+.32,0],[1.8,.72,3.35],'silver',[0,0,-side*8])
            for face in (-1,1):
                box('axe',f'blade_inset_{side}_{i}_{face}',[x,y,face*1.72],[1.55,height-2.7,.38],'black')
                box('axe',f'blade_laminate_{side}_{i}_{face}',[x,y+.3,face*1.99],[1.34,height-4.25,.26],'iron')
                box('axe',f'blade_glyph_{side}_{i}_{face}',[x,y+math.sin(i*1.7),face*2.2],[.34,1.1,.2],'pale_gold')
                if i%2==0:box('axe',f'blade_rivet_{side}_{i}_{face}',[x,y-height/2+2,face*2.12],[.65,.65,.3],'gold')
        # Outer hooks give the huge head an angular, recognizable battleaxe silhouette.
        rod('axe',f'upper_hook_{side}',[side*14.1,38.2,0],[side*16,44.5,0],1.9,2.5,'silver')
        rod('axe',f'lower_hook_{side}',[side*14.1,32,0],[side*15.8,27.4,0],1.7,2.45,'silver')
        for face in (-1,1):
            rod('axe',f'ray_{side}_{face}',[side*2.4,33,face*3.02],[side*10.8,38,face*2.35],.75,.34,'gold')
            box('axe',f'core_frame_{side}_{face}',[side*1.6,33,face*3.2],[.7,4.5,.4],'pale_gold')
    for face in (-1,1):
        box('axe',f'core_socket_{face}',[0,33,face*3.4],[2.5,4,.5],'black')
        box('axe',f'core_light_{face}',[0,33,face*3.7],[1.25,2.4,.35],'energy',[0,0,45])
        box('axe',f'core_bezel_top_{face}',[0,35.4,face*3.3],[2.8,.6,.7],'gold')
        box('axe',f'core_bezel_bottom_{face}',[0,30.6,face*3.3],[2.8,.6,.7],'gold')
    rod('axe','crest_spike',[0,39,0],[0,47,0],1.0,1.8,'silver')

    # Straight jian: strong central spine, bilateral edges and four-pronged guard.
    box('sword','grip',[0,-.7,0],[1.6,10,1.8],'dark')
    for i in range(9):box('sword',f'wrap_{i}',[0,-4.4+i,0],[1.8,.38,2],'black')
    rings('sword',[-6.2,-5.6,3.5,5.0],1.1)
    box('sword','pommel',[0,-7.2,0],[2.8,2.1,2.8],'gold',[0,0,45])
    box('sword','pommel_inset',[0,-7.2,-1.5],[1.4,1.4,.3],'black',[0,0,45])
    box('sword','guard',[0,5.2,0],[10,1.5,2.7],'gold')
    for s in (-1,1):
        rod('sword',f'guard_wing_{s}',[s*3.6,5.4,0],[s*6.4,8,0],1.35,2.5,'silver')
        rod('sword',f'guard_lower_{s}',[s*2.7,5,0],[s*4.2,3.5,0],.8,1.9,'pale_gold')
        box('sword',f'guard_rivet_{s}',[s*3.7,5.2,-1.5],[.72,.72,.45],'pale_gold',[0,0,45])
    for i in range(16):
        y=7+i*1.8
        width=3.25 if i<10 else 3.25-(i-9)*.25
        box('sword',f'blade_{i}',[0,y,0],[width,1.95,1.05],'iron')
        box('sword',f'spine_{i}',[0,y,-.62],[.72,1.95,.38],'silver')
        box('sword',f'back_spine_{i}',[0,y,.62],[.72,1.95,.38],'silver')
        for s in (-1,1):
            box('sword',f'edge_{i}_{s}',[s*(width/2-.18),y,0],[.4,1.96,.95],'silver')
            if i<6:box('sword',f'fuller_{i}_{s}',[s*.8,y,-.60],[.21,1.32,.24],'gold')
    for i in range(10):
        width=1.7*(1-i/10)
        box('sword',f'tip_{i}',[0,35+i*.48,0],[width,.56,.8*(1-i/14)],'silver')
    for face in (-1,1):
        box('sword',f'guard_jewel_{face}',[0,5.5,face*1.63],[1.8,2.2,.6],'black')
        box('sword',f'guard_energy_{face}',[0,5.5,face*2],[.8,1.1,.32],'energy')

    # A substantial hand grip and conducting head. The chain is animated as
    # world-space segments to agree exactly with the server's swept arc.
    box('whip','grip',[0,0,0],[1.9,11,1.9],'dark')
    for i in range(10):box('whip',f'grip_wrap_{i}',[0,-4.5+i,0],[2.05,.35,2.05],'black')
    rings('whip',[-6,-4.9,4.8,6.5,9.1],1.4)
    box('whip','conductor',[0,7.5,0],[3.0,3.6,3],'iron')
    for s in (-1,1):
        box('whip',f'energy_inlay_x_{s}',[s*1.56,7.5,0],[.25,2.2,1],'energy')
        box('whip',f'energy_inlay_z_{s}',[0,7.5,s*1.56],[1,2.2,.25],'energy')
        rod('whip',f'crown_prong_{s}',[s*1.1,8,0],[s*1.8,11.3,0],.65,.85,'gold')
    box('whip','chain_socket',[0,10,0],[1.5,1.6,1.5],'silver')


def runtime():
    result={'format':1,'texture':'textures/entity/yang_jian.png','texture_size':[2048,2048],'weapons':{}}
    for name,elements in PARTS.items():
        faces=[]
        for e in elements:
            for points in cube_faces(e):
                vertices=[convert(p)+[round(uv[0]/2048,8),round(uv[1]/2048,8)] for p,uv in reversed(points)]
                a=subtract(vertices[1],vertices[0]);b=subtract(vertices[2],vertices[0])
                n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
                length=math.sqrt(sum(v*v for v in n));assert length>1e-8
                faces.append({'v':vertices,'n':[round(v/length,6) for v in n],'glow':e['p2_material']=='energy'})
        result['weapons'][name]={'faces':faces,'cubes':len(elements)}
    (ASSETS/'mesh/yang_jian_weapons.json').write_text(json.dumps(result,separators=(',',':')),'utf8')
    (ROOT/'modeling/yang_jian_p2_weapon_parts.json').write_text(json.dumps(PARTS,separators=(',',':')),'utf8')
    print('P2 weapons:',{name:len(parts) for name,parts in PARTS.items()})


if __name__=='__main__':build();runtime()
