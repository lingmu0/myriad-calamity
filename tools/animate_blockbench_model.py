"""Author articulated, spline-interpolated clips using the real Blockbench MCP server."""
import sys,json,math
from pathlib import Path
from blockbench_mcp import request
ROOT=Path(__file__).resolve().parents[1]
def call(name,args):
 r=request('tools/call',{'name':name,'arguments':args})
 if r.get('isError'):raise RuntimeError(r)
 return r
F=lambda t,rotation=None,position=None,scale=None:dict(time=t,**({'rotation':rotation} if rotation is not None else {}),**({'position':position} if position is not None else {}),**({'scale':scale} if scale is not None else {}))
# Input rotations use the MCP tool's axis convention; the exporter converts the saved BB axes.
def pose(t,lean=0,twist=0,arms=(-6,6),reach=0,elbow=8,wrist=0,legs=(-10,-4),knees=(16,9),spread=0,drop=0,energy=1,head=0):
 p={
 'body':{'rotation':[lean,twist,0],'position':[0,drop,0]},
 'chest':{'rotation':[lean*.24,math.sin(t*3)*2*energy,-math.sin(t*2)*1.4*energy]},
 'abdomen':{'rotation':[-lean*.2,-math.sin(t*3-0.3)*2.8*energy,math.sin(t*2-.3)*1.5*energy]},
 'pelvis':{'rotation':[-lean*.08,math.sin(t*3-.6)*3*energy,math.sin(t*2.6)*2.6*energy]},
 'neck':{'rotation':[-lean*.22+head*.4,-math.sin(t*3)*2*energy,0]},
 'head':{'rotation':[-lean*.23+head*.6,math.sin(t*2.5)*3*energy,math.sin(t*2.4-.2)*2*energy]},
 'crown':{'rotation':[-lean*.06+math.sin(t*5-.7)*1.5*energy,0,math.sin(t*3-.8)*1.2*energy]},
 'gear':{'rotation':[0,0,t*100]},
 'core':{'scale':[1+.04*math.sin(t*5),1+.04*math.sin(t*5),1]},
 'skirt':{'rotation':[-lean*.18,math.sin(t*3-.5)*3*energy,0]},
 'halo':{'rotation':[0,0,math.sin(t*1.8)*3*energy]}}
 for i,(s,sign) in enumerate([('left',-1),('right',1)]):
  p[s+'_arm']={'rotation':[reach,sign*abs(reach)*.36,arms[i]+math.sin(t*3.2+i*.8)*3*energy]}
  p[s+'_shoulder']={'rotation':[-reach*.12+math.sin(t*4-.3)*1.8*energy,0,sign*(spread*.2+math.sin(t*3.8)*1.6*energy)]}
  p[s+'_elbow']={'rotation':[abs(reach)*.12,sign*5,sign*elbow]}
  p[s+'_wrist']={'rotation':[wrist,sign*wrist*.4,-sign*elbow*.3]}
  p[s+'_blade']={'rotation':[-wrist*.2,0,sign*math.sin(t*5-.2)*2*energy]}
  p[s+'_leg']={'rotation':[legs[i],0,sign*(5+spread*.25)]}
  p[s+'_knee']={'rotation':[knees[i],sign*math.sin(t*3+i)*2*energy,-sign*2]}
  p[s+'_ankle']={'rotation':[-knees[i]*.55-legs[i]*.4,0,sign*2]}
  p[s+'_hip_plate']={'rotation':[-lean*.15,sign*spread*.32,sign*(spread*.4+math.sin(t*3-.2)*2*energy)]}
  for j in range(3):p[s+'_tail_'+str(j)]={'rotation':[-lean*.12-spread*.18+math.sin(t*5-j*.65+i*.45)*2.8*energy,sign*math.sin(t*3-j*.5)*2*energy,sign*(spread*.2+math.sin(t*4-j*.7)*1.5*energy)]}
 return p
clips=[]
def clip(name,length,frames,loop=False,linear=()):
 bones={}
 for t,args in frames:
  p=pose(t,**args)
  for b,channels in p.items():bones.setdefault(b,[]).append(F(t,**channels))
 if loop:
  for b,keys in bones.items():keys[-1]={**keys[0],'time':length}
  for key in bones['gear']:key['rotation']=[0,0,360*key['time']/length]
 clips.append((name,length,loop,bones,list(linear)))
# Idle keys sample a complete periodic breathing cycle, with staggered armor sway.
clip('idle',2.4,[(i*.3,dict(drop=.3-.3*math.cos(i*math.pi/4),lean=math.sin(i*math.pi/4)*1.2,arms=(4-3*math.sin(i*math.pi/4),-6+3*math.sin(i*math.pi/4)),elbow=24,wrist=-8,legs=(-14-7*math.sin(i*math.pi/4),-7+7*math.sin(i*math.pi/4)),knees=(22+8*math.sin(i*math.pi/4),15-7*math.sin(i*math.pi/4)))) for i in range(9)],True)
coil=dict(lean=-17,arms=(-34,28),reach=-36,elbow=42,wrist=-20,legs=(-42,18),knees=(70,30),spread=6,drop=-.9,energy=1.4)
launch=dict(lean=58,arms=(-9,12),reach=-72,elbow=10,wrist=24,legs=(-52,32),knees=(58,34),spread=18,drop=.2,energy=2)
dash=dict(lean=52,arms=(-12,9),reach=-66,elbow=5,wrist=14,legs=(-32,24),knees=(40,44),spread=15,energy=1.2)
brake=dict(lean=-9,arms=(-24,20),reach=-28,elbow=30,wrist=-13,legs=(-48,-12),knees=(68,32),spread=26,drop=-.7,energy=1.8)
clip('dash_windup',1,[(0,{}),(.22,dict(lean=-4,elbow=18)),(.65,coil),(1,{**coil,'lean':-20,'drop':-1.1})])
clip('dash',1.8,[(0,coil),(.1,launch),(.28,dash),(.45,{**dash,'lean':55,'legs':(-16,34),'knees':(22,60)}),(.68,{**dash,'legs':(-43,18),'knees':(54,28)}),(.86,dash),(1.02,brake),(1.24,dict(lean=7,arms=(-18,18),elbow=20,spread=10,energy=1.4)),(1.55,dict(lean=-2,elbow=10)),(1.8,{})])
rise=dict(lean=-10,arms=(-76,76),reach=-17,elbow=38,wrist=-20,legs=(-58,-47),knees=(100,86),spread=14,head=13,energy=1.5)
clip('slam_windup',1,[(0,{}),(.23,dict(lean=12,drop=-.65,elbow=22,knees=(28,28))),(.7,rise),(1,{**rise,'lean':-16,'elbow':45})])
impact=dict(lean=32,arms=(6,-6),reach=-75,elbow=10,wrist=23,legs=(-40,-34),knees=(68,60),spread=24,drop=-1.8,energy=2.1)
clip('slam',2.1,[(0,rise),(.23,dict(lean=13,arms=(-48,48),reach=-53,elbow=24,legs=(12,7),knees=(20,16),spread=13)),(.4,impact),(.56,{**impact,'lean':37,'spread':32,'drop':-2.3}),(.8,dict(lean=15,reach=-40,elbow=35,legs=(-24,-18),knees=(38,31),spread=12,drop=-.8)),(1.2,dict(lean=6,reach=-20,elbow=20)),(1.7,dict(lean=-2,elbow=12)),(2.1,{})])
preduet=dict(lean=-8,twist=-18,arms=(-30,30),reach=-25,elbow=30,wrist=-12,legs=(12,-12),knees=(22,12),spread=8)
clip('duet_windup',1,[(0,{}),(.3,dict(lean=5,twist=8,elbow=16)),(.75,preduet),(1,preduet)])
spinning=dict(arms=(-60,60),reach=-8,elbow=-12,wrist=12,legs=(-22,18),knees=(30,22),spread=34,drop=.4,energy=1.2)
embrace=dict(lean=-10,twist=720,arms=(-8,8),reach=-68,elbow=54,wrist=-18,legs=(12,-12),knees=(24,18),spread=8)
clip('duet',3.9,[(0,preduet),(.35,dict(twist=-22,arms=(-38,38),elbow=28,spread=13)),(.8,{**spinning,'twist':0}), (1.25,{**spinning,'twist':180,'lean':4,'legs':(-48,8),'knees':(65,18)}),(1.7,{**spinning,'twist':360,'lean':-3,'legs':(10,-42),'knees':(21,58)}),(2.15,{**spinning,'twist':540,'lean':4,'legs':(-45,8),'knees':(60,19)}),(2.6,{**spinning,'twist':720}),(2.95,embrace),(3.3,{**embrace,'lean':-14}),(3.55,embrace),(3.9,dict(twist=720))],linear=('body','gear'))
clip('rewind',3,[(0,{}),(.4,dict(lean=12,reach=-30,elbow=38)),(.8,dict(lean=20,reach=-55,elbow=50,head=18,drop=-.6)),(1.4,dict(lean=18,reach=-55,elbow=50,head=18,energy=.45)),(2.15,dict(lean=21,reach=-55,elbow=50,head=18,energy=.45)),(2.55,dict(lean=8,reach=-25,elbow=25)),(3,{})],linear=('gear',))
fallen=dict(lean=30,arms=(18,-18),reach=9,elbow=18,wrist=23,legs=(12,12),knees=(23,23),head=22,drop=-1.8,energy=.4)
clip('failed_duet',4.5,[(0,preduet),(.6,dict(lean=-8,reach=-54,elbow=40,arms=(-8,8),drop=.3)),(1.2,dict(lean=-12,reach=-70,elbow=55,arms=(-5,5),drop=.6)),(1.45,dict(lean=5,reach=-48,elbow=40,head=6)),(1.7,fallen),(2.0,{**fallen,'lean':36,'drop':-2}),(2.5,fallen),(3.65,fallen),(4.1,dict(lean=22,reach=5,elbow=12,head=18,drop=-.5)),(4.5,dict(lean=15,arms=(15,-15),head=18,energy=.4))])
clip('solo_idle',3,[(i*.375,dict(lean=15+1.6*math.sin(i*math.pi/4),head=18+2*math.sin(i*math.pi/4),arms=(15,-15),reach=5,energy=.4,drop=.15*math.sin(i*math.pi/4))) for i in range(9)],True)
clip('death',1,[(0,dict(energy=.3)),(.2,dict(lean=15,reach=-12,elbow=32,knees=(28,20))),(.5,dict(lean=40,arms=(25,-20),reach=12,elbow=38,knees=(45,30),drop=-3,head=24)),(.8,dict(lean=58,arms=(30,-30),reach=15,elbow=16,knees=(24,20),drop=-5,head=36)),(1,dict(lean=65,arms=(30,-30),reach=15,elbow=18,knees=(26,18),drop=-6,head=40,energy=0))])
clip('barrage_windup',1,[(0,{}),(.28,dict(lean=-8,arms=(-40,40),elbow=24,wrist=-18,spread=12)),(.65,dict(lean=-15,arms=(-46,42),reach=-32,elbow=40,wrist=-22,legs=(28,-20),knees=(40,18),spread=16)),(1,coil)])
clip('barrage_dash',1,[(0,coil),(.17,{**coil,'lean':-22,'elbow':46}),(.35,coil),(.43,launch),(.57,{**dash,'lean':62,'legs':(-54,22),'knees':(68,38)}),(.76,dash),(.85,brake),(1,coil)],True)
clip('barrage_recover',1.2,[(0,coil),(.2,brake),(.45,dict(lean=10,arms=(-20,20),elbow=24,spread=14,drop=-.5)),(.75,dict(lean=-3,elbow=12,spread=6)),(1.2,{})])
call('risky_eval',{'code':'Animation.all.slice().forEach(a=>a.remove()); "Removed previous dancer clips"'})
for name,length,loop,bones,linear in clips:
 call('create_animation',{'name':name,'animation_length':length,'loop':loop,'bones':bones})
 # Real editable BB spline keys, keeping rotational wheels and whole-body pirouettes linear.
 code='(()=>{const a=Animation.all.find(a=>a.name===%s);for(const b of Object.values(a.animators)){for(const k of b.keyframes){k.interpolation=%s.includes(b.name)?"linear":"catmullrom";}}return a.name;})()'%(json.dumps('animation.'+name),json.dumps(list(linear)+['gear']))
 call('risky_eval',{'code':code})
 print(name,':',sum(len(v) for v in bones.values()),'bone poses',flush=True)
(ROOT/'.work/animation-spec.json').write_text(json.dumps(clips,indent=2),'utf8')
r=call('export_model',{'codec_id':'project','max_content_length':2000000})
(ROOT/'.work/bb-export-response.json').write_text(json.dumps(r),'utf8')
print('Editable spline animations exported through Blockbench MCP.',flush=True)
