"""Capture a multi-angle + animated visual inspection sheet of Cogwork Dancer v2 via Blockbench MCP."""
from pathlib import Path
import json, base64, io
from PIL import Image, ImageDraw, ImageFont
from blockbench_mcp import request

ROOT = Path(__file__).resolve().parents[1]

def call(name, args):
    r = request('tools/call', {'name': name, 'arguments': args})
    if r.get('isError'):
        raise RuntimeError(r)
    return r

def grab(pos, target, zoom=0.28, hide_halo=True, silver=False, anim=None, time=0.0):
    code = f"""(() => {{
        const list = (typeof ModelProject !== 'undefined' && ModelProject.all) ? ModelProject.all : [];
        const pTab = list.find(x => x && x.name && x.name.includes('Cogwork Dancer'));
        if (pTab) pTab.select();
        const halo = Group.all.find(g => g.name === 'halo');
        if (halo) {{
            halo.visibility = {str(not hide_halo).lower()};
            halo.forEachChild(c => c.visibility = {str(not hide_halo).lower()});
        }}
        const targetTex = Texture.all.find(t => t.name === '{'cogwork_dancer_silver' if silver else 'cogwork_dancer'}');
        if (targetTex) {{
            for (const c of Cube.all) for (const f of Object.values(c.faces)) f.texture = targetTex.uuid;
        }}
        unselectAll();
        if ({json.dumps(anim)} !== null) {{
            Canvas.updateAll();
            Modes.options.animate.select();
            Animation.all.forEach(a => a.playing = false);
            const a = Animation.all.find(a => a.name === {json.dumps('animation.' + anim if anim else '')});
            if (a) {{
                a.select();
                a.playing = true;
                Timeline.setTime({time});
                Animator.preview();
            }}
        }} else {{
            Animation.all.forEach(a => a.playing = false);
            Modes.options.edit.select();
            Canvas.updateAll();
        }}
        const p = Preview.selected;
        p.setProjectionMode(true);
        p.camOrtho.zoom = {zoom};
        p.camera.position.set({pos[0]}, {pos[1]}, {pos[2]});
        p.controls.target.set({target[0]}, {target[1]}, {target[2]});
        p.camera.lookAt(p.controls.target);
        p.camOrtho.updateProjectionMatrix();
        p.controls.update();
        return true;
    }})()"""
    call('risky_eval', {'code': code})
    r = call('capture_screenshot', {})
    c = next(x for x in r['content'] if x['type'] == 'image')
    im = Image.open(io.BytesIO(base64.b64decode(c['data']))).convert('RGB')
    return im

def fit(im, w, h):
    scale = min(w / im.width, h / im.height)
    nw, nh = int(im.width * scale), int(im.height * scale)
    resized = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new('RGB', (w, h), '#10141c')
    canvas.paste(resized, ((w - nw) // 2, (h - nh) // 2))
    return canvas

panels = [
    ('Dash Windup Coil',     [78, 36, -110],  [0, 26, 0],   0.25, False, False, 'dash_windup', 0.90),
    ('Dash Launch Streak',   [78, 36, -110],  [0, 26, 0],   0.24, False, False, 'dash', 0.26),
    ('Dash Brake Cut (0.6s)',[78, 36, -110],  [0, 26, 0],   0.24, False, False, 'dash', 0.60),
    ('Slam Impact (0.4s)',   [-78, 36, -110], [0, 25, 0],   0.24, False, True,  'slam', 0.40),
    ('Barrage Stage Glide',  [78, 36, -110],  [0, 26, 0],   0.24, False, False, 'barrage_dash', 0.16),
    ('Barrage Burst Cleave', [78, 36, -110],  [0, 26, 0],   0.24, False, False, 'barrage_dash', 0.37),
    ('Barrage Whip Exit',    [-78, 36, -110], [0, 26, 0],   0.24, False, True,  'barrage_dash', 0.66),
    ('Duet Waltz Spin',      [78, 36, -110],  [0, 27, 0],   0.25, False, False, 'duet', 1.25),
]

pw, ph = 460, 480
sheet = Image.new('RGB', (pw * 4, ph * 2), '#0b0e14')
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
draw = ImageDraw.Draw(sheet)

for idx, (label, pos, target, zoom, hide_halo, silver, anim, time) in enumerate(panels):
    im = fit(grab(pos, target, zoom, hide_halo, silver, anim, time), pw - 16, ph - 44)
    col, row = idx % 4, idx // 4
    x, y = col * pw + 8, row * ph + 36
    sheet.paste(im, (x, y))
    draw.text((col * pw + 14, row * ph + 8), label, fill='#f0d9a8', font=font)

# Restore gold texture & edit mode
grab([68, 36, -108], [0, 28, 0], 0.34, False, False, None, 0)
out = ROOT / 'modeling/previews/cogwork_dancer_v2_sheet.png'
out.parent.mkdir(parents=True, exist_ok=True)
sheet.save(out)
print('Saved', out)
