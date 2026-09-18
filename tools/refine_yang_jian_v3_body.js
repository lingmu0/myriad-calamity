(api) => {
  const {parts,bones,box,bar,framed,stud,crest,group,mat}=api;
  Object.assign(mat,{
    steelMid:{color:[100,106,114],noise:5,edge:17},
    steelDark:{color:[82,87,95],noise:4,edge:14},
    engraving:{color:[58,64,73],noise:3,edge:6},
    oldSilver:{color:[148,150,148],noise:4,edge:14},
    clothFold:{color:[39,48,61],noise:4,edge:4},
    boneGold:{color:[169,155,126],noise:4,edge:16}
  });
  const replaced=new Set(['chest','waist','hips','neck','rear_sash','rear_sash_tip']);
  for(const side of ['left','right'])for(const suffix of ['thigh','shin','foot','skirt','skirt_lower','front_cloth','front_cloth_tip','back_cloth'])replaced.add(side+'_'+suffix);
  for(let i=parts.length-1;i>=0;i--)if(replaced.has(parts[i].bone))parts.splice(i,1);
  function plaque(name,bone,x,y,z,w,h,base='steelDark',rim='oldSilver',r=.18){
    framed(name,bone,[x,y,z],[w,h,.47],base,rim,r);
    box(name+'_upper_bevel',bone,[x,y+h/2-.26,z-.36],[w-.38,.18,.15],'steelMid');
  }
  function filigree(name,bone,x,y,z,scale=1,material='boneGold'){
    const pattern=[[-2,0],[-2,1],[-1,1],[-1,2],[0,2],[1,2],[1,1],[2,1],[2,0],[1,-1],[0,-1],[-1,-1],[0,0]];
    for(let i=0;i<pattern.length;i++){
      const [u,v]=pattern[i];box(name+'_'+i,bone,[x+u*.27*scale,y+v*.27*scale,z],[.3*scale,.31*scale,.17*scale],material);
    }
  }

  box('v3_cuirass_padding','chest',[0,38.95,.1],[11.75,10.2,6.6],'clothDark');
  box('v3_cuirass_spine','chest',[0,39.05,3.43],[2.25,8.4,.72],'steelDark');
  for(const s of [-1,1]){
    box('v3_cuirass_flank_'+s,'chest',[s*5.55,39,.04],[1.3,8.8,6.5],'steelDark');
    box('v3_back_panel_'+s,'chest',[s*3.1,39.0,3.47],[3.8,8.35,.77],'iron');
    for(let i=0;i<4;i++){
      box('v3_back_scale_'+s+'_'+i,'chest',[s*3.12,36.2+i*1.82,3.89],[3.76,1.57,.42],'steelDark');
      box('v3_back_edge_'+s+'_'+i,'chest',[s*3.12,35.65+i*1.82,4.14],[3.69,.21,.18],'steelMid');
    }
    for(let row=0;row<6;row++){
      const heights=[42.6,41.1,39.6,38.1,36.6,35.1],widths=[4.65,5.25,5.55,5.4,4.9,4.0];
      const w=widths[row],y=heights[row],x=s*(.26+w/2),z=-3.32-(row<3?row*.19:(5-row)*.14);
      box('v3_breastplate_rank_'+s+'_'+row,'chest',[x,y,z],[w,1.65,.74],row===0?'iron':'steelMid');
      box('v3_breastplate_inner_edge_'+s+'_'+row,'chest',[s*.44,y,z-.46],[.24,1.6,.24],'steelDark');
      box('v3_breastplate_outer_bevel_'+s+'_'+row,'chest',[s*(w+.17),y-.22,z-.31],[.26,1.38,.37],'oldSilver');
      if(row===5)box('v3_breastplate_bottom_'+s,'chest',[x,y-.59,z-.49],[w,.26,.25],'silver');
    }
    for(let col=0;col<3;col++){
      const x=s*(1.3+col*1.62),height=col===0?6.8:col===1?7.2:5.5;
      box('v3_chest_contour_'+s+'_'+col,'chest',[x,39.45,-4.11],[.25,height,.16],col===1?'steelDark':'steelMid');
    }
    box('v3_harness_strap_'+s,'chest',[s*5.08,39.25,-4.04],[.71,9.35,.45],'dark',[0,0,-s*4]);
    box('v3_harness_braid_'+s,'chest',[s*5.23,39.12,-4.36],[.19,8.7,.19],'goldDark',[0,0,-s*4]);
    for(let i=0;i<5;i++){
      stud('v3_harness_pin_'+s+'_'+i,'chest',s*(5.26+(i-2)*.035),35.85+i*1.61,-4.47,.24);
      box('v3_harness_clasp_'+s+'_'+i,'chest',[s*5.05,35.82+i*1.61,-4.41],[.49,.36,.24],i%2?'boneGold':'gold');
    }
    for(let row=0;row<4;row++)box('v3_side_lamella_'+s+'_'+row,'chest',[s*6.02,35.4+row*1.78,.07],[.51,1.5,6.41],'steelDark');
  }
  // Cut a real neckline through the padding and upper plates before adding its rim.
  const collarCuts=[[42.20,42.64,1.05],[42.64,43.10,1.52],[43.10,43.55,1.92],[43.55,46,2.20]];
  let collarFragment=0;
  function subtractCollar(p,lo,hi){
    const a=p.from.map((v,i)=>Math.max(v,lo[i])),b=p.to.map((v,i)=>Math.min(v,hi[i]));
    if(a.some((v,i)=>v>=b[i]))return [p];
    if(p.rotation.some(v=>Math.abs(v)>.001))throw Error('Rotated chest part in neckline: '+p.name);
    const result=[];
    const add=(from,to)=>{
      if(from.some((v,i)=>to[i]-v<=.00001))return;
      result.push({...p,name:p.name.split('_neckcut_')[0]+'_neckcut_'+collarFragment++,from,to,origin:from.map((v,i)=>(v+to[i])/2)});
    };
    add([...p.from],[a[0],p.to[1],p.to[2]]);
    add([b[0],p.from[1],p.from[2]],[...p.to]);
    add([a[0],p.from[1],p.from[2]],[b[0],a[1],p.to[2]]);
    add([a[0],b[1],p.from[2]],[b[0],p.to[1],p.to[2]]);
    add([a[0],a[1],p.from[2]],[b[0],b[1],a[2]]);
    add([a[0],a[1],b[2]],[b[0],b[1],p.to[2]]);
    return result;
  }
  for(let i=parts.length-1;i>=0;i--){
    if(parts[i].bone!=='chest')continue;
    let pieces=[parts[i]];
    for(const [bottom,top,halfWidth] of collarCuts){
      pieces=pieces.flatMap(p=>subtractCollar(p,[-halfWidth,bottom,-16],[halfWidth,top,2.10]));
    }
    parts.splice(i,1,...pieces);
  }
  // The upright neck continues down inside the opening; its top remains under the jaw.
  box('v3_neck','neck',[0,43.46,-.15],[3.25,2.62,3.40],'skin');
  box('v3_collar_throat_shadow','neck',[0,44.58,-1.89],[3.18,.16,.08],'skinShade');
  const collarPath=[[0,42.25],[1.05,42.25],[1.05,42.68],[1.52,42.68],[1.52,43.13],[1.92,43.13],[1.92,43.57],[2.20,43.57],[2.20,43.97]];
  const collarZ=y=>-4.14+(y-42.25)*.53;
  for(const s of [-1,1])for(let i=1;i<collarPath.length;i++){
    const [ax,ay]=collarPath[i-1],[bx,by]=collarPath[i],az=collarZ(ay),bz=collarZ(by);
    const x=s*(ax+bx)/2,y=(ay+by)/2,z=(az+bz)/2;
    const w=Math.abs(bx-ax),h=Math.abs(by-ay),d=Math.abs(bz-az);
    box('v3_collar_lapel_'+s+'_'+i,'chest',[x,y-.035,z+.13],[w+.48,h+.48,d+.28],'clothDark');
    box('v3_collar_linen_'+s+'_'+i,'chest',[x,y,z-.08],[w+.28,h+.28,d+.18],'linen');
  }
  for(const s of [-1,1]){
    for(let chain=0;chain<3;chain++)for(let link=0;link<7;link++){
      const x=s*(.39+link*.67),y=40.37-chain*.63+Math.pow(link/6,1.58)*2.61,z=-4.48+(link===6?.15:0);
      box('v3_necklace_link_back_'+s+'_'+chain+'_'+link,'chest',[x,y,z],[.55,.59,.28],'goldDark',[0,0,s*20]);
      box('v3_necklace_link_light_'+s+'_'+chain+'_'+link,'chest',[x-.07,y+.12,z-.2],[.35,.31,.17],(link+chain)%3?'boneGold':'paleGold');
      box('v3_necklace_link_hole_'+s+'_'+chain+'_'+link,'chest',[x+.025,y-.04,z-.22],[.13,.15,.08],'goldDark');
    }
  }
  for(let row=0;row<5;row++){
    const w=[2.2,3.15,2.65,1.72,.78][row],y=39.22-row*.52;
    box('v3_pendant_rank_'+row,'chest',[0,y,-4.7],[w,.64,.53],row%2?'goldDark':'gold');
    for(let i=0;i<Math.floor(w/.42);i++)box('v3_pendant_bead_'+row+'_'+i,'chest',[(i-(Math.floor(w/.42)-1)/2)*.43,y+.07,-5.02],[.31,.31,.2],'boneGold');
  }
  filigree('v3_pendant_relief','chest',0,38.5,-5.14,1.18);
  box('v3_abdomen_padding','waist',[0,33.0,0],[10.5,3.7,6.05],'dark');
  for(let row=0;row<3;row++){
    const width=8.4-row*.79,y=34.38-row*.92;
    box('v3_abdomen_rank_'+row,'waist',[0,y,-3.21],[width,.94,.65],'steelDark');
    box('v3_abdomen_bevel_'+row,'waist',[0,y+.34,-3.64],[width-.32,.21,.23],'silver');
    for(const s of [-1,1])box('v3_abdomen_corner_'+row+'_'+s,'waist',[s*(width/2-.24),y-.12,-3.64],[.43,.48,.21],'steelMid');
  }
  box('v3_waist_core','hips',[0,30.45,.0],[11.9,2.9,6.34],'clothDark');
  box('v3_sword_belt','hips',[0,30.68,.04],[12.95,1.33,6.99],'red');
  for(const z of [-3.57,3.67])for(const y of [30.2,31.14])box('v3_belt_stitch_'+z+'_'+y,'hips',[0,y,z],[12.7,.13,.17],'goldDark');
  for(const s of [-1,1]){
    for(let i=0;i<3;i++){
      const x=s*(2.5+i*1.31);
      box('v3_belt_keeper_'+s+'_'+i,'hips',[x,30.68,-3.67],[.4,1.52,.42],i===1?'gold':'leather');
      stud('v3_belt_stud_'+s+'_'+i,'hips',x+.44,30.7,-3.73,.24);
    }
  }
  plaque('v3_buckle_base','hips',0,30.64,-3.8,3.35,2.56,'goldDark','boneGold',.28);
  crest('v3_buckle_taotie','hips',0,30.69,-4.24,.81);
  for(const s of [-1,1]){
    const side=s===1?'right':'left',thigh=side+'_thigh',shin=side+'_shin',foot=side+'_foot';
    box('v3_thigh_cloth_'+side,thigh,[s*3.88,21.84,.11],[4.7,13.45,5.2],'clothDark');
    for(let row=0;row<4;row++)plaque('v3_hidden_thigh_scale_'+side+'_'+row,thigh,s*3.88,24.5-row*2.1,-2.6,4.5,1.95,'steelDark','steelMid',.15);
    box('v3_knee_padding_'+side,shin,[s*4.04,13.64,.08],[4.56,3.9,4.78],'dark');
    for(let tier=0;tier<3;tier++){
      box('v3_kneecap_tier_'+side+'_'+tier,shin,[s*4.04,14.77-tier*1.04,-2.61-tier*.14],[4.46-(tier===2?.61:0),1.35,.84],tier===1?'steelMid':'iron');
      box('v3_kneecap_lip_'+side+'_'+tier,shin,[s*4.04,14.38-tier*1.04,-3.15-tier*.14],[4.4-(tier===2?.61:0),.25,.27],'oldSilver');
    }
    crest('v3_knee_taotie_'+side,shin,s*4.04,13.06,-3.64,.94);
    for(const t of [-1,1]){
      box('v3_knee_wing_'+side+'_'+t,shin,[s*4.04+t*2.1,13.82,-1.48],[.83,2.25,2.02],'steelMid',[0,0,t*12]);
      stud('v3_knee_pin_'+side+'_'+t,shin,s*4.04+t*1.54,14.94,-3.34,.29);
    }
    box('v3_shin_core_'+side,shin,[s*4.16,8.5,.22],[3.7,7.25,3.96],'dark');
    for(let i=0;i<3;i++){
      const x=s*4.16+(i-1)*1.11;
      box('v3_greave_facet_'+side+'_'+i,shin,[x,8.46,-1.95+(i===1?-.19:0)],[1.09,6.73,.64],i===1?'steelMid':'iron');
      box('v3_greave_edge_'+side+'_'+i,shin,[x+(i-1)*.27,8.4,-2.34+(i===1?-.16:0)],[.21,6.48,.16],i===1?'steel':'steelDark');
    }
    box('v3_greave_top_cap_'+side,shin,[s*4.16,11.45,-2.2],[3.58,.39,.82],'steel');
    box('v3_ankle_binding_'+side,foot,[s*4.16,4.23,.12],[3.88,1.55,4.22],'dark');
    box('v3_boot_heel_'+side,foot,[s*4.16,2.22,.28],[4.42,3.3,4.64],'iron');
    box('v3_boot_sole_'+side,foot,[s*4.16,.45,-1.51],[5.16,.7,7.2],'dark');
    box('v3_boot_toecap_'+side,foot,[s*4.16,1.65,-2.68],[4.96,1.68,3.39],'steelDark');
    box('v3_boot_toe_base_'+side,foot,[s*4.16,1.0,-4.25],[4.92,.43,.68],'steelMid');
    box('v3_boot_inst_step_'+side,foot,[s*4.16,2.87,-1.61],[4.1,.69,2.54],'steelMid',[-14,0,0]);
    for(let i=0;i<4;i++){
      const x=s*4.16+(i-1.5)*1.04;
      box('v3_toe_facet_'+side+'_'+i,foot,[x,2.55,-3.46],[.87,.42,1.55],i%2?'steelMid':'silver');
      box('v3_toe_facet_end_'+side+'_'+i,foot,[x,2.09,-4.16],[.87,.54,.25],'steel');
    }
    for(const t of [-1,1]){
      box('v3_boot_side_edge_'+side+'_'+t,foot,[s*4.16+t*2.01,1.4,-1.41],[.35,.59,4.73],'steelMid');
      box('v3_boot_ankle_plate_'+side+'_'+t,foot,[s*4.16+t*1.61,4.41,-1.95],[.83,1.64,.66],'oldSilver',[0,0,t*10]);
    }
    plaque('v3_boot_front_clasp_'+side,foot,s*4.16,4.62,-2.31,2.53,.89,'dark','oldSilver',.17);

    bones[side+'_front_cloth'].rotation=[0,0,s*3];
    bones[side+'_back_cloth'].rotation=[-6,0,s*6];
    bones[side+'_skirt'].rotation=[0,0,s*15];
    const front=side+'_front_cloth',tip=side+'_front_cloth_tip',skirt=side+'_skirt',lower=side+'_skirt_lower',back=side+'_back_cloth';
    for(let i=0;i<5;i++){
      const x=s*(.85+i*1.25),bottom=[15.3,14.45,14.9,13.2,12.1][i],top=29.0,rot=s*(1+i*1.1);
      box('v3_front_robe_panel_'+side+'_'+i,front,[x,(top+bottom)/2,-2.79-(i%2)*.18],[1.43,top-bottom,1.05],i%2?'cloth':'clothFold',[0,0,rot]);
      box('v3_front_fold_shadow_'+side+'_'+i,front,[x-s*.48,(top+bottom)/2,-3.39-(i%2)*.18],[.2,top-bottom-.3,.14],'clothDark',[0,0,rot]);
      box('v3_front_hem_'+side+'_'+i,tip,[x+s*(top-bottom)/2*Math.sin(rot*Math.PI/180),bottom+.16,-3.33-(i%2)*.18],[1.43,.37,.26],'linen',[0,0,rot]);
      if(i>=2)box('v3_front_hem_return_'+side+'_'+i,tip,[x+s*(top-bottom)/2*Math.sin(rot*Math.PI/180)+s*.51,bottom+.7,-3.39],[.27,1.05,.21],'oldSilver');
    }
    for(let layer=0;layer<3;layer++){
      const x=s*(6.3+layer*.73),y=20.4+layer*.08,z=-.9+layer*1.24;
      box('v3_outer_robe_layer_'+side+'_'+layer,front,[x,y,z],[1.49,17.3,1.73],layer%2?'clothDark':'cloth',[0,0,s*(9+layer*2)]);
      box('v3_outer_robe_highlight_'+side+'_'+layer,front,[x+s*.27,y,z-.9],[.31,16.0,.16],'clothFold',[0,0,s*(9+layer*2)]);
    }
    box('v3_front_red_ribbon_'+side,front,[s*2.38,22.2,-3.69],[1.12,14.0,.42],'red');
    box('v3_front_red_ribbon_tip_'+side,tip,[s*2.38,14.65,-3.89],[1.27,1.33,.45],'red');
    for(const t of [-1,1]){
      box('v3_ribbon_gold_stitch_'+side+'_'+t,front,[s*2.38+t*.45,22.2,-3.98],[.13,14.0,.15],'goldDark');
      box('v3_ribbon_tip_gold_stitch_'+side+'_'+t,tip,[s*2.38+t*.51,14.65,-4.16],[.14,1.33,.13],'gold');
    }
    plaque('v3_ribbon_end_plate_'+side,tip,s*2.38,14.07,-4.16,1.36,1.34,'goldDark','boneGold',.19);
    plaque('v3_ribbon_hanging_talisman_'+side,front,s*2.38,22.8,-4.11,1.15,1.63,'goldDark','boneGold',.22);
    for(let i=0;i<5;i++)box('v3_ribbon_embroidery_'+side+'_'+i,front,[s*2.38+(i%2?.16:-.16),25.5+i*.45,-4.02],[.22,.27,.16],'oldSilver');
    for(let row=0;row<4;row++){
      const y=28.65-row*2.86,bone=row<3?skirt:lower,x=s*(5.3+row*.17),w=3.65+row*.16;
      box('v3_tasset_underlay_'+side+'_'+row,bone,[x,y-.75,-2.1],[w,3.47,2.34],'dark');
      for(let col=0;col<4;col++){
        const xx=x+(col-1.5)*w/4,yy=y-(col===0||col===3?.2:0),hh=2.69-(col===0||col===3?.22:0),z=-3.34-(col===1||col===2?.14:0);
        box('v3_tasset_plate_'+side+'_'+row+'_'+col,bone,[xx,yy-.57,z],[w/4-.055,hh,.57],(row+col)%4===0?'iron':'steelDark');
        box('v3_tasset_lip_'+side+'_'+row+'_'+col,bone,[xx,yy-.57-hh/2+.16,z-.34],[w/4+.065,.26,.22],'oldSilver');
        box('v3_tasset_raised_scale_'+side+'_'+row+'_'+col,bone,[xx-.04,yy-.39,z-.36],[w/4-.28,1.31,.24],'steelMid');
        if(col%2===0)stud('v3_tasset_rivet_'+side+'_'+row+'_'+col,bone,xx,yy+.37,z-.53,.25);
      }
      for(const t of [-1,1])box('v3_tasset_side_bind_'+side+'_'+row+'_'+t,bone,[x+t*(w/2-.14),y-.65,-3.66],[.3,2.77,.31],'oldSilver');
      box('v3_tasset_corner_pin_'+side+'_'+row,bone,[x+s*(w/2-.32),y-1.8,-3.91],[.25,.25,.18],'boneGold');
    }
    const sideBone=group(side+'_tasset_outer',[s*6.3,29.1,.7],'hips',[0,-s*73,s*14]);
    for(let row=0;row<4;row++){
      const x=s*6.3,y=28.2-row*2.93;
      plaque('v3_outer_tasset_'+side+'_'+row,sideBone,x,y,-.4,4.4,2.87,'steelDark','oldSilver',.27);
      for(let i=0;i<3;i++)box('v3_outer_tasset_scale_'+side+'_'+row+'_'+i,sideBone,[x+(i-1)*1.19,y+.22,-.76],[1.04,1.62,.26],i===1?'steelMid':'iron');
    }
    for(let i=0;i<5;i++){
      const x=s*(.77+i*1.27),bottom=12.2-(i%2)*.68,top=29.1;
      box('v3_rear_robe_fold_'+side+'_'+i,back,[x,(top+bottom)/2,3.19+i*.08],[1.46,top-bottom,.82],i%2?'clothFold':'cloth',[0,0,s*i*1.2]);
      box('v3_rear_robe_border_'+side+'_'+i,back,[x+s*.43,bottom+.22,3.71+i*.08],[1.58,.39,.25],'linen');
    }
  }
  box('v3_center_tabard','hips',[0,22.15,-3.56],[2.62,13.9,.58],'clothDark');
  box('v3_center_tabard_tip','hips',[0,14.11,-3.72],[2.64,2.25,.61],'cloth');
  for(const s of [-1,1]){
    box('v3_tabard_hem_vertical_'+s,'hips',[s*1.18,14.46,-4.11],[.25,2.83,.24],'oldSilver');
    box('v3_tabard_hem_step_'+s,'hips',[s*.82,13.08,-4.11],[.82,.31,.24],'boneGold');
  }
  filigree('v3_tabard_cloud','hips',0,14.26,-4.21,1.52,'boneGold');
  box('v3_rear_red_sash','rear_sash',[0,23.11,3.71],[2.68,14.21,.5],'red',[-3,0,0]);
  box('v3_rear_red_sash_tip','rear_sash_tip',[0,14.6,4.68],[2.73,4.08,.6],'red');
  for(const s of [-1,1]){
    box('v3_rear_sash_border_'+s,'rear_sash',[s*1.12,23.15,4.05],[.22,14.21,.2],'goldDark',[-3,0,0]);
    box('v3_rear_sash_tip_border_'+s,'rear_sash_tip',[s*1.15,14.6,5.08],[.25,4.16,.2],'gold');
  }
  filigree('v3_rear_cloud','rear_sash_tip',0,13.8,5.16,1.95,'boneGold');
  for(const s of [-1,1])box('v3_rear_cloud_corner_'+s,'rear_sash_tip',[s*.82,12.7,5.13],[.4,.3,.23],'gold');
  for(const p of parts){
    if(p.name.startsWith('v3_knee_taotie_')){
      if(p.material==='gold')p.material='oldSilver';
      else if(p.material==='paleGold')p.material='boneGold';
    }
    if(p.bone==='chest'||p.bone==='waist'){
      const scale=p.bone==='chest'?1.10:1.055;
      p.from[0]*=scale;p.to[0]*=scale;p.origin[0]*=scale;
    }
  }
}
