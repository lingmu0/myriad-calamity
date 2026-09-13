(() => {
  if (!Project?.name.includes('Cogwork Dancer')) throw new Error('Select the dancer project');
  const g={};Group.all.forEach(b=>g[b.name]=b);
  const tex=Texture.all.find(t=>t.name==='cogwork_dancer');
  const group=(name,origin,parent)=>g[name]||(g[name]=new Group({name,origin}).addTo(g[parent]).init());
  group('chest',[0,27.7,0],'body');group('abdomen',[0,25,0],'body');group('pelvis',[0,20.3,0],'abdomen');
  group('neck',[0,35.7,0],'chest');g.head.addTo(g.neck);group('crown',[0,42.3,0],'head');
  g.gear.addTo(g.chest);g.skirt.addTo(g.abdomen);
  for(const side of [-1,1]){
    const s=side<0?'left':'right';g[s+'_arm'].addTo(g.chest);g[s+'_leg'].addTo(g.pelvis);
    group(s+'_shoulder',[side*6.2,32.9,0],s+'_arm');
    group(s+'_elbow',[side*11.4,30.1,0],s+'_arm');group(s+'_wrist',[side*16.1,33.1,-.1],s+'_elbow');
    group(s+'_blade',[side*16.8,33,-.3],s+'_wrist');
    group(s+'_knee',[side*2.8,11,0],s+'_leg');group(s+'_ankle',[side*3.8,2.5,-.7],s+'_knee');
    group(s+'_hip_plate',[side*3,23,1],'skirt');
    for(let i=0;i<3;i++)group(s+'_tail_'+i,[side*(3.1+i*.35),22.5-i*4.5,1.5+i*.18],i===0?'skirt':s+'_tail_'+(i-1));
  }
  for(const e of Outliner.elements){
    const n=e.name;const side=n.includes('_-1')?'left':'right';
    if(/^neck_/.test(n))e.addTo(g.neck);
    else if(/crown/.test(n))e.addTo(g.crown);
    else if(/^(inner_clockwork|bevelled_breastplate|upper_chest|main_chest|chest_|side_vent)/.test(n))e.addTo(g.chest);
    else if(/^abdomen_/.test(n)){const i=Number(n.split('_').at(-1));e.addTo(i<2?g.pelvis:g.abdomen);}
    else if(/^(pauldron_|shoulder_bolt)/.test(n))e.addTo(g[side+'_shoulder']);
    else if(/^(square_elbow|forearm_)/.test(n))e.addTo(g[side+'_elbow']);
    else if(/^wrist_guard/.test(n))e.addTo(g[side+'_wrist']);
    else if(/^(stepped_scythe|scythe_dark)/.test(n))e.addTo(g[side+'_blade']);
    else if(/^(square_knee|knee_front|lower_leg|shin_plate)/.test(n))e.addTo(g[side+'_knee']);
    else if(/^pointed_foot/.test(n))e.addTo(g[side+'_ankle']);
    else if(/^(coattail_layer|coattail_trim)/.test(n))e.addTo(g[side+'_tail_'+n.split('_').at(-1)]);
    else if(/^tail_tip/.test(n))e.addTo(g[side+'_tail_2']);
    else if(/^(flared_hip|hip_rivet)/.test(n))e.addTo(g[side+'_hip_plate']);
  }
  function cube(name,bone,from,to,mat=0,rotation=[0,0,0]){
    const c=new Cube({name:'detail_'+name,from,to,origin:from.map((v,i)=>(v+to[i])/2),rotation,box_uv:false,autouv:0});
    const x=mat%4*64,y=Math.floor(mat/4)*64;for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=[x+10,y+10,x+54,y+54];}
    c.addTo(g[bone]).init();return c;
  }
  function beam(name,bone,a,b,w,d,mat){const len=Math.hypot(b[0]-a[0],b[1]-a[1]);const c=cube(name,bone,[a[0]-w/2,a[1],a[2]-d/2],[a[0]+w/2,a[1]+len,a[2]+d/2],mat);c.origin=a;c.rotation=[0,0,-Math.atan2(b[0]-a[0],b[1]-a[1])*180/Math.PI];return c;}
  for(const side of [-1,1]){
    const s=side<0?'left':'right';
    for(let i=0;i<3;i++){
      cube('collar_step_'+side+'_'+i,'chest',[side*(2.9-i*.33)-.3,34.65+i*.37,-3.05],[side*(2.9-i*.33)+.3,35.2+i*.37,-2.7],i===2?3:1);
      cube('rib_vent_'+side+'_'+i,'chest',[side<0?-4.5:3.65,29.4+i*1.15,1.1],[side<0?-3.65:4.5,29.82+i*1.15,2.65],7);
      cube('shoulder_lamella_'+side+'_'+i,s+'_shoulder',[side*6.6-.85,30+i*.65,-1.55],[side*6.6+.85,30.45+i*.65,-1.15],i===1?1:7);
      cube('forearm_vent_'+side+'_'+i,s+'_elbow',[side*(12.9+i*.65)-.15,31.1+i*.36,-1.03],[side*(12.9+i*.65)+.15,31.55+i*.36,-.78],2);
      cube('blade_grip_'+side+'_'+i,s+'_wrist',[side*16.05-.4,32.9+i*.35,-.8],[side*16.05+.4,33.15+i*.35,-.45],i===1?1:2);
      cube('tail_hinge_'+side+'_'+i,s+'_tail_'+i,[side*(3.1+i*.35)-.62,22.1-i*4.5,.96+i*.18],[side*(3.1+i*.35)+.62,22.6-i*4.5,1.5+i*.18],2);
      cube('tail_clasp_'+side+'_'+i,s+'_tail_'+i,[side*(3.1+i*.35)-.25,21.45-i*4.5,.9+i*.18],[side*(3.1+i*.35)+.25,22.45-i*4.5,1.02+i*.18],3);
    }
    beam('bicep_hydraulic_'+side,s+'_arm',[side*7.3,31.7,.7],[side*10.5,30.15,.7],.4,.45,3);
    cube('elbow_pin_back_'+side,s+'_elbow',[side*11.4-.42,29.68,.9],[side*11.4+.42,30.52,1.25],3);
    cube('wrist_square_joint_'+side,s+'_wrist',[side*16.1-.57,32.65,-.45],[side*16.1+.57,33.65,.5],2);
    cube('blade_socket_'+side,s+'_blade',[side*17-.64,32.4,-.93],[side*17+.64,33.55,-.65],7);
    cube('blade_socket_glint_'+side,s+'_blade',[side*17-.22,32.7,-1.04],[side*17+.22,33.2,-.91],5);
    beam('thigh_tendon_'+side,s+'_leg',[side*2.6,17.8,.7],[side*3.2,12,.7],.28,.28,3);
    cube('knee_side_bolt_'+side,s+'_knee',[side*2.8-.88,10.7,-.3],[side*2.8+.88,11.25,.2],7);
    cube('ankle_cuff_'+side,s+'_ankle',[side*3.8-.63,2.2,-1.1],[side*3.8+.63,3.2,-.2],7);
    for(let i=0;i<2;i++)cube('shin_overlap_'+side+'_'+i,s+'_knee',[side*(3.05+i*.17)-.55,7.9-i*1.6,-.92],[side*(3.05+i*.17)+.55,8.65-i*1.6,-.55],1);
    cube('temple_bracket_'+side,'head',[side<0?-4.83:4.4,38.8,-1.9],[side<0?-4.4:4.83,41.2,.4],7);
    cube('temple_inlay_'+side,'head',[side<0?-4.95:4.77,39.3,-1.4],[side<0?-4.77:4.95,40.7,-.6],3);
    beam('crown_brace_'+side,'crown',[side*4.05,43.1,1],[side*2.85,46.7,1],.26,.34,2);
    cube('jaw_latch_'+side,'head',[side*2.7-.24,37.3,-3.04],[side*2.7+.24,38.45,-2.79],3);
    cube('hip_underplate_'+side,s+'_hip_plate',[side*3.9-.55,20,1.35],[side*3.9+.55,22.5,1.75],2);
  }
  for(let i=0;i<5;i++)cube('segmented_spine_'+i,'chest',[-.9,28+i*1.35,2.45],[.9,28.7+i*1.35,2.88],i%2?3:2);
  for(let i=0;i<3;i++)cube('sternum_tick_'+i,'chest',[-.7,31.2+i*.7,-3.13],[.7,31.37+i*.7,-2.97],3);
  cube('pelvis_central_pin','pelvis',[-.6,19.5,-2.1],[.6,20.7,-1.86],3);
  cube('neck_front_piston','neck',[-.42,35.8,-1.25],[.42,37.5,-.99],3);
  group('core',[0,26.65,-2.7],'body');
  Outliner.elements.filter(e=>e.name==='square_clockwork_core').forEach(e=>e.addTo(g.core));
  Modes.options.edit.select();Canvas.updateAll();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length});
})()
