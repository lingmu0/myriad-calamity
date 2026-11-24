"""Bake the stance, stepping feet and knee bend against the game mesh's actual pivots."""
from functools import lru_cache
import math

import numpy as np

from animation_baker import bake, sample
from yang_jian_arm_motion import continuous_euler, rig_data, rotation, unit

BACK = np.array([0., 0., 1.])
SIDES = ('left', 'right')
AIR_WINDOWS = {
    'step_approach': ((.5, 5.0, 1.),),
    'axe_slam': ((8, 28, 3),),
    'transition': ((7, 72, 4),),
    'sweep_beam': ((6, 137, 4),),
    'tracking_beam': ((7, 78, 4),),
    'myriad_swords': ((7, 138, 5),),
    'aerial_combo': ((7, 130, 4),),
    'divine_judgement': ((7, 116, 5), (119, 304, 5)),
}


@lru_cache(None)
def leg_data(side):
    bones, _ = rig_data()
    thigh, shin, foot = (bones[side + part] for part in ('_thigh', '_shin', '_foot'))
    hip, upper, lower = (np.array(bone['pivot']) for bone in (thigh, shin, foot))
    sole = np.array([vertex[:3] for face in foot['faces'] for vertex in face['v']])
    return hip, upper, lower, sole


def smooth(value):
    t = min(1, max(0, value))
    return t * t * (3 - 2 * t)


def air_weight(name, tick):
    key = 'step_approach' if name.startswith('step_approach') else name
    return max((smooth((tick - start) / fade) * smooth((end - tick) / fade)
                for start, end, fade in AIR_WINDOWS.get(key, ())), default=0)


def pivot_step(name, tick, timing, side):
    """Raise the unweighted foot during the turn and give the other a heel pivot."""
    if name not in ('divine_sweep', 'divine_spin', 'whip_sweep', 'whip_spin'):
        return 0., 0., 0.
    hit, active = timing['hit_ticks'][0], timing['active_ticks']
    t = (tick - hit) / active
    if not 0 <= t <= 1:
        return 0., 0., 0.
    left = side == 'left'
    # Shift the weight between feet. The short sweep uses one step; a full
    # turn brings the rear foot around before it takes the load again.
    start, end = ((.08, .54) if left else (.56, .97))
    if name.endswith('sweep') and not left:
        return 0., 2.4 * math.sin(math.pi * t), 0.
    progress = min(1, max(0, (t - start) / (end - start)))
    swing = math.sin(math.pi * progress)
    lift = swing * (2.7 if left else 2.35)
    return lift, -13 * swing, (-5.5 if left else 4.4) * swing


def frame(axis, back):
    y = unit(axis)
    z = unit(back - y * np.dot(back, y))
    return np.column_stack((np.cross(y, z), y, z))


def solve_leg(parent, hip, ankle, upper, lower, back, boot):
    offset = ankle - hip
    distance = np.linalg.norm(offset)
    axis = unit(offset)
    l1, l2 = np.linalg.norm(upper), np.linalg.norm(lower)
    distance = float(np.clip(distance, l1 - l2 + .05, (l1 + l2) * .9995))
    along = (l1*l1 - l2*l2 + distance*distance) / (2 * distance)
    front = unit(-back + axis * np.dot(back, axis))
    knee = axis * along + front * math.sqrt(max(0, l1*l1 - along*along))
    thigh = frame(knee, back) @ frame(upper, BACK).T
    shin = frame(axis * distance - knee, back) @ frame(lower, BACK).T
    return parent.T @ thigh, thigh.T @ shin, shin.T @ boot


def refine_footwork(name, bones, ticks, loop, timing):
    """Use the authored motion in the air and a shared floor contact solve on landing."""
    length = ticks / 20
    times = sorted({key[0] for key in bake(bones['root']['position'], length, loop)})
    leg_names = [side + suffix for side in SIDES for suffix in ('_thigh', '_shin', '_foot')]
    channels = ['root', 'hips', *leg_names]
    source = {bone: bones[bone]['rotation'] for bone in channels}
    root_track = bones['root']['position']
    keys = {bone: [] for bone in leg_names}
    heights = []
    rig, _ = rig_data()
    hip_pivot = np.array(rig['hips']['pivot'])
    low_pose = name in ('phase_clear', 'death', 'p3_death')

    for time in times:
        tick = time * 20
        angles = {bone: np.array(sample(source[bone], time, loop), dtype=float) for bone in channels}
        root_position = np.array(sample(root_track, time, loop), dtype=float)
        root = rotation(angles['root'])
        yaw = angles['root'][1]
        facing = rotation((0, yaw, 0))
        back = facing @ BACK
        hips = root @ rotation(angles['hips'])
        hip_center = root_position + root @ hip_pivot
        airborne = air_weight(name, tick)
        allowed = 0 if name == 'transition' else .65 if name.startswith('divine_') and name != 'divine_judgement' else 4.0
        lowering = 0.
        planned = {}

        for side in SIDES:
            sign = -1 if side == 'left' else 1
            thigh, shin, foot = (angles[side + suffix].copy() for suffix in ('_thigh', '_shin', '_foot'))
            # In the local axes, +X flexes the knee and brings the heel back.
            shin[0] = min(112, max(3.5, abs(shin[0])))
            shin[1:] *= .25
            foot[0] = float(np.clip(foot[0], -29, 24))
            if airborne > 0 and not name.startswith('step_approach'):
                pulse = math.sin(tick * .16 + sign * .83)
                thigh[0] -= 6 * airborne * pulse
                shin[0] = max(5, shin[0] + airborne * (12 + 9 * pulse))
                foot[0] += airborne * (5 + 8 * pulse)
                foot[1] += airborne * sign * 4
            if low_pose:
                planned[side] = (thigh, shin, foot)
                continue

            hip, upper, lower, sole = leg_data(side)
            point = hip_center + hips @ hip
            swing = hips @ rotation(thigh)
            raw_ankle = point + swing @ upper + swing @ rotation(shin) @ lower
            natural = hip + upper + lower + hip_pivot
            local = facing.T @ (raw_ankle - root_position)
            lift, pitch, stride = pivot_step(name, tick, timing, side)
            if name == 'walk':
                lift = 2.8 * max(0, sign * math.sin(tick * math.pi / 12))
            width = float(np.clip(natural[0]*.55 + local[0]*.45, -11.5, 11.5))
            width = sign * max(5.2, sign * width)
            reach = float(np.clip(local[2]*.62 + stride, -10.6, 9.5))
            boot = facing @ rotation((pitch + foot[0]*.23, -sign*7 + foot[1]*.48, 0))
            target = root_position + facing @ np.array([width, 0., reach])
            target[1] = -.05 - np.max((sole @ boot.T)[:, 1]) - lift
            reach_limit = (np.linalg.norm(upper) + np.linalg.norm(lower)) * .9995
            horizontal = math.hypot(target[0]-point[0], target[2]-point[2])
            max_drop = math.sqrt(max(0, reach_limit**2 - horizontal**2))
            lowering = max(lowering, target[1]-point[1]-max_drop+.12)
            planned[side] = (point, target, upper, lower, boot, (thigh, shin, foot), reach_limit)

        lowering = min(allowed, lowering) * (1 - airborne)
        heights.append([time, root_position[0], root_position[1] + lowering, root_position[2]])
        for side in SIDES:
            if low_pose:
                solved = planned[side]
            else:
                point, target, upper, lower, boot, flight, reach_limit = planned[side]
                point = point + (0, lowering, 0)
                gap = target - point
                horizontal = math.hypot(gap[0], gap[2])
                max_reach = math.sqrt(max(.001, reach_limit**2 - gap[1]**2))
                if horizontal > max_reach:
                    gap[[0, 2]] *= max_reach / horizontal
                    target = point + gap
                matrices = solve_leg(hips, point, target, upper, lower, back, boot)
                solved = []
                for idx, key in enumerate((side+'_thigh', side+'_shin', side+'_foot')):
                    ground = np.array(continuous_euler(matrices[idx]))
                    air = flight[idx] + 360 * np.round((ground - flight[idx]) / 360)
                    solved.append(ground + airborne * (air - ground))
            for suffix, value in zip(('_thigh', '_shin', '_foot'), solved):
                name_key = side + suffix
                previous = keys[name_key][-1][1:] if keys[name_key] else value
                value = value + 360 * np.round((np.array(previous) - value) / 360)
                keys[name_key].append([time, *value])

    def to_track(points):
        if loop:
            points[-1][1:] = points[0][1:]
        return [{'time': p[0], 'values': [round(float(v), 5) for v in p[1:]], 'mode': 'linear'}
                for p in points]

    for bone, points in keys.items():
        bones[bone]['rotation'] = to_track(points)
    if name != 'transition':
        bones['root']['position'] = to_track(heights)
