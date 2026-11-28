"""Author and bake full articulated animations for the Sekiro Divine Dragon boss.

Generates 11 clips at 60 Hz compatible with AnimationTrack / AnimationPlayback:
  idle, intro_awaken, roar_push, slash_sweep, slash_vertical, slash_fan,
  slash_combo, tempest_barrage, summon_lightning, thunder_cataclysm, death_dissolve.
Also embeds sparse editable Blockbench animations back into modeling/divine_dragon.bbmodel.
"""
from pathlib import Path
import json
import math
import uuid
from animation_baker import bake

ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / 'modeling/divine_dragon.bbmodel'
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'

MODEL = json.loads(MODEL_PATH.read_text('utf8'))
GROUP_BY_NAME = {g['name']: g for g in MODEL['groups']}
ALL_BONES = set(GROUP_BY_NAME.keys())

BODY_BONES = [f'body_{i}' for i in range(22) if f'body_{i}' in ALL_BONES]
NECK_BONES = [f'neck_{i}' for i in range(1, 37) if f'neck_{i}' in ALL_BONES]
HEAD_MANE_BONES = sorted(n for n in ALL_BONES if n.startswith('mane_'))
NECK_RIBBON_BONES = sorted(n for n in ALL_BONES if n.startswith('neck_ribbon_'))


def to_runtime_rot(bb_rot):
    return [-bb_rot[0], bb_rot[1], -bb_rot[2]]


def to_runtime_pos(bb_pos):
    return [bb_pos[0], -bb_pos[1], bb_pos[2]]


def add_vec(a, b):
    return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]


def build_clip(name, ticks, loop, keyframe_fn, extra_fps=60):
    """Evaluate keyframe_fn(t_norm, tick) -> dict[bone_name, {'rotation': [rx,ry,rz], 'position': [px,py,pz]}]
    at every game tick (20 Hz) and bake cleanly to 60 Hz runtime tracks."""
    length = ticks / 20.0
    raw_bb = {}

    for tick in range(ticks + 1):
        t = tick / 20.0
        u = tick / ticks if ticks > 0 else 0.0
        frame = keyframe_fn(u, tick)
        for bone, channels in frame.items():
            if bone not in ALL_BONES:
                continue
            bmap = raw_bb.setdefault(bone, {})
            for ch, vals in channels.items():
                bmap.setdefault(ch, []).append({'time': t, 'values': list(vals), 'mode': 'catmullrom'})

    runtime_bones = {}
    for bone, channels in raw_bb.items():
        rt_ch = {}
        for ch, keys in channels.items():
            if loop and keys:
                keys[-1]['values'] = keys[0]['values'][:]
            converted = [
                {
                    'time': k['time'],
                    'values': to_runtime_rot(k['values']) if ch == 'rotation' else to_runtime_pos(k['values']),
                    'mode': k['mode'],
                }
                for k in keys
            ]
            rt_ch[ch] = bake(converted, length, loop=loop, fps=extra_fps)
        runtime_bones[bone] = rt_ch

    return {'length': round(length, 6), 'loop': loop, 'bones': runtime_bones}, raw_bb


def base_breathing(u, phase_offset=0.0, amp=1.0):
    s = math.sin((u * 2.0 * math.pi) + phase_offset)
    c = math.cos((u * 2.0 * math.pi) + phase_offset)
    out = {}
    for i, b in enumerate(BODY_BONES):
        if i >= 6:
            frac = (i - 6) / max(1, len(BODY_BONES) - 6)
            out[b] = {'rotation': [s * 0.45 * amp * frac, c * 0.25 * amp * frac, 0.0]}
    out['chest'] = {'rotation': [s * 1.4 * amp, c * 1.1 * amp, 0.0]}
    for idx, nb in enumerate(NECK_BONES):
        nf = idx / max(1, len(NECK_BONES) - 1)
        w = math.sin(u * 2.0 * math.pi - nf * 3.8 + phase_offset)
        w2 = math.cos(u * 2.0 * math.pi - nf * 2.6 + phase_offset)
        out[nb] = {'rotation': [w * 0.55 * amp, w2 * 0.45 * amp, w * 0.35 * amp]}
    out['head'] = {'rotation': [s * 2.2 * amp, c * 2.6 * amp, s * 1.2 * amp]}
    out['jaw'] = {'rotation': [max(0.0, s) * 3.0 * amp, 0.0, 0.0]}
    for k, mb in enumerate(HEAD_MANE_BONES):
        w = math.sin(u * 4.0 * math.pi + k * 0.7)
        out[mb] = {'rotation': [w * 4.0 * amp, w * 2.4 * amp, math.cos(u * 4.0 * math.pi + k) * 3.2 * amp]}
    for k, rb in enumerate(NECK_RIBBON_BONES):
        w = math.sin(u * 4.0 * math.pi + k * 0.9)
        out[rb] = {'rotation': [w * 4.5 * amp, w * 3.0 * amp, w * 2.5 * amp]}
    out['right_arm'] = {'rotation': [s * 2.0 * amp, c * 1.5 * amp, s * 1.5 * amp]}
    out['right_forearm'] = {'rotation': [c * 1.8 * amp, 0.0, s * 1.2 * amp]}
    out['right_hand'] = {'rotation': [0.0, 0.0, c * 1.5 * amp]}
    out['seven_branched_sword'] = {'rotation': [0.0, 0.0, s * 1.2 * amp]}
    out['left_arm_stump'] = {'rotation': [s * 1.2 * amp, 0.0, c * 1.0 * amp]}
    return out


def smooth_pulse(x, a, b, c, d):
    if x <= a or x >= d:
        return 0.0
    if x < b:
        t = (x - a) / max(1e-6, b - a)
        return t * t * (3 - 2 * t)
    if x <= c:
        return 1.0
    t = 1.0 - (x - c) / max(1e-6, d - c)
    return t * t * (3 - 2 * t)


def merge_rot(frame, bone, delta):
    cur = frame.setdefault(bone, {}).get('rotation', [0.0, 0.0, 0.0])
    frame[bone]['rotation'] = add_vec(cur, delta)


def anim_idle(u, tick):
    return base_breathing(u, amp=1.0)


def anim_intro(u, tick):
    f = base_breathing(u * 2.0, amp=0.7)
    coiled = smooth_pulse(u, -0.1, 0.0, 0.25, 0.58)
    roar = smooth_pulse(u, 0.42, 0.58, 0.78, 0.98)
    for b in BODY_BONES[8:]:
        merge_rot(f, b, [-1.4 * coiled + 0.9 * roar, 0.0, 0.0])
    merge_rot(f, 'chest', [-10.0 * coiled + 10.0 * roar, 0.0, 0.0])
    for idx, nb in enumerate(NECK_BONES):
        merge_rot(f, nb, [-1.1 * coiled + 0.85 * roar, 0.0, 0.0])
    merge_rot(f, 'head', [-16.0 * coiled + 20.0 * roar, 0.0, 0.0])
    merge_rot(f, 'jaw', [38.0 * roar, 0.0, 0.0])
    merge_rot(f, 'right_arm', [-24.0 * coiled + 18.0 * roar, -18.0 * coiled + 22.0 * roar, -22.0 * coiled + 18.0 * roar])
    merge_rot(f, 'right_forearm', [-18.0 * coiled, 0.0, 14.0 * roar])
    merge_rot(f, 'seven_branched_sword', [0.0, 0.0, -16.0 * coiled + 14.0 * roar])
    return f


def anim_roar_push(u, tick):
    f = base_breathing(u, amp=0.6)
    windup = smooth_pulse(u, 0.0, 0.26, 0.40, 0.55)
    blast = smooth_pulse(u, 0.40, 0.52, 0.74, 0.98)
    merge_rot(f, 'chest', [14.0 * windup - 12.0 * blast, 0.0, 0.0])
    for idx, nb in enumerate(NECK_BONES):
        if idx > 10:
            merge_rot(f, nb, [0.9 * windup - 0.8 * blast, 0.0, 0.0])
    merge_rot(f, 'head', [18.0 * windup - 10.0 * blast, 0.0, 0.0])
    merge_rot(f, 'jaw', [8.0 * windup + 42.0 * blast, 0.0, 0.0])
    merge_rot(f, 'right_arm', [12.0 * windup - 10.0 * blast, 24.0 * windup - 16.0 * blast, 22.0 * windup - 10.0 * blast])
    merge_rot(f, 'seven_branched_sword', [0.0, 0.0, 16.0 * windup - 12.0 * blast])
    return f


def anim_slash_sweep(u, tick):
    # Windup 0..22 ticks (u=0..0.42), strike 22..32 (u=0.42..0.62), recovery 32..52
    f = base_breathing(u, amp=0.45)
    wind = smooth_pulse(u, 0.0, 0.30, 0.42, 0.50)
    swing = smooth_pulse(u, 0.42, 0.54, 0.68, 0.98)
    merge_rot(f, 'chest', [6.0 * wind - 12.0 * swing, -34.0 * wind + 44.0 * swing, -6.0 * wind + 8.0 * swing])
    for b in BODY_BONES[10:]:
        merge_rot(f, b, [0.4 * wind - 0.8 * swing, -1.8 * wind + 2.4 * swing, 0.0])
    merge_rot(f, 'head', [4.0 * wind - 8.0 * swing, 24.0 * wind - 26.0 * swing, 0.0])
    merge_rot(f, 'jaw', [16.0 * swing, 0.0, 0.0])
    merge_rot(f, 'right_arm', [-24.0 * wind + 28.0 * swing, -52.0 * wind + 68.0 * swing, -24.0 * wind + 36.0 * swing])
    merge_rot(f, 'right_forearm', [-18.0 * wind + 14.0 * swing, -22.0 * wind + 32.0 * swing, -12.0 * wind + 16.0 * swing])
    merge_rot(f, 'right_hand', [12.0 * wind - 18.0 * swing, 0.0, -34.0 * wind + 56.0 * swing])
    merge_rot(f, 'seven_branched_sword', [18.0 * wind + 64.0 * swing, 0.0, -48.0 * wind + 62.0 * swing])
    return f


def anim_slash_vertical(u, tick):
    # Windup 0..24 ticks (u=0..0.46), cleave 24..34 (u=0.46..0.65), recovery 34..52
    f = base_breathing(u, amp=0.45)
    raise_p = smooth_pulse(u, 0.0, 0.32, 0.46, 0.54)
    cleave = smooth_pulse(u, 0.45, 0.56, 0.72, 0.98)
    merge_rot(f, 'chest', [16.0 * raise_p - 22.0 * cleave, -10.0 * raise_p + 12.0 * cleave, 0.0])
    for b in BODY_BONES[10:]:
        merge_rot(f, b, [0.8 * raise_p - 1.2 * cleave, 0.0, 0.0])
    merge_rot(f, 'head', [12.0 * raise_p - 16.0 * cleave, 0.0, 0.0])
    merge_rot(f, 'jaw', [22.0 * cleave, 0.0, 0.0])
    merge_rot(f, 'right_arm', [68.0 * raise_p - 58.0 * cleave, -12.0 * raise_p + 18.0 * cleave, 36.0 * raise_p - 18.0 * cleave])
    merge_rot(f, 'right_forearm', [22.0 * raise_p - 26.0 * cleave, 0.0, 14.0 * raise_p - 10.0 * cleave])
    merge_rot(f, 'right_hand', [24.0 * raise_p - 46.0 * cleave, 0.0, 0.0])
    merge_rot(f, 'seven_branched_sword', [28.0 * raise_p - 78.0 * cleave, 0.0, 10.0 * raise_p - 8.0 * cleave])
    return f


def anim_slash_fan(u, tick):
    f = base_breathing(u, amp=0.45)
    draw = smooth_pulse(u, 0.0, 0.28, 0.40, 0.48)
    flick = smooth_pulse(u, 0.40, 0.54, 0.70, 0.98)
    merge_rot(f, 'chest', [-6.0 * draw + 6.0 * flick, 28.0 * draw - 36.0 * flick, 6.0 * draw - 8.0 * flick])
    merge_rot(f, 'head', [0.0, -18.0 * draw + 20.0 * flick, 0.0])
    merge_rot(f, 'right_arm', [18.0 * draw + 12.0 * flick, 44.0 * draw - 54.0 * flick, 28.0 * draw - 34.0 * flick])
    merge_rot(f, 'right_forearm', [-14.0 * draw + 18.0 * flick, 24.0 * draw - 34.0 * flick, 16.0 * draw - 24.0 * flick])
    merge_rot(f, 'seven_branched_sword', [-24.0 * draw + 34.0 * flick, 0.0, 44.0 * draw - 56.0 * flick])
    return f


def anim_slash_combo(u, tick):
    # 88 ticks (4.4s): vertical hit@22, sweep hit@44, fan/cross hit@66
    f = base_breathing(u * 2.0, amp=0.5)
    t = tick
    w1 = smooth_pulse(t, 0, 14, 21, 26)
    h1 = smooth_pulse(t, 20, 24, 30, 36)
    w2 = smooth_pulse(t, 30, 38, 43, 47)
    h2 = smooth_pulse(t, 42, 46, 52, 58)
    w3 = smooth_pulse(t, 54, 61, 65, 69)
    h3 = smooth_pulse(t, 64, 68, 76, 87)

    merge_rot(f, 'chest', [
        14 * w1 - 18 * h1 + 6 * w2 - 10 * h2 + 12 * w3 - 18 * h3,
        -10 * w1 + 10 * h1 - 36 * w2 + 46 * h2 + 32 * w3 - 44 * h3,
        0.0
    ])
    merge_rot(f, 'jaw', [18 * h1 + 20 * h2 + 28 * h3, 0.0, 0.0])
    merge_rot(f, 'right_arm', [
        62 * w1 - 54 * h1 - 22 * w2 + 28 * h2 + 42 * w3 - 48 * h3,
        -12 * w1 + 16 * h1 - 50 * w2 + 66 * h2 + 38 * w3 - 54 * h3,
        32 * w1 - 16 * h1 - 24 * w2 + 36 * h2 + 32 * w3 - 34 * h3
    ])
    merge_rot(f, 'seven_branched_sword', [
        26 * w1 - 74 * h1 + 18 * w2 + 58 * h2 + 24 * w3 - 68 * h3,
        0.0,
        8 * w1 - 46 * w2 + 58 * h2 + 40 * w3 - 54 * h3
    ])
    return f


def anim_tempest_barrage(u, tick):
    # 116 ticks (5.8s): 5 escalating slashes at ticks 22, 42, 60, 78, 96
    f = base_breathing(u * 3.0, amp=0.9)
    t = tick
    hits = [22, 42, 60, 78, 96]
    cx, cy, ax, ay, az, sx, sz = 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    jaw = 0.0
    for idx, ht in enumerate(hits):
        w = smooth_pulse(t, ht - 14, ht - 5, ht - 1, ht + 2)
        h = smooth_pulse(t, ht - 1, ht + 3, ht + 8, ht + 15)
        jaw += 22.0 * h
        if idx % 2 == 0:
            cy += -34 * w + 46 * h
            ax += -20 * w + 26 * h
            ay += -48 * w + 64 * h
            az += -20 * w + 34 * h
            sx += 16 * w + 56 * h
            sz += -44 * w + 58 * h
        else:
            cx += 15 * w - 20 * h
            ax += 64 * w - 56 * h
            az += 32 * w - 16 * h
            sx += 26 * w - 74 * h
    merge_rot(f, 'chest', [cx, cy, 0.0])
    merge_rot(f, 'jaw', [min(42.0, jaw), 0.0, 0.0])
    merge_rot(f, 'right_arm', [ax, ay, az])
    merge_rot(f, 'seven_branched_sword', [sx, 0.0, sz])
    return f


def anim_summon_lightning(u, tick):
    # 56 ticks (2.8s): raise sword high to sky (0..22), pulse strike (22..34), settle
    f = base_breathing(u, amp=0.5)
    charge = smooth_pulse(u, 0.0, 0.28, 0.42, 0.50)
    call = smooth_pulse(u, 0.42, 0.54, 0.72, 0.98)
    merge_rot(f, 'chest', [14.0 * charge - 10.0 * call, -12.0 * charge, 0.0])
    for idx, nb in enumerate(NECK_BONES):
        if idx > 12:
            merge_rot(f, nb, [0.75 * charge - 0.4 * call, 0.0, 0.0])
    merge_rot(f, 'head', [18.0 * charge - 8.0 * call, 0.0, 0.0])
    merge_rot(f, 'jaw', [26.0 * charge + 18.0 * call, 0.0, 0.0])
    merge_rot(f, 'right_arm', [74.0 * charge - 28.0 * call, -18.0 * charge + 10.0 * call, 42.0 * charge - 14.0 * call])
    merge_rot(f, 'right_forearm', [26.0 * charge - 18.0 * call, 0.0, 18.0 * charge])
    merge_rot(f, 'seven_branched_sword', [12.0 * charge - 42.0 * call, 0.0, 8.0 * charge])
    return f


def anim_thunder_cataclysm(u, tick):
    # 96 ticks (4.8s): sky-filling golden thunder cataclysm with 3 sky-call pulses at 24, 52, 80
    f = base_breathing(u * 2.0, amp=1.1)
    t = tick
    hold = smooth_pulse(t, 0, 16, 82, 95)
    p1 = smooth_pulse(t, 20, 24, 28, 36)
    p2 = smooth_pulse(t, 48, 52, 56, 64)
    p3 = smooth_pulse(t, 76, 80, 84, 92)
    pulse = p1 + p2 + p3

    merge_rot(f, 'chest', [14.0 * hold - 8.0 * pulse, 0.0, 0.0])
    for idx, nb in enumerate(NECK_BONES):
        merge_rot(f, nb, [0.55 * hold, 0.0, math.sin(u * 6.0 * math.pi + idx * 0.25) * 0.8 * hold])
    merge_rot(f, 'head', [22.0 * hold - 6.0 * pulse, 0.0, 0.0])
    merge_rot(f, 'jaw', [24.0 * hold + 20.0 * pulse, 0.0, 0.0])
    merge_rot(f, 'right_arm', [78.0 * hold - 22.0 * pulse, -14.0 * hold, 46.0 * hold - 12.0 * pulse])
    merge_rot(f, 'right_forearm', [24.0 * hold - 14.0 * pulse, 0.0, 16.0 * hold])
    merge_rot(f, 'seven_branched_sword', [16.0 * hold - 30.0 * pulse, 0.0, 6.0 * hold])
    return f


def anim_death_dissolve(u, tick):
    # 120 ticks (6.0s): solemn droop of the colossal neck and sword, serene fade into dust
    # u=0..0.55: gradual solemn droop; u=0.55..1.0: peaceful motionless descent as dust rises
    d = smooth_pulse(u, 0.0, 0.56, 1.0, 1.5)
    f = {}
    for i, b in enumerate(BODY_BONES):
        if i >= 6:
            frac = (i - 6) / max(1, len(BODY_BONES) - 6)
            f[b] = {'rotation': [-1.45 * d * frac, 0.0, 0.0]}
    f['chest'] = {'rotation': [-12.0 * d, 0.0, -4.0 * d]}
    # Gracefully uncoil and droop the colossal 36-segment neck downward
    for idx, nb in enumerate(NECK_BONES):
        nf = idx / max(1, len(NECK_BONES) - 1)
        droop_x = -2.25 * d * (0.4 + 0.6 * nf)
        droop_z = -0.55 * d * math.sin(nf * math.pi)
        f[nb] = {'rotation': [droop_x, 0.0, droop_z]}
    f['head'] = {'rotation': [-24.0 * d, 0.0, -6.0 * d]}
    f['jaw'] = {'rotation': [14.0 * d, 0.0, 0.0]}
    f['right_arm'] = {'rotation': [-42.0 * d, 16.0 * d, -34.0 * d]}
    f['right_forearm'] = {'rotation': [-26.0 * d, 0.0, -14.0 * d]}
    f['right_hand'] = {'rotation': [-18.0 * d, 0.0, -12.0 * d]}
    f['seven_branched_sword'] = {'rotation': [-32.0 * d, 0.0, -18.0 * d]}
    f['left_arm_stump'] = {'rotation': [-12.0 * d, 0.0, 8.0 * d]}
    for k, mb in enumerate(HEAD_MANE_BONES):
        drift = math.sin(u * 2.0 * math.pi + k * 0.6) * (1.0 - 0.6 * d)
        f[mb] = {'rotation': [14.0 * d + drift * 2.5, drift * 1.5, 0.0]}
    for k, rb in enumerate(NECK_RIBBON_BONES):
        f[rb] = {'rotation': [10.0 * d, 0.0, 0.0]}
    return f


def embed_bb_animations(bb_anims):
    animations = []
    for name, (length, loop, bones_raw) in bb_anims.items():
        animators = {}
        for bone_name, channels in bones_raw.items():
            g = GROUP_BY_NAME.get(bone_name)
            if not g:
                continue
            kfs = []
            for ch, keys in channels.items():
                step = max(1, len(keys) // 16)
                picked = keys[::step]
                if keys[-1] not in picked:
                    picked.append(keys[-1])
                for k in picked:
                    v = k['values']
                    kfs.append({
                        'channel': ch,
                        'data_points': [{'x': str(round(v[0], 3)), 'y': str(round(v[1], 3)), 'z': str(round(v[2], 3))}],
                        'uuid': str(uuid.uuid4()),
                        'time': round(k['time'], 4),
                        'color': -1,
                        'interpolation': 'catmullrom',
                    })
            animators[g['uuid']] = {
                'name': bone_name,
                'type': 'bone',
                'keyframes': kfs,
            }
        animations.append({
            'uuid': str(uuid.uuid4()),
            'name': name,
            'loop': 'loop' if loop else 'hold' if name == 'death_dissolve' else 'once',
            'override': False,
            'length': round(length, 4),
            'snapping': 20,
            'selected': name == 'idle',
            'anim_time_update': '',
            'blend_weight': '',
            'start_delay': '',
            'loop_delay': '',
            'animators': animators,
        })
    MODEL['animations'] = animations
    MODEL_PATH.write_text(json.dumps(MODEL, separators=(',', ':')), 'utf8')


def main():
    specs = [
        ('idle', 80, True, anim_idle),
        ('intro_awaken', 80, False, anim_intro),
        ('roar_push', 48, False, anim_roar_push),
        ('slash_sweep', 52, False, anim_slash_sweep),
        ('slash_vertical', 52, False, anim_slash_vertical),
        ('slash_fan', 52, False, anim_slash_fan),
        ('slash_combo', 88, False, anim_slash_combo),
        ('tempest_barrage', 116, False, anim_tempest_barrage),
        ('summon_lightning', 56, False, anim_summon_lightning),
        ('thunder_cataclysm', 96, False, anim_thunder_cataclysm),
        ('death_dissolve', 120, False, anim_death_dissolve),
    ]
    clips = {}
    bb_anims = {}
    for name, ticks, loop, fn in specs:
        rt_clip, raw_bb = build_clip(name, ticks, loop, fn, extra_fps=30)
        clips[name] = rt_clip
        bb_anims[name] = (ticks / 20.0, loop, raw_bb)

    (ASSETS / 'boss_animations').mkdir(parents=True, exist_ok=True)
    out_path = ASSETS / 'boss_animations/divine_dragon.json'
    out_path.write_text(json.dumps({'format': 1, 'sampling_fps': 30, 'clips': clips}, separators=(',', ':')), 'utf8')
    embed_bb_animations(bb_anims)
    size_kb = out_path.stat().st_size / 1024.0
    print(f'Baked {len(clips)} Divine Dragon clips -> {out_path.name} ({size_kb:.1f} KB); embedded in {MODEL_PATH.name}')


if __name__ == '__main__':
    main()
