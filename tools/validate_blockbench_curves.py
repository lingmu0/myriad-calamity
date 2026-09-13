"""Compare offline curve baking against the running Blockbench's native interpolator."""
import sys,json
from pathlib import Path
from blockbench_mcp import request
from animation_baker import sample
ROOT=Path(__file__).resolve().parents[1]
code='(()=>{const result=[];for(const a of Animation.all){for(const f of [.137,.373,.691]){const t=a.length*f;Timeline.setTime(t);for(const b of Object.values(a.animators)){if(["body","chest","left_elbow","left_wrist","right_knee","left_tail_1","gear"].includes(b.name))result.push({clip:a.name,bone:b.name,time:t,values:b.interpolate("rotation",false)});}}}return JSON.stringify(result);})()'
r=request('tools/call',{'name':'risky_eval','arguments':{'code':code}})
if r.get('isError'):raise RuntimeError(r)
data=r['content'][0]['text']
while isinstance(data,str):data=json.loads(data)
model=json.loads((ROOT/'modeling/cogwork_dancer.bbmodel').read_text('utf8'))
clips={a['name']:a for a in model['animations']}
maximum=0
for row in data:
 a=clips[row['clip']];animator=next(b for b in a['animators'].values() if b['name']==row['bone'])
 keys=[{'time':k['time'],'mode':k['interpolation'],'values':[float(k['data_points'][0][axis]) for axis in 'xyz']} for k in animator['keyframes'] if k['channel']=='rotation']
 expected=sample(sorted(keys,key=lambda k:k['time']),row['time'],a['loop']=='loop')
 error=max(abs(x-y) for x,y in zip(expected,row['values']));maximum=max(maximum,error)
 if error>1e-6:raise AssertionError((row,expected,error))
report={'checked_native_samples':len(data),'max_degrees_error':maximum,'passed':True}
(ROOT/'.work/native-curve-check.json').write_text(json.dumps(report,indent=2),'utf8');print(report)
