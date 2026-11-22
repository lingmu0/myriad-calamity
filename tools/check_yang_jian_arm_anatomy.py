"""Check physical elbow flexion and lateral deviation in the shipped animation.

This deliberately does not import the arm authoring/IK implementation. It reads
the mesh pivots and runtime tracks, applies the same Rz * Ry * Rx transform as
the renderer, then measures the actual elbow-to-wrist vector in the upper-arm
frame. Euler X/Y/Z values alone are not anatomical joint angles.

Run: python tools/check_yang_jian_arm_anatomy.py
Optional: --runtime other.json --report .work/arm-anatomy-report.json
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'
MIN_FLEX = 0.0
MAX_FLEX = 135.0
MAX_LATERAL = 13.0
TOLERANCE = 1e-4


def normalize(vector):
    vector = np.asarray(vector, dtype=float)
    length = np.linalg.norm(vector)
    if not math.isfinite(length) or length < 1e-9:
        raise ValueError('Anatomy measurement requires a nonzero finite rig vector')
    return vector / length


def index_rig(roots):
    bones, parents = {}, {}

    def visit(bone, parent):
        name = bone['name']
        if name in bones:
            raise ValueError(f'Duplicate rig bone: {name}')
        bones[name], parents[name] = bone, parent
        for child in bone['children']:
            visit(child, name)

    for root in roots:
        visit(root, None)
    for side in ('left', 'right'):
        arm, forearm, hand = (side + suffix for suffix in ('_arm', '_forearm', '_hand'))
        if any(name not in bones for name in (arm, forearm, hand)):
            raise ValueError(f'Missing {side} arm rig chain')
        if parents[forearm] != arm or parents[hand] != forearm:
            raise ValueError(f'Unexpected {side} arm hierarchy')
    return bones


def sample_times(clip):
    """Every 60 Hz tick, actual track key, and midpoint between adjacent samples."""
    length = float(clip['length'])
    if not math.isfinite(length) or length < 0:
        raise ValueError('Invalid animation length')
    times = {0.0, length}
    times.update(i / 60 for i in range(math.floor(length * 60) + 1))
    for side in ('left', 'right'):
        for suffix in ('_forearm', '_hand'):
            channels = clip['bones'].get(side + suffix, {})
            for keys in channels.values():
                times.update(float(key[0]) for key in keys if 0 <= key[0] <= length)
    # Baked times are rounded to 7 decimals. Treat a 60 Hz time and that saved
    # representation as the same instant, retaining the saved runtime value.
    base = sorted({round(time, 7) for time in times})
    return np.array(sorted(set(base) | {(a + b) / 2 for a, b in zip(base, base[1:])}))


def rotation_samples(keys, times):
    if not keys:
        return np.zeros((len(times), 3))
    values = np.asarray(keys, dtype=float)
    if values.ndim != 2 or values.shape[1] != 4 or not np.isfinite(values).all():
        raise ValueError('Invalid runtime rotation channel')
    if np.any(np.diff(values[:, 0]) <= 0):
        raise ValueError('Runtime rotation key times must strictly increase')
    return np.column_stack([np.interp(times, values[:, 0], values[:, axis]) for axis in (1, 2, 3)])


def physical_angles(upper, lower, euler_degrees):
    """Return front flexion and sideways deviation from actual segment vectors.

    Pivots use runtime coordinates: +Y points down and -Z points forward.
    The hinge direction is perpendicular to both the upper arm and front.
    Projecting out its lateral component isolates the flexion angle even when
    there is forearm twist or a mirrored carrying angle.
    """
    upper, lower = normalize(upper), normalize(lower)
    front = np.array([0., 0., -1.])
    front = normalize(front - upper * np.dot(front, upper))
    hinge = normalize(np.cross(upper, front))
    # SciPy extrinsic xyz equals the renderer's Rz(z) * Ry(y) * Rx(x).
    direction = Rotation.from_euler('xyz', euler_degrees, degrees=True).apply(lower)
    along, forward, lateral = direction @ upper, direction @ front, direction @ hinge
    flex = np.degrees(np.arctan2(forward, along))
    deviation = np.degrees(np.arcsin(np.clip(lateral, -1, 1)))
    return flex, deviation


def audit(mesh, runtime):
    bones = index_rig(mesh['bones'])
    summary, failures = [], []
    for name, clip in runtime['clips'].items():
        times = sample_times(clip)
        for side in ('left', 'right'):
            forearm, hand = bones[side + '_forearm'], bones[side + '_hand']
            keys = clip['bones'].get(forearm['name'], {}).get('rotation')
            euler = rotation_samples(keys, times) + np.asarray(forearm['rotation'])
            flex, lateral = physical_angles(forearm['pivot'], hand['pivot'], euler)
            bad = ((flex < MIN_FLEX - TOLERANCE) | (flex > MAX_FLEX + TOLERANCE)
                   | (np.abs(lateral) > MAX_LATERAL + TOLERANCE))
            summary.append({
                'clip': name, 'bone': forearm['name'], 'samples': len(times),
                'minimum_flex_degrees': float(np.min(flex)),
                'maximum_flex_degrees': float(np.max(flex)),
                'maximum_lateral_degrees': float(np.max(np.abs(lateral))),
                'failed_samples': int(np.count_nonzero(bad)),
            })
            for index in np.flatnonzero(bad):
                failures.append({
                    'clip': name, 'bone': forearm['name'],
                    'time_seconds': float(times[index]), 'tick': float(times[index] * 20),
                    'flex_degrees': float(flex[index]), 'lateral_degrees': float(lateral[index]),
                    'rotation_degrees': euler[index].tolist(),
                })
    return {
        'clips': len(runtime['clips']), 'samples': sum(row['samples'] for row in summary),
        'bounds': {'minimum_flex': MIN_FLEX, 'maximum_flex': MAX_FLEX, 'maximum_lateral': MAX_LATERAL},
        'passed': not failures, 'failed_samples': len(failures),
        'summary': summary, 'failures': failures,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mesh', type=Path, default=ASSETS / 'mesh/yang_jian.json')
    parser.add_argument('--runtime', type=Path, default=ASSETS / 'boss_animations/yang_jian.json')
    parser.add_argument('--report', type=Path, help='Optional JSON report containing every failure.')
    parser.add_argument('--max-errors', type=int, default=24, help='Maximum failed samples printed to the terminal.')
    args = parser.parse_args()
    result = audit(json.loads(args.mesh.read_text('utf8')), json.loads(args.runtime.read_text('utf8')))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2), encoding='utf8')
    print(f"Checked {result['clips']} clips / {result['samples']} arm samples (60 Hz keys and midpoints).")
    print(f"Allowed flexion: {MIN_FLEX:g}..{MAX_FLEX:g} degrees; lateral deviation: +/-{MAX_LATERAL:g} degrees.")
    for row in result['failures'][:max(0, args.max_errors)]:
        print(f"FAIL {row['clip']} {row['bone']} tick={row['tick']:.5f}: "
              f"flex={row['flex_degrees']:.4f} lateral={row['lateral_degrees']:.4f}")
    if result['failures']:
        print(f"FAIL: {result['failed_samples']} samples outside anatomical limits.")
        if len(result['failures']) > args.max_errors:
            print('Use --report PATH for the complete list of offending samples.')
    else:
        print('PASS: all physical elbow samples remain in the forward hinge range.')
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
