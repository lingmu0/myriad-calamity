(() => {
  if(!Project?.name.includes('Cogwork Dancer'))throw new Error('Select the dancer project');
  const g={};Group.all.forEach(b=>g[b.name]=b);
  const tex=Texture.all.find(t=>t.name==='cogwork_dancer');
  function cube(name,bone,from,to,mat=0){
    const c=new Cube({name,from,to,origin:from.map((v,i)=>(v+to[i])/2),box_uv:false,autouv:0});
    const x=mat%4*64,y=Math.floor(mat/4)*64;for(const f of Object.values(c.faces)){f.texture=tex.uuid;f.uv=[x+10,y+10,x+54,y+54];}
    c.addTo(g[bone]).init();return c;
  }
  for(const c of Cube.all){
    let factor=1;
    if(/^(upper_arm_piston_|forearm_main_)/.test(c.name))factor=1.9;
    else if(/^(upper_leg_frame_|lower_leg_frame_)/.test(c.name))factor=2.05;
    else if(/^(thigh_armour_|shin_plate_)/.test(c.name))factor=1.8;
    else if(/^(square_elbow_|square_knee_|knee_front_guard_)/.test(c.name))factor=1.35;
    else if(/^(forearm_raised_plate_|detail_shin_overlap_)/.test(c.name))factor=1.45;
    if(factor!==1){for(const axis of [0,2]){const mid=(c.from[axis]+c.to[axis])/2,half=(c.to[axis]-c.from[axis])*factor/2;c.from[axis]=mid-half;c.to[axis]=mid+half;}}
  }
  for(const m of Mesh.all.slice()){
    if(/^(pointed_foot_|wrist_guard_)/.test(m.name)){m.remove();continue;}
    if(/^(stepped_scythe_|scythe_dark_back_)/.test(m.name)){
      const sign=m.name.includes('_-1')?-1:1,px=sign*16.8,py=33,pz=-.3;
      for(const v of Object.values(m.vertices)){v[0]=px+(v[0]-px)*1.4;v[1]=py+(v[1]-py)*1.25;v[2]=pz+(v[2]-pz)*1.85;}
    }
  }
  for(const sign of [-1,1]){
    const s=sign<0?'left':'right';
    g[s+'_shoulder'].addTo(g.chest);
    g[s+'_arm'].rotation=[0,sign*28,-sign*58];
    g[s+'_elbow'].rotation=[0,sign*65,sign*20];
    g[s+'_wrist'].rotation=[0,-sign*25,sign*38];
    const x=sign*16.1;
    cube('reinforced_palm_'+s,s+'_wrist',[x-1.02,32.25,-1.05],[x+1.02,34.1,.96],2);
    cube('gauntlet_plate_'+s,s+'_wrist',[x-.84,32.48,-1.38],[x+.84,33.94,-1.02],0);
    cube('gauntlet_trim_'+s,s+'_wrist',[x-.88,33.42,-1.51],[x+.88,33.86,-1.33],1);
    for(let i=0;i<3;i++){
      cube('square_knuckle_'+s+'_'+i,s+'_wrist',[x-.76+i*.52,31.76,-.82],[x-.32+i*.52,32.4,.75],3);
      cube('gripping_finger_'+s+'_'+i,s+'_wrist',[x-.7+i*.52,31.74,.55],[x-.36+i*.52,32.78,1.02],2);
    }
    cube('wrist_cuff_'+s,s+'_wrist',[x-1.16,33.8,-.95],[x+1.16,34.32,.95],7);
    cube('thigh_outer_shell_'+s,s+'_leg',[sign*2.35-1,14.2,-1.05],[sign*2.35+1,17.4,-.5],0);
    cube('thigh_panel_seam_'+s,s+'_leg',[sign*2.35-.16,14.5,-1.16],[sign*2.35+.16,17.1,-1.04],2);
    cube('knee_axle_plate_'+s,s+'_knee',[sign*2.8-1.02,10.3,-1.2],[sign*2.8+1.02,11.74,-.87],7);
    cube('shin_upper_shell_'+s,s+'_knee',[sign*3.12-.91,6.1,-1.01],[sign*3.12+.91,9.72,-.54],0);
    cube('shin_joint_rail_'+s,s+'_knee',[sign*3.35-.23,3.7,-1.03],[sign*3.35+.23,6.24,-.69],3);
    const f=sign*3.8;
    cube('square_heel_'+s,s+'_ankle',[f-1.03,.72,-1.65],[f+1.03,3.32,.68],2);
    cube('armoured_boot_'+s,s+'_ankle',[f-1.23,.7,-3.06],[f+1.23,2.5,-1.22],0);
    cube('square_toe_cap_'+s,s+'_ankle',[f-1.18,.76,-3.67],[f+1.18,1.8,-2.88],3);
    cube('boot_sole_'+s,s+'_ankle',[f-1.28,.38,-3.71],[f+1.28,.84,.68],2);
    for(let i=0;i<2;i++)cube('boot_instep_'+s+'_'+i,s+'_ankle',[f-.94,1.82+i*.32,-2.88+i*.55],[f+.94,2.15+i*.32,-2.36+i*.55],1);
  }
  Animation.all.forEach(a=>a.playing=false);Modes.options.edit.select();unselectAll();Canvas.updateAll();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length});
})()
