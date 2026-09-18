"""Author P1 articulated motion and bake it to 60 Hz, without changing the model.

All angles below use Blockbench axes and are converted once at export. Primary
poses are matched to YangJianSkill's server hit ticks; armor and hair follow with
small delays. Sparse editable source curves are saved alongside the runtime data.
"""
from pathlib import Path
import json
import math
from animation_baker import bake, sample

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/myriad_calamity'
GROUPS = {g['name'] for g in json.loads((ROOT/'modeling/yang_jian_v3.bbmodel').read_text('utf8'))['groups']}
CLIPS = {}
SOURCE = {}


def pose(**overrides):
    p = {'hips': [0,0,0], 'waist': [0,0,0], 'chest': [0,0,0], 'neck': [0,0,0], 'head': [0,0,0],
         'left_arm': [8,0,7], 'left_forearm': [5,0,-7], 'left_hand': [0,0,0],
         'right_arm': [5,-5,-6], 'right_forearm': [-6,0,6], 'right_hand': [0,0,0],
         'left_thigh': [0,0,-2], 'right_thigh': [0,0,2], 'left_shin': [0,0,0], 'right_shin': [0,0,0],
         'left_foot': [0,0,0], 'right_foot': [0,0,0], 'weapon': [0,0,0], 'root': [0,0,0]}
    p.update(overrides)
    # Torso/head bones extend upward from their pivots; arms/legs extend down.
    # A forward lean therefore uses the opposite X sign to a forward limb swing.
    for b in ('hips','waist','chest','neck','head'):
        p[b]=[-p[b][0],p[b][1],p[b][2]]
    return p


def attack_pose(kind, loaded=False, follow=False, side=1):
    # Each strike is driven by the hips, then chest, shoulder, elbow and wrist.
    if kind == 'overhead':
        return pose(hips=[-4 if loaded else 13,0,0], waist=[-9 if loaded else 17,0,0],
            chest=[-12 if loaded else 21,0,0], neck=[8 if loaded else -12,0,0],
            head=[6 if loaded else -10,0,0], right_arm=[134 if loaded else 50,-8,-12],
            right_forearm=[-48 if loaded else -13,0,13], right_hand=[-105 if loaded else -126,0,0],
            left_arm=[112 if loaded else 61,15,22], left_forearm=[-36 if loaded else -23,6,-22],
            left_hand=[-26,0,0], left_thigh=[-18 if loaded else 31,0,-7],
            right_thigh=[16 if loaded else -18,0,7], left_shin=[20 if loaded else -28,0,0],
            right_shin=[-14 if loaded else 26,0,0], left_foot=[-6,0,0], right_foot=[-8,0,0])
    if kind == 'upward':
        return pose(hips=[12 if loaded else -3,-20 if loaded else 22,0],
            waist=[9 if loaded else -8,-24 if loaded else 25,0], chest=[7 if loaded else -12,-30 if loaded else 24,-7],
            neck=[-5,9 if loaded else -14,0], head=[-7,18 if loaded else -22,0],
            right_arm=[25 if loaded else 118,-25 if loaded else 20,-25 if loaded else -11],
            right_forearm=[-10 if loaded else -20,0,26], right_hand=[-103 if loaded else -121,0,16],
            left_arm=[17 if loaded else 70,15,28], left_forearm=[-18,0,-16],
            left_thigh=[27 if loaded else 8,0,-5], right_thigh=[-20 if loaded else -9,0,5],
            left_shin=[-20 if loaded else -5,0,0], right_shin=[22 if loaded else 9,0,0])
    angle = (-1 if loaded else 1)*side
    return pose(hips=[7,angle*18,angle*2], waist=[4,angle*26,-angle*3], chest=[3,angle*37,-angle*4],
        neck=[0,-angle*13,0], head=[-3,-angle*18,0],
        right_arm=[53 if loaded else 77,angle*19,-25 if loaded else 5],
        right_forearm=[-32 if loaded else -9,angle*12,24 if loaded else -7],
        right_hand=[-109 if loaded else -142,angle*13,angle*16],
        left_arm=[23 if loaded else 61,-angle*15,24], left_forearm=[-29,0,-17], left_hand=[0,0,-8],
        left_thigh=[angle*19,0,-8], right_thigh=[-angle*19,0,8],
        left_shin=[-angle*10,0,0], right_shin=[angle*10,0,0], left_foot=[0,-angle*8,0], right_foot=[0,angle*8,0])


def curves(frames, length, loop=False):
    names = set().union(*(p for _,p,_ in frames))
    bones = {}
    for name in names:
        assert name in GROUPS, name
        keys = []
        for tick, p, position in frames:
            vector = p.get(name, [0,0,0])
            keys.append({'time': tick/20, 'values': [-vector[0],vector[1],-vector[2]], 'mode': 'catmullrom'})
        # Duplicate tick entries would create ill-defined spline intervals.
        keys = list({k['time']: k for k in keys}.values())
        keys.sort(key=lambda k: k['time'])
        bones[name] = {'rotation': keys}
    root_positions = [{'time': tick/20, 'values': [pos[0],-pos[1],pos[2]], 'mode': 'catmullrom'}
                      for tick,_,pos in frames]
    bones.setdefault('root', {})['position'] = sorted({k['time']:k for k in root_positions}.values(), key=lambda k:k['time'])
    return bones


def finish(name, frames, ticks, loop=False, metadata=None):
    bones = curves(frames, ticks/20, loop)
    # Overlapping plates, split cloth and hair follow the preceding body motion.
    secondary = ['left_shoulder','right_shoulder','left_vambrace','right_vambrace',
        'left_skirt','right_skirt','left_skirt_lower','right_skirt_lower',
        'left_front_cloth','right_front_cloth','left_front_cloth_tip','right_front_cloth_tip',
        'left_back_cloth','right_back_cloth','rear_sash','rear_sash_tip','front_tabard',
        'left_tasset_outer','right_tasset_outer','hair_back_01','hair_back_02','hair_back_03',
        'left_hair_lock','right_hair_lock','topknot','crown','hair_top']
    for b in secondary:
        if b not in GROUPS:
            continue
        keys = []
        for tick in range(ticks+1):
            t = tick/20
            delay = 1.5 if 'tip' in b or '03' in b else 1 if 'hair' in b else .5
            src_time = ((tick-delay)/20)%(ticks/20) if loop else max(0,(tick-delay)/20)
            chest = sample(bones['chest']['rotation'], src_time, loop)
            hips = sample(bones['hips']['rotation'], src_time, loop)
            side = -1 if b.startswith('left') else 1
            frequency = 2*math.pi/(ticks/20) if loop else 5.7
            wave = math.sin(t*frequency+delay+side*.4)
            if 'hair' in b or b in ('topknot','crown'):
                amount = .3 if 'back' in b else .13
                v = [-chest[0]*amount+wave*1.1,-chest[1]*amount,-chest[2]*.25+wave*.7]
            elif 'shoulder' in b or 'vambrace' in b:
                arm = sample(bones['left_arm' if side<0 else 'right_arm']['rotation'], src_time, loop)
                v = [-arm[0]*.12,0,-arm[2]*.14]
            else:
                thigh = sample(bones['left_thigh' if side<0 else 'right_thigh']['rotation'],src_time,loop)
                v = [thigh[0]*.20-hips[0]*.18+wave*1.4,-hips[1]*.25,side*abs(thigh[0])*.075+wave*.8]
            if name.endswith('death') or name=='phase_clear':
                fade = max(0,1-t/max(.01,ticks/20))
                v = [a*fade for a in v]
            keys.append({'time': t, 'values': v, 'mode': 'linear'})
        bones[b] = {'rotation': keys}
    SOURCE[name] = {'length': ticks/20,'loop': loop,'bones':bones,'timing':metadata or {}}
    CLIPS[name] = {'length':ticks/20,'loop':loop,'bones':{
        b:{channel:bake(keys,ticks/20,loop) for channel,keys in channels.items()}
        for b,channels in bones.items()}}


def combos(name, windups, gap, recovery, kinds):
    frames, starts, hits = [], [], []
    tick, turns = 0, 0
    for i,(windup,kind) in enumerate(zip(windups,kinds)):
        starts.append(tick); hits.append(tick+windup)
        side = 1 if i%2==0 else -1
        ready = pose(chest=[2,-side*9,0],left_thigh=[8,0,-4],right_thigh=[-7,0,4])
        loaded = attack_pose(kind, True, side=side)
        hit = attack_pose(kind, side=side)
        follow = attack_pose(kind, follow=True, side=side)
        for p in (ready,loaded,hit,follow):
            p['root']=[0,turns,0]
        if kind == 'spin':
            # One clockwise turn. The model is rendered mirrored, so the clockwise look is
            # authored here as increasing yaw; the world-space sweeps reach it the other way.
            loaded['root'][1] = turns+40
            hit['root'][1] = turns+275
            follow['root'][1] = turns+360
            turns += 360
        crouch = -.9 if kind=='overhead' else -.4
        frames += [(tick,ready,[0,0,0]),(tick+windup*.66,loaded,[0,crouch,0]),
                   (tick+windup-1.5,loaded,[0,crouch,0]),(tick+windup,hit,[0,-.4,-.25]),
                   (tick+windup+2,follow,[0,-.25,-.15])]
        tick += windup+3+gap
    end=pose();end['root']=[0,turns,0]
    duration=tick-gap+recovery
    frames += [(duration,end,[0,0,0])]
    finish(name,frames,duration,metadata={'step_start_ticks':starts,'hit_ticks':hits,'windup_ticks':windups,'active_ticks':3,'gap_ticks':gap})


def main():
    idle = []
    for tick in range(0,81,4):
        w=math.sin(tick*math.pi/40)
        p=pose(chest=[w*.8,0,0],neck=[-w*.3,0,0],right_forearm=[-6+w*.65,0,6],left_hand=[w*.6,0,0])
        idle.append((tick,p,[0,w*.20,0]))
    finish('idle',idle,80,True)
    walk=[]
    for tick in range(0,25,2):
        w=math.sin(tick*math.pi/12);c=math.cos(tick*math.pi/12)
        p=pose(hips=[2,w*3,0],waist=[-1,-w*4,0],chest=[4,-w*5,w*.5],head=[-2,w*3,0],
            left_thigh=[w*24,0,-2],right_thigh=[-w*24,0,2],left_shin=[-max(0,-w)*31,0,0],
            right_shin=[-max(0,w)*31,0,0],left_foot=[max(0,c)*8,0,0],right_foot=[max(0,-c)*8,0,0],
            left_arm=[-w*13,0,7],left_forearm=[8+max(0,w)*9,0,-7],right_arm=[5+w*4,-5,-6])
        walk.append((tick,p,[0,abs(w)*.65,0]))
    finish('walk',walk,24,True)
    combos('combo',[8,7,10,17],3,14,['horizontal','upward','spin','overhead'])
    combos('four_combo',[7,5,7,16],2,16,['horizontal','horizontal','spin','overhead'])
    combos('six_combo',[7,5,15,5,7,18],2,20,['upward','spin','spin','horizontal','upward','overhead'])

    ready=pose(hips=[5,0,0],waist=[4,-12,0],chest=[7,-18,0],head=[-9,15,0],
        right_arm=[60,-10,-10],right_forearm=[-18,0,9],right_hand=[-116,0,0],
        left_arm=[65,20,25],left_forearm=[-25,0,-14],left_thigh=[24,0,-5],right_thigh=[-25,0,5],right_shin=[18,0,0])
    thrust=pose(hips=[13,0,0],waist=[8,16,0],chest=[15,18,0],head=[-17,-22,0],
        right_arm=[72,8,-4],right_forearm=[-8,0,2],right_hand=[-118,0,0],
        left_arm=[35,-10,28],left_forearm=[-34,0,-17],left_thigh=[40,0,-5],right_thigh=[-39,0,5],
        left_shin=[-21,0,0],right_shin=[29,0,0],right_foot=[-10,0,0])
    # Normalized windup and active pieces are time-scaled to the current server plan.
    finish('thrust_windup',[(0,pose(),[0,0,0]),(13,ready,[0,-.7,0]),(20,ready,[0,-.7,0])],20)
    finish('thrust',[(0,ready,[0,-.7,0]),(2,thrust,[0,-1,-.8]),(6,thrust,[0,-1,-.8]),(17,pose(),[0,0,0])],17)
    throw_load=pose(hips=[-5,18,0],waist=[-7,25,0],chest=[-8,20,-5],head=[3,-32,0],
        right_arm=[132,-25,-18],right_forearm=[-33,0,24],right_hand=[-95,0,4],
        left_arm=[58,5,27],left_forearm=[-11,0,-18],left_thigh=[18,0,-4],right_thigh=[-11,0,4])
    throw_release=pose(hips=[8,-20,0],waist=[9,-25,0],chest=[11,-18,0],head=[-10,24,0],
        right_arm=[92,6,-4],right_forearm=[-2,0,1],right_hand=[-152,0,0],
        left_arm=[25,0,23],left_thigh=[30,0,-4],right_thigh=[-25,0,4])
    finish('throw',[(0,pose(),[0,0,0]),(12,throw_load,[0,1,0]),(14.5,throw_load,[0,1,0]),
        (16,throw_release,[0,.6,0]),(22,throw_release,[0,0,0]),(26,pose(),[0,0,0])],26)
    finish('recall',[(0,pose(right_arm=[70,0,-8],right_forearm=[-9,0,3]),[0,0,0]),
        (5,pose(right_arm=[30,-12,-10],right_forearm=[-34,0,12],chest=[-3,10,0]),[0,0,0]),
        (14,pose(),[0,0,0])],14)
    finish('throw_followup',[(0,attack_pose('horizontal'),[0,-.4,-.3]),
        (3,attack_pose('horizontal',follow=True),[0,-.2,0]),(6,pose(),[0,0,0])],6)
    summon=pose(chest=[-3,8,0],head=[5,-9,0],left_arm=[72,15,42],left_forearm=[-10,0,-20],left_hand=[-30,0,12],
        right_arm=[-3,-10,-7],right_forearm=[-10,0,10])
    finish('summon_hound',[(0,pose(),[0,0,0]),(10,summon,[0,.3,0]),(22,summon,[0,.3,0]),(36,pose(),[0,0,0])],36)
    finish('coordinated',[(0,pose(),[0,0,0]),(8,attack_pose('horizontal',True),[0,-.4,0]),
        (12,attack_pose('horizontal'),[0,-.4,-.2]),(22,ready,[0,-.6,0]),(36,ready,[0,-.6,0]),
        (38,thrust,[0,-1,-.7]),(43,thrust,[0,-1,-.7]),(61,pose(),[0,0,0])],61)
    guard=pose(hips=[2,12,0],waist=[0,10,0],chest=[-3,14,0],neck=[0,-12,0],head=[0,-14,0],
        right_arm=[76,12,-20],right_forearm=[-43,0,15],right_hand=[-34,0,-52],
        left_arm=[69,-8,31],left_forearm=[-30,0,-24],left_hand=[-6,0,-15],
        left_thigh=[14,0,-5],right_thigh=[-13,0,5],right_shin=[10,0,0])
    finish('guard',[(0,pose(),[0,0,0]),(3,guard,[0,-.55,0]),(20,guard,[0,-.55,0])],20)
    finish('counter_windup',[(0,guard,[0,-.5,0]),(9,ready,[0,-.7,0]),(20,ready,[0,-.7,0])],20)
    finish('counter',[(0,ready,[0,-.7,0]),(1.5,thrust,[0,-1,-.7]),(6,thrust,[0,-1,-.7]),(16,pose(),[0,0,0])],16)
    kneel=pose(hips=[12,0,0],waist=[9,0,0],chest=[12,0,0],neck=[9,0,0],head=[10,0,0],
        left_thigh=[44,0,-7],right_thigh=[-49,0,7],left_shin=[-68,0,0],right_shin=[-60,0,0],
        right_arm=[38,0,-13],right_forearm=[-8,0,7],right_hand=[-28,0,0],
        left_arm=[38,0,22],left_forearm=[-13,0,-10])
    finish('phase_clear',[(0,pose(),[0,0,0]),(8,pose(chest=[-15,0,0],head=[-12,0,0]),[0,-.7,0]),
        (25,kneel,[0,-7.5,0]),(50,kneel,[0,-7.5,0])],50)
    death=pose(root=[0,0,76],chest=[16,0,7],head=[18,0,0],right_arm=[29,0,-26],left_arm=[20,0,34],
        left_thigh=[17,0,-9],right_thigh=[-13,0,7],left_shin=[-12,0,0],right_shin=[-19,0,0])
    finish('death',[(0,pose(),[0,0,0]),(8,kneel,[0,-7,0]),(20,death,[0,-1.2,0])],20)
    from generate_yang_jian_p2_animations import append_p2
    append_p2(pose,finish,attack_pose)
    from generate_yang_jian_p3_animations import append_p3
    append_p3(pose,finish,attack_pose)
    from generate_yang_jian_step_animations import append_steps
    append_steps(pose,finish)
    (ASSETS/'animations').mkdir(parents=True,exist_ok=True)
    (ASSETS/'animations/yang_jian.json').write_text(json.dumps({'format':1,'sampling_fps':60,'clips':CLIPS},separators=(',',':')),'utf8')
    (ROOT/'modeling/yang_jian-animation-source.json').write_text(json.dumps({'format':1,'axes':'Minecraft runtime, converted from Blockbench','clips':SOURCE},separators=(',',':')),'utf8')
    print('Baked',len(CLIPS),'clips; affected bones:',len(set().union(*(c['bones'] for c in CLIPS.values()))))
    for name,c in SOURCE.items():
        if c['timing']: print(name,c['timing'])


if __name__ == '__main__':
    main()
