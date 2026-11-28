"""Export the Sekiro Divine Dragon Blockbench model to myriad_calamity runtime assets.

Reads modeling/divine_dragon.bbmodel (created via Blockbench MCP) and emits:
  - src/main/resources/assets/myriad_calamity/mesh/divine_dragon.json
  - src/main/resources/assets/myriad_calamity/textures/entity/divine_dragon.png
  - modeling/divine_dragon-runtime-manifest.json
"""
from pathlib import Path
import base64
import hashlib
import json
import math

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'modeling/divine_dragon.bbmodel'
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'


def subtract(a, b):
    return [a[i] - b[i] for i in range(3)]


def convert(v):
    return [round(v[0], 6), round(-v[1], 6), round(v[2], 6)]


def rotate(v, angles):
    x, y, z = v
    for axis, angle in enumerate(angles):
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        if axis == 0:
            y, z = y * c - z * s, y * s + z * c
        elif axis == 1:
            x, z = x * c + z * s, -x * s + z * c
        else:
            x, y = x * c - y * s, x * s + y * c
    return [x, y, z]


def is_glow_element(name, face_name):
    if name.startswith('sword_') or name.startswith('barb_'):
        if name.startswith('sword_grip_') or name.startswith('sword_pommel_collar') or name.startswith('sword_collar_ring'):
            return False
        return True
    if name.startswith('head_eye_iris_') and face_name in ('east', 'west', 'north'):
        return True
    return False


def cube_faces(element):
    x0, y0, z0 = element['from']
    x1, y1, z1 = element['to']
    assert x1 > x0 and y1 > y0 and z1 > z0, element['name']
    origin = element.get('origin', [0, 0, 0])
    sides = {
        'north': [[x1, y0, z0], [x0, y0, z0], [x0, y1, z0], [x1, y1, z0]],
        'south': [[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]],
        'west': [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]],
        'east': [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]],
        'up': [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]],
        'down': [[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]],
    }
    for face_name, positions in sides.items():
        face = element['faces'][face_name]
        if face.get('texture') is None:
            continue
        u0, v0, u1, v1 = face['uv']
        uvs = [[u0, v1], [u1, v1], [u1, v0], [u0, v0]]
        offset = (int(face.get('rotation', 0)) // 90) % 4
        uvs = uvs[offset:] + uvs[:offset]
        result = []
        for point, uv in zip(positions, uvs):
            point = rotate(subtract(point, origin), element.get('rotation', [0, 0, 0]))
            result.append(([point[i] + origin[i] for i in range(3)], uv))
        yield face_name, result


def export():
    model = json.loads(SOURCE.read_text('utf8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    texture = model['textures'][0]
    tw = model.get('resolution', {}).get('width', texture.get('width', 1024))
    th = model.get('resolution', {}).get('height', texture.get('height', 1024))
    counts = {'faces': 0, 'glow_faces': 0, 'elements': 0}
    anchors = {}

    def node(entry, parent_origin):
        g = groups[entry['uuid']]
        origin = g['origin']
        rotation = g.get('rotation', [0, 0, 0])
        out = {
            'name': g['name'],
            'pivot': convert(subtract(origin, parent_origin)),
            'rotation': [-rotation[0], rotation[1], -rotation[2]],
            'faces': [],
            'children': [],
        }
        if g['name'] == 'head':
            anchors['head_center'] = convert([0, 4, -36])
        elif g['name'] == 'chest':
            anchors['chest_center'] = convert([0, 0, 0])
        elif g['name'] == 'seven_branched_sword':
            anchors['sword_heel'] = convert([0, 20, 0])
            anchors['sword_tip'] = convert([0, 330, 0])

        for child in entry['children']:
            if isinstance(child, dict):
                out['children'].append(node(child, origin))
                continue
            element = elements[child]
            counts['elements'] += 1
            for face_name, points in cube_faces(element):
                vertices = [
                    convert(subtract(p, origin)) + [round(uv[0] / tw, 8), round(uv[1] / th, 8)]
                    for p, uv in reversed(points)
                ]
                a = subtract(vertices[1], vertices[0])
                b = subtract(vertices[2], vertices[0])
                normal = [
                    a[1] * b[2] - a[2] * b[1],
                    a[2] * b[0] - a[0] * b[2],
                    a[0] * b[1] - a[1] * b[0],
                ]
                length = math.sqrt(sum(v * v for v in normal))
                if length < 1e-9:
                    raise ValueError('Degenerate face: ' + element['name'])
                glow = is_glow_element(element['name'], face_name)
                if glow:
                    counts['glow_faces'] += 1
                out['faces'].append({
                    'v': vertices,
                    'n': [round(v / length, 6) for v in normal],
                    'glow': glow,
                })
                counts['faces'] += 1
        return out

    mesh = {
        'format': 1,
        'source': 'Blockbench MCP Sekiro Divine Dragon V5 model',
        'texture_size': [tw, th],
        'bones': [node(n, [0, 0, 0]) for n in model['outliner']],
        'anchors': anchors,
    }
    assert counts['elements'] == len(elements), (counts['elements'], len(elements))
    assert set(anchors) == {'head_center', 'chest_center', 'sword_heel', 'sword_tip'}, anchors

    (ASSETS / 'mesh').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'mesh/divine_dragon.json').write_text(json.dumps(mesh, separators=(',', ':')), 'utf8')
    (ASSETS / 'textures/entity').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'textures/entity/divine_dragon.png').write_bytes(
        base64.b64decode(texture['source'].split(',', 1)[1])
    )
    manifest = {
        'source': SOURCE.relative_to(ROOT).as_posix(),
        'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        'cubes': len(elements),
        'bones': len(groups),
        'rendered_faces': counts['faces'],
        'glow_faces': counts['glow_faces'],
        'texture_size': [tw, th],
    }
    (ROOT / 'modeling/divine_dragon-runtime-manifest.json').write_text(json.dumps(manifest, indent=2), 'utf8')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    export()
