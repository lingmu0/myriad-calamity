"""Lossless cuboid/UV export of the approved Blockbench Yang Jian model.

The artist's source project is read only. Bone pivots and rest rotations are
retained, so the forward head, shallow breastplate and open collar stay intact.
Run this and generate_yang_jian_animations.py to rebuild the runtime resources.
"""
from pathlib import Path
import base64
import hashlib
import json
import math

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'modeling/yang_jian_v3.bbmodel'
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
            y, z = y*c-z*s, y*s+z*c
        elif axis == 1:
            x, z = x*c+z*s, -x*s+z*c
        else:
            x, y = x*c-y*s, x*s+y*c
    return [x, y, z]


def cube_faces(element):
    assert element['type'] == 'cube', 'Approved model should contain cuboids only'
    x0, y0, z0 = element['from']
    x1, y1, z1 = element['to']
    assert x1 > x0 and y1 > y0 and z1 > z0, element['name']
    origin = element.get('origin', [0, 0, 0])
    sides = {
        'north': [[x1,y0,z0],[x0,y0,z0],[x0,y1,z0],[x1,y1,z0]],
        'south': [[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]],
        'west': [[x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0]],
        'east': [[x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1]],
        'up': [[x0,y1,z1],[x1,y1,z1],[x1,y1,z0],[x0,y1,z0]],
        'down': [[x0,y0,z0],[x1,y0,z0],[x1,y0,z1],[x0,y0,z1]],
    }
    for face_name, positions in sides.items():
        face = element['faces'][face_name]
        if face.get('texture') is None:
            continue
        u0, v0, u1, v1 = face['uv']
        uvs = [[u0,v1],[u1,v1],[u1,v0],[u0,v0]]
        offset = (int(face.get('rotation', 0)) // 90) % 4
        uvs = uvs[offset:] + uvs[:offset]
        result = []
        for point, uv in zip(positions, uvs):
            point = rotate(subtract(point, origin), element.get('rotation', [0,0,0]))
            result.append(([point[i] + origin[i] for i in range(3)], uv))
        yield result


def export():
    model = json.loads(SOURCE.read_text('utf8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    texture = model['textures'][0]
    tw, th = texture['width'], texture['height']
    counts = {'faces': 0, 'elements': 0}

    def node(entry, parent_origin):
        g = groups[entry['uuid']]
        origin = g['origin']
        rotation = g.get('rotation', [0,0,0])
        out = {'name': g['name'], 'pivot': convert(subtract(origin, parent_origin)),
               'rotation': [-rotation[0], rotation[1], -rotation[2]], 'faces': [], 'children': []}
        for child in entry['children']:
            if isinstance(child, dict):
                out['children'].append(node(child, origin))
                continue
            element = elements[child]
            counts['elements'] += 1
            for points in cube_faces(element):
                vertices = [convert(subtract(p, origin)) + [round(uv[0]/tw, 8), round(uv[1]/th, 8)]
                            for p, uv in reversed(points)]
                a = subtract(vertices[1], vertices[0])
                b = subtract(vertices[2], vertices[0])
                normal = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
                length = math.sqrt(sum(v*v for v in normal))
                if length < 1e-9:
                    raise ValueError('Degenerate face: ' + element['name'])
                # Metal uses the scene light. Only the inlaid third-eye gem glows.
                glow = 'third_eye_gem' in element['name']
                out['faces'].append({'v': vertices, 'n': [round(v/length, 6) for v in normal], 'glow': glow})
                counts['faces'] += 1
        return out

    mesh = {'format': 1, 'source': 'Approved Blockbench MCP model, rest pose preserved',
            'texture_size': [tw, th], 'bones': [node(n, [0,0,0]) for n in model['outliner']]}
    assert counts['elements'] == len(elements)
    (ASSETS/'mesh').mkdir(parents=True, exist_ok=True)
    (ASSETS/'mesh/yang_jian.json').write_text(json.dumps(mesh, separators=(',', ':')), 'utf8')
    (ASSETS/'textures/entity').mkdir(parents=True, exist_ok=True)
    (ASSETS/'textures/entity/yang_jian.png').write_bytes(base64.b64decode(texture['source'].split(',',1)[1]))
    manifest = {'source': str(SOURCE.relative_to(ROOT)), 'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                'cubes': counts['elements'], 'bones': len(groups), 'rendered_faces': counts['faces'],
                'texture_size': [tw,th], 'preserves_rest_pose': True}
    (ROOT/'modeling/yang_jian-runtime-manifest.json').write_text(json.dumps(manifest, indent=2), 'utf8')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    export()
