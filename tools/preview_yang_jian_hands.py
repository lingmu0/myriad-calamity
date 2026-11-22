"""Render a reproducible arm-motion contact sheet without launching Minecraft.

Uses the shipped cuboid mesh, UV atlas, baked tracks and the runtime ZYX FK.
The camera follows the chest's forward direction, so a spinning clip is still
shown from the front rather than accidentally inspecting the back of the rig.
Only the phase transition replaces the held spear with the giant axe.
"""
from __future__ import annotations

import argparse
from bisect import bisect_right
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/myriad_calamity'
POSES = [('combo', 8), ('thrust', 2), ('throw', 19.667),
         ('guard', 3), ('step_approach', 2), ('transition', 40)]
UPPER = {'chest', 'neck', 'head', 'left_arm', 'right_arm', 'left_forearm',
         'right_forearm', 'left_hand', 'right_hand', 'left_shoulder',
         'right_shoulder', 'left_vambrace', 'right_vambrace'}
MODEL_SCALE = .94


def sample(keys, time, fallback):
    if not keys:
        return np.full(3, fallback, dtype=float)
    right = bisect_right([key[0] for key in keys], time)
    if not right:
        return np.asarray(keys[0][1:], dtype=float)
    if right == len(keys):
        return np.asarray(keys[-1][1:], dtype=float)
    a, b = keys[right - 1], keys[right]
    t = (time - a[0]) / (b[0] - a[0]) if b[0] != a[0] else 0
    return np.asarray(a[1:]) * (1 - t) + np.asarray(b[1:]) * t


def matrix(bone, clip, time):
    tracks = clip['bones'].get(bone['name'], {})
    x, y, z = np.radians(np.asarray(bone['rotation']) + sample(tracks.get('rotation'), time, 0))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    out = np.eye(4)
    out[:3, :3] = (rz @ ry @ rx) * sample(tracks.get('scale'), time, 1)
    out[:3, 3] = (np.asarray(bone['pivot']) + sample(tracks.get('position'), time, 0)) / 16
    return out


def axe_growth(tick):
    # YangJianTransition.SUMMON_START=24, SUMMON_END=40; steady giant scale=3.
    fraction = np.clip((tick - 24) / 16, 0, 1)
    return float(3 * fraction * fraction * (3 - 2 * fraction))


class Rig:
    def __init__(self):
        self.roots = json.loads((ASSETS / 'mesh/yang_jian.json').read_text('utf8'))['bones']
        self.axe = json.loads((ASSETS / 'mesh/yang_jian_weapons.json').read_text('utf8'))['weapons']['axe']['faces']
        self.atlas = np.asarray(Image.open(ASSETS / 'textures/entity/yang_jian.png').convert('RGB'))
        self.face_cache = {}

    def faces(self, raw, key):
        if key not in self.face_cache:
            self.face_cache[key] = (np.asarray([f['v'] for f in raw], dtype=float),
                                    np.asarray([f['n'] for f in raw], dtype=float),
                                    np.asarray([f.get('glow', False) for f in raw]))
        return self.face_cache[key]

    def pose(self, runtime, name, tick):
        clip = runtime['clips'][name]
        time = tick / 20
        time = time % clip['length'] if clip['loop'] else min(time, clip['length'])
        points, normals, uvs, glows, framing = [], [], [], [], []
        transforms = {}
        reflection = np.diag([-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE])

        def walk(bone, parent):
            transform = parent @ matrix(bone, clip, time)
            transforms[bone['name']] = transform
            raw, key, scale = bone['faces'], bone['name'], 1
            if key == 'weapon' and name == 'transition' and tick >= 24:
                raw, key, scale = self.axe, 'giant_axe', axe_growth(tick)
            if raw:
                verts, norms, glow = self.faces(raw, key)
                world = ((verts[:, :, :3] * (scale / 16)) @ transform[:3, :3].T + transform[:3, 3]) @ reflection.T
                normal = norms @ np.linalg.inv(transform[:3, :3]) @ reflection.T
                normal /= np.maximum(np.linalg.norm(normal, axis=1, keepdims=True), 1e-12)
                points.append(world)
                normals.append(normal)
                uvs.append(verts[:, :, 3:5])
                glows.append(glow)
                if bone['name'] in UPPER:
                    framing.append(world.reshape(-1, 3))
            for child in bone['children']:
                walk(child, transform)

        for root in self.roots:
            walk(root, np.eye(4))
        front = reflection @ transforms['chest'][:3, :3] @ np.array([0., 0., -1.])
        front_yaw = math.degrees(math.atan2(-front[0], front[2]))
        return (np.concatenate(points), np.concatenate(normals), np.concatenate(uvs),
                np.concatenate(glows), np.concatenate(framing), front_yaw)


def camera(yaw, pitch=9):
    yaw, pitch = math.radians(yaw), math.radians(pitch)
    c, s, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    return np.array([[c, 0, s], [s * sp, cp, -c * sp], [-s * cp, sp, c * cp]])


def frame_pair(before, after, transform, size):
    bounds = np.concatenate([before[4], after[4]]) @ transform.T
    low, high = bounds[:, :2].min(axis=0), bounds[:, :2].max(axis=0)
    span = np.maximum(high - low, [2.3, 2.05])
    zoom = min((size[0] - 32) / span[0], (size[1] - 52) / span[1])
    return (low + high) / 2, zoom


def render(pose, atlas, transform, framing, size):
    """Orthographic textured triangles, with a per-pixel z buffer (no painter sorting)."""
    width, height = size
    pixels = np.full((height, width, 3), [46, 51, 60], dtype=np.uint8)
    depths = np.full((height, width), -np.inf)
    points, norms, uv, glows = pose[:4]
    projected = points @ transform.T
    normals = norms @ transform.T
    center, zoom = framing
    screen = projected.copy()
    screen[:, :, 0] = width / 2 + (screen[:, :, 0] - center[0]) * zoom
    screen[:, :, 1] = height / 2 - (screen[:, :, 1] - center[1]) * zoom
    light = np.array([-.35, .70, .62])
    light /= np.linalg.norm(light)
    lighting = np.clip(.53 + .47 * np.maximum(0, normals @ light), .4, 1)
    lighting[glows] = 1.15
    tex_h, tex_w = atlas.shape[:2]
    for i in np.flatnonzero(normals[:, 2] > .001):
        for indices in ((0, 1, 2), (0, 2, 3)):
            tri = screen[i, list(indices)]
            tex = uv[i, list(indices)]
            x0, y0 = np.maximum(np.floor(tri[:, :2].min(axis=0)).astype(int), 0)
            x1, y1 = np.minimum(np.ceil(tri[:, :2].max(axis=0)).astype(int), [width - 1, height - 1])
            if x1 < x0 or y1 < y0:
                continue
            ax, ay, bx, by, cx, cy = *tri[0, :2], *tri[1, :2], *tri[2, :2]
            denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(denominator) < 1e-9:
                continue
            xx, yy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
            a = ((by - cy) * (xx - cx) + (cx - bx) * (yy - cy)) / denominator
            b = ((cy - ay) * (xx - cx) + (ax - cx) * (yy - cy)) / denominator
            c = 1 - a - b
            z = a * tri[0, 2] + b * tri[1, 2] + c * tri[2, 2]
            region = depths[y0:y1 + 1, x0:x1 + 1]
            mask = (a >= -1e-7) & (b >= -1e-7) & (c >= -1e-7) & (z > region)
            if not np.any(mask):
                continue
            u = a[mask] * tex[0, 0] + b[mask] * tex[1, 0] + c[mask] * tex[2, 0]
            v = a[mask] * tex[0, 1] + b[mask] * tex[1, 1] + c[mask] * tex[2, 1]
            tx = np.clip((u * tex_w).astype(int), 0, tex_w - 1)
            ty = np.clip((v * tex_h).astype(int), 0, tex_h - 1)
            pixels[y0:y1 + 1, x0:x1 + 1][mask] = np.clip(atlas[ty, tx] * lighting[i], 0, 255).astype(np.uint8)
            region[mask] = z[mask]
    return Image.fromarray(pixels)


def font(size):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)
    except OSError:
        return ImageFont.load_default()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, default=ROOT / '.work/yang-arm-before/runtime.json')
    parser.add_argument('--after', type=Path, default=ASSETS / 'boss_animations/yang_jian.json')
    parser.add_argument('--output', type=Path, default=ROOT / 'modeling/previews/yang_jian_hand_motion.png')
    parser.add_argument('--quick', action='store_true', help='Only render idle + thrust for camera inspection.')
    parser.add_argument('--pose', action='append', help='Render selected clip:tick (repeatable).')
    args = parser.parse_args()
    before = json.loads(args.before.read_text('utf8'))
    after = json.loads(args.after.read_text('utf8'))
    rig = Rig()
    poses = [('idle', 0), ('thrust', 2)] if args.quick else POSES
    if args.pose:
        poses = [(name, float(tick)) for name, tick in (value.rsplit(':', 1) for value in args.pose)]
    cell_size, gap, row_title, top = (440, 390), 8, 36, 76
    width = cell_size[0] * 4 + gap * 5
    height = top + len(poses) * (cell_size[1] + row_title + gap) + 18
    sheet = Image.new('RGB', (width, height), '#191e27')
    draw = ImageDraw.Draw(sheet)
    draw.text((20, 12), 'YANG JIAN  /  ARM MOTION', font=font(25), fill='#f0e0bd')
    draw.text((20, 46), 'Actual game mesh + UV atlas + baked animation. Identical framing for each before / after pair.',
              font=font(16), fill='#a8b1c3')
    for row, (name, tick) in enumerate(poses):
        old, new = rig.pose(before, name, tick), rig.pose(after, name, tick)
        y = top + row * (cell_size[1] + row_title + gap)
        draw.text((18, y + 5), f'{name.upper()}  /  tick {tick:g}', font=font(18), fill='#e4dfd3')
        # Left-hand casts and the two-handed axe read better from the left side.
        offsets = (32, 90) if name in {'idle','p3_idle','eye_beam','tracking_beam','sweep_beam',
                                     'third_eye_open','step_approach','transition'} else (-32, -90)
        for view, offset in enumerate(offsets):
            transform = camera(old[5] + offset)
            framing = frame_pair(old, new, transform, cell_size)
            for state, pose in enumerate((old, new)):
                col = view * 2 + state
                image = render(pose, rig.atlas, transform, framing, cell_size)
                d = ImageDraw.Draw(image)
                label = f'{"BEFORE" if state == 0 else "AFTER"}  /  {"FRONT 3/4" if view == 0 else "SIDE"}'
                d.rectangle((0, 0, cell_size[0], 26), fill='#242a35')
                d.text((9, 3), label, font=font(15), fill='#c4cbd7' if state == 0 else '#9ed8b2')
                sheet.paste(image, (gap + col * (cell_size[0] + gap), y + row_title))
        print(f'Rendered {name} at tick {tick:g}', flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)
    print(args.output)


if __name__ == '__main__':
    main()
