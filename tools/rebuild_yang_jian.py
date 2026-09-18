"""Build and capture Yang Jian in an open Blockbench through its local MCP server."""
import argparse
import base64
import json
from pathlib import Path
from blockbench_mcp import request

ROOT = Path(__file__).resolve().parents[1]
VERSION = 'v3'


def call(name, args):
    response = request('tools/call', {'name': name, 'arguments': args})
    if response.get('isError'):
        raise RuntimeError(response)
    return response


def evaluate(code):
    result = call('risky_eval', {'code': code})
    for entry in result.get('content', []):
        if entry.get('type') == 'text':
            print(entry['text'], flush=True)
    return result


def export():
    result = call('export_model', {'codec_id': 'project', 'max_content_length': 2000000})
    payload = json.loads(next(c['text'] for c in result['content'] if c['type'] == 'text'))
    if payload['truncated']:
        response = evaluate('(()=>{globalThis.yjExportContent=Codecs.project.compile();if(typeof globalThis.yjExportContent!=="string")globalThis.yjExportContent=JSON.stringify(globalThis.yjExportContent);return globalThis.yjExportContent.length;})()')
        length = json.loads(next(c['text'] for c in response['content'] if c['type'] == 'text'))
        chunks = []
        try:
            for offset in range(0, int(length), 250000):
                result = call('risky_eval', {'code': f'globalThis.yjExportContent.slice({offset},{offset + 250000})'})
                chunks.append(json.loads(next(c['text'] for c in result['content'] if c['type'] == 'text')))
            content = ''.join(chunks)
        finally:
            call('risky_eval', {'code': 'delete globalThis.yjExportContent'})
    else:
        content = payload['content']
    model = json.loads(content)
    for name in [f'yang_jian_{VERSION}.bbmodel', 'yang_jian.bbmodel']:
        destination = ROOT / 'modeling' / name
        destination.write_text(content, encoding='utf-8')
        print('Saved:', destination, destination.stat().st_size, 'bytes', flush=True)
    for texture in model.get('textures', []):
        source = texture.get('source', '')
        if source.startswith('data:image/png;base64,'):
            path = ROOT / 'modeling/textures' / (texture['name'] + '.png')
            path.parent.mkdir(exist_ok=True, parents=True)
            path.write_bytes(base64.b64decode(source.split(',', 1)[1]))
    saved_path = json.dumps(str(ROOT / f'modeling/yang_jian_{VERSION}.bbmodel'))
    evaluate('(()=>{Project.save_path=' + saved_path + ';Project.saved=true;return "Project linked to saved file";})()')


def capture(view):
    positions = {'front': [0, 37, -160], 'three_quarter': [70, 51, -160],
                 'back': [-72, 50, 155], 'side': [160, 37, 0],
                 'face': [22, 56, -160]}
    target = [0, 51.5, 0] if view == 'face' else [1.8, 37, 0]
    zoom = .62 if view == 'face' else .24
    evaluate('(()=>{unselectAll();globalThis.yjCaptureBackground=scene.background;scene.background=new THREE.Color(0x626d7b);const p=Preview.selected;p.setProjectionMode(true);'
             f'p.camOrtho.zoom={zoom};p.camera.position.set({",".join(map(str, positions[view]))});'
             f'p.controls.target.set({",".join(map(str, target))});'
             'p.camera.lookAt(p.controls.target);p.camOrtho.updateProjectionMatrix();p.controls.update();Canvas.updateAll();return "View ready";})()')
    result = call('capture_screenshot', {})
    entry = next(c for c in result['content'] if c['type'] == 'image')
    path = ROOT / 'modeling/previews' / f'yang_jian_{VERSION}_{view}.png'
    path.parent.mkdir(exist_ok=True, parents=True)
    path.write_bytes(base64.b64decode(entry['data']))
    evaluate('(()=>{scene.background=globalThis.yjCaptureBackground;delete globalThis.yjCaptureBackground;Canvas.updateAll();return "Preview restored";})()')
    print('Captured:', path, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['build', 'export', 'capture'])
    parser.add_argument('--version', choices=['v2', 'v3'], default='v3')
    parser.add_argument('--view', default='three_quarter', choices=['front', 'three_quarter', 'back', 'side', 'face'])
    args = parser.parse_args()
    VERSION = args.version
    if args.action == 'build':
        source = (ROOT / 'tools/build_yang_jian_v2.js').read_text(encoding='utf-8-sig')
        if VERSION == 'v3':
            source = source[:source.index("  const atlas=document.createElement('canvas')")]
            api = '{parts,bones,mat,box,bar,framed,stud,crest,group}'
            for component in ['body', 'head', 'arms', 'weapon', 'volume']:
                module = (ROOT / f'tools/refine_yang_jian_v3_{component}.js').read_text(encoding='utf-8-sig').strip()
                source += f'\n({module})({api});\n'
            texture_module = (ROOT / 'tools/texture_yang_jian_v3.js').read_text(encoding='utf-8-sig').strip()
            source += f'\nreturn ({texture_module})({api});\n' + '})()'
            source = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('//'))
            (ROOT / '.work/build_yang_jian_v3.generated.js').write_text(source, encoding='utf-8')
        call('create_project', {'name': f'Yang Jian - Reference 04 {VERSION.upper()}', 'format': 'free'})
        evaluate(source)
        export()
        capture('three_quarter')
    elif args.action == 'export':
        export()
    else:
        capture(args.view)
