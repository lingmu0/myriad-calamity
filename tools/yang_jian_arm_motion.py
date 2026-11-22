"""Offline retargeting with anatomical joints calibrated to each arm's geometry.

Euler X/Y/Z are not flexion, pronation and carrying angle. Flexion follows the
actual shoulder/elbow segment; twist follows elbow/wrist and cannot displace
the wrist. NumPy/SciPy are authoring dependencies, never game dependencies.
"""
from functools import lru_cache
import json
import math
from pathlib import Path
import numpy as np
from scipy.optimize import least_squares
from scipy.spatial.transform import Rotation

ROOT = Path(__file__).resolve().parents[1]
WRIST_LIMITS = (55., 30., 25.)


def unit(v):
    return v / np.linalg.norm(v)


def rotation(angles):
    return Rotation.from_euler('xyz', angles, degrees=True).as_matrix()


def axis_rotation(axis, degrees):
    return Rotation.from_rotvec(unit(axis) * math.radians(degrees)).as_matrix()


@lru_cache(None)
def rig_data():
    mesh = json.loads((ROOT / 'src/main/resources/assets/myriad_calamity/mesh/yang_jian.json').read_text('utf8'))
    bones = {}
    def visit(bone):
        bones[bone['name']] = bone
        for child in bone['children']:
            visit(child)
    for bone in mesh['bones']:
        visit(bone)
    return bones,mesh['anchors']


@lru_cache(None)
def rig(side):
    bones,_=rig_data()
    arm, forearm, hand = (bones[side + suffix] for suffix in ('_arm', '_forearm', '_hand'))
    return tuple(np.array(value) for value in
                 (arm['rotation'], forearm['pivot'], hand['pivot'], arm['pivot']))


@lru_cache(None)
def joint_frame(side):
    _, upper, lower, _ = rig(side)
    up = unit(upper)
    front = unit(np.array([0., 0., -1.]) - up * np.dot(up, [0., 0., -1.]))
    hinge = unit(np.cross(up, front))
    raw = unit(lower)
    cross = np.cross(raw, up)
    angle = math.acos(np.clip(np.dot(raw, up), -1, 1))
    base = axis_rotation(cross, math.degrees(angle)) if angle > 1e-9 else np.eye(3)
    return up, front, hinge, base


def forearm_matrix(side, angles):
    """angles = forward flexion, small lateral carry, longitudinal twist."""
    _, _, lower, _ = rig(side)
    _, front, hinge, base = joint_frame(side)
    flex, carry, twist = angles
    return axis_rotation(hinge, flex) @ axis_rotation(front, carry) @ base @ axis_rotation(lower, twist)


def joint_angles(side, matrix):
    _, _, lower, _ = rig(side)
    up, front, hinge, _ = joint_frame(side)
    direction = unit(matrix @ lower)
    flex = math.degrees(math.atan2(np.dot(direction, front), np.dot(direction, up)))
    carry = -math.degrees(math.asin(np.clip(np.dot(direction, hinge), -1, 1)))
    untwisted = forearm_matrix(side, (flex, carry, 0))
    twist = math.degrees(np.dot(Rotation.from_matrix(untwisted.T @ matrix).as_rotvec(), unit(lower)))
    return np.array([flex, carry, twist])


def continuous_euler(matrix, previous=None):
    primary = Rotation.from_matrix(matrix).as_euler('xyz', degrees=True)
    if previous is None:
        return primary.tolist()
    # RzRyRx has two equivalent branches. Axis-wise 360 wrapping alone fails
    # near +/-90-degree Y and can bend the elbow backwards between two samples.
    alternative = np.array([primary[0] + 180, 180 - primary[1], primary[2] + 180])
    candidates = [v + 360 * np.round((np.asarray(previous) - v) / 360)
                  for v in (primary, alternative)]
    return min(candidates, key=lambda v: np.linalg.norm(v - previous)).tolist()


def bake_forearm(side, keys, length, loop):
    """Add samples where linear Euler playback would leave the physical curve.

    Runtime can sample between ANY two keys. A fixed 60 Hz bake alone still
    folds backwards near a fast Euler pole crossing, despite valid endpoints.
    """
    from animation_baker import bake, sample
    points=bake(keys,length,loop)
    output=[]
    def actual(time):
        return forearm_matrix(side,sample(keys,time,loop))
    def segment(ta,a,tb,b,depth=0):
        tm=(ta+tb)*.5
        interpolated=rotation((np.asarray(a)+b)*.5)
        physical=joint_angles(side,interpolated)
        error=math.degrees(Rotation.from_matrix(actual(tm).T@interpolated).magnitude())
        if depth<9 and (error>.2 or physical[0]<.2 or abs(physical[1])>12.5):
            middle=continuous_euler(actual(tm),a)
            segment(ta,a,tm,middle,depth+1)
            b=continuous_euler(actual(tb),output[-1][1:])
            segment(tm,output[-1][1:],tb,b,depth+1)
        else:
            output.append([tb]+list(b))
    first=continuous_euler(actual(points[0][0]))
    output.append([points[0][0]]+first)
    for point in points[1:]:
        previous=output[-1]
        values=continuous_euler(actual(point[0]),previous[1:])
        segment(previous[0],previous[1:],point[0],values)
    return output


def forward(side, angles):
    """Shoulder Euler xyz, anatomical elbow flex/carry/twist, wrist Euler xyz."""
    rest, upper, lower, _ = rig(side)
    shoulder = rotation(angles[:3] + rest)
    elbow = shoulder @ forearm_matrix(side, angles[3:6])
    return shoulder @ upper + elbow @ lower, elbow @ rotation(angles[6:]), shoulder @ upper


def original_transform(side, values):
    rest, upper, lower, _ = rig(side)
    a = rotation(np.asarray(values[:3]) + rest)
    f = a @ rotation(values[3:6])
    return a @ upper + f @ lower, f @ rotation(values[6:])


def natural_free_hand(values):
    """Reinterpret every free-hand gesture, not a whitelist of broken clips.

    Legacy negative flexion was compensated by an over-raised shoulder. Restore
    forward flexion, lower that compensation, and keep the palm near the forearm.
    Relaxed hands roll toward the thigh; raised hands unroll to gesture.
    """
    a = np.asarray(values[:3], dtype=float).copy()
    f = joint_angles('left', rotation(values[3:6]))
    raised = np.clip((-a[0] - 30) / 65, 0, 1)
    flex = np.clip(abs(f[0]), 8, 125)
    if f[0] < 0:
        a[0] += min(24, flex * .30)
        if -values[0] > 95 and flex > 40:
            flex = max(95, flex)
            a[0] = max(-85, a[0])
    a = np.clip(a, [-95, -50, -35], [35, 50, 55])
    twist = np.clip(values[4] + 35 * (1 - raised), -45, 55)
    hand = np.clip(np.asarray(values[6:]) * .30, [-25, -18, -18], [25, 18, 18])
    return np.r_[a, flex, 8., twist, hand]


def solve(side, target_position, target_rotation, guide, support=False):
    lo = np.array([-150, -95, -90, 2, -12, -100, -55, -30, -25], dtype=float)
    hi = np.array([65, 95, 90, 130, 12, 100, 55, 30, 25], dtype=float)
    # An overhand release uses shoulder elevation. The longer forearm cannot
    # follow the spear through that arc with the shoulder stopped at 90 degrees.
    if side == 'right':
        lo[2] = -148
    seed = np.clip(guide, lo + 1e-5, hi - 1e-5)
    palm=np.asarray(rig_data()[1]['left_palm']) if support else None
    def error(angles):
        position, orientation, elbow = forward(side, angles)
        if support:
            position=position+orientation@palm
            aim = (orientation[:, 1] - target_rotation[:, 1]) * 10
        else:
            aim = Rotation.from_matrix(target_rotation.T @ orientation).as_rotvec() * 6
        inward = -elbow[0] if side == 'right' else elbow[0]
        # Keep elbows outside the ribs, in front, and below the head instead of
        # forcing an impossible old grip through an unnatural shoulder rotation.
        clearance = [max(0, inward + .25) * .9, max(0, elbow[2] - 1.0) * .65,
                     max(0, -2.2 - elbow[1]) * .7]
        return np.r_[(position - target_position) * (1.5 if support else .31), aim, clearance,
                     (angles[:3] - guide[:3]) * .004,
                     angles[6:] / WRIST_LIMITS * .04, angles[5] * .0008]
    candidates = []
    for flex, yaw in ((seed[3], seed[1]), (55, -45), (95, 45), (95, -65)):
        start = seed.copy()
        start[3], start[1] = flex, yaw
        result = least_squares(error, start, bounds=(lo, hi), max_nfev=180,
                               ftol=1e-7, xtol=1e-7, gtol=1e-7)
        candidates.append(result)
    return min(candidates, key=lambda result: np.linalg.norm(result.fun)).x


@lru_cache(None)
def retarget(side, values, contact=False):
    values = np.asarray(values, dtype=float)
    f = joint_angles(side, rotation(values[3:6]))
    if side == 'left':
        return tuple(natural_free_hand(values))
    hand_limit = (70, 30, 25) if contact else WRIST_LIMITS
    valid = (2 <= f[0] <= 130 and abs(f[1]) <= 12 and abs(f[2]) <= 100
             and all(abs(x) <= limit for x, limit in zip(values[6:], hand_limit)))
    if valid:
        return tuple(np.r_[values[:3], f, values[6:]])
    position, orientation = original_transform(side, values)
    guide = np.r_[values[:3], np.clip(abs(f[0]), 8, 125), -8, np.clip(f[2], -85, 85), values[6:]]
    return tuple(solve(side, position, orientation, guide))


@lru_cache(None)
def support_grip(right_values, left_values):
    right = np.asarray(right_values)
    position, orientation, _ = forward('right', right)
    bones,anchors=rig_data()
    origin = rig('right')[3] + position + orientation @ bones['weapon']['pivot']
    left_shoulder = rig('left')[3]
    candidates = []
    guide = np.asarray(left_values)
    for along in (-10., -7., 7., 10.):
        point = origin + orientation[:, 1] * along - left_shoulder
        result = solve('left', point, orientation, guide, support=True)
        p, r, _ = forward('left', result)
        score = np.linalg.norm(p + r@anchors['left_palm'] - point) + 5 * np.linalg.norm(r[:, 1] - orientation[:, 1])
        candidates.append((score, result))
    return tuple(min(candidates, key=lambda item: item[0])[1])


def improve_pose(pose, clip_name, tick):
    def convert(v):
        return [-float(v[0]), float(v[1]), -float(v[2])]
    # A pose may declare itself two-handed: the empty hand then supports the weapon exactly like
    # the transition's grip, so both hands swing as one instead of the free hand gesturing apart.
    two_handed = bool(pose.pop('two_handed', False))
    result = {}
    for side in ('right', 'left'):
        values = tuple(value for suffix in ('_arm', '_forearm', '_hand')
                       for value in convert(pose[side + suffix]))
        if clip_name=='idle':
            # The sculpt already carries the spear in a bent elbow. Breathe from that stance.
            breath=math.sin(tick*math.pi/40)
            flex,carry,twist=joint_angles(side,np.eye(3))
            result[side]=[breath*.55,0,0,max(2,flex)+breath*.75,np.clip(carry,-12,12),
                          twist,breath*.3,0,0]
        else:
            result[side] = retarget(side, values, clip_name == 'transition' and tick >= 72)
    if two_handed or (clip_name == 'transition' and 32 <= tick <= 68):
        result['left'] = support_grip(result['right'], result['left'])
    for side, values in result.items():
        pose[side + '_arm'] = convert(values[:3])
        # Keep anatomical coordinates until AFTER interpolation. The curve builder
        # normally converts BB axes, so reverse that conversion at this boundary.
        pose[side + '_forearm'] = convert(values[3:6])
        pose[side + '_hand'] = convert(values[6:])
