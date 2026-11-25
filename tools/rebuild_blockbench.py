"""Rebuild the editable Cogwork Dancer v2 model through a running Blockbench MCP server, then export game assets."""
from pathlib import Path
import json,re,subprocess,sys
from blockbench_mcp import request
ROOT=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(ROOT/'tools/blockbench_mcp.py'),'init'],check=True,cwd=ROOT,stdout=subprocess.PIPE,text=True)
def call(name,args):
 result=request('tools/call',{'name':name,'arguments':args})
 if result.get('isError'):raise RuntimeError(result)
 return result
def strip_js_comments(src):
 src=re.sub(r'/\*[\s\S]*?\*/','',src)
 return '\n'.join(re.sub(r'//.*$','',line) for line in src.splitlines())
reuse=call('risky_eval',{'code':'(()=>{const p=ModelProject.all.find(x=>x.name&&x.name.includes("Cogwork Dancer"));if(p){p.select();return "reused";}return "new";})()'})
if 'new' in str(reuse.get('content')):
 call('create_project',{'name':'Cogwork Dancer - Myriad Calamity','format':'free'})
for script in ['build_cogwork_dancer_v2.js','texture_cogwork_dancer_v2.js']:
 code=strip_js_comments((ROOT/'tools'/script).read_text('utf-8-sig'))
 result=call('risky_eval',{'code':code})
 print(script,':',result.get('content'))
subprocess.run([sys.executable,str(ROOT/'tools/animate_blockbench_model.py')],check=True,cwd=ROOT)
response=json.loads((ROOT/'.work/bb-export-response.json').read_text('utf8'))
data=json.loads(next(c['text'] for c in response['content'] if c['type']=='text'))
if data['truncated']:raise RuntimeError('Incomplete Blockbench export')
(ROOT/'modeling').mkdir(exist_ok=True)
(ROOT/'modeling/cogwork_dancer.bbmodel').write_text(data['content'],'utf8')
subprocess.run([sys.executable,str(ROOT/'tools/export_blockbench_assets.py')],check=True,cwd=ROOT)
