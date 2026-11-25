"""Author articulated Silksong clockwork-mantis clips using the real Blockbench MCP server.

Verified Blockbench + Minecraft axis signs for Cogwork Dancer (-Z front, +Z back):
- body / chest / abdomen / neck / head (+Y up):   rx < 0 leans FORWARD (-Z),  rx > 0 arches BACKWARD (+Z).
- legs / knees / arms / tails (-Y down):          rx > 0 swings FORWARD (-Z), rx < 0 sweeps/bends BACKWARD (+Z).
- knees ALWAYS bend naturally backward (rx <= 0).
- left_arm (-X) / right_arm (+X) raptorial mantis forelimbs:
    arm_z:   left < 0 / right > 0 raises OUT & UP;  left > 0 / right < 0 tucks IN toward ribs.
    arm_y:   left < 0 / right > 0 folds claws INWARD across chest (mantis guard);
             left > 0 / right < 0 sweeps scythes WIDE BACK (wing-cleave).
    elbow_y: left < 0 / right > 0 tightens raptorial Z-hook; left > 0 / right < 0 opens scythe blade wide.
"""
import json, math
from pathlib import Path
from blockbench_mcp import request

ROOT = Path(__file__).resolve().parents[1]

def call(name, args):
    r = request('tools/call', {'name': name, 'arguments': args})
    if r.get('isError'):
        raise RuntimeError(r)
    return r

def F(t, rotation=None, position=None, scale=None):
    d = {'time': round(t, 4)}
    if rotation is not None:
        d['rotation'] = [round(v, 3) for v in rotation]
    if position is not None:
        d['position'] = [round(v, 3) for v in position]
    if scale is not None:
        d['scale'] = [round(v, 3) for v in scale]
    return d

def pair(v, mirror_sign=1):
    if isinstance(v, (list, tuple)):
        return float(v[0]), float(v[1])
    return float(v), float(v) * mirror_sign

# Semantic helper:
#   lean > 0   means LEAN FORWARD (-Z) in intuitive human/mantis terms (converted to -lean in BB rx!).
#   knees > 0  means NATURAL BACKWARD KNEE BEND (converted to -knee in BB rx!).
#   legs > 0   means SWING LEG FORWARD (-Z); legs < 0 means TRAIL LEG BACKWARD (+Z).
#   reach > 0  means THRUST/REACH ARM FORWARD (-Z); reach < 0 means SWEEP WING-BLADE BACKWARD (+Z).
#   arm_lift > 0 raises arms OUT/UP (left -Z, right +Z); < 0 tucks arms down against thorax.
#   arm_fold > 0 folds mantis scythes INWARD across chest (left -Y, right +Y); < 0 spreads scythes WIDE.
#   elbow_fold > 0 tightens mantis Z-hook inward; < 0 extends scythe open.
#   tail_trail > 0 streams coattails BACKWARD (+Z, converted to -tail_trail in BB rx!).
def pose(
    t,
    lean=0.0,
    twist=0.0,
    roll=0.0,
    drop=0.0,
    chest_lean=None,
    chest_twist=0.0,
    chest_roll=0.0,
    abdomen_twist=0.0,
    pelvis_twist=0.0,
    arm_lift=0.0,
    arm_fold=14.0,
    reach=0.0,
    elbow_fold=16.0,
    elbow_pitch=0.0,
    wrist_pitch=0.0,
    wrist_fold=0.0,
    blade_pitch=0.0,
    legs=(-8.0, -14.0),
    leg_splay=4.0,
    knees=(22.0, 30.0),
    ankles=None,
    spread=0.0,
    tail_trail=0.0,
    tail_whip=0.0,
    head=0.0,
    head_yaw=0.0,
    head_roll=0.0,
    gear_deg=None,
    gear_speed=110.0,
    halo_spin=None,
    energy=1.0,
):
    l_lift, r_lift = pair(arm_lift, 1)
    l_fold, r_fold = pair(arm_fold, 1)
    l_reach, r_reach = pair(reach, 1)
    l_efold, r_efold = pair(elbow_fold, 1)
    l_epitch, r_epitch = pair(elbow_pitch, 1)
    l_wpitch, r_wpitch = pair(wrist_pitch, 1)
    l_wfold, r_wfold = pair(wrist_fold, 1)
    l_bpitch, r_bpitch = pair(blade_pitch, 1)
    l_leg, r_leg = pair(legs, 1)
    l_knee, r_knee = pair(knees, 1)
    l_ls, r_ls = pair(leg_splay, 1)
    if ankles is None:
        # Pointed digitigrade mantis toes (en pointe grace)
        l_ank = 22.0 + l_knee * 0.35 - l_leg * 0.25
        r_ank = 22.0 + r_knee * 0.35 - r_leg * 0.25
    else:
        l_ank, r_ank = pair(ankles, 1)

    c_lean = lean * 0.24 if chest_lean is None else chest_lean
    # Head stays razor-level locked on the target during forward lunges (classic mantis gaze!)
    neck_rx = lean * 0.36 - head * 0.42
    head_rx = lean * 0.46 - head * 0.58

    p = {
        'body': {
            'rotation': [-lean, twist, roll],
            'position': [0.0, drop, 0.0],
        },
        'chest': {
            'rotation': [
                -c_lean,
                chest_twist + math.sin(t * 3.0) * 1.4 * energy,
                chest_roll - math.sin(t * 2.0) * 1.0 * energy,
            ]
        },
        'abdomen': {
            'rotation': [
                lean * 0.16,
                abdomen_twist - math.sin(t * 3.0 - 0.35) * 1.8 * energy,
                -roll * 0.20 + math.sin(t * 2.0 - 0.35) * 1.1 * energy,
            ]
        },
        'pelvis': {
            'rotation': [
                lean * 0.10,
                pelvis_twist + math.sin(t * 3.0 - 0.65) * 2.0 * energy,
                -roll * 0.15 + math.sin(t * 2.6) * 1.5 * energy,
            ]
        },
        'neck': {
            'rotation': [
                neck_rx,
                head_yaw * 0.42 - chest_twist * 0.35,
                head_roll * 0.40 - roll * 0.28,
            ]
        },
        'head': {
            'rotation': [
                head_rx,
                head_yaw * 0.58 - chest_twist * 0.48 + math.sin(t * 2.4) * 1.5 * energy,
                head_roll * 0.60 - roll * 0.34 + math.sin(t * 2.4 - 0.2) * 1.2 * energy,
            ]
        },
        'crown': {
            'rotation': [
                lean * 0.05 + math.sin(t * 5.0 - 0.7) * 1.0 * energy,
                0.0,
                math.sin(t * 3.0 - 0.8) * 0.9 * energy,
            ]
        },
        'gear': {
            'rotation': [0.0, 0.0, t * gear_speed if gear_deg is None else gear_deg]
        },
        'core': {
            'scale': [
                1.0 + 0.045 * math.sin(t * 5.2),
                1.0 + 0.045 * math.sin(t * 5.2),
                1.0,
            ]
        },
        'skirt': {
            'rotation': [
                -lean * 0.22 - tail_trail * 0.38,
                pelvis_twist * 0.40 + math.sin(t * 3.0 - 0.5) * 2.0 * energy,
                tail_whip * 0.40,
            ]
        },
        'halo': {
            'rotation': [
                0.0,
                0.0,
                (math.sin(t * 1.8) * 3.0 * energy) if halo_spin is None else halo_spin,
            ]
        },
    }

    sides = [
        ('left', -1, l_lift, l_fold, l_reach, l_efold, l_epitch, l_wpitch, l_wfold, l_bpitch, l_leg, l_ls, l_knee, l_ank),
        ('right', 1, r_lift, r_fold, r_reach, r_efold, r_epitch, r_wpitch, r_wfold, r_bpitch, r_leg, r_ls, r_knee, r_ank),
    ]
    for i, (s, sign, lift, fold, rc, efold, epitch, wpitch, wfold, bpitch, lg, ls, kn, an) in enumerate(sides):
        p[s + '_shoulder'] = {
            'rotation': [
                rc * 0.14 + math.sin(t * 4.0 - 0.3) * 1.2 * energy,
                sign * fold * 0.10,
                sign * (lift * 0.18 + spread * 0.14 + math.sin(t * 3.8) * 1.0 * energy),
            ]
        }
        # Mantis raptorial upper forelimb:
        #   rx: + forward / - backward
        #   ry: sign * fold (left -Y, right +Y folds INWARD toward chest!)
        #   rz: sign * lift (left -Z, right +Z raises OUT & UP!)
        p[s + '_arm'] = {
            'rotation': [
                rc,
                sign * fold,
                sign * lift + math.sin(t * 3.2 + i * 0.8) * 1.8 * energy,
            ]
        }
        p[s + '_elbow'] = {
            'rotation': [
                epitch,
                sign * efold,
                -sign * (lift * 0.16),
            ]
        }
        p[s + '_wrist'] = {
            'rotation': [
                wpitch,
                sign * wfold,
                -sign * (efold * 0.22),
            ]
        }
        p[s + '_blade'] = {
            'rotation': [
                bpitch,
                0.0,
                sign * math.sin(t * 5.0 - 0.2) * 1.4 * energy,
            ]
        }
        # Digitigrade spindly mantis legs:
        #   leg rx:  + forward / - trailing back
        #   knee rx: ALWAYS <= 0 (natural backward flexion!)
        #   ankle rx: pointed insect toes (en pointe)
        p[s + '_leg'] = {'rotation': [lg, 0.0, sign * (ls + spread * 0.18)]}
        p[s + '_knee'] = {
            'rotation': [-max(0.0, kn), sign * math.sin(t * 3.0 + i) * 1.4 * energy, -sign * 1.5]
        }
        p[s + '_ankle'] = {'rotation': [an, 0.0, sign * 1.5]}
        p[s + '_hip_plate'] = {
            'rotation': [
                -lean * 0.18 - tail_trail * 0.24,
                sign * spread * 0.28,
                sign * (spread * 0.36 + math.sin(t * 3.0 - 0.2) * 1.5 * energy) + tail_whip * 0.22,
            ]
        }
        for j in range(3):
            lag = j * 0.65
            p[s + '_tail_' + str(j)] = {
                'rotation': [
                    -lean * (0.10 + j * 0.02) - tail_trail * (0.24 + j * 0.08)
                    + math.sin(t * 5.2 - lag + i * 0.45) * (1.8 + j * 0.45) * energy,
                    sign * math.sin(t * 3.0 - j * 0.5) * 1.6 * energy + tail_whip * (0.26 + j * 0.14),
                    sign * (spread * (0.15 + j * 0.04) + math.sin(t * 4.0 - j * 0.7) * 1.1 * energy)
                    + tail_whip * (0.40 + j * 0.14),
                ]
            }
    return p


clips = []

def clip(name, length, frames, loop=False, linear=(), gear_turns=None):
    bones = {}
    for t, args in frames:
        p = pose(t, **args)
        for b, channels in p.items():
            bones.setdefault(b, []).append(F(t, **channels))
    if loop:
        for b, keys in bones.items():
            keys[-1] = {**keys[0], 'time': round(length, 4)}
        turns = 1 if gear_turns is None else gear_turns
        for key in bones['gear']:
            key['rotation'] = [0.0, 0.0, round(360.0 * turns * key['time'] / length, 3)]
    clips.append((name, length, loop, bones, list(linear)))


# 1. Idle: regal clockwork mantis hover — upright thorax, raptorial scythes folded in formal guard, en pointe spindly legs
clip(
    'idle',
    2.4,
    [
        (
            i * 0.3,
            dict(
                drop=0.40 - 0.36 * math.cos(i * math.pi / 4),
                lean=3.0 + 1.5 * math.sin(i * math.pi / 4),
                roll=1.8 * math.sin(i * math.pi / 4 + 0.5),
                chest_twist=2.8 * math.sin(i * math.pi / 4),
                arm_lift=(-4.0 + 2.5 * math.sin(i * math.pi / 4), -4.0 - 2.5 * math.sin(i * math.pi / 4)),
                arm_fold=(18.0 + 3.0 * math.cos(i * math.pi / 4), 18.0 - 3.0 * math.cos(i * math.pi / 4)),
                reach=(8.0, 10.0),
                elbow_fold=(22.0, 20.0),
                wrist_pitch=(-10.0, -10.0),
                legs=(-6.0 - 5.0 * math.sin(i * math.pi / 4), -14.0 + 5.0 * math.sin(i * math.pi / 4)),
                knees=(24.0 + 6.0 * math.sin(i * math.pi / 4), 34.0 - 6.0 * math.sin(i * math.pi / 4)),
                spread=4.0,
                tail_trail=6.0,
                energy=0.85,
            ),
        )
        for i in range(9)
    ],
    loop=True,
)

# 2. Dash Windup (1.0s normalized) & Dash (1.8s = 36 ticks; server DASH_TRAVEL_TICKS = 12 = 0.60s!)
# Classic Silksong mantis pounce:
#   - Coil: crouched FORWARD, twin raptorial sickles drawn tight & high across chest, spindly legs folded sharply beneath
#   - Launch & Streak (0.00..0.56s): streamlined horizontal mantis dart — body leaning FORWARD 46°, head level staring ahead,
#       twin scythes swept RAZOR-BACK like folded insect wings (or staggered piercing wing-slash), BOTH legs trailing cleanly behind!
#   - Brake Cut (0.60s, exact server stop!): crisp staccato upright snap + explosive X-scissor mantis double-scythe cross-slash!
mantis_coil = dict(
    lean=22.0,
    twist=-14.0,
    roll=-5.0,
    chest_twist=-10.0,
    drop=-1.45,
    arm_lift=(10.0, 4.0),
    arm_fold=(34.0, 28.0),
    reach=(22.0, 14.0),
    elbow_fold=(36.0, 32.0),
    elbow_pitch=(16.0, 14.0),
    wrist_pitch=(-18.0, -14.0),
    legs=(30.0, -24.0),
    knees=(68.0, 56.0),
    spread=8.0,
    tail_trail=14.0,
    head=4.0,
    head_yaw=16.0,
    energy=1.25,
)
mantis_coil_peak = dict(
    **{
        **mantis_coil,
        'lean': 28.0,
        'twist': -18.0,
        'drop': -1.85,
        'arm_lift': (14.0, 8.0),
        'arm_fold': (40.0, 34.0),
        'reach': (28.0, 18.0),
        'elbow_fold': (44.0, 38.0),
        'legs': (36.0, -28.0),
        'knees': (78.0, 64.0),
        'gear_deg': -48.0,
    }
)
mantis_dash_launch = dict(
    lean=48.0,
    twist=10.0,
    roll=8.0,
    chest_twist=12.0,
    drop=-0.25,
    arm_lift=(26.0, 34.0),
    arm_fold=(-26.0, -34.0),
    reach=(-32.0, -44.0),
    elbow_fold=(-16.0, -20.0),
    elbow_pitch=(-18.0, -22.0),
    wrist_pitch=(18.0, 22.0),
    blade_pitch=(14.0, 16.0),
    legs=(-34.0, -22.0),
    knees=(38.0, 52.0),
    spread=18.0,
    tail_trail=38.0,
    tail_whip=-10.0,
    head=2.0,
    head_yaw=-10.0,
    gear_deg=90.0,
    energy=2.0,
)
mantis_dash_streak_a = dict(
    lean=52.0,
    twist=6.0,
    roll=11.0,
    chest_twist=8.0,
    drop=-0.35,
    arm_lift=(32.0, 28.0),
    arm_fold=(-32.0, -28.0),
    reach=(-44.0, -38.0),
    elbow_fold=(-22.0, -18.0),
    elbow_pitch=(-24.0, -20.0),
    wrist_pitch=(22.0, 20.0),
    blade_pitch=(18.0, 16.0),
    legs=(-42.0, -28.0),
    knees=(32.0, 54.0),
    spread=20.0,
    tail_trail=44.0,
    tail_whip=-8.0,
    head=2.0,
    gear_deg=260.0,
    energy=1.5,
)
mantis_dash_streak_b = dict(
    lean=50.0,
    twist=-8.0,
    roll=-10.0,
    chest_twist=-10.0,
    drop=-0.30,
    arm_lift=(28.0, 34.0),
    arm_fold=(-24.0, -34.0),
    reach=(-36.0, -46.0),
    elbow_fold=(-16.0, -22.0),
    elbow_pitch=(-18.0, -24.0),
    wrist_pitch=(20.0, 24.0),
    blade_pitch=(16.0, 18.0),
    legs=(-26.0, -44.0),
    knees=(52.0, 34.0),
    spread=22.0,
    tail_trail=42.0,
    tail_whip=10.0,
    head=2.0,
    gear_deg=420.0,
    energy=1.6,
)
# Exact t=0.60s braking stop: body snaps upright-arches slightly, BOTH mantis sickles whip FORWARD-INWARD in a crisp X-scissor cross-cut!
mantis_dash_brake = dict(
    lean=-8.0,
    twist=20.0,
    roll=-8.0,
    chest_twist=18.0,
    pelvis_twist=-8.0,
    drop=-1.10,
    arm_lift=(18.0, 12.0),
    arm_fold=(46.0, 42.0),
    reach=(48.0, 42.0),
    elbow_fold=(34.0, 28.0),
    elbow_pitch=(12.0, 10.0),
    wrist_pitch=(-16.0, -14.0),
    blade_pitch=(-16.0, -14.0),
    legs=(34.0, -18.0),
    knees=(58.0, 40.0),
    spread=26.0,
    tail_trail=22.0,
    tail_whip=16.0,
    head=-4.0,
    head_yaw=-20.0,
    gear_deg=540.0,
    energy=1.9,
)
mantis_dash_flourish = dict(
    lean=4.0,
    twist=8.0,
    roll=-3.0,
    chest_twist=6.0,
    drop=-0.35,
    arm_lift=(30.0, 30.0),
    arm_fold=(-24.0, -24.0),
    reach=(-10.0, -10.0),
    elbow_fold=(-10.0, -10.0),
    wrist_pitch=(8.0, 8.0),
    legs=(10.0, -18.0),
    knees=(32.0, 36.0),
    spread=18.0,
    tail_trail=14.0,
    head_yaw=-8.0,
    gear_deg=610.0,
    energy=1.2,
)

clip(
    'dash_windup',
    1.0,
    [
        (0.0, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, gear_deg=0.0)),
        (0.24, dict(lean=-6.0, twist=8.0, drop=0.35, arm_lift=(16.0, 16.0), arm_fold=(8.0, 8.0), reach=(-6.0, -6.0), elbow_fold=(18.0, 18.0), legs=(-10.0, -18.0), knees=(32.0, 42.0), gear_deg=18.0)),
        (0.62, {**mantis_coil, 'gear_deg': -20.0}),
        (0.86, {**mantis_coil_peak, 'gear_deg': -40.0}),
        (1.0, mantis_coil_peak),
    ],
)

clip(
    'dash',
    1.8,
    [
        (0.00, mantis_coil_peak),
        (0.08, mantis_dash_launch),
        (0.26, mantis_dash_streak_a),
        (0.45, mantis_dash_streak_b),
        (0.60, mantis_dash_brake),
        (0.80, mantis_dash_flourish),
        (1.12, dict(lean=6.0, twist=-4.0, drop=0.15, arm_lift=(6.0, 6.0), arm_fold=(16.0, 16.0), reach=(6.0, 6.0), elbow_fold=(22.0, 22.0), legs=(-4.0, -14.0), knees=(26.0, 34.0), spread=8.0, tail_trail=8.0, gear_deg=660.0, energy=1.0)),
        (1.48, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, legs=(-6.0, -14.0), knees=(24.0, 32.0), gear_deg=700.0, energy=0.9)),
        (1.80, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, gear_deg=720.0)),
    ],
)

# 3. Slam Windup (1.0s normalized) & Slam (2.1s = 42 ticks; server dive active 0..8 = 0.00..0.40s!)
# Mantis aerial poise: arched regal hover with twin sickles raised & crossed high overhead like clock hands at XII,
# then needle-straight downward mantis cleave into deep insect crouch tearing the scythes wide open at impact!
mantis_slam_rise = dict(
    lean=-12.0,
    twist=-6.0,
    drop=0.75,
    arm_lift=(58.0, 58.0),
    arm_fold=(22.0, 22.0),
    reach=(26.0, 26.0),
    elbow_fold=(34.0, 34.0),
    elbow_pitch=(20.0, 20.0),
    wrist_pitch=(-24.0, -24.0),
    legs=(-14.0, -24.0),
    knees=(76.0, 86.0),
    spread=12.0,
    tail_trail=8.0,
    head=-14.0,
    energy=1.45,
)
mantis_slam_zenith = dict(
    **{
        **mantis_slam_rise,
        'lean': -18.0,
        'twist': 0.0,
        'drop': 1.05,
        'arm_lift': (68.0, 68.0),
        'arm_fold': (30.0, 30.0),
        'reach': (34.0, 34.0),
        'elbow_fold': (42.0, 42.0),
        'legs': (-10.0, -20.0),
        'knees': (88.0, 96.0),
        'head': -20.0,
        'gear_deg': -60.0,
    }
)
mantis_slam_dive = dict(
    lean=36.0,
    drop=-0.15,
    arm_lift=(12.0, 12.0),
    arm_fold=(34.0, 34.0),
    reach=(58.0, 58.0),
    elbow_fold=(18.0, 18.0),
    elbow_pitch=(8.0, 8.0),
    wrist_pitch=(18.0, 18.0),
    blade_pitch=(14.0, 14.0),
    legs=(-38.0, -30.0),
    knees=(24.0, 28.0),
    spread=10.0,
    tail_trail=44.0,
    head=-10.0,
    gear_deg=120.0,
    energy=2.0,
)
mantis_slam_impact = dict(
    lean=38.0,
    drop=-2.50,
    arm_lift=(38.0, 38.0),
    arm_fold=(-38.0, -38.0),
    reach=(26.0, 26.0),
    elbow_fold=(-22.0, -22.0),
    elbow_pitch=(-12.0, -12.0),
    wrist_pitch=(20.0, 20.0),
    blade_pitch=(-12.0, -12.0),
    legs=(42.0, 34.0),
    knees=(82.0, 74.0),
    spread=34.0,
    tail_trail=32.0,
    head=16.0,
    gear_deg=290.0,
    energy=2.3,
)
mantis_slam_recoil = dict(
    lean=42.0,
    drop=-2.80,
    arm_lift=(46.0, 46.0),
    arm_fold=(-48.0, -48.0),
    reach=(10.0, 10.0),
    elbow_fold=(-26.0, -26.0),
    wrist_pitch=(14.0, 14.0),
    legs=(46.0, 38.0),
    knees=(86.0, 78.0),
    spread=40.0,
    tail_trail=36.0,
    head=18.0,
    gear_deg=370.0,
    energy=1.9,
)

clip(
    'slam_windup',
    1.0,
    [
        (0.00, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, gear_deg=0.0)),
        (0.24, dict(lean=16.0, drop=-0.85, arm_lift=(4.0, 4.0), arm_fold=(30.0, 30.0), reach=(14.0, 14.0), elbow_fold=(34.0, 34.0), legs=(22.0, 14.0), knees=(56.0, 48.0), gear_deg=24.0)),
        (0.68, {**mantis_slam_rise, 'gear_deg': -28.0}),
        (1.00, mantis_slam_zenith),
    ],
)

clip(
    'slam',
    2.1,
    [
        (0.00, mantis_slam_zenith),
        (0.18, mantis_slam_dive),
        (0.40, mantis_slam_impact),
        (0.56, mantis_slam_recoil),
        (0.86, dict(lean=20.0, drop=-0.95, arm_lift=(22.0, 22.0), arm_fold=(-12.0, -12.0), reach=(16.0, 16.0), elbow_fold=(12.0, 12.0), legs=(18.0, 10.0), knees=(52.0, 44.0), spread=16.0, tail_trail=18.0, gear_deg=450.0, energy=1.35)),
        (1.26, dict(lean=4.0, drop=0.30, arm_lift=(8.0, 8.0), arm_fold=(14.0, 14.0), reach=(6.0, 6.0), elbow_fold=(20.0, 20.0), legs=(-4.0, -14.0), knees=(28.0, 36.0), spread=8.0, tail_trail=10.0, gear_deg=520.0, energy=1.0)),
        (1.72, dict(lean=2.0, drop=0.10, arm_fold=18.0, elbow_fold=22.0, gear_deg=565.0, energy=0.9)),
        (2.10, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, gear_deg=600.0)),
    ],
)

# 4. Duet Windup & Duet (3.9s = 78 ticks) — Twin Clockwork Mantis Waltz!
preduet = dict(
    lean=-8.0,
    twist=-22.0,
    roll=-6.0,
    chest_twist=-10.0,
    arm_lift=(18.0, 14.0),
    arm_fold=(20.0, 12.0),
    reach=(16.0, 10.0),
    elbow_fold=(26.0, 22.0),
    wrist_pitch=(-12.0, -10.0),
    legs=(-14.0, 10.0),
    knees=(30.0, 22.0),
    spread=10.0,
    tail_trail=10.0,
    head_yaw=18.0,
)
spinning = dict(
    arm_lift=(54.0, 54.0),
    arm_fold=(-26.0, -26.0),
    reach=(-10.0, -10.0),
    elbow_fold=(-16.0, -16.0),
    wrist_pitch=(14.0, 14.0),
    legs=(-16.0, -28.0),
    knees=(30.0, 56.0),
    spread=36.0,
    tail_trail=24.0,
    drop=0.50,
    energy=1.3,
)
embrace = dict(
    lean=-10.0,
    twist=720.0,
    arm_lift=(14.0, 14.0),
    arm_fold=(42.0, 42.0),
    reach=(46.0, 46.0),
    elbow_fold=(46.0, 46.0),
    wrist_pitch=(-20.0, -20.0),
    legs=(-12.0, -18.0),
    knees=(28.0, 36.0),
    spread=8.0,
    tail_trail=8.0,
    head=6.0,
)
clip(
    'duet_windup',
    1.0,
    [
        (0.0, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0)),
        (0.32, dict(lean=8.0, twist=10.0, roll=4.0, arm_fold=24.0, elbow_fold=28.0)),
        (0.74, preduet),
        (1.0, {**preduet, 'twist': -26.0, 'lean': -10.0}),
    ],
)
clip(
    'duet',
    3.9,
    [
        (0.00, preduet),
        (0.36, dict(lean=10.0, twist=-28.0, roll=-8.0, arm_lift=(36.0, 36.0), arm_fold=(-10.0, -10.0), reach=(8.0, 8.0), elbow_fold=(12.0, 12.0), spread=18.0, tail_trail=14.0)),
        (0.80, {**spinning, 'twist': 0.0, 'roll': 8.0}),
        (1.25, {**spinning, 'twist': 180.0, 'lean': 8.0, 'roll': 10.0, 'legs': (26.0, -24.0), 'knees': (68.0, 26.0), 'tail_whip': 14.0}),
        (1.70, {**spinning, 'twist': 360.0, 'lean': -4.0, 'roll': -10.0, 'legs': (-24.0, 24.0), 'knees': (26.0, 66.0), 'tail_whip': -14.0}),
        (2.15, {**spinning, 'twist': 540.0, 'lean': 8.0, 'roll': 10.0, 'legs': (24.0, -22.0), 'knees': (64.0, 26.0), 'tail_whip': 14.0}),
        (2.60, {**spinning, 'twist': 720.0, 'roll': 0.0}),
        (2.96, embrace),
        (3.30, {**embrace, 'lean': -14.0, 'drop': 0.35}),
        (3.56, embrace),
        (3.90, dict(lean=3.0, twist=720.0, arm_fold=18.0, elbow_fold=22.0)),
    ],
    linear=('body', 'gear'),
)

# 5. Rewind, Failed Duet, Solo Idle, Death — ALL properly bowing FORWARD with natural backward knees!
clip(
    'rewind',
    3.0,
    [
        (0.00, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, gear_deg=0.0)),
        (0.42, dict(lean=16.0, twist=-10.0, arm_lift=(4.0, 4.0), arm_fold=(34.0, 34.0), reach=(24.0, 24.0), elbow_fold=(40.0, 40.0), drop=-0.35, gear_deg=-120.0)),
        (0.85, dict(lean=24.0, twist=8.0, arm_lift=(0.0, 0.0), arm_fold=(46.0, 46.0), reach=(38.0, 38.0), elbow_fold=(50.0, 50.0), head=-16.0, drop=-0.75, gear_deg=-320.0)),
        (1.45, dict(lean=22.0, twist=-6.0, arm_lift=(0.0, 0.0), arm_fold=(46.0, 46.0), reach=(38.0, 38.0), elbow_fold=(50.0, 50.0), head=-16.0, drop=-0.65, energy=0.5, gear_deg=-620.0)),
        (2.15, dict(lean=26.0, twist=0.0, arm_lift=(2.0, 2.0), arm_fold=(50.0, 50.0), reach=(42.0, 42.0), elbow_fold=(54.0, 54.0), head=-18.0, drop=-0.85, energy=0.55, gear_deg=-900.0)),
        (2.58, dict(lean=-8.0, arm_lift=(34.0, 34.0), arm_fold=(-22.0, -22.0), reach=(-10.0, -10.0), elbow_fold=(-10.0, -10.0), spread=16.0, drop=0.35, gear_deg=-720.0)),
        (3.00, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0, gear_deg=-720.0)),
    ],
    linear=('gear',),
)

fallen = dict(
    lean=32.0,
    arm_lift=(-14.0, -14.0),
    arm_fold=(12.0, 12.0),
    reach=(10.0, 10.0),
    elbow_fold=(16.0, 16.0),
    wrist_pitch=(22.0, 22.0),
    legs=(24.0, 24.0),
    knees=(76.0, 76.0),
    head=-22.0,
    drop=-2.1,
    tail_trail=14.0,
    energy=0.35,
)
clip(
    'failed_duet',
    4.5,
    [
        (0.00, preduet),
        (0.60, dict(lean=-6.0, arm_lift=(12.0, 12.0), arm_fold=(34.0, 34.0), reach=(42.0, 42.0), elbow_fold=(36.0, 36.0), drop=0.35)),
        (1.20, dict(lean=-10.0, arm_lift=(16.0, 16.0), arm_fold=(42.0, 42.0), reach=(54.0, 54.0), elbow_fold=(46.0, 46.0), drop=0.65)),
        (1.48, dict(lean=8.0, arm_lift=(4.0, 4.0), arm_fold=(28.0, 28.0), reach=(34.0, 34.0), elbow_fold=(30.0, 30.0), head=-8.0)),
        (1.72, fallen),
        (2.05, {**fallen, 'lean': 38.0, 'drop': -2.35}),
        (2.55, fallen),
        (3.65, fallen),
        (4.12, dict(lean=22.0, arm_lift=(-10.0, -10.0), arm_fold=(14.0, 14.0), reach=(6.0, 6.0), elbow_fold=(16.0, 16.0), legs=(6.0, -8.0), knees=(34.0, 42.0), head=-16.0, drop=-0.55)),
        (4.50, dict(lean=16.0, arm_lift=(-12.0, -10.0), arm_fold=(16.0, 12.0), reach=(4.0, 2.0), head=-16.0, energy=0.35)),
    ],
)

clip(
    'solo_idle',
    3.0,
    [
        (
            i * 0.375,
            dict(
                lean=16.0 + 1.8 * math.sin(i * math.pi / 4),
                roll=2.2 * math.sin(i * math.pi / 4 + 0.7),
                head=-16.0 - 2.0 * math.sin(i * math.pi / 4),
                head_roll=-5.0 + 2.0 * math.cos(i * math.pi / 4),
                arm_lift=(-12.0, -10.0),
                arm_fold=(18.0, 12.0),
                reach=(4.0, 2.0),
                elbow_fold=(18.0, 14.0),
                legs=(-4.0, -14.0),
                knees=(28.0, 38.0),
                tail_trail=8.0,
                energy=0.34,
                drop=0.16 * math.sin(i * math.pi / 4),
            ),
        )
        for i in range(9)
    ],
    loop=True,
)

clip(
    'death',
    1.0,
    [
        (0.00, dict(lean=3.0, energy=0.3)),
        (0.20, dict(lean=18.0, roll=-6.0, arm_lift=(-8.0, -8.0), arm_fold=(24.0, 24.0), reach=(12.0, 12.0), elbow_fold=(26.0, 26.0), legs=(14.0, 8.0), knees=(44.0, 36.0))),
        (0.50, dict(lean=42.0, roll=8.0, arm_lift=(-18.0, -16.0), arm_fold=(8.0, 4.0), reach=(16.0, 14.0), elbow_fold=(12.0, 10.0), legs=(26.0, 20.0), knees=(68.0, 56.0), drop=-3.2, head=-24.0)),
        (0.80, dict(lean=60.0, roll=4.0, arm_lift=(-24.0, -24.0), arm_fold=(-8.0, -8.0), reach=(14.0, 14.0), elbow_fold=(6.0, 6.0), legs=(18.0, 14.0), knees=(54.0, 44.0), drop=-5.2, head=-36.0)),
        (1.00, dict(lean=68.0, arm_lift=(-26.0, -26.0), arm_fold=(-12.0, -12.0), reach=(14.0, 14.0), elbow_fold=(6.0, 6.0), legs=(18.0, 14.0), knees=(50.0, 40.0), drop=-6.2, head=-40.0, energy=0.0)),
    ],
)

# 6. Interwoven Barrage (交织穿梭 — 螳螂极速变向滑步 + 贴地双镰交错斩!)
# Server timing:
#   - barrage_dash plays active % 15 / 20F = 0.00s..0.75s per pass!
#   * 0.00..0.28s (local 0..5.6): banked mantis hover-glide into lane start, twin sickles folded tight across sternum, spindly legs trailing!
#   * 0.30..0.34s (local 6.0..6.8): razor-crisp clockwork-tick stalk coil!
#   * 0.37..0.63s (local 7.4..12.6): blinding horizontal mantis wing-scythe blitz down the lane!
#   * 0.66..0.75s (local 13.2..15.0): crisp staccato scissor-pivot back into folded mantis glide for the next pass!
mantis_barrage_stage = dict(
    lean=24.0,
    twist=-22.0,
    roll=-16.0,
    chest_twist=-14.0,
    pelvis_twist=8.0,
    drop=0.20,
    arm_lift=(8.0, 16.0),
    arm_fold=(34.0, 22.0),
    reach=(16.0, -10.0),
    elbow_fold=(36.0, 26.0),
    wrist_pitch=(-16.0, -10.0),
    legs=(-20.0, -30.0),
    knees=(48.0, 60.0),
    spread=20.0,
    tail_trail=28.0,
    tail_whip=16.0,
    head=4.0,
    head_yaw=24.0,
    head_roll=10.0,
    energy=1.7,
)
mantis_barrage_coil = dict(
    lean=26.0,
    twist=-18.0,
    roll=-8.0,
    chest_twist=-12.0,
    drop=-1.05,
    arm_lift=(12.0, 10.0),
    arm_fold=(42.0, 38.0),
    reach=(26.0, 22.0),
    elbow_fold=(44.0, 42.0),
    elbow_pitch=(16.0, 14.0),
    wrist_pitch=(-20.0, -18.0),
    legs=(28.0, -24.0),
    knees=(72.0, 60.0),
    spread=12.0,
    tail_trail=14.0,
    tail_whip=6.0,
    head=4.0,
    head_yaw=20.0,
    energy=2.0,
)
mantis_barrage_burst = dict(
    lean=52.0,
    twist=16.0,
    roll=14.0,
    chest_twist=16.0,
    pelvis_twist=-8.0,
    drop=-0.25,
    arm_lift=(32.0, 42.0),
    arm_fold=(-22.0, -40.0),
    reach=(18.0, -46.0),
    elbow_fold=(-12.0, -24.0),
    elbow_pitch=(-8.0, -24.0),
    wrist_pitch=(18.0, 24.0),
    blade_pitch=(14.0, 18.0),
    legs=(-38.0, -24.0),
    knees=(38.0, 56.0),
    spread=26.0,
    tail_trail=46.0,
    tail_whip=-14.0,
    head=4.0,
    head_yaw=-14.0,
    energy=2.2,
)
mantis_barrage_mid = dict(
    lean=54.0,
    twist=-12.0,
    roll=-12.0,
    chest_twist=-14.0,
    drop=-0.30,
    arm_lift=(42.0, 30.0),
    arm_fold=(-40.0, -22.0),
    reach=(-46.0, 16.0),
    elbow_fold=(-24.0, -12.0),
    elbow_pitch=(-24.0, -8.0),
    wrist_pitch=(24.0, 18.0),
    blade_pitch=(18.0, 14.0),
    legs=(-24.0, -40.0),
    knees=(56.0, 36.0),
    spread=28.0,
    tail_trail=48.0,
    tail_whip=14.0,
    head=4.0,
    head_yaw=10.0,
    energy=2.1,
)
mantis_barrage_exit = dict(
    lean=8.0,
    twist=20.0,
    roll=14.0,
    chest_twist=14.0,
    drop=-0.10,
    arm_lift=(20.0, 20.0),
    arm_fold=(38.0, 38.0),
    reach=(32.0, 32.0),
    elbow_fold=(34.0, 34.0),
    wrist_pitch=(-14.0, -14.0),
    legs=(16.0, -26.0),
    knees=(50.0, 48.0),
    spread=26.0,
    tail_trail=26.0,
    tail_whip=-18.0,
    head=0.0,
    head_yaw=-18.0,
    energy=1.9,
)

clip(
    'barrage_windup',
    1.0,
    [
        (0.00, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0)),
        (0.28, dict(lean=-10.0, twist=12.0, roll=6.0, drop=0.40, arm_lift=(44.0, 44.0), arm_fold=(-16.0, -16.0), reach=(-8.0, -8.0), elbow_fold=(-8.0, -8.0), spread=18.0, head=-4.0, energy=1.3)),
        (0.66, dict(lean=20.0, twist=-18.0, roll=-10.0, drop=-0.85, arm_lift=(12.0, 10.0), arm_fold=(36.0, 34.0), reach=(22.0, 18.0), elbow_fold=(38.0, 36.0), legs=(22.0, -20.0), knees=(62.0, 52.0), spread=14.0, head_yaw=20.0, energy=1.6)),
        (1.00, mantis_barrage_stage),
    ],
)

clip(
    'barrage_dash',
    1.0,
    [
        (0.00, mantis_barrage_stage),
        (0.16, {**mantis_barrage_stage, 'lean': 30.0, 'twist': -26.0, 'roll': -20.0, 'legs': (-28.0, -36.0), 'knees': (50.0, 66.0), 'tail_whip': 20.0}),
        (0.30, mantis_barrage_coil),
        (0.37, mantis_barrage_burst),
        (0.52, mantis_barrage_mid),
        (0.66, mantis_barrage_exit),
        (0.75, mantis_barrage_stage),
        (0.88, {**mantis_barrage_stage, 'lean': 28.0, 'roll': -18.0}),
        (1.00, mantis_barrage_stage),
    ],
    loop=True,
    gear_turns=2,
)

clip(
    'barrage_recover',
    1.2,
    [
        (0.00, mantis_barrage_exit),
        (0.18, mantis_dash_brake),
        (0.48, dict(lean=14.0, twist=-12.0, roll=-5.0, drop=-0.55, arm_lift=(26.0, 22.0), arm_fold=(-16.0, -12.0), reach=(-6.0, -4.0), elbow_fold=(8.0, 8.0), legs=(10.0, -18.0), knees=(38.0, 42.0), spread=20.0, tail_trail=16.0, head=-6.0, head_yaw=12.0, energy=1.35)),
        (0.82, dict(lean=2.0, twist=4.0, roll=2.0, drop=0.25, arm_lift=(4.0, 4.0), arm_fold=(16.0, 16.0), reach=(6.0, 6.0), elbow_fold=(20.0, 20.0), legs=(-4.0, -14.0), knees=(26.0, 34.0), spread=8.0, energy=1.0)),
        (1.20, dict(lean=3.0, arm_fold=18.0, elbow_fold=22.0)),
    ],
)

# Select Cogwork Dancer tab safely before writing animations!
select_code = """(() => {
    const list = (typeof ModelProject !== 'undefined' && ModelProject.all) ? ModelProject.all : [];
    const p = list.find(x => x && x.name && x.name.includes('Cogwork Dancer'));
    if (p) p.select();
    if (!Project || !Project.name || !Project.name.includes('Cogwork Dancer')) {
        throw new Error('Cogwork Dancer project is not open in Blockbench: ' + (Project ? Project.name : 'none'));
    }
    Animation.all.slice().forEach(a => a.remove());
    return Project.name;
})()"""
print('Selected Blockbench project:', call('risky_eval', {'code': select_code}), flush=True)

for name, length, loop, bones, linear in clips:
    call('create_animation', {'name': name, 'animation_length': length, 'loop': loop, 'bones': bones})
    code = (
        '(()=>{const a=Animation.all.find(a=>a.name===%s);'
        'for(const b of Object.values(a.animators)){'
        'for(const k of b.keyframes){k.interpolation=%s.includes(b.name)?"linear":"catmullrom";}}'
        'return a.name;})()'
        % (json.dumps('animation.' + name), json.dumps(list(linear) + ['gear']))
    )
    call('risky_eval', {'code': code})
    print(name, ':', sum(len(v) for v in bones.values()), 'bone poses', flush=True)

(ROOT / '.work/animation-spec.json').write_text(json.dumps(clips, indent=2), 'utf8')
r = call('export_model', {'codec_id': 'project', 'max_content_length': 2000000})
(ROOT / '.work/bb-export-response.json').write_text(json.dumps(r), 'utf8')
text = next(c['text'] for c in r['content'] if c.get('type') == 'text')
data = json.loads(text)
if isinstance(data, dict) and 'content' in data:
    if data.get('truncated'):
        raise RuntimeError('Incomplete Blockbench export')
    (ROOT / 'modeling/cogwork_dancer.bbmodel').write_text(data['content'], 'utf8')
else:
    (ROOT / 'modeling/cogwork_dancer.bbmodel').write_text(text, 'utf8')
print('Editable Silksong mantis spline animations exported through Blockbench MCP.', flush=True)
