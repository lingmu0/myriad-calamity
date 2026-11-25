"""Author P1 articulated motion and bake it to 60 Hz, without changing the model.

All angles below use Blockbench axes and are converted once at export. Primary
poses are matched to YangJianSkill's server hit ticks; armor and hair follow with
small delays. Sparse editable source curves are saved alongside the runtime data.
"""
from pathlib import Path
import json
import math
import copy
from animation_baker import bake, sample
from yang_jian_arm_motion import improve_pose, bake_forearm
from yang_jian_foot_motion import refine_footwork
from export_yang_jian_assets import SOURCE as MODEL

ROOT = Path(__file__).resolve().parents[1]
# Legacy authoring convention only. Physical flex/carry/twist are calibrated to
# each side's actual geometry by the shared retargeter before any curve is baked.
CARRY_ANGLE = 18
ASSETS = ROOT/'src/main/resources/assets/myriad_calamity'
GROUPS = {g['name'] for g in json.loads(MODEL.read_text('utf8'))['groups']}
CLIPS = {}
SOURCE = {}


def pose(**overrides):
    # A real arm abducts at the shoulder and then carries the forearm back inward again (the
    # elbow's carrying angle), so the hands hang under the shoulders instead of splaying out:
    # the upper arm keeps its outward roll and the forearm gets the mirrored inward channel.
    p = {'hips': [0,0,0], 'waist': [0,0,0], 'chest': [0,0,0], 'neck': [0,0,0], 'head': [0,0,0],
         'left_arm': [6,0,-8], 'left_forearm': [-10,0,CARRY_ANGLE], 'left_hand': [0,0,0],
         'right_arm': [4,-4,8], 'right_forearm': [-10,0,-CARRY_ANGLE], 'right_hand': [0,0,0],
         'left_thigh': [0,0,-2], 'right_thigh': [0,0,2], 'left_shin': [0,0,0], 'right_shin': [0,0,0],
         'left_foot': [0,0,0], 'right_foot': [0,0,0], 'weapon': [0,0,0], 'root': [0,0,0]}
    p.update(overrides)
    # Torso/head bones extend upward from their pivots; arms/legs extend down.
    # A forward lean therefore uses the opposite X sign to a forward limb swing.
    for b in ('hips','waist','chest','neck','head'):
        p[b]=[-p[b][0],p[b][1],p[b][2]]
    return p


def attack_pose(kind, loaded=False, follow=False, side=1, snap=False):
    # Full kinetic-chain poses: feet -> hips -> waist -> chest -> shoulder -> forearm -> wrist!
    # `snap=True` fires ~1.4 ticks before impact: hips & waist uncoil early while chest & arm still stretch back.
    if kind == 'overhead':
        if snap:
            return pose(two_handed=True, hips=[10, 6, 0], waist=[6, 4, 0],
                chest=[-4, 0, 0], neck=[4, 0, 0], head=[-2, 0, 0],
                right_arm=[116, -6, -12], right_forearm=[-38, 0, 13], right_hand=[-94, 0, 4],
                left_arm=[98, 14, 22], left_forearm=[-32, 6, -20], left_hand=[-20, 0, 0],
                left_thigh=[16, 0, -8], right_thigh=[-6, 0, 8], left_shin=[-18, 0, 0],
                right_shin=[12, 0, 0], left_foot=[-4, 0, 0], right_foot=[8, 0, 0])
        if follow:
            return pose(two_handed=True, hips=[16, 4, 0], waist=[20, 3, 0],
                chest=[24, 2, 0], neck=[-14, 0, 0], head=[-12, 0, 0],
                right_arm=[38, -6, -10], right_forearm=[-20, 0, 12], right_hand=[-132, 0, -4],
                left_arm=[50, 14, 20], left_forearm=[-28, 6, -20], left_hand=[-28, 0, 0],
                left_thigh=[36, 0, -8], right_thigh=[-22, 0, 8], left_shin=[-34, 0, 0],
                right_shin=[28, 0, 0], left_foot=[-8, 0, 0], right_foot=[-10, 0, 0])
        return pose(two_handed=True, hips=[-6 if loaded else 14, 0, 0], waist=[-11 if loaded else 18, 0, 0],
            chest=[-15 if loaded else 22, 0, 0], neck=[10 if loaded else -13, 0, 0],
            head=[8 if loaded else -11, 0, 0], right_arm=[138 if loaded else 48, -8, -13],
            right_forearm=[-52 if loaded else -12, 0, 14], right_hand=[-100 if loaded else -128, 0, 0],
            left_arm=[116 if loaded else 58, 16, 22], left_forearm=[-38 if loaded else -22, 6, -22],
            left_hand=[-26, 0, 0], left_thigh=[-22 if loaded else 34, 0, -8],
            right_thigh=[20 if loaded else -20, 0, 8], left_shin=[22 if loaded else -32, 0, 0],
            right_shin=[-16 if loaded else 28, 0, 0], left_foot=[-6, 0, 0], right_foot=[-8, 0, 0])
    if kind == 'upward':
        if snap:
            return pose(hips=[6, 12, -2], waist=[3, 4, -2], chest=[4, -14, -5],
                neck=[-4, 6, 0], head=[-6, 12, 0],
                right_arm=[48, -14, -22], right_forearm=[-22, 0, 22], right_hand=[-96, -8, 12],
                left_arm=[34, 10, 26], left_forearm=[-22, 0, -16], left_hand=[-12, 8, -8],
                left_thigh=[22, 0, -6], right_thigh=[-14, 0, 6],
                left_shin=[-18, 0, 0], right_shin=[18, 0, 0])
        if follow:
            return pose(hips=[-5, 28, 2], waist=[-10, 28, 2], chest=[-14, 26, -6],
                neck=[6, -18, 0], head=[4, -26, 0],
                right_arm=[126, 26, -8], right_forearm=[-26, 0, 22], right_hand=[-126, 14, 18],
                left_arm=[76, -12, 32], left_forearm=[-28, 0, -18], left_hand=[-18, -10, 10],
                left_thigh=[4, 0, -6], right_thigh=[-6, 0, 6],
                left_shin=[-4, 0, 0], right_shin=[8, 0, 0])
        return pose(hips=[14 if loaded else -4, -24 if loaded else 24, -2 if loaded else 2],
            waist=[10 if loaded else -9, -26 if loaded else 26, 0],
            chest=[8 if loaded else -13, -32 if loaded else 26, -8],
            neck=[-6, 11 if loaded else -16, 0], head=[-8, 20 if loaded else -24, 0],
            right_arm=[22 if loaded else 120, -28 if loaded else 22, -28 if loaded else -10],
            right_forearm=[-14 if loaded else -18, 0, 26],
            right_hand=[-98 if loaded else -124, -10 if loaded else 12, 14 if loaded else 18],
            left_arm=[14 if loaded else 72, 18 if loaded else -10, 30],
            left_forearm=[-20 if loaded else -24, 0, -16], left_hand=[-10 if loaded else -22, 8, -10],
            left_thigh=[30 if loaded else 6, 0, -6], right_thigh=[-22 if loaded else -8, 0, 6],
            left_shin=[-24 if loaded else -5, 0, 0], right_shin=[24 if loaded else 10, 0, 0])
    angle = (-1 if loaded else 1) * side
    if snap:
        return pose(hips=[9, side * 12, side * 3], waist=[5, side * 5, -side * 2],
            chest=[4, -side * 12, -side * 4], neck=[-1, side * 8, 0], head=[-4, side * 14, 0],
            right_arm=[60, -side * 16, -24], right_forearm=[-36, -side * 6, 20],
            right_hand=[-102, -side * 14, -side * 10],
            left_arm=[36, side * 10, 26], left_forearm=[-30, 0, -18], left_hand=[-16, side * 10, -10],
            left_thigh=[side * 22, 0, -8], right_thigh=[-side * 20, 0, 8],
            left_shin=[-side * 14, 0, 0], right_shin=[side * 14, 0, 0],
            left_foot=[0, -side * 8, 0], right_foot=[0, side * 8, 0])
    return pose(hips=[8, angle * (24 if follow else 18), angle * 3],
        waist=[5, angle * (16 if follow else 18), -angle * 3],
        chest=[4, angle * (22 if follow else 24), -angle * 5],
        neck=[-1, -angle * 13, 0], head=[-4, -angle * 21, 0],
        right_arm=[50 if loaded else 64 if follow else 79, angle * (28 if follow else 22), -28 if loaded else 8],
        right_forearm=[-36 if loaded else -24 if follow else -8, angle * 11, 24 if loaded else -7],
        right_hand=[-104 if loaded else -120 if follow else -138, angle * (18 if follow else 14), angle * (16 if follow else 14)],
        left_arm=[26 if loaded else 40 if follow else 58, -angle * 18, 26],
        left_forearm=[-32 if loaded else -26, 0, -18],
        left_hand=[-14 if loaded else -24 if follow else -20, -angle * 12, -10],
        left_thigh=[angle * 24, 0, -8], right_thigh=[-angle * 22, 0, 8],
        left_shin=[-angle * 14, 0, 0], right_shin=[angle * 14, 0, 0],
        left_foot=[0, -angle * 10, 0], right_foot=[0, angle * 10, 0])


def thrust_poses():
    """Shared martial spear-lunge sequence: hip-coiled chamber -> explosive surge -> braking plant."""
    coil = pose(hips=[8, -18, -2], waist=[6, -15, -2], chest=[9, -18, -3], neck=[-4, 14, 1], head=[-9, 22, 1],
        right_arm=[56, -18, 12], right_forearm=[-36, 0, -CARRY_ANGLE], right_hand=[-112, -10, -8],
        left_arm=[76, 18, 14], left_forearm=[-22, 0, CARRY_ANGLE], left_hand=[-16, 12, -10],
        left_thigh=[34, 0, -7], right_thigh=[-28, 0, 7], left_shin=[-40, 0, 0], right_shin=[-34, 0, 0],
        left_foot=[10, 0, 0], right_foot=[16, 0, 0])
    surge = pose(hips=[16, 18, 2], waist=[11, 14, 2], chest=[16, 16, 3], neck=[-6, -12, -1], head=[-15, -20, -1],
        right_arm=[80, 14, -6], right_forearm=[-7, 0, -CARRY_ANGLE], right_hand=[-126, 8, 6],
        left_arm=[-14, -18, 34], left_forearm=[-36, 0, CARRY_ANGLE], left_hand=[-18, -12, 10],
        left_thigh=[48, 0, -6], right_thigh=[-46, 0, 6], left_shin=[-26, 0, 0], right_shin=[-18, 0, 0],
        left_foot=[-12, 0, 0], right_foot=[26, 0, 0])
    apex = copy.deepcopy(surge)
    apex['hips'][1] = 24; apex['chest'][0] = -18; apex['right_arm'][0] = 86; apex['right_hand'][0] = -132
    apex['left_arm'] = [-22, -22, 38]
    brake = pose(hips=[10, 10, 1], waist=[7, 6, 1], chest=[10, 8, 1], neck=[-4, -6, 0], head=[-8, -10, 0],
        right_arm=[54, 4, 4], right_forearm=[-28, 0, -CARRY_ANGLE], right_hand=[-114, 4, 2],
        left_arm=[32, 6, 22], left_forearm=[-30, 0, CARRY_ANGLE], left_hand=[-12, 4, -6],
        left_thigh=[36, 0, -7], right_thigh=[-22, 0, 7], left_shin=[-42, 0, 0], right_shin=[-28, 0, 0],
        left_foot=[12, 0, 0], right_foot=[12, 0, 0])
    return coil, surge, apex, brake


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
    # Poses are shared between clips and repeated hold keys. Never retarget the
    # author's dictionaries in place, or subsequent clips solve an already fixed arm.
    frames = copy.deepcopy(frames)
    # Convert legacy authoring to its old full pose before anatomical retargeting.
    # The fixed legacy roll is never used as the actual output elbow constraint.
    seen=set()
    for tick,p,_ in frames:
        if id(p) in seen:
            continue
        seen.add(id(p))
        for side,sign in (('left',1),('right',-1)):
            arm,fore=side+'_arm',side+'_forearm'
            if arm in p and fore in p:
                p[arm][2]+=p[fore][2]-sign*CARRY_ANGLE
                p[fore][2]=sign*CARRY_ANGLE
    legacy = curves(copy.deepcopy(frames), ticks/20, loop) if name == 'transition' else None
    seen=set()
    for tick,p,_ in frames:
        if id(p) in seen:
            continue
        seen.add(id(p))
        improve_pose(p, name, tick)
    bones = curves(frames, ticks/20, loop)
    if name in ('combo','four_combo','six_combo'):
        # Bounded yaw gives the feet a single turn to follow through the strike.
        keys = bones['root']['rotation']
        for key in keys:
            key['mode'] = 'pchip'
        bones['root']['rotation'] = [
            {'time': key[0], 'values': key[1:], 'mode': 'linear'} for key in bake(keys, ticks/20)
        ]
    if name in ('divine_sweep','divine_spin'):
        for key in bones['root']['rotation']:
            if 20 <= key['time']*20 <= 30:
                key['mode']='linear'
    if name == 'eye_blink_cleave':
        for key in bones['root']['rotation']:
            if 40 <= key['time']*20 <= 48:
                key['mode']='linear'
    # Bounded smooth curves prevent the wrist recoiling past its authored joint
    # limits. Store the bake as editable linear keys too: Blockbench has no PCHIP.
    # Forearms interpolate flex/carry/twist, then convert to runtime Euler angles.
    # Interpolating Euler channels first would reintroduce reverse bending in-between.
    for side in ('left', 'right'):
        for suffix in ('_arm', '_forearm', '_hand'):
            keys = bones[side + suffix]['rotation']
            for key in keys:
                key['mode'] = 'pchip'
            output=[]
            baked=bake_forearm(side,keys,ticks/20,loop) if suffix=='_forearm' else bake(keys,ticks/20,loop)
            for key in baked:
                values=key[1:]
                if legacy and side=='right' and key[0] >= 72/20:
                    # This independently fitted floor-contact span already has
                    # valid forward elbows. Keep its exact weapon registration.
                    values=sample(legacy[side+suffix]['rotation'],key[0],loop)
                output.append({'time':key[0],'values':values,'mode':'linear'})
            bones[side + suffix]['rotation']=output
    if name == 'transition':
        from yang_jian_axe_descent import create_tracks
        preview_clip={'bones':{b:{c:bake(keys,ticks/20,loop) for c,keys in channels.items()}
                               for b,channels in bones.items()}}
        # Moving the elbow to its natural side changes the chain's interpolation.
        # Follow the axe's short rotation arc during descent and keep floor contact.
        for bone,keys in create_tracks(preview_clip).items():
            kept=[key for key in bones[bone]['rotation'] if key['time']<68/20 or key['time']>72/20]
            kept.extend({'time':key[0],'values':key[1:],'mode':'linear'} for key in keys)
            bones[bone]['rotation']=sorted(kept,key=lambda key:key['time'])
    refine_footwork(name,bones,ticks,loop,metadata or {})
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
            tail_segment=int(b[-1]) if b.startswith('hair_back_') else 0
            delay = .75+tail_segment*.8 if tail_segment else 1.6 if 'tip' in b or 'lower' in b else .65
            src_time = ((tick-delay)/20)%(ticks/20) if loop else max(0,(tick-delay)/20)
            chest = sample(bones['chest']['rotation'], src_time, loop)
            hips = sample(bones['hips']['rotation'], src_time, loop)
            side = -1 if b.startswith('left') else 1
            frequency = 2*math.pi/(ticks/20) if loop else 5.7
            wave = math.sin(t*frequency+delay+side*.4)
            if b in ('hair_top','topknot','crown'):
                # Skull, hairline, and bound crown move as one with the face.
                v=[0,0,0]
            elif tail_segment:
                amount=.075+.038*tail_segment
                v=[-chest[0]*amount+wave*.48*tail_segment,-chest[1]*amount,
                   -chest[2]*amount+wave*.32*tail_segment]
            elif b.endswith('hair_lock'):
                v=[max(-2,min(2,-chest[0]*.04+wave*.35)),0,max(-1.2,min(1.2,-chest[2]*.04))]
            elif 'vambrace' in b:
                # The vambrace is rigidly attached to the forearm. The old delayed
                # upper-arm rotation made its cuff tear away from the wrist.
                v = [0,0,0]
            elif 'shoulder' in b:
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
        ready = pose(hips=[3,-side*7,0],chest=[3,-side*10,0],left_thigh=[10,0,-5],right_thigh=[-9,0,5])
        loaded = attack_pose(kind, True, side=side)
        snapped = attack_pose(kind, side=side, snap=True)
        hit = attack_pose(kind, side=side)
        follow = attack_pose(kind, follow=True, side=side)
        for p in (ready,loaded,snapped,hit,follow):
            p['root']=[0,turns,0]
        if kind == 'spin':
            # One clockwise turn. The model is rendered mirrored, so the clockwise look is
            # authored here as increasing yaw; the world-space sweeps reach it the other way.
            loaded['root'][1] = turns+29
            snapped['root'][1] = turns+64
            hit['root'][1] = turns+128
            follow['root'][1] = turns+292
            finish_turn = copy.deepcopy(follow)
            finish_turn['root'][1] = turns+360
            turns += 360
        crouch = -1.15 if kind=='overhead' else -.55
        load_t = tick + windup * .62
        snap_t = tick + windup - 1.4
        frames += [(tick,ready,[0,-.15,0]),
                   (load_t,loaded,[0,crouch,0]),
                   (snap_t,snapped,[0,crouch-.12,-.10]),
                   (tick+windup,hit,[0,-.55,-.35]),
                   (tick+windup+2.1,follow,[0,-.32,-.20])]
        if kind == 'spin':
            frames.append((tick+windup+3,finish_turn,[0,-.22,0]))
        tick += windup+3+gap
    end=pose();end['root']=[0,turns,0]
    duration=tick-gap+recovery
    frames += [(duration,end,[0,0,0])]
    finish(name,frames,duration,metadata={'step_start_ticks':starts,'hit_ticks':hits,'windup_ticks':windups,'active_ticks':3,'gap_ticks':gap})


def main():
    idle = []
    for tick in range(0,81,4):
        w=math.sin(tick*math.pi/40)
        p=pose(chest=[w*.8,0,0],neck=[-w*.3,0,0],right_forearm=[-6+w*.65,0,-CARRY_ANGLE],left_hand=[w*.6,0,0])
        idle.append((tick,p,[0,w*.20,0]))
    finish('idle',idle,80,True)
    walk=[]
    for tick in range(0,25,2):
        w=math.sin(tick*math.pi/12);c=math.cos(tick*math.pi/12)
        p=pose(hips=[2,w*3,0],waist=[-1,-w*4,0],chest=[4,-w*5,w*.5],head=[-2,w*3,0],
            left_thigh=[w*24,0,-2],right_thigh=[-w*24,0,2],left_shin=[-max(0,-w)*31,0,0],
            right_shin=[-max(0,w)*31,0,0],left_foot=[max(0,c)*8,0,0],right_foot=[max(0,-c)*8,0,0],
            left_arm=[-w*13,0,-8],left_forearm=[8+max(0,w)*9,0,CARRY_ANGLE],right_arm=[5+w*4,-5,-8])
        walk.append((tick,p,[0,abs(w)*.65,0]))
    finish('walk',walk,24,True)
    combos('combo',[8,7,10,17],3,14,['horizontal','upward','spin','overhead'])
    combos('four_combo',[7,5,7,16],2,16,['horizontal','horizontal','spin','overhead'])
    combos('six_combo',[7,5,15,5,7,18],2,20,['upward','spin','spin','horizontal','upward','overhead'])

    ready, thrust, thrust_apex, thrust_brake = thrust_poses()
    # Normalized windup and active pieces are time-scaled to the current server plan.
    finish('thrust_windup',[(0,pose(),[0,0,0]),(6,pose(hips=[4,-10,0],chest=[5,-11,0],left_thigh=[16,0,-5],right_thigh=[-14,0,5]),[0,-.45,0]),
        (14,ready,[0,-1.15,0]),(20,ready,[0,-1.25,0])],20)
    finish('thrust',[(0,ready,[0,-1.2,0]),(1.4,thrust,[0,-.95,-.85]),(3.8,thrust_apex,[0,-1.05,-.95]),
        (6.0,thrust,[0,-.90,-.70]),(9.0,thrust_brake,[0,-1.15,-.25]),(13.0,pose(hips=[3,3,0],chest=[3,3,0]),[0,-.35,0]),
        (17,pose(),[0,0,0])],17)
    throw_load=pose(hips=[-5,18,0],waist=[-7,25,0],chest=[-8,20,-5],head=[3,-32,0],
        right_arm=[132,-25,-18],right_forearm=[-33,0,24],right_hand=[-95,0,4],
        left_arm=[58,5,27],left_forearm=[-11,0,-18],left_thigh=[18,0,-4],right_thigh=[-11,0,4])
    throw_release=pose(hips=[8,-20,0],waist=[9,-25,0],chest=[11,-18,0],head=[-10,24,0],
        right_arm=[92,6,-4],right_forearm=[-2,0,1],right_hand=[-152,0,0],
        left_arm=[25,0,23],left_thigh=[30,0,-4],right_thigh=[-25,0,4])
    finish('throw',[(0,pose(),[0,0,0]),(12,throw_load,[0,1,0]),(14.5,throw_load,[0,1,0]),
        (16,throw_release,[0,.6,0]),(22,throw_release,[0,0,0]),(26,pose(),[0,0,0])],26)
    finish('recall',[(0,pose(right_arm=[70,0,-8],right_forearm=[-9,0,-18]),[0,0,0]),
        (5,pose(right_arm=[30,-12,-10],right_forearm=[-34,0,-CARRY_ANGLE],chest=[-3,10,0]),[0,0,0]),
        (14,pose(),[0,0,0])],14)
    finish('throw_followup',[(0,attack_pose('horizontal'),[0,-.4,-.3]),
        (3,attack_pose('horizontal',follow=True),[0,-.2,0]),(6,pose(),[0,0,0])],6)
    summon=pose(chest=[-3,8,0],head=[5,-9,0],left_arm=[72,15,16],left_forearm=[-10,0,CARRY_ANGLE],left_hand=[-30,0,12],
        right_arm=[-3,-10,-7],right_forearm=[-10,0,-18])
    finish('summon_hound',[(0,pose(),[0,0,0]),(10,summon,[0,.3,0]),(22,summon,[0,.3,0]),(36,pose(),[0,0,0])],36)
    finish('coordinated',[(0,pose(),[0,0,0]),(8,attack_pose('horizontal',True),[0,-.55,0]),
        (10.6,attack_pose('horizontal',snap=True),[0,-.60,-.1]),
        (12,attack_pose('horizontal'),[0,-.50,-.25]),(23,ready,[0,-1.05,0]),(36,ready,[0,-1.20,0]),
        (37.6,thrust,[0,-.95,-.80]),(40.5,thrust_apex,[0,-1.05,-.90]),(43,thrust,[0,-.85,-.65]),
        (47,thrust_brake,[0,-1.05,-.20]),(61,pose(),[0,0,0])],61)
    guard=pose(hips=[2,12,0],waist=[0,10,0],chest=[-3,14,0],neck=[0,-12,0],head=[0,-14,0],
        right_arm=[76,12,8],right_forearm=[-43,0,-CARRY_ANGLE],right_hand=[-34,0,-52],
        left_arm=[69,-8,14],left_forearm=[-30,0,CARRY_ANGLE],left_hand=[-6,0,-15],
        left_thigh=[14,0,-5],right_thigh=[-13,0,5],right_shin=[10,0,0])
    finish('guard',[(0,pose(),[0,0,0]),(3,guard,[0,-.55,0]),(20,guard,[0,-.55,0])],20)
    finish('counter_windup',[(0,guard,[0,-.55,0]),(9,ready,[0,-1.15,0]),(20,ready,[0,-1.25,0])],20)
    finish('counter',[(0,ready,[0,-1.20,0]),(1.3,thrust,[0,-.95,-.85]),(3.5,thrust_apex,[0,-1.05,-.95]),
        (5.5,thrust,[0,-.90,-.65]),(8.5,thrust_brake,[0,-1.10,-.20]),(16,pose(),[0,0,0])],16)
    # P1 Variation Skill 1: VAULT_SLAM (45 ticks: 24 windup + 3 active + 18 recovery)
    vault_crouch = pose(hips=[14, -8, 0], waist=[10, -6, 0], chest=[14, -8, 0], head=[-12, 8, 0],
        right_arm=[48, -14, 8], right_forearm=[-36, 0, -CARRY_ANGLE], right_hand=[-108, 0, 0],
        left_arm=[42, 12, 22], left_forearm=[-28, 0, CARRY_ANGLE],
        left_thigh=[36, 0, -7], right_thigh=[24, 0, 7], left_shin=[-52, 0, 0], right_shin=[-44, 0, 0])
    vault_air = attack_pose('overhead', loaded=True)
    vault_air['left_thigh'] = [42, 0, -9]; vault_air['right_thigh'] = [28, 0, 9]
    vault_air['left_shin'] = [-66, 0, 0]; vault_air['right_shin'] = [-56, 0, 0]
    vault_dive = attack_pose('overhead', snap=True)
    vault_dive['left_thigh'] = [32, 0, -8]; vault_dive['right_thigh'] = [-12, 0, 8]
    vault_slam_hit = attack_pose('overhead')
    vault_slam_follow = attack_pose('overhead', follow=True)
    finish('vault_slam', [
        (0, pose(), [0, 0, 0]), (4, vault_crouch, [0, -1.35, 0]), (11, vault_air, [0, 0.2, 0]),
        (19, vault_air, [0, 0.3, 0]), (22.5, vault_dive, [0, -0.2, 0]),
        (24, vault_slam_hit, [0, -1.35, -0.3]), (27, vault_slam_follow, [0, -1.25, -0.2]),
        (34, pose(hips=[8, 0, 0], chest=[8, 0, 0], left_thigh=[20, 0, -6], right_thigh=[-14, 0, 6]), [0, -0.5, 0]),
        (45, pose(), [0, 0, 0]),
    ], 45, metadata={'hit_ticks': [24], 'windup_ticks': [24], 'active_ticks': 3})
    # P1 Variation Skill 2: CLOUD_FEINT_THRUST (56 ticks: step0 hit=9..13, sidestep=14..18, step1 hit=33..37, end=56)
    sidestep = pose(hips=[11, -14, -7], waist=[8, -10, -5], chest=[12, -12, -7], neck=[-4, 8, 3], head=[-10, 14, 4],
        right_arm=[42, -16, 14], right_forearm=[-34, 0, -CARRY_ANGLE], right_hand=[-108, -8, -6],
        left_arm=[62, 16, 20], left_forearm=[-28, 0, CARRY_ANGLE], left_hand=[-20, 10, -8],
        left_thigh=[44, 0, -10], right_thigh=[-34, 0, 10], left_shin=[-42, 0, 0], right_shin=[-26, 0, 0])
    finish('cloud_feint_thrust', [
        (0, pose(), [0, 0, 0]), (5.5, attack_pose('horizontal', True, side=1), [0, -0.55, 0]),
        (7.6, attack_pose('horizontal', side=1, snap=True), [0, -0.62, -0.1]),
        (9, attack_pose('horizontal', side=1), [0, -0.52, -0.25]),
        (12.5, attack_pose('horizontal', follow=True, side=1), [0, -0.35, -0.15]),
        (15.5, sidestep, [0, -0.85, 0]), (18, sidestep, [0, -0.70, 0]),
        (26, ready, [0, -1.15, 0]), (31.5, ready, [0, -1.25, 0]),
        (33.2, thrust, [0, -0.95, -0.85]), (35.8, thrust_apex, [0, -1.05, -0.95]),
        (38, thrust, [0, -0.88, -0.65]), (42, thrust_brake, [0, -1.12, -0.20]),
        (56, pose(), [0, 0, 0]),
    ], 56, metadata={'step_start_ticks': [0, 18], 'hit_ticks': [9, 33], 'windup_ticks': [9, 15], 'active_ticks': 5, 'gap_ticks': 4})
    kneel=pose(hips=[12,0,0],waist=[9,0,0],chest=[12,0,0],neck=[9,0,0],head=[10,0,0],
        left_thigh=[44,0,-7],right_thigh=[-49,0,7],left_shin=[-68,0,0],right_shin=[-60,0,0],
        right_arm=[38,0,8],right_forearm=[-8,0,-CARRY_ANGLE],right_hand=[-28,0,0],
        left_arm=[38,0,8],left_forearm=[-13,0,18])
    finish('phase_clear',[(0,pose(),[0,0,0]),(8,pose(chest=[-15,0,0],head=[-12,0,0]),[0,-.7,0]),
        (25,kneel,[0,-7.5,0]),(50,kneel,[0,-7.5,0])],50)
    death=pose(root=[0,0,76],chest=[16,0,7],head=[18,0,0],right_arm=[29,0,8],left_arm=[20,0,8],
        left_thigh=[17,0,-9],right_thigh=[-13,0,7],left_shin=[-12,0,0],right_shin=[-19,0,0])
    finish('death',[(0,pose(),[0,0,0]),(8,kneel,[0,-7,0]),(20,death,[0,-1.2,0])],20)
    from generate_yang_jian_p2_animations import append_p2
    append_p2(pose,finish,attack_pose)
    from generate_yang_jian_p3_animations import append_p3
    append_p3(pose,finish,attack_pose)
    from generate_yang_jian_step_animations import append_steps
    append_steps(pose,finish)
    (ASSETS/'boss_animations').mkdir(parents=True,exist_ok=True)
    (ASSETS/'boss_animations/yang_jian.json').write_text(json.dumps({'format':1,'sampling_fps':60,'clips':CLIPS},separators=(',',':')),'utf8')
    (ROOT/'modeling/yang_jian-animation-source.json').write_text(json.dumps({'format':1,'axes':'Minecraft runtime, converted from Blockbench','clips':SOURCE},separators=(',',':')),'utf8')
    print('Baked',len(CLIPS),'clips; affected bones:',len(set().union(*(c['bones'] for c in CLIPS.values()))))
    for name,c in SOURCE.items():
        if c['timing']: print(name,c['timing'])


if __name__ == '__main__':
    main()
