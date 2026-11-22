"""Bake scalar animation curves to portable 60 Hz channels.
Catmull-Rom retains Blockbench's SplineCurve behavior. The optional ``pchip``
mode uses nonuniform, shape-preserving cubic Hermite interpolation; each axis
stays between its two segment endpoints. A looping PCHIP curve requires an
explicit final key equal to its first key and shares tangents across that seam.
Angles are not wrapped: this is essential for full-turn animations.
"""
import math


def _pchip_join(h_before, h_after, slope_before, slope_after):
 """Weighted harmonic tangent at a key, zero at plateaus or turning points."""
 if slope_before == 0 or slope_after == 0 or (slope_before > 0) != (slope_after > 0):
  return 0.0
 w_before = 2*h_after+h_before
 w_after = h_after+2*h_before
 return (w_before+w_after)/(w_before/slope_before+w_after/slope_after)


def _pchip_endpoint(h_edge, h_inner, slope_edge, slope_inner):
 tangent = ((2*h_edge+h_inner)*slope_edge-h_edge*slope_inner)/(h_edge+h_inner)
 if tangent == 0 or (tangent > 0) != (slope_edge > 0):
  return 0.0
 if (slope_edge > 0) != (slope_inner > 0) and abs(tangent) > 3*abs(slope_edge):
  return 3*slope_edge
 return tangent


def _pchip_tangent(keys, index, axis, loop):
 def segment(left, right):
  width = keys[right]['time']-keys[left]['time']
  if width <= 0:
   raise ValueError('PCHIP key times must be strictly increasing')
  return width, (keys[right]['values'][axis]-keys[left]['values'][axis])/width

 last = len(keys)-1
 if last == 1:
  return segment(0, 1)[1]
 if index == 0 or index == last:
  if loop:
   h_before, slope_before = segment(last-1, last)
   h_after, slope_after = segment(0, 1)
   return _pchip_join(h_before, h_after, slope_before, slope_after)
  if index == 0:
   h_edge, slope_edge = segment(0, 1)
   h_inner, slope_inner = segment(1, 2)
  else:
   h_edge, slope_edge = segment(last-1, last)
   h_inner, slope_inner = segment(last-2, last-1)
  return _pchip_endpoint(h_edge, h_inner, slope_edge, slope_inner)
 h_before, slope_before = segment(index-1, index)
 h_after, slope_after = segment(index, index+1)
 return _pchip_join(h_before, h_after, slope_before, slope_after)


def _sample_pchip(keys, left, time, loop):
 if loop and any(abs(a-b) > 1e-9 for a, b in zip(keys[0]['values'], keys[-1]['values'])):
  raise ValueError('Looping PCHIP requires equal first and final key values')
 b, c = keys[left], keys[left+1]
 width = c['time']-b['time']
 if width <= 0:
  raise ValueError('PCHIP key times must be strictly increasing')
 t = (time-b['time'])/width
 result = []
 for axis in range(3):
  p, q = b['values'][axis], c['values'][axis]
  m0 = width*_pchip_tangent(keys, left, axis, loop)
  m1 = width*_pchip_tangent(keys, left+1, axis, loop)
  value = (2*t**3-3*t*t+1)*p+(t**3-2*t*t+t)*m0+(-2*t**3+3*t*t)*q+(t**3-t*t)*m1
  # PCHIP is bounded analytically; contain floating-point noise at flat keys too.
  result.append(max(min(p, q), min(max(p, q), value)))
 return result


def sample(keys,time,loop=False):
 if not keys:return [0.,0.,0.]
 right=next((i for i,k in enumerate(keys) if k['time']>=time),len(keys))
 if right==0:return keys[0]['values'][:]
 if right==len(keys):return keys[-1]['values'][:]
 b=keys[right-1];c=keys[right]
 if b['mode']!='step' and (b['mode']=='pchip' or c['mode']=='pchip'):
  return _sample_pchip(keys,right-1,time,loop)
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
