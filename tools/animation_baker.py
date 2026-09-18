"""Bake Blockbench's scalar SplineCurve interpolation to portable 60 Hz channels.
The saved .bbmodel retains sparse editable Catmull-Rom keyframes. Angles are
not wrapped: this is essential for full-turn dance and clockwork animations.
"""
import math

def sample(keys,time,loop=False):
 if not keys:return [0.,0.,0.]
 right=next((i for i,k in enumerate(keys) if k['time']>=time),len(keys))
 if right==0:return keys[0]['values'][:]
 if right==len(keys):return keys[-1]['values'][:]
 b=keys[right-1];c=keys[right]
 if abs(time-c['time'])<1/1200:return c['values'][:]
 if abs(time-b['time'])<1/1200 or b['mode']=='step':return b['values'][:]
 t=(time-b['time'])/(c['time']-b['time'])
 if b['mode']=='catmullrom' or c['mode']=='catmullrom':
  a=keys[right-2] if right>1 else (keys[-2] if loop and len(keys)>=3 else b)
  d=keys[right+1] if right+1<len(keys) else (keys[1] if loop and len(keys)>=3 else c)
  result=[]
  for i in range(3):
   v0=(c['values'][i]-a['values'][i])*.5;v1=(d['values'][i]-b['values'][i])*.5
   p=b['values'][i];q=c['values'][i]
   result.append((2*p-2*q+v0+v1)*t**3+(-3*p+3*q-2*v0-v1)*t*t+v0*t+p)
  return result
 if b['mode'] not in ('linear','step') or c['mode'] not in ('linear','step'):
  raise ValueError('Unsupported Blockbench interpolation; add an explicit baker for this mode')
 return [b['values'][i]+(c['values'][i]-b['values'][i])*t for i in range(3)]

def bake(keys,length,loop=False,fps=60):
 keys=sorted(keys,key=lambda k:k['time'])
 times=sorted(set([round(i/fps,7) for i in range(math.floor(length*fps)+1)]+[length]+[k['time'] for k in keys]))
 return [[round(t,7)]+[round(v,6) for v in sample(keys,t,loop)] for t in times]
