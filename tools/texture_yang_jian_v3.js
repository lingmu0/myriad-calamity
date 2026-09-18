(api) => {
  const {parts,bones,mat}=api;
  const size=2048,density=3;
  const canvas=document.createElement('canvas');canvas.width=size;canvas.height=size;
  const ctx=canvas.getContext('2d');ctx.fillStyle='#11151c';ctx.fillRect(0,0,size,size);
  const color=a=>'rgb('+a.map(v=>Math.max(0,Math.min(255,Math.round(v)))).join(',')+')';
  let seed=927146,px=2,py=2,rowHeight=0;
  function random(){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;}
  function tile(width,height,key,face){
    const w=Math.max(2,Math.ceil(width*density)),h=Math.max(2,Math.ceil(height*density));
    if(px+w+2>size){px=2;py+=rowHeight+2;rowHeight=0;}
    if(py+h+2>size)throw Error('UV atlas overflow at '+py);
    const m=mat[key];if(!m)throw Error('Unknown material '+key);
    const u=px,v=py,skin=/skin|eye|pupil/.test(key),cloth=/cloth|linen|red|leather/.test(key),hair=/hair/.test(key),metal=!skin&&!cloth&&!hair;
    const patches=Array.from({length:Math.ceil(h/3)+1},()=>Array.from({length:Math.ceil(w/3)+1},()=>random()-.5));
    const direction={north:1,south:.95,east:.95,west:.91,up:1.035,down:.8}[face];
    for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){
      let n=(random()-.5)*(m.noise||0)*1.8;
      if(metal){
        n+=patches[Math.floor(yy/3)][Math.floor(xx/3)]*13;
        n+=(.55-yy/Math.max(1,h-1))*6;
        if(w>3&&h>3){
          if(yy===0)n+=m.edge*.9;
          else if(yy===h-1)n-=m.edge*.72;
          if(xx===0)n+=m.edge*.55;
          else if(xx===w-1)n-=m.edge*.55;
          if(Math.min(xx,w-1-xx,yy,h-1-yy)===1)n-=3.2;
        }
        const wear=random();
        if(wear<.03)n-=15;
        else if(wear>.985)n+=17;
      }else if(cloth){
        n+=patches[Math.floor(yy/3)][Math.floor(xx/3)]*6;
        if(w>3)n+=Math.sin((xx+.5)/w*Math.PI)*5-4;
        if((xx+yy*2)%7===0)n+=1.8;
        if(xx===0||xx===w-1)n-=3;
      }else if(hair){
        n+=patches[Math.floor(yy/3)][Math.floor(xx/3)]*4;
        if(xx%4===0)n-=3;else if(xx%4===1)n+=2.5;
        if(yy===0)n+=2;
      }
      ctx.fillStyle=color(m.color.map(c=>c*(skin?1:direction)+n));ctx.fillRect(u+xx,v+yy,1,1);
    }
    px+=w+2;rowHeight=Math.max(rowHeight,h);return [u,v,u+w,v+h];
  }
  for(const p of parts){
    const d=p.to.map((v,i)=>v-p.from[i]);
    if(d.some(v=>!Number.isFinite(v)||v<=0))throw Error('Invalid box '+p.name);
    if(!bones[p.bone])throw Error('Missing group '+p.bone+' for '+p.name);
    p.uv={};
    for(const f of ['north','south','east','west','up','down']){
      const dims=f==='north'||f==='south'?[d[0],d[1]]:f==='east'||f==='west'?[d[2],d[1]]:[d[0],d[2]];
      p.uv[f]=tile(dims[0],dims[1],p.material,f);
    }
  }
  Project.texture_width=size;Project.texture_height=size;
  const tex=new Texture({name:'yang_jian_v3_atlas',render_mode:'default'}).fromDataURL(canvas.toDataURL()).add(false);
  for(const p of parts){
    const cube=new Cube({name:p.name,from:p.from,to:p.to,origin:p.origin,rotation:p.rotation,box_uv:false,autouv:0});
    for(const face of Object.keys(p.uv)){cube.faces[face].texture=tex.uuid;cube.faces[face].uv=p.uv[face];}
    cube.addTo(bones[p.bone]).init();
  }
  Modes.options.edit.select();unselectAll();Canvas.updateAll();
  const p=Preview.selected;p.setProjectionMode(true);p.camOrtho.zoom=.24;p.camera.position.set(70,51,-160);p.controls.target.set(1.6,34,0);p.camera.lookAt(p.controls.target);p.camOrtho.updateProjectionMatrix();p.controls.update();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length,texture:[size,size],atlasUsedHeight:py+rowHeight});
}
