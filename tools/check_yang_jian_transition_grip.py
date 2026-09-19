"""Check that both hands hold the summoned giant axe through the transition spin.

The right hand owns the weapon bone, so a two-handed carry means the left palm sits on the handle
shaft, aligned with it, for the whole spin and the dive -- and lets go before the slam lands.
"""
from bisect import bisect_right
import json
import math
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'
GRIP_TICKS = (32, 68)
RELEASE_TICK = 72
POSITION_TOLERANCE = .22
ALIGNMENT_TOLERANCE = .90


def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def apply(matrix, point):
    return [sum(matrix[i][j] * point[j] for j in range(3)) + matrix[i][3] for i in range(3)]


def direction(matrix):
    origin, tip = apply(matrix, [0, 0, 0]), apply(matrix, [0, 1, 0])
    length = math.dist(origin, tip)
    return [(tip[i] - origin[i]) / length for i in range(3)]


def sample(keys, time, fallback):
    if not keys:
        return [fallback] * 3
    right = bisect_right([key[0] for key in keys], time)
    if right == 0:
        return keys[0][1:]
    if right == len(keys):
        return keys[-1][1:]
    a, b = keys[right - 1], keys[right]
    t = (time - a[0]) / (b[0] - a[0]) if b[0] != a[0] else 0
    return [a[i] + (b[i] - a[i]) * t for i in range(1, 4)]


def bone_path(bone, name):
    if bone['name'] == name:
        return [bone]
    for child in bone['children']:
        path = bone_path(child, name)
        if path:
            return [bone] + path
    return None


def transform(path, clip, time):
    result = identity()
    for bone in path:
        tracks = clip['bones'].get(bone['name'], {})
        position = sample(tracks.get('position'), time, 0)
        rotation = sample(tracks.get('rotation'), time, 0)
        scale = sample(tracks.get('scale'), time, 1)
        x, y, z = [math.radians(a + b) for a, b in zip(bone['rotation'], rotation)]
        cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
        rx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
        ry = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
        rz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
        local = multiply(multiply(rz, ry), rx)
        for row in range(3):
            for column in range(3):
                local[row][column] *= scale[column]
            local[row][3] = (bone['pivot'][row] + position[row]) / 16
        result = multiply(result, local)
    return result


def timing():
    java = (ROOT / 'src/main/java/net/xuwu/myriadcalamity/entity/YangJianTransition.java').read_text('utf8')
    return {key: float(value) for key, value in re.findall(r'\b([A-Z_]+)=([\d.]+)', java)}


def smooth(t):
    t = min(1, max(0, t))
    return t * t * (3 - 2 * t)


def growth(age, timing_values):
    if age < timing_values['SUMMON_START']:
        return 0.0
    if age < timing_values['SUMMON_END']:
        return 3 * smooth((age - timing_values['SUMMON_START'])
                          / (timing_values['SUMMON_END'] - timing_values['SUMMON_START']))
    return 3.0


def grip_state(paths, clip, tick, timing_values):
    """Left-hand distance to the handle axis, the palm alignment and the grip position along it."""
    time = tick / 20
    scale = growth(tick, timing_values)
    weapon = transform(paths['weapon'], clip, time)
    left = transform(paths['left_hand'], clip, time)
    origin = apply(weapon, [0, 0, 0])
    shaft = direction(weapon)
    hand = apply(left, [0, 0, 0])
    hand_axis = direction(left)
    offset = [hand[i] - origin[i] for i in range(3)]
    along = sum(offset[i] * shaft[i] for i in range(3))
    distance = math.sqrt(max(0, sum(value * value for value in offset) - along * along))
    span_head, span_butt = 47 * scale / 16, 19.5 * scale / 16
    return {
        'distance': distance,
        'alignment': abs(sum(hand_axis[i] * shaft[i] for i in range(3))),
        'along': along,
        'span': (-span_head, span_butt),
        'separation': math.dist(hand, origin),
    }


def validate():
    mesh = json.loads((ASSETS / 'mesh/yang_jian.json').read_text('utf8'))
    clip = json.loads((ASSETS / 'animations/yang_jian.json').read_text('utf8'))['clips']['transition']
    timing_values = timing()
    paths = {}
    for name in ('weapon', 'left_hand'):
        paths[name] = next(path for bone in mesh['bones'] if (path := bone_path(bone, name)))
    checked = 0
    worst_distance = 0.0
    worst_tick = GRIP_TICKS[0]
    worst_alignment = 1.0
    separations = []
    for quarter in range(GRIP_TICKS[0] * 4, GRIP_TICKS[1] * 4 + 1):
        tick = quarter / 4
        state = grip_state(paths, clip, tick, timing_values)
        assert state['span'][0] + .35 <= state['along'] <= state['span'][1] - .2, \
            f'Second hand leaves the handle at tick {tick}: along {state["along"]:.3f} outside {state["span"]}'
        assert state['distance'] <= POSITION_TOLERANCE, \
            f'Left palm is off the shaft at tick {tick}: {state["distance"]:.3f} blocks'
        assert state['alignment'] >= ALIGNMENT_TOLERANCE, \
            f'Left palm is not aligned with the shaft at tick {tick}: {state["alignment"]:.3f}'
        assert state['separation'] >= .25, \
            f'Both hands overlap at tick {tick}: {state["separation"]:.3f} blocks'
        if state['distance'] > worst_distance:
            worst_distance, worst_tick = state['distance'], tick
        worst_alignment = min(worst_alignment, state['alignment'])
        separations.append(state['separation'])
        checked += 1
    released = grip_state(paths, clip, RELEASE_TICK, timing_values)
    assert released['distance'] >= .5, \
        f'The left hand still grips the handle at the slam: {released["distance"]:.3f} blocks'
    print(f'Two-handed giant axe: {checked} poses checked; worst palm offset {worst_distance:.4f} blocks at '
          f'tick {worst_tick}; worst palm alignment {worst_alignment:.3f}; hand spacing '
          f'{min(separations):.3f}..{max(separations):.3f}; released before the slam '
          f'({released["distance"]:.3f} blocks off the shaft at tick {RELEASE_TICK}).')


if __name__ == '__main__':
    validate()
