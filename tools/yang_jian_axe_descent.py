"""Bridge a natural giant-axe carry to the authored ground-contact pose.

The end-effector follows a quaternion short arc instead of interpolating the
three joints' unrelated Euler rotations. This avoids tipping the threefold axe
through the floor midway through the four-tick descent. The exported 60 Hz
tracks preserve both endpoint poses and do not modify a clip or resource file.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from scipy.optimize import least_squares
from scipy.spatial.transform import Rotation, Slerp

from check_yang_jian_transition_contact import bone_path, sample, smooth, transform
from yang_jian_arm_motion import forward, forearm_matrix, joint_angles, rig, rotation, solve

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'
BONES = ('right_arm', 'right_forearm', 'right_hand')
START_TICK, END_TICK = 68., 72.


def endpoint(clip, tick):
    values = [np.asarray(sample(clip['bones'][name]['rotation'], tick / 20, 0)) for name in BONES]
    return np.r_[values[0], joint_angles('right', rotation(values[1])), values[2]]


def create_tracks(clip, fps=60):
    """Return ``{bone_name: [[seconds, rx, ry, rz], ...]}`` for ticks 68..72.

    Input is an already baked transition clip, with its final torso/root tracks
    and the desired exact right-arm endpoints. Only the returned three tracks
    need to be spliced into the clip. The phase transition intentionally retains
    its pre-existing 66-degree wrist at contact, so this bridge permits 70 while
    retaining the shared anatomical elbow constraints.
    """
    mesh = json.loads((ASSETS / 'mesh/yang_jian.json').read_text('utf8'))
    weapon_mesh = json.loads((ASSETS / 'mesh/yang_jian_weapons.json').read_text('utf8'))['weapons']['axe']
    axe_points = np.asarray(list({tuple(v[:3]) for face in weapon_mesh['faces'] for v in face['v']})) * 3
    chest_path = next(path for root in mesh['bones'] if (path := bone_path(root, 'chest')))
    weapon_path = next(path for root in mesh['bones'] if (path := bone_path(root, 'weapon')))
    weapon = weapon_path[-1]
    handle_offset = np.asarray(weapon['pivot'])
    arm_pivot = rig('right')[3]
    q0, q1 = endpoint(clip, START_TICK), endpoint(clip, END_TICK)
    p0, r0, _ = forward('right', q0)
    p1, r1, _ = forward('right', q1)
    sweep = Slerp([0, 1], Rotation.from_matrix(np.array([r0, r1])))
    # Reserve one degree inside the shared 12-degree lateral limit: the runtime
    # interpolates exported Euler samples, not the anatomical parameter vector.
    lo = np.array([-150, -95, -90, 2, -11, -100, -70, -30, -25], dtype=float)
    hi = np.array([65, 95, 90, 130, 11, 100, 70, 30, 25], dtype=float)
    count = round((END_TICK - START_TICK) / 20 * fps)
    ticks = np.linspace(START_TICK, END_TICK, count + 1)
    result = {name: [] for name in BONES}
    previous = q0.copy()
    previous_euler = [sample(clip['bones'][name]['rotation'], START_TICK / 20, 0) for name in BONES]
    for index, tick in enumerate(ticks):
        fraction = (tick - START_TICK) / (END_TICK - START_TICK)
        eased = smooth(fraction)
        target_p = p0 * (1 - eased) + p1 * eased
        target_r = sweep([eased]).as_matrix()[0]
        guide = q0 * (1 - eased) + q1 * eased
        parent = np.asarray(transform(chest_path, clip, tick / 20))
        # Match the phase transition's server-authoritative 8-block descent.
        flight = 8 * (1 - smooth((tick - 64) / 8))
        weapon_tracks = clip['bones'].get('weapon', {})
        weapon_r = rotation(np.asarray(weapon['rotation']) + sample(weapon_tracks.get('rotation'), tick / 20, 0))
        weapon_t = handle_offset + sample(weapon_tracks.get('position'), tick / 20, 0)
        local_axe = axe_points @ weapon_r.T + weapon_t

        def height(position, orientation):
            points = (local_axe @ orientation.T + position + arm_pivot) / 16
            return float(np.min(-.94 * (points @ parent[1, :3] + parent[1, 3]) + flight))

        def error(q):
            p, r, elbow = forward('right', q)
            clearance = [max(0, .25 - elbow[0]), max(0, elbow[2] - 1)]
            return np.r_[(p - target_p) * 2,
                         Rotation.from_matrix(target_r.T @ r).as_rotvec() * 18,
                         max(0, .015 - height(p, r)) * 28,
                         np.asarray(clearance) * .12,
                         (q - guide) * .0005]

        if index == 0:
            q = q0
        elif index == len(ticks) - 1:
            q = q1
        else:
            shared_seed = solve('right', target_p, target_r, guide)
            candidates = []
            for seed in (previous, guide, shared_seed):
                fit = least_squares(error, np.clip(seed, lo + 1e-8, hi - 1e-8), bounds=(lo, hi),
                                    max_nfev=220, ftol=1e-9, xtol=1e-9, gtol=1e-9)
                candidates.append(fit)
            q = min(candidates, key=lambda fit: np.linalg.norm(fit.fun)).x
        previous = q.copy()
        eulers = [q[:3], Rotation.from_matrix(forearm_matrix('right', q[3:6])).as_euler('xyz', degrees=True), q[6:]]
        for i, (name, angles) in enumerate(zip(BONES, eulers)):
            if index in (0, len(ticks) - 1):
                angles = np.asarray(sample(clip['bones'][name]['rotation'], tick / 20, 0))
            else:
                angles += np.round((np.asarray(previous_euler[i]) - angles) / 360) * 360
            result[name].append([round(tick / 20, 7), *[round(float(v), 6) for v in angles]])
            previous_euler[i] = angles
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=ASSETS / 'boss_animations/yang_jian.json')
    parser.add_argument('--output', type=Path, help='Optional standalone track JSON; the input is never edited.')
    args = parser.parse_args()
    clip = json.loads(args.runtime.read_text('utf8'))['clips']['transition']
    tracks = create_tracks(clip)
    payload = json.dumps(tracks, separators=(',', ':'))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, 'utf8')
        print(args.output)
    else:
        print(payload)


if __name__ == '__main__':
    main()
