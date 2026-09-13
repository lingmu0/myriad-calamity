(() => {
  if(!Project || !Project.name.includes('Cogwork Dancer'))throw new Error('Select the Cogwork Dancer project first');
  const groups={};Group.all.forEach(g=>groups[g.name]=g);
  const tex=Texture.all.find(t=>t.name==='cogwork_dancer');
  if(!tex || !groups.body)throw new Error('Missing dancer rig or texture');
  [...Outliner.elements].forEach(e=>{if(e instanceof Mesh || e instanceof Cube)e.remove();});
  function uv(mat){const x=mat%4*64,y=Math.floor(mat/4)*64;return [x+10,y+10,x+54,y+54];}
  function cube(name,bone,from,to,mat=0,rotation=[0,0,0],origin){
    const c=new Cube({name,from,to,origin:origin||[(from[0]+to[0])/2,(from[1]+to[1])/2,(from[2]+to[2])/2],rotation,box_uv:false,autouv:0});
    for(const face of Object.values(c.faces)){face.texture=tex.uuid;face.uv=uv(mat);}
    c.addTo(groups[bone]).init();return c;
  }
  function mesh(name,bone,vertices,faces,mat=0){
    const m=new Mesh({name,origin:[0,0,0],vertices:{}});m.vertices={};m.faces={};const keys=m.addVertices(...vertices);const box=uv(mat);
    for(const indices of faces){const ids=indices.map(i=>keys[i]),map={};ids.forEach((k,j)=>map[k]=[[box[0],box[3]],[box[2],box[3]],[box[2],box[1]],[box[0],box[1]]][j%4]);m.addFaces(new MeshFace(m,{vertices:ids,uv:map,texture:tex.uuid}));}
    m.addTo(groups[bone]).init();return m;
  }
  function panel(name,bone,outline,z,depth,mat=0){
    const n=outline.length,v=outline.map(p=>[p[0],p[1],z]).concat(outline.map(p=>[p[0],p[1],z+depth])),f=[];
    for(let i=0;i<n;i++){let j=(i+1)%n;f.push([i,j,n+j,n+i]);}
    for(let i=1;i<n-1;i++){f.push([0,i+1,i]);f.push([n,n+i,n+i+1]);}return mesh(name,bone,v,f,mat);
  }
  function beam(name,bone,a,b,width,depth,mat=2){const len=Math.hypot(b[0]-a[0],b[1]-a[1]);return cube(name,bone,[a[0]-width/2,a[1],a[2]-depth/2],[a[0]+width/2,a[1]+len,a[2]+depth/2],mat,[0,0,-Math.atan2(b[0]-a[0],b[1]-a[1])*180/Math.PI],a);}
  function blade(name,bone,path,widths,mat=3){const v=[],f=[];
    path.forEach((p,i)=>{let a=path[Math.max(0,i-1)],b=path[Math.min(path.length-1,i+1)],dx=b[0]-a[0],dy=b[1]-a[1],len=Math.hypot(dx,dy)||1,nx=-dy/len*widths[i],ny=dx/len*widths[i];
      v.push([p[0]+nx,p[1]+ny,p[2]-.3],[p[0]-nx,p[1]-ny,p[2]-.3],[p[0]-nx,p[1]-ny,p[2]+.3],[p[0]+nx,p[1]+ny,p[2]+.3]);});
    for(let i=0;i<path.length-1;i++)for(let j=0;j<4;j++)f.push([i*4+j,i*4+(j+1)%4,(i+1)*4+(j+1)%4,(i+1)*4+j]);f.push([3,2,1,0]);let k=(path.length-1)*4;f.push([k,k+1,k+2,k+3]);return mesh(name,bone,v,f,mat);
  }
  cube('inner_clockwork_frame','body',[-1.65,21,-1.55],[1.65,36,1.55],2);
  cube('neck_joint','body',[-1,35.8,-1],[1,38.1,1],2);
  cube('neck_gold_collar','body',[-2.15,35.5,-1.8],[2.15,36.4,1.8],1);
  panel('bevelled_breastplate','body',[[-3.8,28.2],[-4.6,32.8],[-3.4,35.6],[3.4,35.6],[4.6,32.8],[3.8,28.2]],-2.5,4.8,0);
  cube('upper_chest_overlap','body',[-3.6,33.5,-2.9],[3.6,35.3,-2.25],1);
  cube('main_chest_inset','body',[-3.4,30.8,-2.94],[3.4,33.35,-2.4],0);
  cube('chest_lower_lamella','body',[-3.1,28.5,-2.92],[3.1,30.6,-2.3],7);
  cube('chest_central_seam','body',[-.18,28.3,-3.07],[.18,35.5,-2.88],2);
  for(let side of [-1,1]){
    cube('chest_side_rail_'+side,'body',[side<0?-3.7:3.38,28.8,-3.02],[side<0?-3.38:3.7,34.1,-2.8],1);
    for(let i=0;i<3;i++)cube('chest_rivet_'+side+'_'+i,'body',[side*2.8-.21,29.4+i*2.2,-3.2],[side*2.8+.21,29.82+i*2.2,-2.98],3);
    for(let i=0;i<3;i++)cube('side_vent_'+side+'_'+i,'body',[side<0?-4.3:3.4,29.2+i*1.15,-.5],[side<0?-3.4:4.3,29.55+i*1.15,1.45],2);
  }
  for(let i=0;i<4;i++){
    const y=18+i*2.3,w=1.9+i*.28;
    cube('abdomen_shadow_joint_'+i,'body',[-w+.2,y-.2,-1.4],[w-.2,y+.5,1.4],2);
    cube('abdomen_plate_'+i,'body',[-w,y+.2,-1.9],[w,y+2.1,1.6],i%2?7:0);
    cube('abdomen_overlap_lip_'+i,'body',[-w-.18,y+1.62,-2.13],[w+.18,y+2.15,-1.65],1);
    cube('abdomen_engraving_'+i,'body',[-.15,y+.5,-2.05],[.15,y+1.5,-1.85],2);
  }
  cube('square_waist_frame','body',[-1.7,25,-2.4],[1.7,28.2,-1.5],2);
  cube('square_waist_trim','body',[-1.25,25.45,-2.66],[1.25,27.85,-2.36],1);
  cube('square_core_recess','body',[-.85,25.8,-2.82],[.85,27.5,-2.64],6);
  cube('square_clockwork_core','body',[-.55,26.08,-2.98],[.55,27.2,-2.8],5);
  panel('cut_corner_helmet','head',[[-3.1,37],[-4.4,38.2],[-4.4,41.1],[-3.3,42.5],[3.3,42.5],[4.4,41.1],[4.4,38.2],[3.1,37]],-2.55,5.1,0);
  cube('visor_dark_inset','head',[-3.7,38.4,-2.82],[3.7,41.15,-2.53],6);
  cube('layered_brow_shadow','head',[-4.65,41.2,-2.75],[4.65,41.65,2.75],2);
  cube('layered_brow_gold','head',[-4.55,41.6,-2.92],[4.55,42.25,2.72],1);
  cube('layered_helmet_top','head',[-3.55,42.15,-2.5],[3.55,42.75,2.45],0);
  cube('central_mask_divider','head',[-.22,37.8,-2.98],[.22,41.15,-2.7],1);
  for(let side of [-1,1]){
    const x=side*2.3;
    cube('square_eye_socket_'+side,'head',[x-1.07,38.6,-2.99],[x+1.07,40.96,-2.79],1);
    cube('square_glowing_eye_'+side,'head',[x-.76,38.9,-3.15],[x+.76,40.66,-2.98],4);
    cube('eye_inner_highlight_'+side,'head',[x-.42,39.18,-3.19],[x+.42,40.38,-3.13],4);
    cube('helmet_side_rivet_'+side,'head',[side<0?-4.62:4.38,39.4,-.4],[side<0?-4.38:4.62,40.15,.4],3);
    cube('jaw_armour_'+side,'head',[side<0?-3.2:.5,37.45,-2.81],[side<0?-.5:3.2,38.1,-2.5],7);
  }
  const arch=[[-4.7,42.1,-.2],[-4.05,44.5,-.2],[0,47,-.2],[4.05,44.5,-.2],[4.7,42.1,-.2]];
  for(let i=0;i<arch.length-1;i++)beam('angular_crown_arch_'+i,'head',arch[i],arch[i+1],.48,.75,3);
  for(let side of [-1,1]){
    blade('outer_crown_spire_'+side,'head',[[side*4.5,43.1,0],[side*5,46.2,0],[side*5.5,50,0]],[.42,.26,.025],3);
    blade('inner_crown_spire_'+side,'head',[[side*2.8,45,0],[side*2.8,48.1,0],[side*2.8,52,0]],[.48,.3,.025],3);
    cube('crown_socket_'+side,'head',[side*2.8-.5,44.2,-.5],[side*2.8+.5,45.3,.5],1);
  }
  blade('centre_crown_spire','head',[[0,45.6,-.4],[0,48,-.4],[0,49.1,-.4],[0,55,-.4]],[.35,.92,.42,.025],3);
  panel('crown_diamond','head',[[0,42.7],[-.55,43.7],[0,44.7],[.55,43.7]],-.9,.4,3);
  for(let side of [-1,1]){
    const arm=side<0?'left_arm':'right_arm',leg=side<0?'left_leg':'right_leg';
    const reflect=points=>points.map(p=>[p[0]*side,p[1]]);
    panel('pauldron_underframe_'+side,arm,reflect([[4.8,29.7],[8.8,31.5],[9.2,36.8],[6,34.6]]),-.35,2.3,2);
    panel('pauldron_main_plate_'+side,arm,reflect([[4.3,30.4],[8.2,32.1],[9.5,37.4],[6.2,35.25]]),-1,1.2,0);
    panel('pauldron_highlight_ridge_'+side,arm,reflect([[5.9,34.5],[8.65,36.25],[9.5,37.4],[6.2,35.25]]),-1.14,.3,1);
    panel('pauldron_lower_layer_'+side,arm,reflect([[4.6,29.5],[8.8,29.9],[7.6,32.1],[4.5,32]]),-.7,.8,7);
    cube('shoulder_bolt_'+side,arm,[side*6.1-.38,32.1,-1.32],[side*6.1+.38,32.86,-.98],3);
    beam('upper_arm_piston_'+side,arm,[side*6.8,32,.1],[side*11.3,30,.1],.85,1.2,2);
    beam('upper_arm_piston_rail_'+side,arm,[side*7.1,32,-.65],[side*10.8,30.3,-.65],.28,.28,3);
    cube('square_elbow_housing_'+side,arm,[side*11.4-.9,29.25,-.9],[side*11.4+.9,31.05,.9],2);
    cube('square_elbow_cap_'+side,arm,[side*11.4-.6,29.55,-1.15],[side*11.4+.6,30.75,-.88],1);
    cube('square_elbow_bolt_'+side,arm,[side*11.4-.22,29.93,-1.25],[side*11.4+.22,30.37,-1.13],3);
    beam('forearm_main_'+side,arm,[side*11.6,30,-.1],[side*16.7,33,-.1],1.05,1.1,0);
    beam('forearm_raised_plate_'+side,arm,[side*12.8,30.7,-.77],[side*15.7,32.4,-.77],.65,.3,1);
    blade('wrist_guard_'+side,arm,[[side*15.8,32.3,-.1],[side*16.2,35.3,-.1],[side*16.2,37.0,-.1]],[.65,.4,.025],2);
    blade('stepped_scythe_'+side,arm,[[side*16.1,33.1,-.4],[side*18.2,32.7,-.4],[side*20.1,29.9,-.4],[side*21.9,25.7,-.4],[side*22.9,20,-.4]],[.65,1.0,.85,.6,.025],3);
    blade('scythe_dark_back_'+side,arm,[[side*16.2,33.55,.03],[side*18.7,32.9,.03],[side*20.6,30,.03],[side*22.1,26,.03]],[.18,.24,.2,.025],2);
    beam('upper_leg_frame_'+side,leg,[side*1.9,19,0],[side*2.8,11.1,0],.8,.95,2);
    beam('thigh_armour_'+side,leg,[side*2,18.5,-.62],[side*2.45,14.5,-.62],1.1,.38,7);
    cube('square_knee_'+side,leg,[side*2.8-.7,10.3,-.75],[side*2.8+.7,11.7,.6],2);
    cube('knee_front_guard_'+side,leg,[side*2.8-.48,10.5,-1],[side*2.8+.48,11.5,-.73],1);
    beam('lower_leg_frame_'+side,leg,[side*2.8,10.4,0],[side*3.8,2.3,-.7],.65,.8,2);
    beam('shin_plate_'+side,leg,[side*2.9,9.8,-.55],[side*3.5,5,-.55],.9,.35,3);
    blade('pointed_foot_'+side,leg,[[side*3.6,4,-.6],[side*4,2,-1],[side*4.8,.45,-2]],[.45,.6,.025],2);
    for(let i=0;i<3;i++){
      let y=22.5-i*4.5,x=side*(3.1+i*.35);
      panel('coattail_layer_'+side+'_'+i,'skirt',[[x-side*1.0,y],[x+side*1.65,y+.45],[x+side*1.5,y-4.75],[x-side*.25,y-5.5]],1.5+i*.18,.62,0);
      beam('coattail_trim_'+side+'_'+i,'skirt',[x+side*1.55,y+.3,1.25+i*.18],[x+side*1.4,y-4.5,1.25+i*.18],.22,.25,1);
    }
    panel('tail_tip_'+side,'skirt',[[side*3.6,10.5],[side*5.4,10.2],[side*3.4,2.5]],2,.6,7);
    panel('flared_hip_plate_'+side,'skirt',reflect([[2.3,23.7],[4.5,22],[7.4,16.6],[3.5,18.7]]),.55,.7,7);
    cube('hip_rivet_'+side,'skirt',[side*3.5-.25,21.2,.25],[side*3.5+.25,21.7,.58],3);
  }
  cube('square_back_gear','gear',[-2.25,26.75,2.4],[2.25,31.25,3.2],2);
  cube('back_gear_inset','gear',[-1.6,27.4,3.2],[1.6,30.6,3.65],7);
  cube('back_gear_axle','gear',[-.6,28.4,3.65],[.6,29.6,4],3);
  for(let i=-1;i<=1;i++)for(let side of [-1,1]){
    cube('gear_tooth_h_'+i+'_'+side,'gear',[side<0?-2.9:2.05,28.65+i*1.45,2.6],[side<0?-2.05:2.9,29.35+i*1.45,3.35],1);
    cube('gear_tooth_v_'+i+'_'+side,'gear',[-.35+i*1.45,side<0?26.1:31.05,2.6],[.35+i*1.45,side<0?26.95:31.9,3.35],1);
  }
  const sigil=[[-10,40,3.6],[0,52,3.6],[10,40,3.6],[0,28,3.6],[-10,40,3.6]];
  for(let i=0;i<4;i++){
    const a=sigil[i],b=sigil[i+1];const mix=t=>a.map((v,k)=>v+(b[k]-v)*t);
    beam('angular_telegraph_'+i+'_a','halo',mix(.06),mix(.43),.13,.13,4);
    beam('angular_telegraph_'+i+'_b','halo',mix(.57),mix(.94),.13,.13,4);
  }
  Modes.options.edit.select();Canvas.updateAll();Preview.selected.camera.zoom=.43;Preview.selected.camera.updateProjectionMatrix();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length,animations:Animation.all.length});
})()
