"""Separate editable P2 project, with three new weapons and animated weapon visibility."""
from pathlib import Path
import copy
import json
import uuid
import sys

ROOT=Path(__file__).resolve().parents[1]
model=json.loads((ROOT/'modeling/yang_jian_v3.bbmodel').read_text('utf8'))
source=json.loads((ROOT/'modeling/yang_jian-animation-source.json').read_text('utf8'))['clips']
weapons=json.loads((ROOT/'modeling/yang_jian_p2_weapon_parts.json').read_text('utf8'))
groups={g['name']:g for g in model['groups']}
grip=groups['weapon']['origin']
def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'myriad:yang_jian:p2:'+name))
def node(nodes,target):
    for n in nodes:
        if isinstance(n,dict):
            if n['uuid']==target:return n
            found=node(n['children'],target)
            if found:return found
    return None
weapon_node=node(model['outliner'],groups['weapon']['uuid'])
old_children=weapon_node['children']
weapon_node['children']=[]
for weapon in ('spear','axe','sword','whip'):
    name='weapon_'+weapon
    g={'name':name,'origin':grip[:],'rotation':[0,0,0],'uuid':uid(name),'export':True,'visibility':True,'children':[]}
    model['groups'].append(g);groups[name]=g
    children=old_children if weapon=='spear' else []
    if weapon!='spear':
        for e in weapons[weapon]:
            e=copy.deepcopy(e)
            for vector in ('from','to','origin'):e[vector]=[e[vector][i]+grip[i] for i in range(3)]
            model['elements'].append(e);children.append(e['uuid'])
    weapon_node['children'].append({'uuid':g['uuid'],'isOpen':False,'children':children})

def weapon_for(name):
    if name.startswith('step_approach_'):return name.removeprefix('step_approach_')
    if name.startswith('axe_') or name=='p2_idle_axe':return 'axe'
    if name in ('flying_swords','draw_slash','invisible_dash','delayed_combo','p2_idle_sword'):return 'sword'
    if name in ('whip_sweep','whip_spin','p2_idle_whip'):return 'whip'
    return 'spear'

animations=[]
for name,clip in source.items():
    animators={}
    for bone,channels in clip['bones'].items():
        frames=[]
        for channel,keys in channels.items():
            for key in keys:
                x,y,z=key['values']
                if channel=='rotation':x,z=-x,-z
                elif channel=='position':y=-y
                frames.append({'channel':channel,'data_points':[{'x':str(x),'y':str(y),'z':str(z)}],
                    'uuid':uid(f'{name}:{bone}:{channel}:{key["time"]}'),'time':key['time'],
                    'color':-1,'interpolation':key['mode']})
        animators[groups[bone]['uuid']]={'name':bone,'type':'bone','rotation_global':False,
            'quaternion_interpolation':False,'keyframes':frames}
    for weapon in ('spear','axe','sword','whip'):
        bone='weapon_'+weapon
        visible=weapon==weapon_for(name)
        values=[(0,int(visible))]
        if name=='axe_summon':values=[(0,weapon=='spear'),(1.2,weapon=='axe')]
        if name=='throw' and weapon=='spear':values=[(0,True),(.8,False)]
        interpolation='step'
        if name=='transition':
            if weapon=='spear':values=[(0,1),(1.2,0)]
            elif weapon=='axe':
                values=[(0,0),(1.2,0),(2.0,3),(6.3,3),(7.2,1)]
                interpolation='linear'
            else:values=[(0,0)]
        frames=[{'channel':'scale','data_points':[{'x':str(float(scale)),'y':str(float(scale)),'z':str(float(scale))}],
            'uuid':uid(f'{name}:{bone}:visibility:{t}'),'time':t,'color':-1,'interpolation':interpolation} for t,scale in values]
        animators[groups[bone]['uuid']]={'name':bone,'type':'bone','rotation_global':False,
            'quaternion_interpolation':False,'keyframes':frames}
    animations.append({'uuid':uid(name),'name':'animation.'+name,'loop':'loop' if clip['loop'] else 'once',
        'override':False,'length':clip['length'],'snapping':20,'selected':name=='p2_idle_axe','animators':animators})
phase3='--phase3' in sys.argv
model['animations']=animations;model['name']='Yang Jian - P3 Divine Eye' if phase3 else 'Yang Jian - P2 Animated Weapons'
if phase3:
    for animation in animations:animation['selected']=animation['name']=='animation.p3_idle'
target=ROOT/('modeling/yang_jian_p3.bbmodel' if phase3 else 'modeling/yang_jian_p2.bbmodel')
target.write_text(json.dumps(model,separators=(',',':'),ensure_ascii=False),'utf8')
print(f'{"P3" if phase3 else "P2"} editable project: {len(model["elements"])} cubes, {len(model["groups"])} bones, {len(animations)} clips')
