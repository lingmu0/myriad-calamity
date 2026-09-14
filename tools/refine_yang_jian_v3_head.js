(api) => {
  const {parts, bones, box, bar, stud} = api;
  const targets = new Set(['head','hair_top','topknot','crown','hair_back_01','hair_back_02','hair_back_03','left_hair_lock','right_hair_lock']);
  for (let i=parts.length-1; i>=0; i--) if (targets.has(parts[i].bone)) parts.splice(i,1);
  const B=(name,bone,c,s,m,r=[0,0,0],o=c)=>box('v3_'+name,bone,c,s,m,r,o);

  // The face is a clean stepped volume: cheek, narrower jaw, and shallow features.
  B('face_cranium','head',[0,51.10,-.15],[7.90,7.82,6.60],'skin');
  B('face_jaw','head',[0,47.56,-.22],[7.22,1.28,6.30],'skin');
  B('face_chin','head',[0,46.94,-.13],[6.38,.48,5.92],'skin');
  B('chin_underside','head',[0,46.71,.10],[5.87,.15,5.27],'skinShade');
  B('forehead','head',[0,53.50,-3.477],[6.98,2.60,.11],'skinLight');
  B('face_center_plane','head',[0,49.37,-3.482],[4.97,3.13,.09],'skin');
  B('nose_bridge','head',[0,50.04,-3.586],[.45,1.05,.25],'skinLight');
  B('nose_lower_bridge','head',[0,49.74,-3.722],[.49,.40,.21],'skinLight');
  B('nose_tip','head',[0,49.61,-3.795],[.56,.31,.27],'skin');
  B('nose_lower_shadow','head',[0,49.45,-3.776],[.34,.10,.12],'skinShade');
  B('mouth_line','head',[0,48.12,-3.551],[1.24,.095,.055],'skinShade');
  B('mouth_center','head',[-.12,48.13,-3.582],[.64,.055,.026],'skinShade');
  B('lower_lip','head',[0,47.95,-3.541],[.91,.09,.035],'skinLight');
  for(const s of [-1,1]) {
    B('jaw_corner_'+s,'head',[s*3.57,47.60,-.08],[.55,.87,5.73],'skin');
    B('temple_shadow_'+s,'head',[s*3.81,51.02,-3.478],[.24,4.98,.10],'skinShade');
    B('ear_'+s,'head',[s*4.15,50.52,-.29],[.82,1.75,1.19],'skin');
    B('ear_helix_'+s,'head',[s*4.49,50.55,-.43],[.18,1.31,.87],'skinLight');
    B('ear_inner_'+s,'head',[s*4.594,50.55,-.37],[.045,.92,.49],'skinShade');
    B('ear_inner_fold_'+s,'head',[s*4.633,50.68,-.26],[.034,.52,.23],'skin');
    B('ear_lobe_'+s,'head',[s*4.28,49.70,-.31],[.57,.31,.68],'skin');
    const ex=s*1.91;
    B('eye_socket_'+s,'head',[ex,51.13,-3.526],[2.32,.89,.12],'skinShade');
    B('eye_white_'+s,'head',[ex,51.16,-3.608],[2.09,.64,.065],'eyeWhite');
    B('iris_'+s,'head',[s*1.72,51.15,-3.664],[.70,.65,.063],'pupil');
    B('iris_inner_'+s,'head',[s*1.72,51.16,-3.703],[.41,.61,.035],'hair');
    B('eye_glint_'+s,'head',[s*1.72-.11,51.31,-3.731],[.13,.14,.025],'eyeWhite');
    B('eye_lower_lid_'+s,'head',[ex,50.797,-3.631],[2.13,.082,.040],'skinShade');
    B('eye_upper_lid_'+s,'head',[ex,51.506,-3.669],[2.23,.15,.075],'hair',[0,0,s*3]);
    B('eye_outer_corner_'+s,'head',[s*3.035,51.23,-3.674],[.12,.51,.07],'hair');
    B('eyebrow_'+s,'head',[s*1.94,51.98,-3.639],[2.90,.35,.155],'hair',[0,0,s*10.5]);
    B('eyebrow_outer_tip_'+s,'head',[s*3.37,52.247,-3.632],[.34,.23,.12],'hair',[0,0,s*10.5]);
  }
  // A recessed third eye, deliberately small and dark like the reference.
  for(const [y,w,h] of [[54.27,.18,.23],[54.04,.43,.28],[53.72,.61,.40],[53.38,.44,.27],[53.16,.19,.21]])
    B('third_eye_border_'+y,'head',[0,y,-3.575],[w,h,.125],'goldDark');
  B('third_eye_socket','head',[0,53.76,-3.655],[.27,.61,.075],'skinShade');
  B('third_eye_pupil','head',[0,53.77,-3.704],[.089,.45,.045],'pupil');
  B('third_eye_inner_light','head',[-.075,53.92,-3.707],[.058,.17,.035],'paleGold');

  // Thick swept hair is assembled as overlapping locks, not one stepped helmet.
  B('hair_back_skull','hair_top',[0,54.36,1.01],[8.82,4.10,6.46],'hair');
  B('hair_rear_crown','hair_top',[0,56.48,1.30],[7.58,1.35,5.88],'hair');
  B('hair_top_fold','hair_top',[0,57.34,1.53],[5.77,1.30,4.82],'hairLight');
  const fringe=[
    [-3.45,54.77,-3.12,1.37,1.13,1.07,-8,'hair'],
    [-2.48,55.29,-3.30,1.51,1.12,1.12,-7,'hairLight'],
    [-1.29,55.50,-3.39,1.50,1.07,1.18,-5,'hair'],
    [-.13,55.55,-3.50,1.22,1.02,1.13,5,'hairLight'],
    [1.03,55.29,-3.39,1.39,1.15,1.18,10,'hair'],
    [2.08,55.32,-3.27,1.37,1.33,1.14,10,'hairLight'],
    [3.17,54.79,-3.07,1.30,1.39,1.18,8,'hair']
  ];
  fringe.forEach((v,i)=>B('swept_fringe_'+i,'hair_top',[v[0],v[1]-.30,v[2]],v.slice(3,6),v[7],[0,0,v[6]]));
  const topTiles=[
    [-3.53,55.73,-1.91,1.65,.97,2.10],[-2.12,56.30,-2.02,1.49,1.02,2.12],
    [-.63,56.60,-2.06,1.57,1.02,2.06],[.98,56.47,-1.87,1.61,.95,2.39],
    [2.55,56.10,-1.79,1.49,1.03,2.47],[3.77,55.20,-1.01,1.28,1.31,2.31],
    [-3.10,56.69,.30,1.60,.98,2.77],[-1.41,57.03,.22,1.62,1.07,2.64],
    [.31,57.36,.47,1.64,.93,2.70],[2.07,56.86,.41,1.65,1.02,2.86],
    [3.52,55.84,1.46,1.21,1.04,2.87]
  ];
  topTiles.forEach((v,i)=>B('scalp_fold_'+i,'hair_top',v.slice(0,3),v.slice(3,6),i%3===0?'hairLight':'hair'));
  for(const s of [-1,1]) {
    const side=s===1?'right':'left', bone=side+'_hair_lock';
    // An overlapping scalp liner closes slivers between the freely stepped locks.
    B('side_scalp_liner_'+s,bone,[s*4.10,50.92,.12],[.60,7.18,5.53],'hair');
    B('temple_hairline_seal_'+s,bone,[s*3.93,54.28,-2.965],[1.03,1.52,1.05],'hair');
    B('temple_main_'+s,bone,[s*4.05,52.11,-2.41],[1.17,5.69,1.86],'hair');
    B('temple_outer_'+s,bone,[s*4.44,52.46,-1.48],[.87,4.82,1.88],'hairLight');
    B('temple_ridge_'+s,bone,[s*4.11,52.01,-3.45],[.57,4.86,.30],'hairLight');
    B('cheek_lock_'+s,bone,[s*3.91,48.96,-2.81],[.97,3.52,1.35],'hair');
    B('cheek_lock_face_'+s,bone,[s*3.88,49.22,-3.525],[.47,3.47,.17],'hairLight');
    B('jaw_lock_'+s,bone,[s*4.15,46.38,-2.34],[.98,2.12,1.31],'hair');
    B('jaw_lock_tip_'+s,bone,[s*4.25,45.19,-2.29],[.66,.64,1.05],'hairLight');
    B('side_hair_over_ear_'+s,bone,[s*4.60,52.57,.43],[1.08,3.47,2.53],'hair');
    B('side_hair_behind_ear_'+s,bone,[s*4.61,47.59,1.30],[1.03,7.18,1.84],'hair');
    B('side_hair_outer_lock_'+s,bone,[s*5.19,47.83,2.14],[.77,8.66,1.59],'hairLight',[0,0,s*1.7]);
    B('side_hair_inner_lock_'+s,bone,[s*4.61,44.27,.66],[.91,4.00,1.13],'hairLight');
    B('side_hair_split_tip_'+s,bone,[s*5.24,42.82,2.33],[.60,1.30,1.07],'hair');
    B('side_hair_inner_tip_'+s,bone,[s*4.64,41.66,.78],[.69,1.22,.87],'hair');
    B('side_hair_clasp_'+s,bone,[s*4.64,42.40,.085],[.76,.41,.27],'goldDark');
    B('side_hair_clasp_lip_'+s,bone,[s*4.64,42.55,-.071],[.69,.13,.13],'gold');
    for(let j=0;j<3;j++) B('temple_strand_'+s+'_'+j,bone,[s*(3.77+j*.235),51.62,-3.70+j*.022],[.085,2.50+(j%2)*.76,.067],j===1?'hairLight':'hair');
  }

  // Folded black topknot with a compact bronze mount rather than tall gold bars.
  B('topknot_base','topknot',[0,58.30,1.50],[5.10,1.48,4.20],'hair');
  B('topknot_loop_left','topknot',[-1.61,59.74,1.66],[1.62,3.18,3.72],'hairLight');
  B('topknot_loop_right','topknot',[1.45,59.60,1.59],[1.51,2.85,3.72],'hair');
  B('topknot_center_fold','topknot',[-.07,59.78,1.57],[1.48,3.25,3.85],'hair');
  B('topknot_upper_fold','topknot',[-.67,61.20,1.60],[2.72,.75,3.07],'hairLight');
  B('topknot_upper_right_fold','topknot',[1.12,61.02,1.97],[1.07,.69,2.51],'hair');
  B('topknot_top_lock','topknot',[-.50,61.65,1.85],[1.68,.49,2.34],'hair');
  B('topknot_top_return','topknot',[-1.10,61.38,.31],[.83,.47,1.07],'hairLight');
  B('topknot_right_return','topknot',[1.57,60.24,3.25],[1.20,1.98,.60],'hairLight');
  B('topknot_front_return','topknot',[.08,60.11,-.44],[1.11,1.78,.35],'hairLight');
  B('crown_lower_band','crown',[0,58.12,1.38],[5.43,.51,4.49],'goldDark');
  B('crown_front_rim','crown',[0,58.27,-.942],[5.50,.19,.27],'gold');
  B('crown_back_rim','crown',[0,58.24,3.710],[5.46,.17,.25],'gold');
  for(const s of [-1,1]) {
    B('crown_side_rim_'+s,'crown',[s*2.70,58.25,1.36],[.24,.17,4.58],'gold');
    B('crown_pin_'+s,'crown',[s*3.23,57.48,.20],[1.16,.34,.51],'goldDark');
    B('crown_pin_bead_'+s,'crown',[s*3.78,57.49,.14],[.41,.54,.62],'gold');
    B('crown_pin_step_'+s,'crown',[s*2.88,57.66,.10],[.63,.26,.51],'gold');
    B('crown_low_upright_'+s,'crown',[s*1.40,59.00,-.895],[.45,1.80,.41],'goldDark');
    B('crown_upright_edge_'+s,'crown',[s*1.40,59.25,-1.14],[.18,1.16,.13],'gold');
    B('crown_upright_tip_'+s,'crown',[s*1.40,60.02,-.90],[.27,.32,.31],'gold');
    B('crown_scroll_'+s,'crown',[s*1.94,58.78,-1.12],[.60,.35,.34],'gold');
    B('crown_scroll_tip_'+s,'crown',[s*2.18,59.02,-1.12],[.27,.44,.28],'goldDark');
    B('crown_medallion_wing_'+s,'crown',[s*.76,57.30,-1.331],[.63,.65,.32],'gold');
    B('crown_medallion_wing_cut_'+s,'crown',[s*1.04,57.49,-1.37],[.33,.37,.22],'paleGold');
    B('crown_brow_'+s,'crown',[s*.46,57.69,-1.57],[.56,.24,.21],'paleGold',[0,0,s*12]);
    B('crown_eye_inset_'+s,'crown',[s*.44,57.42,-1.61],[.30,.15,.12],'goldDark');
  }
  B('crown_center_mount','crown',[0,58.84,-.99],[.98,2.15,.46],'goldDark');
  B('crown_center_shield','crown',[0,58.64,-1.257],[.68,1.08,.25],'gold');
  B('crown_center_inset','crown',[0,58.86,-1.414],[.28,.47,.08],'goldDark');
  B('crown_center_finial','crown',[0,60.14,-1.0],[.37,.48,.35],'gold');
  B('crown_medallion_base','crown',[0,57.37,-1.29],[1.66,1.31,.43],'goldDark');
  B('crown_medallion_forehead','crown',[0,57.79,-1.56],[.60,.54,.34],'gold');
  B('crown_medallion_nose','crown',[0,57.29,-1.69],[.31,.44,.27],'paleGold');
  B('crown_medallion_muzzle','crown',[0,57.04,-1.59],[.69,.29,.26],'gold');
  B('crown_medallion_chin','crown',[0,56.82,-1.47],[.45,.27,.28],'gold');
  for(const s of [-1,1])for(let i=0;i<3;i++)
    B('crown_band_rivet_'+s+'_'+i,'crown',[s*(1.09+i*.60),58.31,-1.105],[.17,.17,.14],i===1?'paleGold':'gold');

  // Nine interleaved locks keep a continuous silhouette while all three rear
  // chain bones can flex. The outer layer has staggered tips and metal clasps.
  B('ponytail_tie_underlay','hair_back_01',[0,56.32,4.42],[3.30,2.50,2.14],'hair');
  B('ponytail_crown_return','hair_back_01',[0,58.02,4.13],[3.03,1.37,1.80],'hairLight');
  const lengths=[6.15,7.75,6.87,8.76,9.35,8.39,7.13,7.74,5.85];
  for(let i=0;i<9;i++) {
    const x=(i-4)*.89, even=i%2===0, z=4.19+(i%3)*.15;
    const extra=(i%4)*.14, width=i===4?1.08:1.01;
    B('rear_root_'+i,'hair_back_01',[x*.86,54.64,z-.24],[width+extra,3.39,1.65],even?'hair':'hairLight');
    B('rear_upper_'+i,'hair_back_01',[x,50.45,z],[width,7.02,1.71],even?'hairLight':'hair');
    B('rear_mid_'+i,'hair_back_02',[x*1.035,42.83,z+.57],[width+.02,8.34,1.75],even?'hair':'hairLight',[-3,0,0]);
    const len=lengths[i], ex=x*1.062, end=38.82-len, ez=z+1.13;
    B('rear_lower_'+i,'hair_back_03',[ex,38.82-len/2,ez],[width-.06,len,1.67],even?'hairLight':'hair',[-3,0,0]);
    B('rear_point_'+i,'hair_back_03',[ex,end-.41,ez+.06],[width-.28,.94,1.30],even?'hair':'hairLight');
    // A second strand crest breaks up the broad lit faces without adding noise.
    const highLen=5.22+(i%3)*.48;
    B('rear_strand_ridge_upper_'+i,'hair_back_01',[x+.17,50.62,z+.90],[.28,highLen,.23],even?'hair':'hairLight');
    B('rear_strand_ridge_mid_'+i,'hair_back_02',[x*1.035-.17,42.92,z+1.50],[.24,5.79+(i%2)*1.09,.18],even?'hairLight':'hair',[-3,0,0]);
    if(i===0||i===3||i===5||i===8) {
      B('rear_clasp_base_'+i,'hair_back_03',[ex,end+.30,ez+.865],[width+.03,.62,.27],'goldDark');
      B('rear_clasp_border_'+i,'hair_back_03',[ex,end+.51,ez+1.025],[width-.10,.13,.12],'gold');
      B('rear_clasp_center_'+i,'hair_back_03',[ex,end+.26,ez+1.040],[.26,.34,.12],'gold');
    }
  }
  for(const s of [-1,1]) {
    B('outer_rear_root_'+s,'hair_back_01',[s*4.12,52.73,3.29],[1.08,5.28,1.79],'hair');
    B('outer_rear_middle_'+s,'hair_back_02',[s*4.55,45.34,4.27],[.94,9.53,1.83],'hairLight',[-6,0,s*2]);
    B('outer_rear_lower_'+s,'hair_back_03',[s*4.97,37.66,5.35],[.87,6.00,1.46],'hair',[-5,0,s*3]);
    B('outer_rear_tip_'+s,'hair_back_03',[s*5.14,34.40,5.53],[.64,.98,1.02],'hairLight');
    B('outer_rear_buckle_'+s,'hair_back_03',[s*5.07,35.20,6.155],[.76,.43,.23],'goldDark');
  }
  const headScale=.85, chinBase=45.685;
  const resizeHead=vector=>{
    vector[0]*=headScale;
    vector[1]=chinBase+(vector[1]-.95-chinBase)*headScale-1;
    vector[2]*=headScale;
  };
  for(const p of parts)if(targets.has(p.bone)){
    resizeHead(p.from);resizeHead(p.to);resizeHead(p.origin);
  }
  for(const name of targets)resizeHead(bones[name].origin);
}
