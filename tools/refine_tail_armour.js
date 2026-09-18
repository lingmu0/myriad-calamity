(() => {
  if(!Project?.name.includes('Cogwork Dancer'))throw new Error('Select the dancer project');
  const g={};Group.all.forEach(b=>g[b.name]=b);
  const tex=Texture.all.find(t=>t.name==='cogwork_dancer');
  function uv(mat){const x=mat%4*64,y=Math.floor(mat/4)*64;return [x+10,y+10,x+54,y+54];}
  function cube(name,bone,from,to,mat){
    const c=new Cube({name,from,to,origin:from.map((v,i)=>(v+to[i])/2),box_uv:false,autouv:0});
    for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=uv(mat);}c.addTo(g[bone]).init();
  }
  function panel(name,bone,outline,z,depth,mat){
    const n=outline.length,vertices=outline.map(p=>[p[0],p[1],z]).concat(outline.map(p=>[p[0],p[1],z+depth]));
    const m=new Mesh({name,origin:[0,0,0],vertices:{}});m.vertices={};m.faces={};const keys=m.addVertices(...vertices),faces=[],box=uv(mat);
    for(let i=0;i<n;i++){const j=(i+1)%n;faces.push([i,j,n+j,n+i]);}
    for(let i=1;i<n-1;i++){faces.push([0,i+1,i]);faces.push([n,n+i,n+i+1]);}
    for(const f of faces){const ids=f.map(i=>keys[i]),map={};ids.forEach((k,j)=>map[k]=[[box[0],box[3]],[box[2],box[3]],[box[2],box[1]],[box[0],box[1]]][j%4]);m.addFaces(new MeshFace(m,{vertices:ids,uv:map,texture:tex.uuid}));}
    m.addTo(g[bone]).init();
  }
  for(const m of Mesh.all.slice()){
    if(m.name.startsWith('tail_tip_')){m.remove();continue;}
    if(m.name.startsWith('coattail_layer_')){
      const i=Number(m.name.split('_').at(-1)),sign=m.name.includes('_-1')?-1:1,x=sign*(3.1+i*.35),z=1.5+i*.18+.31;
      for(const v of Object.values(m.vertices)){v[0]=x+(v[0]-x)*1.18;v[2]=z+(v[2]-z)*2.55;}
    }
  }
  for(const sign of [-1,1]){
    const s=sign<0?'left':'right';
    for(let i=0;i<3;i++){
      const bone=s+'_tail_'+i,x=sign*(3.1+i*.35),y=22.5-i*4.5,z=1.5+i*.18;
      g[bone].rotation=[[-20,-16,-12][i],sign*(i===0?9:3),sign*(i===0?18:5)];
      cube('tail_backing_'+s+'_'+i,bone,[x-1.52,y-4.55,z-.48],[x+1.52,y+.12,z+.06],2);
      cube('tail_overlap_lip_'+s+'_'+i,bone,[x-1.68,y-.25,z+1.02],[x+1.68,y+.38,z+1.7],1);
      cube('tail_recessed_inset_'+s+'_'+i,bone,[x-1.04,y-3.64,z+1.02],[x+1.04,y-1.03,z+1.4],7);
      cube('tail_dorsal_rib_'+s+'_'+i,bone,[x-.25,y-4.26,z+1.42],[x+.25,y-.42,z+1.85],1);
      for(const side of [-1,1])cube('tail_edge_rail_'+s+'_'+i+'_'+side,bone,[x+side*1.22-.17,y-4.18,z+.95],[x+side*1.22+.17,y-.53,z+1.55],0);
    }
    panel('broad_tail_terminal_'+s,s+'_tail_2',[[sign*3.15,11],[sign*5.85,10.7],[sign*5.5,5.65],[sign*4.5,4.6],[sign*3.25,5.65]],1.85,1.55,7);
    cube('tail_terminal_cap_'+s,s+'_tail_2',[sign*4.45-.8,6.4,3.37],[sign*4.45+.8,9.4,3.68],0);
  }
  Animation.all.forEach(a=>a.playing=false);Modes.options.edit.select();unselectAll();Canvas.updateAll();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length});
})()
