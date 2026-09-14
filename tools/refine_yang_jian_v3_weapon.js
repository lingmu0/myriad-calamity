(api) => {
  const {parts, bones, box, bar, framed, stud, crest, group, mat} = api;
  for (let i = parts.length - 1; i >= 0; i--) {
    if (parts[i].bone === 'weapon') parts.splice(i, 1);
  }

  const W = 'weapon', x = 13.05, z = -2.8;
  mat.weaponBlack = {color:[27,30,34], noise:2, edge:3};
  mat.weaponSpine = {color:[46,49,54], noise:2, edge:5};
  mat.weaponBevel = {color:[115,120,126], noise:2, edge:7};
  mat.weaponEdge = {color:[199,202,201], noise:1, edge:5};
  mat.weaponGold = {color:[133,113,78], noise:3, edge:7};
  mat.weaponGoldLight = {color:[164,145,108], noise:2, edge:8};
  mat.weaponWrap = {color:[39,34,29], noise:2, edge:2};

  const weaponBreadth=1.22;
  const yMap = y => y<=46 ? y : 46+(y-46)*1.16;
  const b = (name, dx, y, dz, w, h, d, material, rotation=[0,0,0]) => {
    const low=yMap(y-h/2),high=yMap(y+h/2);
    box('yj3_weapon_'+name, W, [x+dx*weaponBreadth,(low+high)/2,z+dz*weaponBreadth], [w*weaponBreadth,high-low,d*weaponBreadth], material, rotation);
  };
  const line = (name, a, q, width, depth, material, dz=0) =>
    bar('yj3_weapon_'+name, W, [x+a[0]*weaponBreadth,yMap(a[1]),z+dz*weaponBreadth], [x+q[0]*weaponBreadth,yMap(q[1]),z+dz*weaponBreadth], width*weaponBreadth, depth*weaponBreadth, material);
  const ring = (name,y,w,h,material) => b(name,0,y,0,w,h,w,material);
  const doubleFace = fn => { for (const f of [-1,1]) fn(f); };

  // A straight dark shaft continues through the hand, ferrule and blade socket.
  b('blackwood_shaft',0,26.05,0,.78,49.7,.78,'weaponBlack');
  for (let i=0;i<4;i++) {
    const a=i*Math.PI/2;
    b('shaft_corner_'+i,Math.cos(a)*.36,25.3,Math.sin(a)*.36,.075,45.3,.075,'weaponSpine');
  }
  for (let i=0;i<16;i++) {
    const y=7.2+i*2.42;
    for (const f of [-1,1]) b('shaft_grain_'+f+'_'+i,.16*(i%3-1),y,f*.399,.06,.75+.25*(i%3),.032,'weaponSpine');
  }

  b('grip_inner',0,30.0,0,.98,5.35,.98,'weaponWrap');
  for (let i=0;i<12;i++) {
    const y=27.53+i*.445;
    ring('grip_wrap_'+i,y,1.035,.14,i%3===0?'weaponBlack':'weaponWrap');
    doubleFace(f=>b('grip_seam_'+f+'_'+i,.04,y+.04,f*.533,.78,.063,.047,'weaponSpine',[0,0,7]));
  }
  for (const [name,y,w,h] of [
    ['grip_low',27.10,1.10,.4],['grip_high',32.93,1.10,.36],
    ['upper_band',45.96,1.10,.62],['neck_band',48.13,1.15,.64],
    ['low_band',6.07,1.12,.55],['hand_low_band',25.39,1.07,.65]
  ]) {
    ring(name+'_base',y,w,h,'weaponGold');
    ring(name+'_upper',y+h/2-.055,w+.055,.105,'weaponGoldLight');
    ring(name+'_lower',y-h/2+.055,w+.055,.105,'weaponGoldLight');
    doubleFace(f=>b(name+'_inset_'+f,0,y,f*(w/2+.021),.53,h*.46,.052,'weaponSpine'));
    if(h>.5)doubleFace(f=>b(name+'_stud_'+f,0,y,f*(w/2+.06),.16,.19,.079,'weaponGoldLight'));
  }

  // Square ferrule with four recessed panels and a weighted ground contact.
  ring('ferrule_foot',.30,1.38,.54,'weaponSpine');
  ring('ferrule_foot_edge',.62,1.43,.13,'weaponGold');
  ring('ferrule_lower_collar',.92,1.34,.39,'weaponGold');
  ring('ferrule_lower_lip',1.16,1.48,.15,'weaponGoldLight');
  ring('ferrule_stem',1.79,1.08,1.14,'weaponSpine');
  ring('ferrule_cage_body',3.57,1.37,2.86,'weaponSpine');
  for(const f of [-1,1]) {
    b('ferrule_front_inset_'+f,0,3.60,f*.699,.79,2.19,.06,'weaponBlack');
    b('ferrule_side_inset_'+f,f*.699,3.60,0,.06,2.19,.79,'weaponBlack');
    for(const t of [-1,1]) {
      b('ferrule_front_border_'+f+'_'+t,t*.54,3.59,f*.76,.16,2.61,.18,'weaponGold');
      b('ferrule_side_border_'+f+'_'+t,f*.76,3.59,t*.54,.18,2.61,.16,'weaponGold');
    }
    b('ferrule_glyph_stem_'+f,0,3.59,f*.748,.16,1.23,.08,'weaponGold');
    b('ferrule_glyph_top_'+f,0,4.06,f*.75,.43,.16,.083,'weaponGoldLight');
    b('ferrule_glyph_mid_'+f,.15,3.59,f*.75,.17,.42,.083,'weaponGold');
    b('ferrule_glyph_bottom_'+f,0,3.02,f*.75,.43,.16,.083,'weaponGoldLight');
  }
  for(const y of [2.13,4.97]) {
    ring('ferrule_cage_band_'+y,y,1.66,.31,'weaponGold');
    ring('ferrule_cage_bevel_'+y,y+.20,1.45,.12,'weaponGoldLight');
  }

  ring('blade_socket_shaft',49.52,1.11,2.15,'weaponSpine');
  ring('blade_socket_bottom',48.65,1.35,.36,'weaponGold');
  ring('blade_socket_rim',48.91,1.43,.14,'weaponGoldLight');
  ring('blade_socket_top',50.10,1.40,.39,'weaponGold');
  doubleFace(f=>{
    b('neck_recess_'+f,0,49.55,f*.57,.56,.76,.08,'weaponBlack');
    b('neck_etch_'+f,0,49.55,f*.63,.17,.56,.08,'weaponGoldLight');
  });

  // Central lance: narrow silver bevels surrounding a dark, continuous fuller.
  b('lance_tang',0,53.03,0,1.62,5.0,.62,'weaponSpine');
  b('lance_main',0,59.00,0,2.00,11.75,.59,'weaponSpine');
  for(const s of [-1,1]) {
    b('lance_facet_'+s,s*.70,59.09,0,.48,11.77,.68,'weaponBevel');
    b('lance_straight_edge_'+s,s*1.041,59.10,0,.22,11.82,.52,'weaponEdge');
    doubleFace(f=>{
      b('lance_groove_'+s+'_'+f,s*.40,59.11,f*.365,.17,11.64,.055,'weaponBlack');
      b('lance_bevel_line_'+s+'_'+f,s*.88,59.1,f*.354,.095,11.79,.052,'silver');
    });
  }
  doubleFace(f=>{
    b('lance_front_spine_'+f,0,58.60,f*.367,.57,11.35,.19,'weaponBlack');
    b('lance_spine_ridge_'+f,-.12,58.67,f*.478,.13,11.39,.065,'weaponSpine');
    for(let i=0;i<5;i++) {
      const y=54.2+i*1.74;
      b('lance_inlay_'+f+'_'+i,0,y,f*.49,.17,.64,.063,i%2?'weaponGoldLight':'weaponGold');
      b('lance_inlay_pin_'+f+'_'+i,.04,y+.45,f*.491,.105,.15,.065,'weaponGoldLight');
    }
  });

  // Closely packed horizontal slices make a solid pointed tip without cracks.
  const tipBase=64.80,tipEnd=70.13,tipRows=42,dy=(tipEnd-tipBase)/tipRows;
  for(let i=0;i<tipRows;i++) {
    const t=(i+.5)/tipRows,y=tipBase+(i+.5)*dy,w=Math.max(.027,2.20*(1-t));
    b('lance_tip_fill_'+i,0,y,0,w,dy+.018,Math.max(.075,.59*(1-t*.68)),'weaponBevel');
    if(w>.34)doubleFace(f=>b('lance_tip_fuller_'+f+'_'+i,0,y,f*(.309-.12*t),w*.50,dy+.018,.052,'weaponSpine'));
  }
  for(const s of [-1,1]) {
    line('lance_tip_edge_'+s,[s*1.066,64.79],[s*.018,70.12],.132,.38,'weaponEdge');
    line('lance_tip_inner_bevel_'+s,[s*.88,64.84],[s*.016,70.00],.14,.39,'silver');
  }
  b('lance_needle',0,70.135,0,.042,.125,.09,'weaponEdge');

  // Short outward side blades leave deep open channels beside the long lance.
  for(const s of [-1,1]) {
    line('side_root_'+s,[s*.84,51.37],[s*2.98,53.56],.61,.63,'weaponSpine');
    line('side_root_edge_'+s,[s*1.35,51.74],[s*3.19,53.34],.18,.64,'weaponBevel');
    b('side_blade_lower_'+s,s*3.10,55.23,0,.88,4.24,.49,'weaponBevel');
    b('side_blade_dark_'+s,s*2.91,55.23,-.01,.39,4.31,.58,'weaponSpine');
    b('side_blade_outer_edge_'+s,s*3.59,55.30,0,.19,4.32,.42,'weaponEdge');
    b('side_blade_inner_edge_'+s,s*2.57,55.03,0,.13,3.91,.39,'silver');
    // The tip leans outward, as in the reference, rather than forming a parallel fork.
    const rows=29, low=57.32, high=61.18,step=(high-low)/rows;
    for(let i=0;i<rows;i++) {
      const t=(i+.5)/rows,y=low+(i+.5)*step;
      const outer=3.59+.11*t,inner=2.66+1.04*t;
      const width=Math.max(.028,outer-inner);
      b('side_tip_fill_'+s+'_'+i,s*(outer+inner)/2,y,0,width,step+.018,.38-.18*t,'weaponBevel');
      if(width>.22)doubleFace(f=>b('side_tip_facet_'+s+'_'+f+'_'+i,s*(outer+inner)/2,y,f*(.215-.078*t),width*.4,step+.012,.041,'silver'));
    }
    line('side_tip_outer_'+s,[s*3.59,57.3],[s*3.704,61.16],.13,.27,'weaponEdge');
    line('side_tip_inner_'+s,[s*2.65,57.31],[s*3.704,61.16],.105,.29,'silver');
    b('side_tip_needle_'+s,s*3.704,61.17,0,.056,.12,.088,'weaponEdge');
    doubleFace(f=>{
      b('side_blade_recess_'+s+'_'+f,s*2.99,55.27,f*.323,.25,3.73,.057,'weaponSpine');
      b('side_blade_mount_'+s+'_'+f,s*2.62,53.15,f*.397,.34,1.24,.22,'weaponGold');
      b('side_blade_mount_cap_'+s+'_'+f,s*2.62,53.76,f*.439,.43,.18,.22,'weaponGoldLight');
      b('side_blade_mount_pin_'+s+'_'+f,s*2.62,53.23,f*.533,.13,.13,.10,'weaponGoldLight');
    });
  }

  // Compact guardian socket ornament, mirrored so the rear is fully finished.
  b('guard_bridge',0,51.47,0,2.56,.81,.91,'weaponSpine');
  b('guard_base_trim',0,51.13,0,2.42,.20,1.00,'weaponGold');
  const ornamentStart=parts.length;
  crest('yj3_weapon_guardian_front',W,x,52.60,z-.51,.70);
  const frontOrnament=parts.slice(ornamentStart);
  for(const p of frontOrnament) {
    // The crest helper creates its own boxes and bars; remap their axes here.
    // Recomputing rotated bar endpoints preserves connected sloping brows.
    const c=p.from.map((v,i)=>(v+p.to[i])/2),size=p.to.map((v,i)=>v-p.from[i]);
    if(p.rotation[2]) {
      const angle=p.rotation[2]*Math.PI/180;
      const ax=x+(c[0]+Math.sin(angle)*size[1]/2-x)*weaponBreadth, ay=yMap(c[1]-Math.cos(angle)*size[1]/2);
      const bx=x+(c[0]-Math.sin(angle)*size[1]/2-x)*weaponBreadth, by=yMap(c[1]+Math.cos(angle)*size[1]/2);
      c[0]=(ax+bx)/2;c[1]=(ay+by)/2;
      size[1]=Math.hypot(bx-ax,by-ay);
      p.rotation[2]=-Math.atan2(bx-ax,by-ay)*180/Math.PI;
    } else {
      const low=yMap(p.from[1]),high=yMap(p.to[1]);
      c[0]=x+(c[0]-x)*weaponBreadth;c[1]=(low+high)/2;size[1]=high-low;
    }
    size[0]*=weaponBreadth;size[2]*=weaponBreadth;c[2]=z+(c[2]-z)*weaponBreadth;
    p.from=c.map((v,i)=>v-size[i]/2);p.to=c.map((v,i)=>v+size[i]/2);p.origin=[...c];
    // Reuse the sculpted relief while muting the former bright brass palette.
    p.material=p.material==='paleGold'?'weaponGoldLight':p.material==='gold'?'weaponGold':p.material;
    const mirrored={...p,name:p.name.replace('_front','_back'),from:[...p.from],to:[...p.to],origin:[...p.origin],rotation:[...p.rotation]};
    mirrored.from[2]=2*z-p.to[2];mirrored.to[2]=2*z-p.from[2];mirrored.origin[2]=2*z-p.origin[2];
    mirrored.rotation[0]=-p.rotation[0];mirrored.rotation[1]=-p.rotation[1];
    parts.push(mirrored);
  }
  for(const s of [-1,1])doubleFace(f=>{
    line('guard_curl_stem_'+s+'_'+f,[s*.55,52.30],[s*1.69,53.17],.24,.19,'weaponGold',f*.60);
    b('guard_scroll_turn_'+s+'_'+f,s*1.70,53.39,f*.61,.31,.65,.24,'weaponGoldLight');
    b('guard_scroll_cap_'+s+'_'+f,s*1.47,53.78,f*.61,.62,.21,.25,'weaponGold');
    b('guard_scroll_inset_'+s+'_'+f,s*1.58,53.52,f*.755,.20,.20,.09,'weaponSpine');
    b('guard_lower_fang_'+s+'_'+f,s*.76,51.06,f*.65,.21,.72,.22,'weaponGoldLight');
    b('guard_socket_rivet_'+s+'_'+f,s*.95,51.56,f*.585,.16,.17,.17,'weaponGoldLight');
  });
}
