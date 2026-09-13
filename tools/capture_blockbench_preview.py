"""Capture a short preview from Blockbench's actual animation viewport through MCP."""
from pathlib import Path
import json,base64,io
from PIL import Image,ImageDraw,ImageFont
from blockbench_mcp import request
ROOT=Path(__file__).resolve().parents[1]
def call(name,args):
 r=request('tools/call',{'name':name,'arguments':args})
 if r.get('isError'):raise RuntimeError(r)
 return r
frames=[]
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',19)
schedule=[('idle',i/20) for i in range(24)]+[('dash_windup',i/20) for i in range(20)]+[('dash',i/20) for i in range(37)]+[('idle',i/20) for i in range(15)]
for index,(name,time) in enumerate(schedule):
 code='(()=>{Modes.options.animate.select();Animation.all.forEach(a=>a.playing=false);const a=Animation.all.find(a=>a.name===%s);a.select();a.playing=true;Timeline.setTime(%s);Animator.preview();unselectAll();const p=Preview.selected;p.setProjectionMode(true);p.camOrtho.zoom=.21;p.camera.position.set(45,36,-115);p.controls.target.set(0,27,0);p.camera.lookAt(p.controls.target);p.camOrtho.updateProjectionMatrix();p.controls.update();return true;})()'%(json.dumps('animation.'+name),time)
 call('risky_eval',{'code':code})
 r=call('capture_screenshot',{})
 c=next(c for c in r['content'] if c['type']=='image')
 im=Image.open(io.BytesIO(base64.b64decode(c['data']))).convert('RGB')
 viewport_width=min(im.width,round(im.height*720/560));left=(im.width-viewport_width)//2
 im=im.crop((left,0,left+viewport_width,im.height))
 im.thumbnail((720,560))
 frame=Image.new('RGB',(720,600),'#080d13');frame.paste(im,((720-im.width)//2,40+(560-im.height)//2))
 d=ImageDraw.Draw(frame);d.text((25,12),'机枢舞者 · 多关节冲刺  /  Blockbench 预览',fill='#e8d1a9',font=font)
 frames.append(frame)
 if index%16==0:print('Captured',index+1,'/',len(schedule),flush=True)
frames[0].save(ROOT/'modeling/previews/guard-dash-v4.gif',save_all=True,append_images=frames[1:],duration=50,loop=0,optimize=True,disposal=2)
call('risky_eval',{'code':'(()=>{Animation.all.forEach(a=>a.playing=false);Modes.options.edit.select();unselectAll();Canvas.updateAll();const p=Preview.selected;p.setProjectionMode(true);p.camOrtho.zoom=.29;p.camera.position.set(45,36,-115);p.controls.target.set(0,27,0);p.camera.lookAt(p.controls.target);p.camOrtho.updateProjectionMatrix();p.controls.update();return "Model ready for editing";})()'})
print('Saved modeling/previews/guard-dash-v4.gif',flush=True)
