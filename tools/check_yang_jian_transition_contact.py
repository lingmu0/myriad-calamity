"""Check the fully transformed giant axe against the platform, including baked in-between poses."""
from bisect import bisect_right
import json
import math
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'


def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def sample(keys, time, fallback):
    if not keys:
        return [fallback] * 3
    right = bisect_right([key[0] for key in keys], time)
    if right == 0:
        return keys[0][1:]
    if right == len(keys):
        return keys[-1][1:]
    a, b = keys[right - 1], keys[right]
    t = (time - a[0]) / (b[0] - a[0])
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


def smooth(t):
    t = min(1, max(0, t))
    return t * t * (3 - 2 * t)


def analyze():
    mesh = json.loads((ASSETS / 'mesh/yang_jian.json').read_text('utf8'))
    clip = json.loads((ASSETS / 'animations/yang_jian.json').read_text('utf8'))['clips']['transition']
    axe = json.loads((ASSETS / 'mesh/yang_jian_weapons.json').read_text('utf8'))['weapons']['axe']
    vertices = {tuple(v[:3]) for face in axe['faces'] for v in face['v']}
    path = next(path for bone in mesh['bones'] if (path := bone_path(bone, 'weapon')))
    java = (ROOT / 'src/main/java/net/xuwu/myriadcalamity/entity/YangJianTransition.java').read_text('utf8')
    timing = {key: float(value) for key, value in re.findall(r'\b([A-Z_]+)=([\d.]+)', java)}
    renderer = (ROOT / 'src/main/java/net/xuwu/myriadcalamity/client/YangJianRenderer.java').read_text('utf8')
    model_scale = float(re.search(r'MODEL_SCALE=([\d.]+)', renderer)[1])
    results = []
    for quarter_tick in range(int(timing['SUMMON_START'] * 4), int(timing['DURATION'] * 4) + 1):
        tick = quarter_tick / 4
        if tick < timing['ASCEND_END']:
            height = timing['HEIGHT'] * smooth(tick / timing['ASCEND_END'])
        elif tick <= timing['SWEEP_END']:
            height = timing['HEIGHT']
        else:
            height = timing['HEIGHT'] * (1 - smooth((tick - timing['SWEEP_END']) / (timing['IMPACT'] - timing['SWEEP_END'])))
        if tick < timing['SUMMON_END']:
            growth = 3 * smooth((tick - timing['SUMMON_START']) / (timing['SUMMON_END'] - timing['SUMMON_START']))
        elif tick <= timing['WAVE_END']:
            growth = 3
        else:
            growth = 3 - 2 * smooth((tick - timing['WAVE_END']) / (timing['DURATION'] - timing['WAVE_END']))
        y_row = transform(path, clip, tick / 20)[1]
        heights = [-model_scale * (sum(y_row[i] * vertex[i] * growth / 16 for i in range(3)) + y_row[3]) + height
                   for vertex in vertices]
        results.append((tick, min(heights), max(heights)))
    return results


def validate():
    results = analyze()
    worst = min(results, key=lambda row: row[1])
    assert worst[1] >= -.035, f'Giant axe intersects floor at tick {worst[0]}: {worst[1]:.4f} blocks'
    impact = next(row for row in results if row[0] == 72)
    assert -.035 <= impact[1] <= .3, f'Giant axe misses platform at impact: {impact[1]:.4f} blocks'
    print(f'Giant thunder axe: {len(results)} full-mesh poses checked; minimum floor clearance {worst[1]:.4f}; impact {impact[1]:.4f}.')


if __name__ == '__main__':
    validate()
