"""Export Yang Jian's Blockbench cuboids and polygon meshes to the animated game rig.

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
SOURCE = ROOT / 'modeling/yang_jian_v4.bbmodel'
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


def mesh_faces(element):
    origin = element['origin']
    vertices = {}
    for key, local in element['vertices'].items():
        offset = rotate(local, element.get('rotation', [0, 0, 0]))
        vertices[key] = [a+b for a,b in zip(origin,offset)]
    for face in element['faces'].values():
        if face.get('texture') is None:
            continue
        keys=face['vertices']
        if len(keys) not in (3,4):
            raise ValueError(f"Triangulate {element['name']} before exporting")
        yield [(vertices[k],face['uv'][k]) for k in keys]


def painted_eye(plate):
    # Center of the third-eye inlay on the painted facial plate, in atlas pixels.
    u,v=77.0,630.5
    for face in mesh_faces(plate):
        if len(face)!=3 or not all(4<=uv[0]<=147 and 606<=uv[1]<=752 for _,uv in face):
            continue
        (a,(ax,ay)),(b,(bx,by)),(c,(cx,cy))=face
        det=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax)
        if abs(det)<1e-9:continue
        s=((u-ax)*(cy-ay)-(v-ay)*(cx-ax))/det
        t=((bx-ax)*(v-ay)-(by-ay)*(u-ax))/det
        if min(s,t,1-s-t)>=-1e-6:
            p=[a[i]+s*(b[i]-a[i])+t*(c[i]-a[i]) for i in range(3)]
            p[2]-=.025
            return p
    raise ValueError('Third-eye inlay missing from facial plate')


def export():
    model = json.loads(SOURCE.read_text('utf8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    texture = model['textures'][0]
    tw, th = texture['width'], texture['height']
    counts = {'faces': 0, 'elements': 0}
    anchors={}

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
            if element['name']=='visage / painted planes':
                anchors['third_eye']=convert(subtract(painted_eye(element),origin))
            elif element['name']=='central double-edged blade':
                points=[p for face in mesh_faces(element) for p,_ in face]
                top=max(p[1] for p in points)
                rim={tuple(p) for p in points if abs(p[1]-top)<1e-5}
                tip=[sum(p[i] for p in rim)/len(rim) for i in range(3)]
                anchors['spear_tip']=convert(subtract(tip,origin))
            elif element['name']=='gauntlet / palm':
                anchors['left_palm']=convert(subtract(element['origin'],origin))
            polygons=mesh_faces(element) if element['type']=='mesh' else cube_faces(element)
            for points in polygons:
                vertices = [convert(subtract(p, origin)) + [round(uv[0]/tw, 8), round(uv[1]/th, 8)]
                            for p, uv in reversed(points)]
                a = subtract(vertices[1], vertices[0])
                b = subtract(vertices[2], vertices[0])
                normal = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
                length = math.sqrt(sum(v*v for v in normal))
                if length < 1e-9:
                    raise ValueError('Degenerate face: ' + element['name'])
                if len(vertices)==3:
                    vertices.append(vertices[-1])
                out['faces'].append({'v': vertices, 'n': [round(v/length, 6) for v in normal], 'glow': False})
                counts['faces'] += 1
        return out

    mesh = {'format': 1, 'source': 'Approved Blockbench MCP model, rest pose preserved',
            'texture_size': [tw, th], 'bones': [node(n, [0,0,0]) for n in model['outliner']], 'anchors':anchors}
    assert counts['elements'] == len(elements)
    assert set(anchors)=={'third_eye','spear_tip','left_palm'}
    (ASSETS/'mesh').mkdir(parents=True, exist_ok=True)
    (ASSETS/'mesh/yang_jian.json').write_text(json.dumps(mesh, separators=(',', ':')), 'utf8')
    (ASSETS/'textures/entity').mkdir(parents=True, exist_ok=True)
    (ASSETS/'textures/entity/yang_jian.png').write_bytes(base64.b64decode(texture['source'].split(',',1)[1]))
    manifest = {'source': SOURCE.relative_to(ROOT).as_posix(), 'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                'cubes': sum(e['type']=='cube' for e in elements.values()), 'meshes':sum(e['type']=='mesh' for e in elements.values()),
                'bones': len(groups), 'rendered_faces': counts['faces'],
                'texture_size': [tw,th], 'preserves_rest_pose': True}
    (ROOT/'modeling/yang_jian-runtime-manifest.json').write_text(json.dumps(manifest, indent=2), 'utf8')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    export()
