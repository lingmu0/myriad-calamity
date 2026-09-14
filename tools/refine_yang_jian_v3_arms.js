(api) => {
  const {parts, bones, box, bar} = api;
  const owned = /^(left|right)_(arm|shoulder|forearm|hand|vambrace)$/;
  for (let i=parts.length-1;i>=0;i--) if (owned.test(parts[i].bone)) parts.splice(i,1);

  for (const s of [-1,1]) {
    const side=s===1?'right':'left';
    const arm=side+'_arm', shoulder=side+'_shoulder', forearm=side+'_forearm';
    const vambrace=side+'_vambrace', hand=side+'_hand';
    const add=(n,b,c,d,m='iron',r=[0,0,0],o=c)=>box('v3_'+side+'_'+n,b,c,d,m,r,o);
    const shoulderBox=(n,x,y,z,w,h,d,m='iron',r=[0,0,0])=>add('shoulder_'+n,shoulder,[s*x,y,z],[w,h,d],m,r);
    bones[shoulder].rotation=[12,-s*16,-s*11];

    // Continuous padded shoulder volume underneath the stepped metal shell.
    add('sleeve_core',arm,[s*9.0,38.9,.10],[4.1,8.1,4.5],'clothDark',[0,0,s*4]);
    add('sleeve_front_fold',arm,[s*9.1,38.15,-2.22],[3.54,5.8,.27],'cloth');
    for(let k=0;k<3;k++) {
      add('sleeve_side_fold_'+k,arm,[s*11.04,37.65-k*.83,.02],[.26,.50,3.48],k%2?'clothLight':'cloth',[0,0,s*9]);
    }
    shoulderBox('padding',9.0,42.22,.05,5.45,4.52,5.85,'dark');
    shoulderBox('under_shell',9.08,42.90,.12,5.32,3.42,5.98,'iron');
    shoulderBox('upper_crown',8.70,44.78,.03,3.80,.68,4.1,'steel');
    shoulderBox('upper_inner_bevel',6.91,44.20,.03,.90,1.07,4.66,'steel');
    shoulderBox('upper_outer_bevel',10.60,44.18,.03,1.08,.98,4.75,'iron');
    shoulderBox('outer_shell_wall',11.22,42.70,.10,.97,3.47,5.55,'iron');

    // Six interlocking strips over the cap articulate a rounded silhouette in square steps.
    for(let i=0;i<5;i++) {
      const z=-2.50+i*1.24, edge=Math.abs(i-2);
      const y=44.92-edge*.37;
      shoulderBox('cap_facet_'+i,8.67,y,z,4.50,.58,1.12,i===2?'iron':'steel');
      shoulderBox('cap_inner_step_'+i,6.51,y-.38,z,.73,.82,1.13,'steel');
      shoulderBox('cap_outer_step_'+i,10.89,y-.57,z,.80,.95,1.13,i%2?'steel':'iron');
      shoulderBox('cap_crown_edge_'+i,8.23,y+.32,z,2.66,.18,1.11,'silver');
    }
    // Front and rear are broad sloping plate faces, rather than stacked horizontal bars.
    for(const face of [-1,1]) {
      const z=face*3.03;
      shoulderBox('face_'+face,8.93,42.45,z,5.55,3.55,.65,'iron');
      // Offset shallow facets make the ornament part of the shell, with no dark picture-frame recess.
      shoulderBox('face_center_'+face,8.98,42.74,z+face*.39,3.51,2.74,.21,'iron');
      for(let j=0;j<3;j++) {
        shoulderBox('face_facet_'+face+'_'+j,7.15+j*1.43,42.61-j*.11,z+face*(.50+(j===1?.07:0)),1.35,2.52-j*.15,.18,j===1?'iron':'steel');
      }
      shoulderBox('inner_edge_'+face,6.20,42.65,z+face*.33,.21,2.54,.32,'steel');
      shoulderBox('outer_edge_'+face,11.65,42.54,z+face*.28,.23,2.94,.32,'steel');
      shoulderBox('lower_bead_shadow_'+face,8.98,40.55,z+face*.14,6.15,.28,.44,'dark');
      shoulderBox('lower_bead_'+face,8.98,40.59,z+face*.40,6.22,.18,.22,'steel');
      shoulderBox('top_inner_step_'+face,7.0,44.3,z+face*.33,1.15,.56,.45,'silver');
      shoulderBox('top_middle_step_'+face,8.33,44.62,z+face*.32,1.43,.48,.49,'steel');
      shoulderBox('top_outer_step_'+face,10.0,44.22,z+face*.33,1.21,.58,.47,'silver');
      for(let j=0;j<2;j++) {
        shoulderBox('edge_block_'+face+'_'+j,6.59+j*4.2,42.47,z+face*.50,.36,.76,.29,'steel');
        shoulderBox('corner_pin_'+face+'_'+j,6.62+j*4.17,41.23,z+face*.76,.25,.25,.17,'paleGold');
      }
    }
    // The broad lower lip continues outward and down into overlapping skirt plates.
    // Their slanted, shallow faces interrupt the former rectangular perimeter.
    for(const face of [-1,1]) {
      for(let j=0;j<3;j++) {
        const x=7.31+j*1.72,y=40.04-j*.13,z=face*(3.31+j*.04);
        shoulderBox('lower_overlap_'+face+'_'+j,x,y,z,1.86,1.11,.71,j===1?'steel':'iron',[0,0,-s*5]);
        shoulderBox('lower_overlap_facet_'+face+'_'+j,x,y+.13,z+face*.41,1.52,.53,.16,'steel',[0,0,-s*5]);
        shoulderBox('lower_overlap_silver_'+face+'_'+j,x,y-.43,z+face*.41,1.97,.23,.25,'silver',[0,0,-s*5]);
        shoulderBox('lower_overlap_gold_'+face+'_'+j,x,y-.63,z+face*.40,1.94,.14,.25,'gold',[0,0,-s*5]);
      }
    }
    shoulderBox('outer_lower_wrap',11.97,40.60,.05,.38,.37,6.67,'silver');
    shoulderBox('outer_lower_gold',12.05,40.38,.05,.23,.16,6.71,'gold');
    shoulderBox('outer_top_facet',11.71,43.01,.08,.47,2.3,3.76,'steel');
    for(const z of [-1.83,1.94]) {
      shoulderBox('outside_rail_'+z,11.99,42.46,z,.23,2.85,.36,'silver');
      shoulderBox('outside_mount_'+z,12.14,42.52,z,.23,.55,.62,'goldDark');
      shoulderBox('outside_rivet_'+z,12.29,42.55,z,.11,.28,.27,'gold');
    }

    // A wide horned relief lives on the shoulder only; wrists use a different clasp motif.
    const relief=(n,dx,dy,dz,w,h,d,m)=>shoulderBox('beast_'+n,9.43+dx*.85,43.14+dy*.85,-3.71+dz*.85,w*.85,h*.85,d*.85,m);
    relief('silhouette',0,.12,0,2.37,1.88,.25,'goldDark');
    relief('forehead',0,.63,-.17,1.40,.99,.33,'gold');
    relief('forehead_step',0,1.03,-.27,.77,.43,.31,'paleGold');
    relief('brow_mid',0,.30,-.43,.44,.65,.35,'paleGold');
    relief('brow_cap',0,.59,-.51,.80,.28,.27,'gold');
    for(const t of [-1,1]) {
      relief('horn_root_'+t,t*.84,.88,-.12,.57,.86,.39,'gold');
      relief('horn_flare_'+t,t*1.24,1.13,-.16,.65,.42,.38,'paleGold');
      relief('horn_ridge_'+t,t*1.46,1.36,-.10,.29,.67,.33,'gold');
      relief('horn_highlight_'+t,t*1.49,1.54,-.27,.31,.26,.20,'paleGold');
      relief('outer_cheek_'+t,t*1.13,-.15,-.11,.49,1.0,.42,'steel');
      relief('inner_cheek_'+t,t*.85,-.15,-.41,.40,.74,.27,'gold');
      relief('brow_'+t,t*.66,.29,-.54,.89,.34,.29,'paleGold');
      relief('eye_'+t,t*.66,.03,-.46,.53,.22,.12,'goldDark');
      relief('jaw_'+t,t*.73,-.64,-.25,.53,.42,.35,'silver');
      relief('small_fang_'+t,t*.35,-.62,-.62,.24,.50,.25,'paleGold');
      relief('mane_middle_'+t,t*1.25,-.59,-.11,.39,.44,.34,'steel');
      relief('mane_low_'+t,t*.97,-.96,-.12,.45,.34,.31,'silver');
    }
    relief('nose_bridge',0,-.08,-.57,.56,.65,.32,'gold');
    relief('nose_end',0,-.36,-.76,.74,.30,.26,'paleGold');
    relief('muzzle',0,-.58,-.48,.91,.41,.30,'gold');
    relief('mouth',0,-.68,-.67,.44,.14,.13,'goldDark');
    relief('chin',0,-.99,-.39,.65,.33,.31,'gold');
    relief('chin_bottom',0,-1.20,-.12,.46,.28,.27,'steel');

    // Three independently rounded upper-arm lames preserve joint gaps under the shell.
    for(let row=0;row<3;row++) {
      const y=39.35-row*1.26, x=s*(9.56+row*.08), width=4.60-row*.12;
      add('upper_lame_core_'+row,arm,[x,y,.08],[width,.94,4.62],row%2?'iron':'steel');
      add('upper_lame_front_'+row,arm,[x,y-.10,-2.36],[width-.58,.82,.53],'iron');
      add('upper_lame_center_'+row,arm,[x,y+.11,-2.68],[2.68,.55,.17],'steel');
      add('upper_lame_lower_'+row,arm,[x,y-.42,-2.64],[width+.13,.25,.30],'silver');
      add('upper_lame_back_'+row,arm,[x,y-.34,2.40],[width+.02,.28,.38],'steel');
      add('upper_lame_side_'+row,arm,[x+s*(width/2-.06),y-.15,.04],[.35,.72,3.75],'steel');
      add('upper_lame_side_lip_'+row,arm,[x+s*(width/2+.07),y-.40,.06],[.24,.23,4.59],'silver');
    }
    add('elbow_fabric',forearm,[s*9.76,35.22,.08],[3.56,2.4,3.89],'cloth');
    add('elbow_cup',forearm,[s*9.78,35.32,-1.97],[3.83,1.90,1.29],'iron');
    add('elbow_cup_front',forearm,[s*9.78,35.25,-2.60],[3.01,1.1,.46],'steel');
    add('elbow_cup_lip',forearm,[s*9.78,35.78,-2.53],[3.25,.29,.37],'silver');
    add('elbow_side_hinge',forearm,[s*11.60,35.16,.1],[.62,1.20,1.42],'dark');
    add('elbow_hinge_pin',forearm,[s*11.95,35.16,.1],[.19,.61,.63],'steel');

    const cx=s===1?10.5:-10.05, cy=s===1?33.1:31.5, cz=s===1?-1.4:-.35;
    bones[vambrace].rotation=s===1?[24,0,28]:[-3,0,-3];
    const v=(n,dx,dy,dz,w,h,d,m='iron')=>add('bracer_'+n,vambrace,[cx+dx,cy+dy,cz+dz],[w,h,d],m);
    v('padded_core',0,0,0,3.64,5.45,3.88,'dark');
    v('inner_plate',-s*1.62,.03,.02,.44,4.5,3.55,'iron');
    v('outer_plate',s*1.74,.03,.05,.57,4.70,3.70,'steel');
    v('outer_inset',s*2.09,.03,.05,.19,3.78,2.59,'iron');
    for(const z of [-1.35,1.45])v('outer_edge_'+z,s*2.17,.04,z,.21,4.20,.25,'silver');
    v('back_shell',0,.03,1.77,3.41,4.62,.48,'iron');
    v('back_ridge',0,.05,2.06,.90,4.21,.25,'steel');
    v('front_base',0,.05,-1.90,3.57,4.73,.61,'iron');
    v('front_center_facet',0,.04,-2.31,1.37,4.26,.39,'steel');
    for(const t of [-1,1]) {
      v('front_flank_'+t,t*1.18,.03,-2.16,.85,4.15,.34,'iron');
      v('front_flank_ridge_'+t,t*1.36,.01,-2.39,.23,4.24,.18,'silver');
      v('center_inlay_'+t,t*.35,.05,-2.55,.19,3.90,.15,'goldDark');
      v('inner_bevel_'+t,t*1.82,.03,-1.76,.27,4.6,.72,'steel');
      for(let j=0;j<3;j++) {
        v('plate_etch_'+t+'_'+j,t*.88,-1.15+j*1.16,-2.39,.35,.78,.09,j===1?'steel':'iron');
      }
    }
    for(const end of [-1,1]) {
      const y=end*2.18;
      v('band_under_'+end,0,y,0,4.13,.70,4.31,'dark');
      v('band_face_'+end,0,y,-2.29,4.19,.56,.55,'steel');
      v('band_upper_edge_'+end,0,y+.29,-2.56,4.22,.17,.18,'silver');
      v('band_lower_edge_'+end,0,y-.29,-2.55,4.22,.17,.17,'silver');
      v('band_gold_strip_'+end,0,y,-2.61,3.80,.17,.15,'goldDark');
      for(const t of [-1,1]) {
        v('band_side_'+end+'_'+t,t*2.0,y,.03,.42,.66,4.25,'steel');
        v('band_rivet_'+end+'_'+t,t*1.64,y,-2.77,.26,.28,.17,'paleGold');
      }
      v('band_back_'+end,0,y,2.11,3.97,.66,.39,'steel');
    }
    // Compact square scroll clasps on the wrist; no repeated miniature beast faces.
    v('clasp_base',.1,.83,-2.63,1.82,1.64,.42,'goldDark');
    v('clasp_center',.1,.83,-2.91,.69,.76,.34,'gold');
    v('clasp_center_boss',.1,.83,-3.13,.28,.31,.17,'paleGold');
    for(const t of [-1,1]) {
      v('clasp_side_'+t,.1+t*.72,.84,-2.85,.35,1.05,.23,'gold');
      v('clasp_scroll_'+t,.1+t*.49,1.49,-2.85,.79,.27,.25,'paleGold');
      v('clasp_scroll_tip_'+t,.1+t*.82,1.27,-2.94,.22,.38,.20,'gold');
      v('clasp_lower_'+t,.1+t*.49,.15,-2.83,.72,.24,.21,'gold');
    }

    if(s===1) {
      // Four dark articulated fingers curl around the staff; pale exposed pads are short.
      // The staff remains on its original x=13.05,z=-2.8 attachment axis.
      const hx=13.05, hy=30.25, hz=-2.8;
      add('wrist_connect',hand,[11.99,31.12,-2.21],[2.50,1.88,2.95],'dark',[14,0,27]);
      add('gauntlet_cuff',hand,[12.12,31.10,-2.26],[3.45,.83,3.71],'steel',[16,0,27]);
      add('grasp_palm',hand,[hx-.83,hy,hz+.68],[2.28,3.00,1.74],'dark');
      add('grasp_backplate',hand,[hx-1.76,hy+.04,hz+.29],[.48,2.75,2.49],'iron');
      add('grasp_backplate_rim',hand,[hx-2.04,hy+.09,hz+.20],[.20,2.36,1.95],'steel');
      for(let k=0;k<4;k++) {
        const y=hy+1.06-k*.70, d=k===3?.90:1;
        add('grasp_knuckle_'+k,hand,[hx+.66,y,hz+.38],[1.05*d,.59,1.36],'iron');
        add('grasp_finger_side_'+k,hand,[hx+.97,y-.03,hz-.44],[.70*d,.57,.98],'dark');
        add('grasp_finger_bend_'+k,hand,[hx+.49,y-.05,hz-.94],[1.21*d,.53,.62],'dark');
        add('grasp_finger_pad_'+k,hand,[hx-.11,y-.06,hz-.88],[.57,.47,.50],'skinShade');
        add('grasp_knuckle_edge_'+k,hand,[hx+.65,y+.24,hz-.27],[.88*d,.13,.23],'steel');
        add('grasp_finger_seam_'+k,hand,[hx+.72,y-.02,hz-1.24],[.27,.39,.08],'leather');
      }
      add('grasp_thumb_root',hand,[hx-1.13,hy+.83,hz-.08],[1.13,1.49,1.3],'dark',[0,0,-28]);
      add('grasp_thumb_middle',hand,[hx-.75,hy+.90,hz-.84],[.85,1.0,.91],'dark',[0,0,-34]);
      add('grasp_thumb_pad',hand,[hx-.51,hy+.59,hz-1.10],[.70,.74,.48],'skinShade',[0,0,-24]);
      add('grasp_thumb_plate',hand,[hx-1.12,hy+1.17,hz-.64],[.74,.59,.33],'steel',[0,0,-28]);
    } else {
      const hx=-10.18,hy=27.38,hz=-.60;
      add('wrist_connect',hand,[hx,hy+1.10,hz],[3.04,1.5,3.26],'dark');
      add('gauntlet_cuff',hand,[hx,hy+1.16,hz],[3.84,.82,3.78],'steel');
      add('cuff_lip',hand,[hx,hy+.79,hz-1.94],[3.96,.19,.26],'silver');
      add('relaxed_palm',hand,[hx,hy-.30,hz+.12],[3.27,2.63,2.73],'dark');
      add('relaxed_backplate',hand,[hx,hy-.20,hz-1.34],[3.26,2.29,.49],'iron');
      add('relaxed_backplate_facet',hand,[hx,hy-.15,hz-1.62],[2.52,1.69,.21],'steel');
      for(let k=0;k<4;k++) {
        const x=hx-1.18+k*.78, l=[1.35,1.64,1.61,1.25][k];
        add('relaxed_knuckle_'+k,hand,[x,hy-.96,hz-1.48],[.66,.66,.59],'iron');
        add('relaxed_finger_upper_'+k,hand,[x,hy-1.20-l*.30,hz-.93],[.66,l*.64,.90],'dark',[-13,0,0]);
        add('relaxed_finger_lower_'+k,hand,[x,hy-1.28-l*.65,hz-.38],[.63,l*.53,.82],'dark',[-27,0,0]);
        add('relaxed_fingertip_'+k,hand,[x,hy-1.27-l*.80,hz+.02],[.59,.34,.43],'skinShade');
        add('relaxed_knuckle_rim_'+k,hand,[x,hy-.71,hz-1.75],[.64,.16,.17],'steel');
      }
      add('relaxed_thumb_root',hand,[hx+1.73,hy-.20,hz-.43],[.85,1.64,1.11],'dark',[0,0,-15]);
      add('relaxed_thumb_joint',hand,[hx+1.91,hy-.87,hz-.07],[.73,.88,.99],'dark',[-19,0,-10]);
      add('relaxed_thumb_tip',hand,[hx+1.87,hy-1.13,hz+.32],[.62,.52,.49],'skinShade');
    }
  }
}
