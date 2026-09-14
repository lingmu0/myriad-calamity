"""Create a separate editable Blockbench P1 animation project; never overwrite the approved sculpt."""
from pathlib import Path
import json
import uuid

ROOT=Path(__file__).resolve().parents[1]
model=json.loads((ROOT/'modeling/yang_jian_v3.bbmodel').read_text('utf8'))
source=json.loads((ROOT/'modeling/yang_jian-animation-source.json').read_text('utf8'))['clips']
groups={g['name']:g['uuid'] for g in model['groups']}
def uid(value):return str(uuid.uuid5(uuid.NAMESPACE_URL,'myriad_calamity:yang_jian:p1:'+value))
animations=[]
P1_CLIPS={'idle','walk','combo','four_combo','six_combo','throw','throw_followup','recall',
          'thrust_windup','thrust','summon_hound','coordinated','guard','counter_windup','counter','phase_clear','death'}
for name,clip in source.items():
    if name not in P1_CLIPS:continue
    animators={}
    for bone,channels in clip['bones'].items():
        frames=[]
        for channel,keys in channels.items():
            for key in keys:
                x,y,z=key['values']
                if channel=='rotation':x,z=-x,-z
                elif channel=='position':y=-y
                frames.append({'channel':channel,'data_points':[{'x':str(x),'y':str(y),'z':str(z)}],
                               'uuid':uid(f'{name}:{bone}:{channel}:{key["time"]}'),
                               'time':key['time'],'color':-1,'interpolation':key['mode']})
        animators[groups[bone]]={'name':bone,'type':'bone','rotation_global':False,
                                'quaternion_interpolation':False,'keyframes':frames}
    animations.append({'uuid':uid(name),'name':'animation.'+name,'loop':'loop' if clip['loop'] else 'once',
                       'override':False,'length':clip['length'],'snapping':20,'selected':False,
                       'animators':animators})
model['animations']=animations
model['name']='Yang Jian - P1 Animated'
target=ROOT/'modeling/yang_jian_p1.bbmodel'
target.write_text(json.dumps(model,separators=(',',':'),ensure_ascii=False),'utf8')
print(f'{target}: {len(animations)} animations, {len(model["elements"])} unchanged cuboids')
