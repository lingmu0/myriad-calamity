(() => {
  const bones = {}, parts = [];
  const mat = {
    iron: {color:[72,79,89], noise:4, edge:16}, steel:{color:[126,133,141],noise:4,edge:20},
    silver:{color:[176,181,184],noise:3,edge:13}, dark:{color:[39,45,55],noise:3,edge:7},
    gold:{color:[157,135,94],noise:4,edge:17}, paleGold:{color:[186,165,122],noise:3,edge:14},
    goldDark:{color:[104,86,59],noise:3,edge:10}, cloth:{color:[30,42,58],noise:3,edge:3},
    clothLight:{color:[45,57,73],noise:3,edge:4}, clothDark:{color:[22,30,43],noise:2,edge:2},
    red:{color:[96,50,46],noise:3,edge:6}, leather:{color:[47,34,29],noise:2,edge:4},
    hair:{color:[20,22,28],noise:2,edge:3}, hairLight:{color:[29,31,37],noise:2,edge:4},
    skin:{color:[212,175,150],noise:1,edge:0}, skinLight:{color:[222,185,158],noise:1,edge:0},
    skinShade:{color:[181,141,116],noise:1,edge:0}, linen:{color:[183,179,164],noise:2,edge:5},
    eyeWhite:{color:[216,212,202],noise:0,edge:0}, pupil:{color:[44,37,32],noise:0,edge:0},
    blade:{color:[161,171,181],noise:2,edge:11}, bladeEdge:{color:[214,222,225],noise:1,edge:9}
  };
  function group(name, origin, parent, rotation=[0,0,0]) {
    bones[name] = new Group({name,origin,rotation}).addTo(parent?bones[parent]:'root').init();
    return name;
  }
  function box(name,bone,center,size,material='iron',rotation=[0,0,0],origin=center) {
    if(size.some(v=>v<=0)) throw Error('Invalid dimensions: '+name);
    parts.push({name,bone,from:center.map((v,i)=>v-size[i]/2),to:center.map((v,i)=>v+size[i]/2),material,rotation,origin});
  }
  function bar(name,bone,a,b,width,depth,material='gold') {
    const d=b.map((v,i)=>v-a[i]);
    if(Math.abs(d[2])>0.001) throw Error('bar is for XY plane: '+name);
    box(name,bone,a.map((v,i)=>(v+b[i])/2),[width,Math.hypot(d[0],d[1]),depth],material,[0,0,-Math.atan2(d[0],d[1])*180/Math.PI]);
  }
  function framed(name,bone,c,size,material='iron',trim='silver',rim=.22) {
    box(name,bone,c,size,material);
    const [x,y,z]=c,[w,h,d]=size;
    box(name+'_top',bone,[x,y+h/2-rim/2,z-d/2-.055],[w,rim,.15],trim);
    box(name+'_bottom',bone,[x,y-h/2+rim/2,z-d/2-.055],[w,rim,.15],trim);
    box(name+'_left',bone,[x-w/2+rim/2,y,z-d/2-.055],[rim,h-2*rim,.15],trim);
    box(name+'_right',bone,[x+w/2-rim/2,y,z-d/2-.055],[rim,h-2*rim,.15],trim);
  }
  function stud(name,bone,x,y,z,size=.3) {box(name,bone,[x,y,z],[size,size,.19],'paleGold');}
  function crest(name,bone,x,y,z,s=1) {
    const add=(n,dx,dy,dz,w,h,d,m)=>box(name+'_'+n,bone,[x+dx*s,y+dy*s,z+dz*s],[w*s,h*s,d*s],m);
    add('recess',0,0,0,2.4,2.45,.38,'goldDark');
    add('forehead',0,.76,-.24,1.28,.94,.37,'gold');
    add('brow_center',0,.44,-.52,.48,.5,.34,'paleGold');
    for(const sign of [-1,1]) {
      add('temple'+sign,sign*1.0,.13,-.18,.43,1.21,.42,'gold');
      add('horn'+sign,sign*1.09,1.15,-.11,.41,.79,.35,'paleGold');
      add('horn_tip'+sign,sign*1.35,1.32,-.13,.55,.29,.32,'gold');
      add('eye_recess'+sign,sign*.62,.14,-.42,.77,.45,.17,'goldDark');
      add('eye'+sign,sign*.57,.24,-.55,.46,.15,.13,'dark');
      bar(name+'_brow'+sign,bone,[x+sign*.22*s,y+.43*s,z-.67*s],[x+sign*1.03*s,y+.63*s,z-.67*s],.3*s,.26*s,'paleGold');
      add('cheek'+sign,sign*.86,-.2,-.45,.49,.65,.28,'gold');
      add('fang'+sign,sign*.56,-.53,-.79,.21,.55,.18,'paleGold');
      add('mane_out'+sign,sign*1.28,-.3,-.13,.38,.51,.36,'gold');
      add('mane_mid'+sign,sign*1.06,-.83,-.14,.45,.35,.4,'paleGold');
      add('mane_low'+sign,sign*.68,-1.11,-.14,.47,.35,.34,'gold');
    }
    add('nose',0,.08,-.63,.49,.65,.42,'paleGold');
    add('nose_tip',0,-.16,-.87,.65,.29,.29,'gold');
    add('muzzle',0,-.42,-.51,.81,.41,.38,'gold');
    add('mouth',0,-.58,-.72,.44,.17,.12,'goldDark');
    add('chin',0,-.91,-.43,.67,.33,.36,'gold');
    add('beard',0,-1.33,-.18,.36,.56,.28,'paleGold');
  }
  group('root',[0,0,0]); group('hips',[0,29,0],'root'); group('waist',[0,31,0],'hips');
  group('chest',[0,34,0],'waist'); group('neck',[0,44.2,0],'chest'); group('head',[0,46.3,0],'neck');
  group('hair_top',[0,54,0],'head'); group('topknot',[0,57.6,1.1],'hair_top'); group('crown',[0,59,0],'hair_top');
  group('hair_back_01',[0,56,2.7],'head'); group('hair_back_02',[0,47,3.6],'hair_back_01'); group('hair_back_03',[0,38,4.4],'hair_back_02');
  for(const sign of [-1,1]) {
    const side=sign===1?'right':'left';
    group(side+'_arm',[sign*8.15,42,0],'chest');
    group(side+'_shoulder',[sign*8.3,42.3,0],side+'_arm',[0,0,-sign*9]);
    group(side+'_forearm',[sign*9.65,35.2,0],side+'_arm');
    group(side+'_hand',[sign*(sign===1?12.5:10.15),sign===1?30.9:27.8,sign===1?-2.7:-.5],side+'_forearm');
    group(side+'_thigh',[sign*3.65,28.7,0],'hips'); group(side+'_shin',[sign*3.85,13.8,0],side+'_thigh');
    group(side+'_foot',[sign*3.95,4,0],side+'_shin');
    group(side+'_hair_lock',[sign*4.25,54,0],'head');
    group(side+'_skirt',[sign*5.3,29.4,0],'hips',[0,0,sign*11]);
    group(side+'_skirt_lower',[sign*5.7,22,0],side+'_skirt');
    group(side+'_front_cloth',[sign*2.6,29,-3.1],'hips',[0,0,sign*7]);
    group(side+'_front_cloth_tip',[sign*2.6,18,-3.4],side+'_front_cloth');
    group(side+'_back_cloth',[sign*3.2,29,2.5],'hips',[0,0,sign*7]);
  }
  group('rear_sash',[0,30,3.3],'hips'); group('rear_sash_tip',[0,17,4.3],'rear_sash');
  group('weapon',[13.05,31,-2.8],'right_hand');

  box('torso_undercloth','chest',[0,38.8,0],[12.5,10.4,5.6],'clothDark');
  box('upper_back_cuirass','chest',[0,39.3,2.9],[11.8,8.5,.7],'iron');
  for(let row=0;row<3;row++) {
    box('back_lamella_'+row,'chest',[0,35.5+row*2.05,3.25],[11-row*.55,1.9,.45],'steel');
    box('back_lamella_seam_'+row,'chest',[0,34.65+row*2.05,3.51],[10.8-row*.55,.18,.12],'silver');
  }
  for(const sign of [-1,1]) {
    framed('breastplate_'+sign,'chest',[sign*3.0,39.05,-3.0],[5.65,7.5,1.05],'iron','dark',.31);
    for(let col=0;col<3;col++) {
      const x=sign*(1.16+col*1.66), y=39.1+(col===1?.28:0), h=col===1?6.42:5.9, z=-3.61-(col===1?.15:0);
      box('breastplate_facet_'+sign+'_'+col,'chest',[x,y,z],[1.57,h,.48],col===1?'steel':'iron');
      box('breastplate_facet_rim_'+sign+'_'+col,'chest',[x,y-h/2+.12,z-.29],[1.59,.21,.14],'steel');
      box('breastplate_facet_top_'+sign+'_'+col,'chest',[x,y+h/2-.13,z-.27],[1.58,.22,.12],'steel');
      for(let j=0;j<3;j++)box('cuirass_etched_panel_'+sign+'_'+col+'_'+j,'chest',[x,y-1.6+j*1.6,z-.265],[1.15,1.36,.08],(col+j)%2?'iron':'steel');
    }
    bar('chest_upper_bevel_'+sign,'chest',[sign*.5,42.51,-3.55],[sign*5.5,42.3,-3.55],.3,.24,'silver');
    box('breastplate_dark_rail_'+sign,'chest',[sign*5.5,39.1,-3.71],[.45,7.2,.18],'dark');
    box('breastplate_outer_rail_'+sign,'chest',[sign*6.03,38.8,-3.55],[.29,7.0,.22],'gold');
    for(let i=0;i<4;i++)stud('cuirass_pin_'+sign+'_'+i,'chest',sign*5.63,36.1+i*1.7,-3.86,.27);
    bar('collar_inner_'+sign,'chest',[sign*.48,42.65,-3.2],[sign*4.35,44.85,-3.2],.5,.7,'linen');
  }
  box('neck','neck',[0,45.75,0],[3.75,3.4,3.9],'skin');
  box('throat_shadow','neck',[0,44.6,-2.02],[3.2,.95,.12],'skinShade');
  for(const sign of [-1,1]) {
    bar('collar_linen_'+sign,'neck',[sign*.3,43.4,-2.8],[sign*3.05,45.6,-2.8],.85,.48,'linen');
    bar('collar_blue_'+sign,'neck',[sign*.5,42.75,-3.12],[sign*3.65,45.4,-3.12],.65,.42,'clothDark');
    for(let i=0;i<6;i++) {
      const x=sign*(.55+i*.76), y=39.6+i*.65;
      box('chain_base_'+sign+'_'+i,'chest',[x,y,-3.89],[.65,.65,.3],'goldDark',[0,0,sign*15]);
      box('chain_link_'+sign+'_'+i,'chest',[x,y+.12,-4.08],[.51,.4,.18],i%2?'gold':'paleGold');
      if(i>1)box('chain_second_'+sign+'_'+i,'chest',[x,y-.82,-3.91],[.49,.45,.26],'gold');
    }
  }
  crest('chest_guardian','chest',0,38.1,-3.94,1.15);
  for(let row=0;row<3;row++) {
    const w=10.2-row*.38;
    framed('abdomen_segment_'+row,'waist',[0,34.5-row*1.05,-2.5],[w,.97,.7],row===1?'iron':'steel','silver',.16);
  }
  box('waist_inner','hips',[0,30.6,0],[11.5,3.4,5.9],'clothDark');
  box('oxblood_belt','hips',[0,30.55,0],[13.15,1.3,6.7],'red');
  box('belt_front_stitch','hips',[0,31.0,-3.45],[12.9,.13,.16],'goldDark');
  box('belt_front_lower_stitch','hips',[0,30.11,-3.45],[12.9,.13,.16],'goldDark');
  for(const sign of [-1,1]) {
    box('belt_keeper_'+sign,'hips',[sign*4.45,30.6,-3.55],[.48,1.6,.48],'gold');
    for(let i=0;i<3;i++)stud('belt_nail_'+sign+'_'+i,'hips',sign*(2.35+i*1.2),30.63,-3.54,.22);
  }
  framed('buckle_base','hips',[0,30.6,-3.65],[3.6,2.85,.6],'dark','gold',.29);
  crest('belt_guardian','hips',0,30.6,-4.0,.8);

  box('face_core','head',[0,50.95,-.15],[8.8,8.7,6.7],'skin');
  box('chin','head',[0,46.9,-.28],[7.2,.8,6.0],'skin');
  box('chin_lower','head',[0,46.45,-.12],[5.8,.35,5.45],'skinShade');
  box('forehead_plane','head',[0,53.47,-3.535],[7.7,3.05,.13],'skinLight');
  box('nose_bridge','head',[0,50.25,-3.69],[.6,1.45,.38],'skinLight');
  box('nose_tip','head',[0,49.75,-3.94],[.69,.42,.32],'skin');
  box('nose_shadow','head',[0,49.52,-3.96],[.45,.15,.25],'skinShade');
  box('mouth','head',[0,48.37,-3.53],[1.52,.13,.08],'skinShade');
  box('lower_lip','head',[0,48.16,-3.52],[1.17,.1,.06],'skinLight');
  for(const sign of [-1,1]) {
    box('temple_shade_'+sign,'head',[sign*4.05,50.9,-3.5],[.46,5.6,.13],'skinShade');
    box('ear_'+sign,'head',[sign*4.91,50.55,-.75],[1.12,2.0,1.3],'skin');
    box('ear_inset_'+sign,'head',[sign*5.5,50.5,-.81],[.14,1.08,.69],'skinShade');
    box('brow_'+sign,'head',[sign*2.0,52.05,-3.73],[3.1,.52,.28],'hair',[0,0,sign*11]);
    box('eye_socket_'+sign,'head',[sign*2.0,51.3,-3.58],[2.55,1.05,.17],'skinShade');
    box('eye_white_'+sign,'head',[sign*2.0,51.34,-3.7],[2.29,.7,.1],'eyeWhite');
    box('iris_'+sign,'head',[sign*1.71,51.34,-3.77],[.76,.73,.11],'pupil');
    box('pupil_'+sign,'head',[sign*1.68,51.34,-3.84],[.37,.64,.075],'hair');
    box('eye_catchlight_'+sign,'head',[sign*1.68-.12,51.53,-3.9],[.17,.16,.045],'eyeWhite');
    box('eyelid_'+sign,'head',[sign*2.0,51.75,-3.77],[2.55,.23,.15],'hair',[0,0,sign*3]);
  }
  for(const [y,w,h] of [[54.28,.22,.3],[53.96,.53,.4],[53.54,.75,.5],[53.13,.46,.36],[52.85,.2,.25]])
    box('celestial_eye_frame_'+y,'head',[0,y,-3.68],[w,h,.18],'goldDark');
  box('celestial_eye_inlay','head',[0,53.58,-3.81],[.31,.75,.12],'skinShade');
  box('celestial_eye_slit','head',[0,53.62,-3.9],[.105,.57,.07],'pupil');

  box('hair_skull','hair_top',[0,54.35,.7],[9.65,4.1,7.15],'hair');
  box('hair_crown_step','hair_top',[0,56.75,.65],[8.55,1.5,6.65],'hair');
  box('hair_upper_step','hair_top',[0,57.75,.85],[6.7,1.0,5.7],'hair');
  for(const sign of [-1,1]) {
    for(let i=0;i<3;i++) {
      box('swept_fringe_'+sign+'_'+i,'hair_top',[sign*(1.25+i*1.17),55.72-i*.43,-3.14],[2.2,1.23,.93],i%2?'hairLight':'hair',[0,0,sign*8]);
    }
    const bone=sign===1?'right_hair_lock':'left_hair_lock';
    box('temple_lock_'+sign,bone,[sign*4.35,51.86,-2.64],[1.45,6.65,1.75],'hair');
    box('face_lock_'+sign,bone,[sign*4.13,48.5,-2.77],[1.18,4.3,1.25],'hairLight',[0,0,sign*2]);
    box('long_side_lock_'+sign,bone,[sign*4.91,46.3,1.54],[1.32,9.3,2.48],'hair',[0,0,sign*2]);
    box('side_upper_hair_'+sign,bone,[sign*4.91,52.27,1.54],[1.33,3.03,3.0],'hairLight');
    box('side_lock_tip_'+sign,bone,[sign*5.12,41.15,1.59],[.89,2.1,1.4],'hairLight');
    box('side_lock_tie_'+sign,bone,[sign*5.12,41.75,.94],[.93,.54,.31],'goldDark');
    for(let j=0;j<2;j++)box('side_strand_'+sign+'_'+j,bone,[sign*(4.36+j*.46),48.65,-3.37],[.21,8.8,.17],'hair');
  }
  box('topknot_lower','topknot',[0,59.1,1.25],[5.45,2.0,4.55],'hair');
  box('topknot_mid','topknot',[0,60.6,1.35],[4.1,1.6,3.85],'hairLight');
  box('topknot_tip','topknot',[0,61.6,1.55],[2.75,.55,2.5],'hair');
  box('topknot_band','crown',[0,59.22,.94],[5.65,.68,4.78],'goldDark');
  for(const sign of [-1,1]) {
    box('diadem_wing_'+sign,'crown',[sign*3.8,57.65,-.6],[2.1,.57,1.1],'gold');
    box('diadem_end_'+sign,'crown',[sign*4.6,57.68,-.85],[.5,.95,1.1],'paleGold');
    box('crown_upright_'+sign,'crown',[sign*1.48,59.2,-1.72],[.58,3.5,.65],'gold');
    box('crown_upright_tip_'+sign,'crown',[sign*1.48,61.15,-1.66],[.39,.55,.47],'paleGold');
    box('crown_inset_'+sign,'crown',[sign*1.47,59.0,-2.08],[.2,1.75,.13],'dark');
  }
  box('crown_center','crown',[0,59.1,-1.8],[1.15,3.25,.6],'goldDark');
  box('crown_center_ridge','crown',[0,59.63,-2.13],[.39,3.05,.21],'paleGold');
  crest('diadem_medallion','crown',0,57.62,-2.13,.56);
  for(let i=0;i<7;i++) {
    const x=(i-3)*1.08, skew=(i%3)*.27;
    box('back_hair_upper_'+i,'hair_back_01',[x,51.35,4.18+skew],[1.22,9.3,1.8],i%2?'hairLight':'hair');
    box('back_hair_middle_'+i,'hair_back_02',[x*1.1,42.35,4.95+skew],[1.2,9.0,1.6],i%2?'hair':'hairLight',[-5,0,0]);
    const len=6.7+(3-Math.abs(i-3))*.83;
    box('back_hair_lower_'+i,'hair_back_03',[x*1.15,38.55-len/2,5.68+skew],[1.14,len,1.75],i%2?'hairLight':'hair',[-4,0,0]);
    if(i%2===0)box('back_hair_tie_'+i,'hair_back_03',[x*1.15,38.8-len,6.63+skew],[1.18,.56,.3],'goldDark');
  }
  box('pony_tail_central','hair_back_01',[0,54.35,5.1],[3.6,4.45,2.2],'hair');
  for(const sign of [-1,1]) {
    const side=sign===1?'right':'left', shoulder=side+'_shoulder', arm=side+'_arm', forearm=side+'_forearm', hand=side+'_hand';
    box('sleeve_'+side,arm,[sign*9.0,38.9,0],[4.3,7.9,4.6],'clothDark',[0,0,sign*6]);
    box('pauldron_padding_'+side,shoulder,[sign*9.0,42.5,.05],[6.1,4.5,6.15],'dark');
    for(let i=0;i<3;i++) {
      const cx=sign*(8.62+i*.32), y=44.25-i*1.65, w=4.95+i*.72;
      box('pauldron_tier_'+side+'_'+i,shoulder,[cx,y,.1],[w,1.55,6.15+i*.12],i===1?'iron':'steel');
      box('pauldron_front_rim_'+side+'_'+i,shoulder,[cx,y-.55,-3.12-i*.06],[w+.1,.32,.39],i===2?'gold':'silver');
      box('pauldron_back_rim_'+side+'_'+i,shoulder,[cx,y-.55,3.32+i*.06],[w+.1,.32,.37],'silver');
      box('pauldron_outside_rim_'+side+'_'+i,shoulder,[cx+sign*(w/2-.15),y-.55,.1],[.32,.35,6.55+i*.12],i===2?'gold':'silver');
      for(let k=0;k<3;k++)box('pauldron_tile_'+side+'_'+i+'_'+k,shoulder,[cx+(k-1)*1.59,y+.28,-3.13-i*.06],[1.4,.93,.29],k===1?'iron':'steel');
    }
    for(const t of [-1,1]) {
      box('pauldron_corner_cascade_'+side+'_'+t,shoulder,[sign*11.45,42.65,t*2.34],[1.0,3.05,1.2],'iron',[0,0,sign*15]);
      box('pauldron_corner_edge_'+side+'_'+t,shoulder,[sign*11.73,42.4,t*2.8],[.45,2.74,.33],'silver',[0,0,sign*15]);
    }
    box('pauldron_side_relief_'+side,shoulder,[sign*12.06,42.1,.2],[.55,1.73,2.2],'goldDark');
    box('pauldron_side_relief_center_'+side,shoulder,[sign*12.41,42.3,.2],[.25,.74,.94],'gold');
    for(let i=0;i<3;i++)box('shoulder_ridge_'+side+'_'+i,shoulder,[sign*(7.3+i*1.42),45.1,-.2],[1.07,.57,5.1],'silver');
    crest('shoulder_guardian_'+side,shoulder,sign*9.02,42.95,-3.55,.79);
    for(let i=0;i<3;i++) {
      const y=38.6-i*1.12;
      box('rerebrace_'+side+'_'+i,arm,[sign*(9.55+i*.07),y,-.06],[4.18,1.03,4.75],i===1?'iron':'steel');
      box('rerebrace_lip_'+side+'_'+i,arm,[sign*(9.55+i*.07),y-.37,-2.48],[4.35,.26,.32],'silver');
    }
    box('elbow_sleeve_'+side,forearm,[sign*9.7,35.0,0],[3.85,2.4,4.05],'cloth');
    box('elbow_guard_'+side,forearm,[sign*9.77,35.1,-2.12],[4.3,2.02,.85],'dark');
    box('elbow_rim_'+side,forearm,[sign*9.77,35.85,-2.6],[3.88,.27,.23],'silver');
    let fbone=forearm;
    if(sign===1) group('right_vambrace',[10.5,33.1,-1.4],forearm,[24,0,28]);
    else group('left_vambrace',[-10.05,31.5,-.35],forearm,[-3,0,-3]);
    fbone=side+'_vambrace';
    const cx=sign===1?10.5:-10.05, cy=sign===1?33.1:31.5, cz=sign===1?-1.4:-.35;
    box('forearm_core_'+side,fbone,[cx,cy,cz],[3.7,5.25,3.95],'iron');
    framed('forearm_front_'+side,fbone,[cx,cy,cz-2.14],[3.75,4.85,.57],'steel','silver',.29);
    box('forearm_central_ridge_'+side,fbone,[cx,cy,cz-2.53],[.38,4.3,.25],'gold');
    for(const t of [-1,1]) {
      box('forearm_band_'+side+'_'+t,fbone,[cx,cy+t*2.0,cz],[4.16,.65,4.3],'dark');
      box('forearm_band_edge_'+side+'_'+t,fbone,[cx,cy+t*2.0,cz-2.25],[4.24,.25,.23],'gold');
      for(let j=0;j<2;j++)stud('bracer_rivet_'+side+'_'+t+'_'+j,fbone,cx+(j*2-1)*1.4,cy+t*1.4,cz-2.55,.28);
    }
    crest('bracer_guardian_'+side,fbone,cx,cy+.84,cz-2.57,.47);
    const hx=sign===1?12.5:-10.15, hy=sign===1?30.4:26.7, hz=sign===1?-2.8:-.5;
    box('wrist_'+side,hand,[hx,hy+.9,hz],[3.2,1.5,3.45],'dark');
    box('hand_cuff_'+side,hand,[hx,hy+1.33,hz],[3.8,.75,3.8],'steel');
    box('palm_'+side,hand,[hx,hy-.48,hz+.26],[3.25,2.6,2.95],'dark');
    box('hand_backplate_'+side,hand,[hx,hy-.3,hz+1.77],[3.14,2.13,.38],'iron');
    for(let k=0;k<4;k++) {
      if(sign===1) {
        box('grip_finger_'+k,hand,[hx-.02,hy+.38-k*.64,hz-1.66],[2.98,.55,.86],'skinShade');
        box('grip_finger_side_'+k,hand,[hx+1.25,hy+.38-k*.64,hz-.76],[.64,.55,1.62],'dark');
      } else {
        box('finger_'+side+'_'+k,hand,[hx-1.15+k*.75,hy-1.28,hz-1.18],[.62,1.48,.92],'dark');
        box('knuckle_'+side+'_'+k,hand,[hx-1.15+k*.75,hy-.51,hz-1.73],[.6,.48,.28],'iron');
      }
    }
    box('thumb_'+side,hand,[hx-sign*1.7,hy-.34,hz-.35],[.82,1.62,1.12],sign===1?'skinShade':'dark',[0,0,sign*12]);
  }

  for(const sign of [-1,1]) {
    const side=sign===1?'right':'left', thigh=side+'_thigh', shin=side+'_shin', foot=side+'_foot';
    box('trouser_thigh_'+side,thigh,[sign*3.65,22.1,.18],[4.75,13.4,4.8],'clothDark');
    box('thigh_armour_'+side,thigh,[sign*3.65,20.5,-2.15],[4.5,9.2,.78],'iron');
    for(let i=0;i<3;i++)box('thigh_lamella_'+side+'_'+i,thigh,[sign*3.65,23.0-i*2.1,-2.6],[4.35,1.9,.46],'steel');
    box('knee_joint_'+side,shin,[sign*3.85,13.6,0],[4.35,3.8,4.5],'dark');
    framed('kneecap_'+side,shin,[sign*3.85,13.6,-2.58],[4.65,3.75,1.07],'iron','silver',.3);
    crest('knee_guardian_'+side,shin,sign*3.85,13.8,-3.21,.88);
    box('shin_body_'+side,shin,[sign*3.95,8.5,.1],[3.7,7.55,3.7],'dark');
    framed('greave_'+side,shin,[sign*3.95,8.4,-1.9],[3.5,6.5,.68],'iron','steel',.31);
    box('greave_ridge_'+side,shin,[sign*3.95,8.5,-2.33],[.62,6.2,.27],'silver');
    box('ankle_sleeve_'+side,foot,[sign*3.95,4.05,.04],[3.8,1.52,3.95],'clothDark');
    box('boot_heel_'+side,foot,[sign*3.95,2.46,.17],[4.13,3.3,4.55],'dark');
    box('boot_toe_'+side,foot,[sign*3.95,1.73,-2.4],[4.8,1.95,3.6],'iron');
    box('boot_sole_'+side,foot,[sign*3.95,.48,-1.52],[5.15,.8,7.09],'dark');
    box('boot_sole_edge_'+side,foot,[sign*3.95,.9,-5.13],[5.1,.3,.23],'steel');
    box('instep_plate_'+side,foot,[sign*3.95,3.1,-1.9],[3.6,.6,2.98],'steel',[-12,0,0]);
    for(let i=0;i<3;i++)box('toe_segment_'+side+'_'+i,foot,[sign*3.95+(i-1)*1.1,2.45,-3.45],[.93,.5,1.28],i===1?'silver':'steel');
    for(const t of [-1,1])box('boot_instep_trim_'+side+'_'+t,foot,[sign*3.95+t*1.87,2.77,-2.23],[.25,.27,2.47],'silver',[-12,0,0]);
    box('boot_ankle_guard_'+side,foot,[sign*3.95,4.0,-2.11],[3.8,1.1,.55],'steel');
    box('boot_side_clasp_'+side,foot,[sign*(3.95+1.85),3.7,.0],[.42,.72,.82],'gold');

    const front=side+'_front_cloth', tip=side+'_front_cloth_tip';
    for(let i=0;i<4;i++) {
      const x=sign*(.77+i*1.37), y=21.9+(i%2)*.13, len=14.5-(i%2)*.7;
      box('front_robe_fold_'+side+'_'+i,front,[x,y,-2.65-(i%2)*.27],[1.5,len,.9],i%2?'cloth':'clothLight',[0,0,sign*(i+1)*.75]);
      box('front_robe_hem_'+side+'_'+i,tip,[x+sign*.26,14.7+(i%2)*.45,-3.3-(i%2)*.2],[1.56,.48,.25],'linen');
    }
    box('outer_robe_fold_'+side,front,[sign*6.15,21.5,-1.65],[1.85,15.8,2.95],'clothDark',[0,0,sign*7]);
    box('outer_robe_inset_'+side,front,[sign*6.35,20.9,-3.17],[.58,13.3,.18],'clothLight',[0,0,sign*7]);
    box('outer_robe_border_'+side,front,[sign*7.13,13.68,-2.83],[2.0,.54,.85],'linen',[0,0,sign*7]);
    box('front_stole_'+side,front,[sign*2.55,23.0,-3.51],[1.27,12.8,.37],'red',[0,0,-sign*1]);
    box('front_stole_tip_'+side,tip,[sign*2.7,15.8,-3.72],[1.39,3.1,.4],'red');
    for(const t of [-1,1]) {
      box('stole_trim_'+side+'_'+t,front,[sign*2.55+t*.51,23,-3.76],[.16,12.85,.13],'goldDark');
      box('stole_tip_trim_'+side+'_'+t,tip,[sign*2.7+t*.54,15.8,-3.97],[.16,3.0,.13],'gold');
    }
    framed('stole_end_mount_'+side,tip,[sign*2.7,14.35,-4.0],[1.51,1.6,.23],'goldDark','gold',.2);
    box('stole_small_talisman_'+side,front,[sign*2.55,22.9,-3.92],[.91,1.52,.27],'gold');
    box('stole_talisman_inset_'+side,front,[sign*2.55,22.9,-4.08],[.41,.88,.09],'goldDark');
    const skirt=side+'_skirt', lower=side+'_skirt_lower';
    for(let row=0;row<6;row++) {
      const y=28.6-row*1.81, bone=row<4?skirt:lower, x=sign*(5.15+row*.15);
      box('tasset_dark_'+side+'_'+row,bone,[x,y,-.03],[3.0,1.8,6.03],'dark');
      for(let col=0;col<3;col++) {
        box('tasset_plate_'+side+'_'+row+'_'+col,bone,[x+(col-1)*.92,y+.05,-3.12],[.82,1.49,.36],(row+col)%3===1?'iron':'steel');
        if(row%2===0)stud('tasset_rivet_'+side+'_'+row+'_'+col,bone,x+(col-1)*.92,y+.4,-3.4,.2);
      }
      box('tasset_hem_'+side+'_'+row,bone,[x,y-.67,-3.33],[3.2,.3,.21],'silver');
      box('tasset_side_'+side+'_'+row,bone,[x+sign*1.43,y,.04],[.41,1.6,6.25],row%2?'iron':'steel');
      box('tasset_side_rim_'+side+'_'+row,bone,[x+sign*1.59,y-.67,.04],[.2,.3,6.35],'silver');
      box('tasset_rear_'+side+'_'+row,bone,[x,y,3.12],[2.97,1.5,.41],'iron');
      box('tasset_rear_rim_'+side+'_'+row,bone,[x,y-.67,3.35],[3.2,.3,.21],'steel');
    }
    const back=side+'_back_cloth';
    for(let i=0;i<4;i++) {
      const x=sign*(.95+i*1.38);
      box('rear_robe_fold_'+side+'_'+i,back,[x,21.25,2.94+i*.05],[1.63,16.0,.85],i%2?'clothLight':'cloth',[0,0,sign*(i+1)*1.6]);
      box('rear_robe_hem_'+side+'_'+i,back,[x+sign*.46,13.55,3.45+i*.05],[1.74,.53,.23],'linen');
    }
  }
  box('center_tabard','hips',[0,22.2,-3.31],[2.4,14.5,.54],'clothDark');
  box('center_tabard_tip','hips',[0,13.85,-3.5],[2.48,2.7,.58],'cloth');
  for(let i=0;i<5;i++) {
    box('tabard_cloud_step_'+i,'hips',[(i<3?i:4-i)*.31-.31,13.25+i*.3,-3.85],[.72,.37,.18],'gold');
  }
  box('rear_red_sash','rear_sash',[0,23.5,3.6],[2.8,13.2,.5],'red');
  box('rear_red_sash_lower','rear_sash_tip',[0,14.4,4.0],[2.94,5.2,.5],'red');
  for(const sign of [-1,1]) {
    box('rear_sash_edge_'+sign,'rear_sash',[sign*1.15,23.5,3.96],[.25,13.2,.2],'goldDark');
    box('rear_sash_tip_edge_'+sign,'rear_sash_tip',[sign*1.25,14.4,4.36],[.25,5.3,.2],'gold');
  }
  for(let i=0;i<5;i++) {
    const w=[.45,1.0,1.65,1.0,.45][i];
    box('rear_sash_cloud_'+i,'rear_sash_tip',[0,13.0+i*.4,4.41],[w,.31,.2],'gold');
  }

  const wx=13.05,wz=-2.8,W='weapon';
  box('weapon_blackwood_shaft',W,[wx,25.5,wz],[.84,50.4,.84],'dark');
  box('weapon_shaft_highlight',W,[wx-.23,25.5,wz-.46],[.14,48.8,.14],'iron');
  box('weapon_grip_wrap',W,[wx,30.05,wz],[1.03,5.6,1.03],'leather');
  for(let i=0;i<8;i++)box('grip_binding_'+i,W,[wx,27.8+i*.63,wz],[1.09,.13,1.09],'dark');
  for(const y of [1.9,3.9,25.9,33.45,43.2,47.1,49.0]) {
    box('shaft_binding_'+y,W,[wx,y,wz],[1.15,.56,1.15],'goldDark');
    box('shaft_binding_rim_'+y,W,[wx,y+.19,wz],[1.21,.14,1.21],'gold');
  }
  box('weapon_pommel_base',W,[wx,.78,wz],[1.85,1.0,1.85],'goldDark');
  box('weapon_pommel_body',W,[wx,2.73,wz],[1.62,2.9,1.62],'iron');
  for(const sign of [-1,1])box('pommel_gold_rail_'+sign,W,[wx+sign*.65,2.7,wz-.86],[.21,2.8,.2],'gold');
  box('weapon_neck',W,[wx,49.8,wz],[1.65,2.5,1.55],'iron');
  box('weapon_socket',W,[wx,51.1,wz],[3.55,1.2,1.58],'goldDark');
  box('weapon_socket_lip',W,[wx,50.65,wz],[3.77,.38,1.72],'gold');
  crest('weapon_guardian',W,wx,52.1,wz-.89,.88);
  box('central_blade_root',W,[wx,54.1,wz],[3.1,3.65,.96],'blade');
  box('central_blade_long',W,[wx,59.9,wz],[2.75,8.65,.81],'blade');
  box('central_blade_ridge',W,[wx,58.8,wz-.48],[.71,10.1,.21],'iron');
  box('central_blade_back_ridge',W,[wx,58.8,wz+.48],[.71,10.1,.21],'iron');
  box('central_blade_gold_inlay',W,[wx,55.95,wz-.64],[.24,3.5,.11],'goldDark');
  for(const sign of [-1,1]) {
    box('blade_straight_edge_'+sign,W,[wx+sign*1.48,59.9,wz-.04],[.25,8.75,.81],'bladeEdge');
    bar('blade_tip_edge_'+sign,W,[wx+sign*1.48,64.3,wz-.01],[wx+sign*.12,69.6,wz-.01],.26,.73,'bladeEdge');
    for(let i=0;i<5;i++)box('blade_tip_fill_'+sign+'_'+i,W,[wx+sign*(.64-i*.125),64.63+i*.91,wz],[1.1-i*.19,.95,.6],'blade');
    const bx=wx+sign*3.85;
    box('side_prong_base_'+sign,W,[wx+sign*2.45,52.5,wz],[2.3,.65,.92],'iron',[0,0,sign*28]);
    box('side_prong_lower_'+sign,W,[wx+sign*3.46,54.5,wz],[.77,3.95,.76],'blade',[0,0,sign*5]);
    box('side_prong_stem_'+sign,W,[bx,57.8,wz],[.83,3.6,.67],'blade');
    box('side_prong_edge_'+sign,W,[bx+sign*.47,56.75,wz-.015],[.2,5.71,.73],'bladeEdge');
    bar('side_prong_tip_edge_'+sign,W,[bx+sign*.47,59.5,wz],[bx-sign*.16,61.8,wz],.2,.58,'bladeEdge');
    box('side_prong_tip_'+sign,W,[bx+sign*.06,60.23,wz],[.52,1.52,.52],'blade',[0,0,-sign*16]);
    box('blade_base_ornament_'+sign,W,[wx+sign*2.02,53.4,wz-.54],[.55,1.48,.3],'gold');
  }

  const atlas=document.createElement('canvas');atlas.width=1024;atlas.height=1024;
  const ctx=atlas.getContext('2d');ctx.fillStyle='#10151d';ctx.fillRect(0,0,1024,1024);
  const rgb=a=>'rgb('+a.map(v=>Math.max(0,Math.min(255,Math.round(v)))).join(',')+')';
  let px=2,py=2,rowH=0,seed=73219;
  function rand(){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;}
  function tile(w,h,material,face) {
    w=Math.max(2,Math.ceil(w*2));h=Math.max(2,Math.ceil(h*2));
    if(px+w+2>1024){px=2;py+=rowH+2;rowH=0;} if(py+h+2>1024)throw Error('Texture atlas overflow at '+py);
    const u=px,v=py,m=mat[material];
    for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){
      let n=(rand()-.5)*m.noise*2;
      if(m.edge && w>3 && h>3){if(yy===0||xx===0)n+=m.edge*.8;if(yy===h-1||xx===w-1)n-=m.edge*.7;}
      if(material.startsWith('cloth')&&xx%5===0)n-=2;
      if(material.startsWith('hair')&&xx%4===0)n+=2;
      ctx.fillStyle=rgb(m.color.map(c=>c+n));ctx.fillRect(u+xx,v+yy,1,1);
    }
    px+=w+2;rowH=Math.max(rowH,h);return [u,v,u+w,v+h];
  }
  for(const p of parts) {
    const d=p.to.map((v,i)=>v-p.from[i]);p.uv={};
    for(const f of ['north','south','east','west','up','down']) {
      const dims=f==='north'||f==='south'?[d[0],d[1]]:f==='east'||f==='west'?[d[2],d[1]]:[d[0],d[2]];
      p.uv[f]=tile(dims[0],dims[1],p.material,f);
    }
  }
  Project.texture_width=1024;Project.texture_height=1024;
  const tex=new Texture({name:'yang_jian_v2_atlas',render_mode:'default'}).fromDataURL(atlas.toDataURL()).add(false);
  for(const p of parts){
    const c=new Cube({name:p.name,from:p.from,to:p.to,origin:p.origin,rotation:p.rotation,box_uv:false,autouv:0});
    for(const f of Object.keys(p.uv)){c.faces[f].texture=tex.uuid;c.faces[f].uv=p.uv[f];}
    c.addTo(bones[p.bone]).init();
  }
  Modes.options.edit.select();unselectAll();Canvas.updateAll();
  const preview=Preview.selected;preview.setProjectionMode(true);preview.camOrtho.zoom=.24;
  preview.camera.position.set(83,58,-155);preview.controls.target.set(1.8,34,0);preview.camera.lookAt(preview.controls.target);preview.camOrtho.updateProjectionMatrix();preview.controls.update();
  return JSON.stringify({cubes:Cube.all.length,meshes:Mesh.all.length,bones:Group.all.length,textures:Texture.all.length,atlasUsedHeight:py+rowH});
})()
