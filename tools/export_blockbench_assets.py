"""Compile an actual Blockbench MCP-exported project into the mod's articulated mesh format."""
from pathlib import Path
import json,base64,math,hashlib
from animation_baker import bake
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'modeling/cogwork_dancer.bbmodel'
model=json.loads(SOURCE.read_text('utf8'))
RES=ROOT/'src/main/resources/assets/myriad_calamity'
groups={g['uuid']:g for g in model['groups']}; elements={e['uuid']:e for e in model['elements']}
count=0
def convert_vector(v):return [round(v[0],6),round(-v[1],6),round(v[2],6)]
def sub(a,b):return [a[i]-b[i] for i in range(3)]
def rotate(v,rotation):
 x,y,z=v
 for axis,angle in enumerate(rotation):
  a=math.radians(angle);c=math.cos(a);s=math.sin(a)
  if axis==0:y,z=y*c-z*s,y*s+z*c
  elif axis==1:x,z=x*c+z*s,-x*s+z*c
  else:x,y=x*c-y*s,x*s+y*c
 return [x,y,z]
def element_faces(element):
 if element['type']=='mesh':
  for face in element['faces'].values():
   points=[]
   for key in face['vertices']:
    v=rotate(element['vertices'][key],element.get('rotation',[0,0,0]))
    points.append(([v[i]+element['origin'][i] for i in range(3)],face['uv'][key]))
   yield points
 elif element['type']=='cube':
  x0,y0,z0=element['from'];x1,y1,z1=element['to'];origin=element['origin']
  sides={
   'north':[[x1,y0,z0],[x0,y0,z0],[x0,y1,z0],[x1,y1,z0]],
   'south':[[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]],
   'west':[[x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0]],
   'east':[[x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1]],
   'up':[[x0,y1,z1],[x1,y1,z1],[x1,y1,z0],[x0,y1,z0]],
   'down':[[x0,y0,z0],[x1,y0,z0],[x1,y0,z1],[x0,y0,z1]]}
  for name,positions in sides.items():
   face=element['faces'][name]
   if face.get('texture') is None:continue
   u0,v0,u1,v1=face['uv'];uvs=[[u0,v1],[u1,v1],[u1,v0],[u0,v0]]
   shift=int(face.get('rotation',0)/90)%4;uvs=uvs[shift:]+uvs[:shift]
   points=[]
   for pos,uv in zip(positions,uvs):
    v=rotate(sub(pos,origin),element.get('rotation',[0,0,0]))
    points.append(([v[i]+origin[i] for i in range(3)],uv))
   yield points
 else:raise ValueError('Unsupported geometry: '+element['type'])
def node(entry,parent_origin):
 global count
 g=groups[entry['uuid']];origin=g['origin']
 out={'name':g['name'],'pivot':convert_vector(sub(origin,parent_origin)), 'rotation':[-g['rotation'][0],g['rotation'][1],-g['rotation'][2]],'faces':[],'children':[]}
 for child in entry['children']:
  if isinstance(child,dict):out['children'].append(node(child,origin));continue
  element=elements[child]
  for points in element_faces(element):
   if len(points) not in (3,4):raise ValueError('Triangulate polygons before export')
   vertices=[convert_vector(sub(pos,origin))+[round(v/256,7) for v in uv] for pos,uv in reversed(points)]
   a=sub(vertices[1],vertices[0]);b=sub(vertices[2],vertices[0])
   normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
   length=math.sqrt(sum(v*v for v in normal))
   if length<1e-7:continue
   normal=[round(v/length,6) for v in normal]
   if len(vertices)==3:vertices.append(vertices[-1])
   first_uv=points[0][1];material=int(first_uv[0]//64)+int(first_uv[1]//64)*4
   out['faces'].append({'v':vertices,'n':normal,'glow':material in [4,5]});count+=1
 return out
mesh={'format':1,'source':'Blockbench MCP 1.6.1','texture_size':[256,256],'bones':[node(n,[0,0,0]) for n in model['outliner']]}
clips={}
for clip in model['animations']:
 bones={}
 for uid,animator in clip['animators'].items():
  if uid not in groups:continue
  channels={}
  for frame in animator.get('keyframes',[]):
   kind=frame['channel'];point=frame['data_points'][0]
   values=[float(point.get(axis,1 if kind=='scale' else 0)) for axis in 'xyz']
   if kind=='position':values=convert_vector(values)
   if kind=='rotation':values=[-values[0],values[1],-values[2]]
   channels.setdefault(kind,[]).append({'time':frame['time'],'values':values,'mode':frame['interpolation']})
  channels={kind:bake(keys,clip['length'],clip['loop']=='loop') for kind,keys in channels.items()}
  bones[groups[uid]['name']]=channels
 name=clip['name'].removeprefix('animation.')
 clips[name]={'length':clip['length'],'loop':clip['loop']=='loop','bones':bones}
for texture in model['textures']:
 name=texture['name'].removesuffix('.png')
 path=RES/f'textures/entity/{name}.png';path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(base64.b64decode(texture['source'].split(',',1)[1]))
for path,obj in [(RES/'mesh/cogwork_dancer.json',mesh),(RES/'animations/cogwork_dancer.json',{'format':1,'sampling_fps':60,'interpolation_source':'Blockbench SplineCurve / linear','clips':clips})]:
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,separators=(',',':')),'utf8')
manifest={'created_with':'Blockbench MCP','server_version':'1.6.1','source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'cubes':sum(e['type']=='cube' for e in elements.values()),'meshes':sum(e['type']=='mesh' for e in elements.values()),'bones':len(groups),'rendered_faces':count,'animations':list(clips),'texture_size':[256,256],'sampling_fps':60,'interpolation':'Editable Catmull-Rom, baked at 60 Hz'}
(ROOT/'modeling/mcp-export-manifest.json').write_text(json.dumps(manifest,indent=2),'utf8')
print(f'Imported Blockbench output: {len(elements)} cuboid/mesh elements, {len(groups)} bones, {count} faces, {len(clips)} animations.')
