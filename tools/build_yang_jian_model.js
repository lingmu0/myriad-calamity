(()=>{
  Project.texture_width=256; Project.texture_height=256;
  const palettes=[
    ['#dfe7e7','#6e7b83'],['#5b6670','#1d2730'],['#22354a','#0a1422'],['#e4bd68','#79502a'],
    ['#e8b69b','#8a4f43'],['#7b3432','#2a151b'],['#1d2028','#05070b'],['#eee8d9','#a6a69e'],
    ['#9ffcff','#207e93'],['#4d3327','#1b100e'],['#b6c5c8','#53636b'],['#a46b3f','#4d271d'],
    ['#e5d3a5','#8c6b3d'],['#7d93a5','#243747'],['#c5cbd1','#596168'],['#f0e8c9','#8d7445']
  ];
  function makeTexture(name,bright){
    const c=document.createElement('canvas');c.width=256;c.height=256;const ctx=c.getContext('2d');
    ctx.fillStyle='#151b24';ctx.fillRect(0,0,256,256);
    palettes.forEach((pair,i)=>{
      const x=(i%4)*64,y=Math.floor(i/4)*64;let a=pair[0],b=pair[1];
      if(bright&&i===0){a='#f5fbfb';b='#8fa6ab';}
      const g=ctx.createLinearGradient(x,y,x+64,y+64);g.addColorStop(0,a);g.addColorStop(.38,a);g.addColorStop(1,b);ctx.fillStyle=g;ctx.fillRect(x,y,64,64);
      for(let py=0;py<64;py+=8)for(let px=0;px<64;px+=8){ctx.fillStyle=(px+py)%16===0?'rgba(255,255,255,.18)':'rgba(0,0,0,.08)';ctx.fillRect(x+px,y+py,8,8);}
      ctx.fillStyle='rgba(255,255,255,.16)';ctx.fillRect(x+5,y+4,2,56);ctx.fillStyle='rgba(0,0,0,.2)';ctx.fillRect(x+57,y+4,2,56);
      if(i===3||i===12){ctx.fillStyle='rgba(255,244,180,.45)';ctx.fillRect(x+28,y+11,8,42);ctx.fillRect(x+11,y+28,42,8);}
      if(i===8){ctx.fillStyle='#d7ffff';ctx.fillRect(x+18,y+18,28,28);}
    });
    return new Texture({name,render_mode:'default'}).fromDataURL(c.toDataURL()).add(false);
  }
  const tex=makeTexture('yang_jian',false); makeTexture('yang_jian_glow',true);
  const g={};
  function group(name,origin,parent){const q=new Group({name,origin}).addTo(parent?g[parent]:'root').init();g[name]=q;return q;}
  group('body',[0,18,0]);group('torso',[0,24,0],'body');group('chest',[0,26,0],'torso');group('pelvis',[0,18,0],'body');
  group('head',[0,33,0],'body');group('hair',[0,33,1.1],'head');group('crown',[0,37.2,0],'head');
  group('left_arm',[-4.8,27,0],'torso');group('left_forearm',[-7.1,22.7,0],'left_arm');group('left_hand',[-8.1,18.6,0],'left_forearm');
  group('right_arm',[4.8,27,0],'torso');group('right_forearm',[6.2,23,0],'right_arm');group('right_hand',[6.4,20,0],'right_forearm');
  group('left_leg',[-2.1,18,0],'pelvis');group('left_knee',[-2.35,10.2,0],'left_leg');group('left_boot',[-2.75,3.2,0],'left_knee');
  group('right_leg',[2.1,18,0],'pelvis');group('right_knee',[2.35,10.2,0],'right_leg');group('right_boot',[2.75,3.2,0],'right_knee');
  group('cloth',[0,19,1.2],'body');group('spear',[-8,0,0],'body');
  function uv(mat){const x=(mat%4)*64,y=Math.floor(mat/4)*64;return [x+9,y+9,x+55,y+55];}
  function cube(name,bone,from,to,mat=0,rotation=[0,0,0],origin){const c=new Cube({name,from,to,origin:origin||from.map((v,i)=>(v+to[i])/2),rotation,box_uv:false,autouv:0});for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=uv(mat);}c.addTo(g[bone]).init();return c;}
  function mesh(name,bone,vertices,faces,mat=0){const m=new Mesh({name,origin:[0,0,0],vertices:{}});m.vertices={};m.faces={};const keys=m.addVertices(...vertices),box=uv(mat);for(const ids0 of faces){const ids=ids0.map(i=>keys[i]),map={};ids.forEach((k,j)=>map[k]=[[box[0],box[3]],[box[2],box[3]],[box[2],box[1]],[box[0],box[1]]][j%4]);m.addFaces(new MeshFace(m,{vertices:ids,uv:map,texture:tex.uuid}));}m.addTo(g[bone]).init();return m;}
  function panel(name,bone,outline,z,depth,mat=0){const n=outline.length,v=outline.map(p=>[p[0],p[1],z]).concat(outline.map(p=>[p[0],p[1],z+depth])),f=[];for(let i=0;i<n;i++){const j=(i+1)%n;f.push([i,j,n+j,n+i]);}for(let i=1;i<n-1;i++){f.push([0,i+1,i]);f.push([n,n+i,n+i+1]);}return mesh(name,bone,v,f,mat);}
  function beam(name,bone,a,b,width,depth,mat=0){const len=Math.hypot(b[0]-a[0],b[1]-a[1]);const c=cube(name,bone,[a[0]-width/2,a[1],a[2]-depth/2],[a[0]+width/2,a[1]+len,a[2]+depth/2],mat,[0,0,-Math.atan2(b[0]-a[0],b[1]-a[1])*180/Math.PI],a);return c;}
  function rivets(prefix,bone,xs,y,z,mat=3){xs.forEach((x,i)=>cube(prefix+i,bone,[x-.16,y-.16,z-.18],[x+.16,y+.16,z+.18],mat));}

  cube('underlayer','body',[-3.25,19.2,-1.45],[3.25,29.5,1.5],2);
  cube('back_plate','body',[-3.1,21.2,1.35],[3.1,29.3,2.2],1);
  panel('chest_outer_plate','chest',[[-3.9,22.9],[-3.35,29.5],[-2.2,31.1],[2.2,31.1],[3.35,29.5],[3.9,22.9]],-2.05,.68,0);
  cube('chest_inner_plate','chest',[-3.08,24,-2.28],[3.08,29.2,-1.95],1);
  cube('chest_upper_lip','chest',[-2.8,29,-2.48],[2.8,30.2,-2.08],3);
  cube('chest_lower_lamella','chest',[-2.95,22.8,-2.38],[2.95,24.4,-1.98],7);
  cube('sternum_rail','chest',[-.2,23.9,-2.62],[.2,30.05,-2.34],3);
  for(let side of [-1,1]){
    cube('rib_guard_'+side,'chest',[side*3.1-.52,24.4,-2.5],[side*3.65+.52*side,29.1,-2.05],1,[0,0,side*4]);
    cube('rib_inlay_'+side,'chest',[side*3.63-.12,25.1,-2.68],[side*3.63+.12,28.6,-2.48],3);
    for(let i=0;i<3;i++)cube('rib_rivet_'+side+'_'+i,'chest',[side*(2.35+i*.38)-.14,25.1+i*1.15,-2.67],[side*(2.35+i*.38)+.14,25.38+i*1.15,-2.45],3);
  }
  cube('cloud_emblem_base','chest',[-1.25,25.2,-2.7],[1.25,27.15,-2.45],3);
  panel('cloud_emblem','chest',[[0,25.15],[-.9,26.1],[0,27.65],[.9,26.1]],-2.78,.18,12);
  cube('waist_under','pelvis',[-3.45,18,-1.6],[3.45,21.4,1.5],1);
  cube('waist_belt','pelvis',[-3.8,19.4,-2.0],[3.8,20.6,-1.45],3);
  cube('waist_belt_dark','pelvis',[-3.55,18.7,-2.12],[3.55,19.45,-1.75],9);
  cube('belt_buckle','pelvis',[-1.0,18.65,-2.45],[1.0,20.9,-2.1],3);
  cube('belt_buckle_inset','pelvis',[-.56,19.1,-2.62],[.56,20.25,-2.4],12);
  rivets('belt_rivet_', 'pelvis',[-2.9,-1.9,1.9,2.9],20.05,-2.3,12);
  for(let i=0;i<4;i++){
    const y=20.9-i*.62,w=2.9-i*.15;
    cube('abdomen_lamella_'+i,'pelvis',[-w,y,-1.84],[w,y+.54,-1.35],i%2?1:0,[0,0,(i%2?1:-1)*2]);
  }
  panel('front_cloth_left','cloth',[[-3.35,19.1],[-1.2,18.6],[-1.35,9.7],[-2.65,11.1]],-1.72,.58,2);
  panel('front_cloth_right','cloth',[[1.2,18.6],[3.35,19.1],[2.65,11.1],[1.35,9.7]],-1.72,.58,2);
  panel('front_sash_left','cloth',[[-1.7,19.4],[-.35,19.1],[-.5,10.1],[-1.55,8.6]],-2.05,.34,5);
  panel('front_sash_right','cloth',[[.35,19.1],[1.7,19.4],[1.55,8.6],[.5,10.1]],-2.05,.34,5);
  panel('rear_cloak','cloth',[[-4.8,20.2],[-3.4,17.8],[-4.9,8.3],[0,6.1],[4.9,8.3],[3.4,17.8],[4.8,20.2]],1.55,.62,2);
  panel('rear_cloak_inner','cloth',[[-3.75,18],[-2.8,15.8],[-3.5,9],[0,7.1],[3.5,9],[2.8,15.8],[3.75,18]],2.22,.36,7);
  for(let side of [-1,1]){
    const s=side<0?'left':'right';
    cube('shoulder_mount_'+s,s+'_arm',[side*4.55-1.25,26.2,-1.35],[side*4.55+1.25,28.9,.9],1,[0,0,side*5]);
    panel('pauldron_outer_'+s,s+'_arm',side<0?[[ -5.2,27.9],[-8.3,29.2],[-9.2,27.4],[-7.1,25.8],[-4.7,26.3]]:[[5.2,27.9],[8.3,29.2],[9.2,27.4],[7.1,25.8],[4.7,26.3]],-1.55,.85,0);
    panel('pauldron_layer_'+s,s+'_arm',side<0?[[-5.1,26.7],[-8.1,27.7],[-8.3,26.4],[-6.7,25.2],[-4.7,25.7]]:[[5.1,26.7],[8.1,27.7],[8.3,26.4],[6.7,25.2],[4.7,25.7]],-1.74,.35,3);
    cube('shoulder_gold_trim_'+s,s+'_arm',[side*6.55-.18,26.05,-1.86],[side*6.55+.18,28.2,-1.58],3);
    beam('upper_arm_armour_'+s,s+'_arm',[side*5.25,25.8,-.3],[side*6.9,22.4,-.3],1.45,1.2,1);
    cube('upper_arm_plate_'+s,s+'_arm',[side*5.75-.82,24,-1.08],[side*7.15+.82,25.2,-.72],0,[0,0,side*12]);
    cube('elbow_housing_'+s,s+'_forearm',[side*7.25-.95,21.35,-.95],[side*7.25+.95,23.15,.95],1);
    cube('elbow_pin_'+s,s+'_forearm',[side*7.25-.35,22.0,-1.22],[side*7.25+.35,22.55,-1.0],3);
    beam('forearm_armour_'+s,s+'_forearm',[side*7.2,21.8,-.15],[side*8.0,19.0,-.15],1.35,1.1,0);
    cube('forearm_lamella_'+s,s+'_forearm',[side*7.65-.83,19.2,-1.03],[side*8.45+.83,20.9,-.7],7,[0,0,side*10]);
    cube('wrist_cuff_'+s,s+'_hand',[side*8.05-1.05,18.05,-.85],[side*8.05+1.05,19.15,.85],3);
    cube('gauntlet_'+s,s+'_hand',[side*8.05-.95,16.8,-1.0],[side*8.05+.95,18.25,.95],1);
    for(let i=0;i<3;i++)cube('finger_'+s+'_'+i,s+'_hand',[side*(7.45+i*.55)-.2,16.15,-.96],[side*(7.75+i*.55)+.2,17.1,-.2],i===1?3:9);
    cube('hand_backplate_'+s,s+'_hand',[side*8.05-.68,17.0,.65],[side*8.05+.68,18.0,1.05],0);
    g[s+'_arm'].rotation=[0,0,side*5];g[s+'_forearm'].rotation=[0,0,-side*9];g[s+'_hand'].rotation=[0,0,side*4];
  }
  cube('neck_base','torso',[-1.6,29.4,-1.2],[1.6,31.15,1.3],3);
  cube('neck_cloth','torso',[-1.25,29.8,-1.62],[1.25,31.2,-1.18],2);
  cube('face','head',[-2.55,30.85,-1.6],[2.55,35.35,1.6],4);
  cube('jaw_shadow','head',[-2.05,30.2,-1.72],[2.05,31.2,1.25],4,[0,0,0]);
  cube('hair_cap','hair',[-2.9,34.35,-1.92],[2.9,36.75,1.95],6);
  cube('hair_back','hair',[-2.65,29.3,1.4],[2.65,35.6,2.65],6);
  for(let side of [-1,1]){
    cube('side_hair_'+side,'hair',[side*2.35-.6,30.05,-.3],[side*3.12+.6,35.0,1.2],6,[0,0,side*3]);
    cube('temple_lock_'+side,'hair',[side*2.65-.5,31.1,-1.85],[side*3.2+.5,34.7,-1.35],6,[0,0,side*8]);
    cube('brow_'+side,'head',[side*1.0-.7,33.45,-1.82],[side*2.1+.7,33.82,-1.54],6,[0,0,side*4]);
    cube('eye_'+side,'head',[side*1.0-.42,32.55,-1.76],[side*1.55+.42,32.9,-1.55],7);
    cube('crown_side_jewel_'+side,'crown',[side*2.15-.35,36.45,-.5],[side*2.85+.35,37.2,.35],3,[0,0,side*12]);
  }
  cube('third_eye_frame','head',[-.55,32.15,-1.9],[.55,33.62,-1.68],3);
  cube('third_eye','head',[-.24,32.48,-2.02],[.24,33.27,-1.86],8);
  cube('nose_bridge','head',[-.23,31.7,-1.8],[.23,32.45,-1.57],4);
  cube('mouth_shadow','head',[-.7,31.0,-1.78],[.7,31.22,-1.58],9);
  cube('hair_tie','hair',[-2.3,35.9,1.8],[2.3,36.6,2.6],3);
  cube('crown_base','crown',[-2.25,36.4,-.3],[2.25,37.25,.55],3);
  cube('crown_center','crown',[-.48,37.0,-.45],[.48,39.2,.35],3);
  panel('crown_center_blade','crown',[[0,38.2],[-.6,40.8],[0,42.2],[.6,40.8]],-.5,.55,3);
  for(let side of [-1,1]){
    panel('crown_flare_'+side,'crown',side<0?[[-1.2,37.15],[-3.5,38.5],[-3.95,40.2],[-2.55,39.5]]:[[1.2,37.15],[3.5,38.5],[3.95,40.2],[2.55,39.5]],-.38,.5,3);
    beam('crown_ornament_'+side,'crown',[side*1.45,38.0,.1],[side*2.75,40.35,.1],.32,.38,12);
  }
  for(let side of [-1,1]){
    const s=side<0?'left':'right';
    cube('thigh_under_'+s,s+'_leg',[side*2.15-1.25,10.6,-.85],[side*2.15+1.25,17.6,.95],2,[0,0,side*3]);
    panel('thigh_plate_'+s,s+'_leg',side<0?[[-3.35,16.8],[-1.1,17.0],[-1.3,11.4],[-2.9,10.8]]:[[3.35,16.8],[1.1,17.0],[1.3,11.4],[2.9,10.8]],-1.2,.65,0);
    cube('thigh_rail_'+s,s+'_leg',[side*2.25-.18,12.0,-1.54],[side*2.25+.18,16.5,-1.23],3);
    cube('knee_outer_'+s,s+'_knee',[side*2.35-1.35,9.1,-1.15],[side*2.35+1.35,11.2,.95],1,[0,0,side*4]);
    panel('knee_face_'+s,s+'_knee',side<0?[[-3.55,10.9],[-2.35,12.0],[-1.15,10.9],[-1.6,9.25],[-3.1,9.25]]:[[3.55,10.9],[2.35,12.0],[1.15,10.9],[1.6,9.25],[3.1,9.25]],-1.52,.42,3);
    cube('shin_under_'+s,s+'_knee',[side*2.7-.95,3.2,-.7],[side*2.7+.95,9.7,.9],1,[0,0,side*3]);
    cube('shin_front_'+s,s+'_knee',[side*2.7-.8,4.0,-1.3],[side*2.7+.8,9.0,-.86],0,[0,0,side*2]);
    cube('shin_rail_'+s,s+'_knee',[side*2.7-.18,4.0,-1.55],[side*2.7+.18,8.8,-1.29],3);
    cube('ankle_cuff_'+s,s+'_boot',[side*2.85-.95,2.2,-1.05],[side*2.85+.95,3.55,.9],3);
    cube('boot_body_'+s,s+'_boot',[side*2.85-1.15,.7,-1.15],[side*2.85+1.15,2.55,1.15],1);
    cube('boot_toe_'+s,s+'_boot',[side*2.85-1.2,.55,-2.5],[side*2.85+1.2,1.75,-.8],0,[0,0,side*3]);
    cube('boot_sole_'+s,s+'_boot',[side*2.85-1.3,.25,-2.55],[side*2.85+1.3,.75,1.1],9);
    cube('boot_gold_rail_'+s,s+'_boot',[side*2.85-.16,.78,-2.62],[side*2.85+.16,2.35,-2.45],3);
    rivets('knee_rivet_'+s+'_',s+'_knee',[side*2.35-0.55,side*2.35+0.55],10.45,-1.7,12);
  }
  cube('spear_shaft','spear',[-8.32,1,-.28],[-7.68,36.0,.28],1);
  cube('spear_grip_lower','spear',[-8.48,2.0,-.48],[-7.52,2.65,.48],3);
  cube('spear_grip_mid','spear',[-8.44,17.8,-.4],[-7.56,18.45,.4],3);
  cube('spear_grip_upper','spear',[-8.44,29.4,-.4],[-7.56,30.1,.4],3);
  cube('spear_pommel','spear',[-8.7,.4,-.55],[-7.3,1.35,.55],3);
  panel('spear_head','spear',[[-8,34.7],[-9.1,37.2],[-10.35,37.8],[-9.1,39.0],[-8,42.4],[-6.9,39.0],[-5.65,37.8],[-6.9,37.2]],-.55,1.1,0);
  panel('spear_head_inner','spear',[[-8,35.1],[-8.55,37.4],[-9.35,38.0],[-8,41.35],[-6.65,38.0],[-7.45,37.4]],-1.0,.28,3);
  cube('spear_neck','spear',[-9.0,33.7,-.8],[-7.0,35.1,.8],3);
  cube('spear_neck_inset','spear',[-8.55,34.0,-1.0],[-7.45,34.65,-.82],12);
  for(let i=0;i<5;i++)cube('spear_ring_'+i,'spear',[-8.52,4.0+i*5.3,-.42],[-7.48,4.35+i*5.3,.42],i%2?9:3);
  cube('weapon_guard','spear',[-9.2,30.0,-.5],[-6.8,31.0,.5],3);
  cube('weapon_guard_inset','spear',[-8.75,30.2,-.7],[-7.25,30.7,-.52],12);
  panel('spear_side_blade_left','spear',[[-8.35,36.1],[-11.4,35.2],[-10.0,37.1],[-8.45,37.8]],-.92,.38,0);
  panel('spear_side_blade_right','spear',[[-7.65,36.1],[-4.6,35.2],[-6.0,37.1],[-7.55,37.8]],-.92,.38,0);
  for(let side of [-1,1]){
    cube('shoulder_mask_'+side,'torso',[side*4.1-.72,27.2,-2.05],[side*4.1+.72,28.7,-1.65],3,[0,0,side*8]);
    cube('hip_plate_'+side,'pelvis',[side*3.55-1.05,17.5,-1.7],[side*4.3+.1,20.5,-1.15],0,[0,0,side*8]);
    cube('hip_plate_trim_'+side,'pelvis',[side*3.95-.16,17.7,-1.95],[side*3.95+.16,20.2,-1.72],3);
  }
  Modes.options.edit.select();unselectAll();Canvas.updateAll();Preview.selected.camera.zoom=.56;Preview.selected.camera.updateProjectionMatrix();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length,textures:Texture.all.length});
})()
