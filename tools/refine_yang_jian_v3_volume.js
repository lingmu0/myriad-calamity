(api) => {
  const {parts,bones,group}=api;
  const depth=1.25;
  for(const p of parts){
    p.from[2]*=depth;p.to[2]*=depth;p.origin[2]*=depth;
  }
  for(const b of Object.values(bones))b.origin[2]*=depth;
  // Bring the head over the chest opening while flattening only the torso front.
  const forwardBones=new Set(['neck','head','hair_top','topknot','crown','hair_back_01','hair_back_02','hair_back_03','left_hair_lock','right_hair_lock']);
  for(const p of parts){
    for(const vector of [p.from,p.to,p.origin]){
      if(forwardBones.has(p.bone))vector[2]-=1.2;
      else if(p.bone==='chest'&&vector[2]<0)vector[2]*=.75;
    }
  }
  for(const name of forwardBones)bones[name].origin[2]-=1.2;
  for(const side of ['left','right']){
    bones[side+'_front_cloth'].rotation[0]=11;
    bones[side+'_front_cloth_tip'].rotation[0]=0;
    bones[side+'_back_cloth'].rotation[0]=-14;
    bones[side+'_skirt'].rotation[0]=8;
    bones[side+'_skirt_lower'].rotation[0]=0;
  }
  bones.rear_sash.rotation[0]=-10;
  bones.rear_sash_tip.rotation[0]=0;
  group('front_tabard',[0,29,-3.56*depth],'hips',[10,0,0]);
  for(const p of parts){
    if(p.name.startsWith('v3_center_tabard')||p.name.startsWith('v3_tabard_'))p.bone='front_tabard';
    if(p.name.startsWith('v3_right_grasp_')){
      for(const vector of [p.from,p.to,p.origin]){
        vector[0]=13.05+(vector[0]-13.05)*1.22;
        vector[2]=-2.8*depth+(vector[2]+2.8*depth)*1.22;
      }
    }
  }
}
