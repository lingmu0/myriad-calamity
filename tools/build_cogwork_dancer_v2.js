(() => {
  if (!Project || !Project.name.includes('Cogwork Dancer')) {
    throw new Error('Select the Cogwork Dancer project first');
  }
  [...Outliner.elements].forEach(e => e.remove());
  [...Group.all].forEach(g => g.remove());

  const bones = {};
  const parts = [];

  function group(name, origin, parent, rotation = [0, 0, 0]) {
    if (bones[name]) throw new Error('Duplicate bone ' + name);
    const g = new Group({ name, origin, rotation })
      .addTo(parent ? (typeof parent === 'string' ? bones[parent] : parent) : 'root')
      .init();
    bones[name] = g;
    return name;
  }

  function box(name, bone, center, size, material = 'brass', rotation = [0, 0, 0], origin = null) {
    if (size.some(v => !Number.isFinite(v) || v <= 0)) {
      throw new Error('Invalid cube ' + name + ': ' + JSON.stringify(size));
    }
    const c = center;
    parts.push({
      name,
      bone,
      from: [c[0] - size[0] / 2, c[1] - size[1] / 2, c[2] - size[2] / 2],
      to: [c[0] + size[0] / 2, c[1] + size[1] / 2, c[2] + size[2] / 2],
      material,
      rotation,
      origin: origin || [...c]
    });
  }

  function bar(name, bone, a, b, width, depth, material = 'chassis') {
    const dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
    const len = Math.hypot(dx, dy, dz);
    if (len < 1e-5) return;
    const dir = new THREE.Vector3(dx, dy, dz).normalize();
    const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir);
    const e = new THREE.Euler().setFromQuaternion(q, 'ZYX');
    const mid = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2];
    box(name, bone, mid, [width, len, depth], material, [
      e.x * 180 / Math.PI,
      e.y * 180 / Math.PI,
      e.z * 180 / Math.PI
    ]);
  }

  function rivet(name, bone, x, y, z, size = 0.34, depth = 0.22, mat = 'filigree') {
    box(name, bone, [x, y, z], [size, size, depth], mat);
  }

  // =========================================================================
  // 1. CANONICAL 35-BONE ARTICULATED SKELETON (100% animation-compatible)
  // =========================================================================
  group('body', [0, 26.5, 0]);
  group('chest', [0, 28.0, 0], 'body');
  group('neck', [0, 36.2, 0], 'chest');
  group('head', [0, 38.2, 0], 'neck');
  group('crown', [0, 43.2, 0], 'head');
  group('halo', [0, 41.8, 3.85], 'head');
  group('gear', [0, 31.4, 3.25], 'chest');
  group('core', [0, 26.75, -2.55], 'body');
  group('abdomen', [0, 25.4, 0], 'body');
  group('pelvis', [0, 20.5, 0], 'abdomen');
  group('skirt', [0, 23.4, 1.0], 'abdomen');

  for (const sign of [-1, 1]) {
    const s = sign < 0 ? 'left' : 'right';
    group(s + '_shoulder', [sign * 5.6, 34.4, 0], 'chest');
    // Graceful poised ballet-duelist en-garde arm stance
    group(s + '_arm', [sign * 5.7, 33.8, 0], 'chest', [8, sign * 22, -sign * 48]);
    group(s + '_elbow', [sign * 11.1, 31.4, 0], s + '_arm', [0, sign * 44, sign * 20]);
    group(s + '_wrist', [sign * 16.0, 33.4, -0.15], s + '_elbow', [0, -sign * 10, sign * 22]);
    group(s + '_blade', [sign * 16.7, 33.3, -0.25], s + '_wrist', [0, 0, 0]);

    group(s + '_leg', [sign * 2.25, 19.6, 0], 'pelvis');
    group(s + '_knee', [sign * 2.75, 11.2, -0.1], s + '_leg');
    group(s + '_ankle', [sign * 3.15, 2.7, -0.35], s + '_knee');

    group(s + '_hip_plate', [sign * 2.95, 22.8, 0.55], 'skirt');
    // S-curved cascading Victorian swallowtail coattails
    group(s + '_tail_0', [sign * 2.05, 22.2, 1.95], 'skirt', [-20, sign * 14, sign * 10]);
    group(s + '_tail_1', [sign * 2.65, 17.5, 2.35], s + '_tail_0', [4, sign * 4, sign * 4]);
    group(s + '_tail_2', [sign * 3.2, 12.6, 2.7], s + '_tail_1', [6, sign * 3, sign * 3]);
  }

  // =========================================================================
  // 2. NECK & ARISTOCRATIC CATHEDRAL GORGET COLLAR
  // =========================================================================
  box('neck_spine', 'neck', [0, 37.1, 0.1], [1.75, 2.4, 1.75], 'chassisDark');
  box('neck_front_piston', 'neck', [0, 37.0, -0.95], [0.58, 2.0, 0.45], 'chassisLight');
  box('neck_rear_cable', 'neck', [0, 37.1, 1.05], [0.65, 2.1, 0.45], 'chassis');
  box('neck_gorget_base', 'neck', [0, 36.15, 0], [4.8, 0.85, 3.85], 'bronze');
  box('neck_gorget_trim', 'neck', [0, 36.6, -0.15], [4.3, 0.45, 3.6], 'filigree');
  box('neck_gorget_upper', 'neck', [0, 37.05, 0], [3.3, 0.55, 2.95], 'brass');
  box('neck_throat_jewel', 'neck', [0, 36.55, -2.05], [0.68, 0.68, 0.28], 'coreGlow', [0, 0, 45]);
  for (let i = -2; i <= 2; i++) {
    const a = i * 24;
    const rad = a * Math.PI / 180;
    const cx = Math.sin(rad) * 2.65;
    const cz = 0.35 + Math.cos(rad) * 1.95;
    box('neck_ruff_' + i, 'neck', [cx, 37.85, cz], [1.38, 2.25, 0.42], Math.abs(i) === 1 ? 'brassLight' : 'brass', [-18, a, 0]);
    box('neck_ruff_rim_' + i, 'neck', [cx * 1.04, 38.9, cz + 0.14], [1.45, 0.28, 0.48], 'filigree', [-18, a, 0]);
  }
  for (const sign of [-1, 1]) {
    bar('neck_piston_' + sign, 'neck', [sign * 0.9, 36.1, -0.85], [sign * 0.75, 38.15, -0.75], 0.32, 0.32, 'chassisLight');
  }

  // =========================================================================
  // 3. SCULPTED VENETIAN AUTOMATON THEATRE-MASK HEAD
  // =========================================================================
  box('head_cranium_core', 'head', [0, 40.35, 0.15], [7.0, 4.25, 5.1], 'brass');
  box('head_cranium_upper', 'head', [0, 42.45, 0.15], [6.2, 0.9, 4.6], 'brassLight');
  box('head_cranium_cap', 'head', [0, 42.98, 0.2], [4.9, 0.52, 3.8], 'bronze');
  box('head_lower_face', 'head', [0, 38.2, -0.15], [4.8, 1.15, 4.3], 'bronze');
  // Aristocratic V-tapered Venetian mask chin
  box('head_chin_guard', 'head', [0, 37.45, -1.65], [2.4, 1.05, 2.1], 'porcelain', [18, 0, 0]);
  box('head_chin_point', 'head', [0, 36.98, -2.58], [1.18, 0.76, 0.72], 'filigree', [28, 0, 0]);

  box('head_visor_cavity', 'head', [0, 40.25, -2.5], [6.1, 2.6, 0.65], 'visor');
  // Sweeping Venetian theatre brow arch & diamond keystone
  box('head_brow_shadow', 'head', [0, 41.68, -2.72], [7.7, 0.44, 0.7], 'chassisDark');
  box('head_brow_main', 'head', [0, 42.12, -2.85], [8.0, 0.65, 0.9], 'filigree');
  box('head_brow_wing_l', 'head', [-3.65, 42.38, -2.2], [1.75, 0.52, 2.35], 'brassLight', [0, 14, -14]);
  box('head_brow_wing_r', 'head', [3.65, 42.38, -2.2], [1.75, 0.52, 2.35], 'brassLight', [0, -14, 14]);
  box('head_brow_keystone', 'head', [0, 42.24, -3.32], [1.12, 1.12, 0.42], 'porcelain', [0, 0, 45]);
  box('head_brow_gem', 'head', [0, 42.24, -3.52], [0.54, 0.54, 0.25], 'coreGlow', [0, 0, 45]);

  // Slender refined porcelain nose bridge & gilded ridge
  box('head_nose_bridge', 'head', [0, 40.05, -2.88], [0.58, 3.05, 0.55], 'porcelain');
  box('head_nose_ridge', 'head', [0, 40.15, -3.14], [0.28, 2.85, 0.26], 'filigree');

  // Sculpted dorsal cranial crest & layered occipital armor
  box('head_dorsal_crest_top', 'head', [0, 43.3, 0.35], [0.88, 0.82, 4.3], 'filigree');
  box('head_dorsal_crest_mid', 'head', [0, 41.2, 2.8], [0.92, 3.4, 0.9], 'filigree', [-12, 0, 0]);
  box('head_occipital_upper', 'head', [0, 41.4, 2.62], [5.7, 2.2, 0.95], 'brass', [-10, 0, 0]);
  box('head_occipital_lower', 'head', [0, 39.2, 2.42], [4.8, 2.0, 0.9], 'bronze', [14, 0, 0]);
  box('head_occipital_vent', 'head', [0, 39.8, 2.88], [3.1, 1.3, 0.36], 'chassisDark');
  for (let i = 0; i < 3; i++) {
    box('head_occipital_slat_' + i, 'head', [0, 39.35 + i * 0.45, 3.0], [2.8, 0.22, 0.32], 'chassisLight');
  }

  for (const sign of [-1, 1]) {
    // Swept angled Venetian cheek-blades tapering toward the chin
    box('head_cheek_outer_' + sign, 'head', [sign * 3.42, 39.85, -1.6], [1.05, 2.65, 2.5], 'brassLight', [0, -sign * 18, sign * 10]);
    box('head_cheek_mask_' + sign, 'head', [sign * 2.55, 38.68, -2.55], [2.15, 0.72, 0.65], 'porcelain', [4, -sign * 16, sign * 18]);
    box('head_jaw_plate_' + sign, 'head', [sign * 1.85, 37.92, -2.35], [2.15, 0.85, 0.72], 'brass', [10, -sign * 16, sign * 20]);
    box('head_jaw_latch_' + sign, 'head', [sign * 2.42, 38.55, -2.85], [0.42, 1.05, 0.32], 'filigree', [0, 0, sign * 12]);

    // Expressive angled luminous optical eyes with sculpted brow filigree
    const ex = sign * 1.85;
    box('head_eye_bezel_outer_' + sign, 'head', [ex, 40.2, -2.86], [2.2, 1.82, 0.36], 'filigree', [0, 0, sign * 9]);
    box('head_eye_bezel_dark_' + sign, 'head', [ex, 40.2, -2.98], [1.82, 1.44, 0.28], 'visor', [0, 0, sign * 9]);
    box('head_eye_lens_' + sign, 'head', [ex, 40.2, -3.12], [1.5, 1.12, 0.22], 'eyeGlow', [0, 0, sign * 9]);
    box('head_eye_core_' + sign, 'head', [ex - sign * 0.08, 40.24, -3.22], [0.76, 0.68, 0.16], 'coreHot', [0, 0, sign * 9]);
    box('head_eye_brow_arch_' + sign, 'head', [ex + sign * 0.22, 41.22, -3.02], [2.25, 0.32, 0.32], 'porcelain', [0, 0, sign * 14]);
    box('head_eye_wing_' + sign, 'head', [sign * 3.08, 40.55, -2.98], [0.72, 0.26, 0.24], 'filigree', [0, 0, sign * 26]);

    // Ornate temple ear-gears
    const tx = sign * 3.78;
    box('head_temple_mount_' + sign, 'head', [tx, 40.4, 0.05], [0.62, 2.25, 2.25], 'chassisDark');
    box('head_temple_gear_a_' + sign, 'head', [tx + sign * 0.24, 40.4, 0.05], [0.34, 1.85, 1.85], 'filigree');
    box('head_temple_gear_b_' + sign, 'head', [tx + sign * 0.27, 40.4, 0.05], [0.32, 1.85, 1.85], 'brass', [45, 0, 0]);
    box('head_temple_axle_' + sign, 'head', [tx + sign * 0.44, 40.4, 0.05], [0.3, 0.68, 0.68], 'coreGlow');
    box('head_side_plate_' + sign, 'head', [sign * 3.58, 40.55, 1.55], [0.62, 2.55, 1.6], 'bronze', [0, sign * 12, 0]);
  }

  // =========================================================================
  // 4. REGAL GOTHIC-CLOCKWORK CROWN DIADEM (`crown`)
  // =========================================================================
  box('crown_band_front', 'crown', [0, 43.35, -2.25], [6.2, 0.62, 0.72], 'filigree');
  box('crown_band_back', 'crown', [0, 43.35, 2.02], [5.3, 0.56, 0.62], 'brass');
  for (const sign of [-1, 1]) {
    box('crown_band_side_' + sign, 'crown', [sign * 3.38, 43.35, -0.1], [0.65, 0.6, 4.3], 'filigree', [0, sign * 6, 0]);
    bar('crown_arch_front_' + sign, 'crown', [sign * 3.7, 43.5, -1.6], [sign * 1.85, 46.2, -1.3], 0.46, 0.5, 'filigree');
    bar('crown_arch_inner_' + sign, 'crown', [sign * 1.85, 46.2, -1.3], [0, 47.6, -1.0], 0.44, 0.46, 'porcelain');
    bar('crown_arch_rear_' + sign, 'crown', [sign * 3.05, 43.5, 1.6], [sign * 1.35, 46.5, 0.9], 0.34, 0.36, 'chassis');

    box('crown_outer_socket_' + sign, 'crown', [sign * 3.9, 43.8, -0.7], [0.92, 0.96, 0.92], 'bronze');
    bar('crown_outer_low_' + sign, 'crown', [sign * 3.9, 44.1, -0.7], [sign * 4.5, 47.4, -0.4], 0.6, 0.5, 'porcelain');
    bar('crown_outer_mid_' + sign, 'crown', [sign * 4.5, 47.2, -0.4], [sign * 4.98, 50.1, -0.15], 0.38, 0.36, 'porcelain');
    bar('crown_outer_tip_' + sign, 'crown', [sign * 4.98, 49.9, -0.15], [sign * 5.25, 51.8, 0.0], 0.2, 0.22, 'filigree');

    box('crown_inner_socket_' + sign, 'crown', [sign * 2.2, 44.8, -0.9], [0.88, 1.0, 0.88], 'filigree');
    bar('crown_inner_low_' + sign, 'crown', [sign * 2.2, 45.2, -0.9], [sign * 2.5, 48.8, -0.6], 0.64, 0.52, 'porcelain');
    bar('crown_inner_mid_' + sign, 'crown', [sign * 2.5, 48.6, -0.6], [sign * 2.3, 51.8, -0.4], 0.4, 0.36, 'porcelain');
    bar('crown_inner_tip_' + sign, 'crown', [sign * 2.3, 51.6, -0.4], [sign * 2.1, 53.6, -0.25], 0.22, 0.22, 'filigree');
    rivet('crown_inner_gem_' + sign, 'crown', sign * 2.2, 44.95, -1.4, 0.42, 0.22, 'coreGlow');
  }
  box('crown_center_pedestal', 'crown', [0, 44.6, -1.15], [1.4, 1.8, 1.12], 'brass');
  box('crown_center_diamond_frame', 'crown', [0, 46.8, -1.0], [1.82, 1.82, 0.6], 'filigree', [0, 0, 45]);
  box('crown_center_diamond_hole', 'crown', [0, 46.8, -1.0], [1.12, 1.12, 0.7], 'visor', [0, 0, 45]);
  box('crown_center_pearl', 'crown', [0, 46.8, -1.0], [0.68, 0.68, 0.84], 'coreHot', [0, 0, 45]);
  box('crown_center_shaft_a', 'crown', [0, 49.4, -0.9], [0.9, 3.4, 0.64], 'porcelain');
  box('crown_center_shaft_b', 'crown', [0, 52.3, -0.8], [0.54, 2.9, 0.44], 'porcelain');
  box('crown_center_needle', 'crown', [0, 54.6, -0.75], [0.26, 2.2, 0.26], 'filigree');
  box('crown_center_collar', 'crown', [0, 48.1, -0.95], [1.42, 0.44, 0.84], 'filigree');

  // =========================================================================
  // 5. LUMINOUS CLOCKWORK ASTROLABE TELEGRAPH HALO (`halo`)
  // =========================================================================
  const haloZ = 3.9, haloY = 41.8, outerR = 11.2, innerR = 7.6;
  for (let i = 0; i < 8; i++) {
    const a0 = (i * 45 + 7) * Math.PI / 180;
    const a1 = ((i + 1) * 45 - 7) * Math.PI / 180;
    bar('halo_outer_arc_' + i, 'halo',
      [Math.cos(a0) * outerR, haloY + Math.sin(a0) * outerR, haloZ],
      [Math.cos(a1) * outerR, haloY + Math.sin(a1) * outerR, haloZ],
      0.22, 0.22, 'haloGlow');
    if (i % 2 === 0) {
      const b0 = (i * 45 + 12) * Math.PI / 180;
      const b1 = ((i + 1) * 45 - 12) * Math.PI / 180;
      bar('halo_inner_arc_' + i, 'halo',
        [Math.cos(b0) * innerR, haloY + Math.sin(b0) * innerR, haloZ + 0.1],
        [Math.cos(b1) * innerR, haloY + Math.sin(b1) * innerR, haloZ + 0.1],
        0.16, 0.16, 'coreGlow');
    }
    const ta = i * 45 * Math.PI / 180;
    const isCardinal = i % 2 === 0;
    if (isCardinal) {
      bar('halo_tick_' + i, 'halo',
        [Math.cos(ta) * (outerR - 1.5), haloY + Math.sin(ta) * (outerR - 1.5), haloZ],
        [Math.cos(ta) * (outerR + 1.9), haloY + Math.sin(ta) * (outerR + 1.9), haloZ],
        0.28, 0.22, 'haloGlow');
    } else {
      box('halo_node_' + i, 'halo',
        [Math.cos(ta) * outerR, haloY + Math.sin(ta) * outerR, haloZ],
        [0.82, 0.82, 0.24], 'coreGlow', [0, 0, 45]);
    }
  }

  // =========================================================================
  // 6. SCULPTED V-TAPERED ARISTOCRATIC CUIRASS (`chest`)
  // =========================================================================
  box('chest_inner_chassis', 'chest', [0, 32.0, 0.0], [5.8, 7.8, 3.8], 'chassisDark');
  box('chest_upper_core', 'chest', [0, 34.0, -0.15], [8.8, 3.6, 4.7], 'brass');
  box('chest_mid_core', 'chest', [0, 31.1, -0.1], [7.6, 3.1, 4.35], 'brass');
  box('chest_lower_core', 'chest', [0, 28.9, -0.05], [6.1, 1.9, 3.9], 'bronze');

  box('chest_collar_top', 'chest', [0, 35.7, -0.35], [7.4, 0.95, 4.35], 'brassLight');
  box('chest_sternum_plastron', 'chest', [0, 32.5, -2.55], [2.4, 5.4, 0.82], 'brassLight');
  box('chest_sternum_recess', 'chest', [0, 32.4, -2.92], [0.78, 4.9, 0.36], 'chassisDark');
  box('chest_sternum_conduit', 'chest', [0, 32.4, -3.05], [0.34, 4.2, 0.22], 'coreGlow');
  for (let i = 0; i < 3; i++) {
    box('chest_sternum_clasp_' + i, 'chest', [0, 30.8 + i * 1.45, -3.08], [1.65, 0.32, 0.3], 'filigree');
  }

  box('chest_back_upper', 'chest', [0, 34.4, 2.35], [7.8, 2.6, 1.05], 'brass', [-8, 0, 0]);
  box('chest_back_frame', 'chest', [0, 31.4, 2.25], [6.4, 4.6, 1.0], 'chassisDark');
  box('chest_back_arch_top', 'chest', [0, 34.3, 2.95], [4.8, 0.68, 0.65], 'filigree');
  box('chest_back_arch_bot', 'chest', [0, 28.6, 2.65], [4.2, 0.62, 0.65], 'bronze');

  for (const sign of [-1, 1]) {
    box('chest_pec_plate_' + sign, 'chest', [sign * 2.55, 33.4, -2.38], [3.35, 3.15, 0.95], 'brass', [0, -sign * 14, -sign * 5]);
    box('chest_pec_rim_' + sign, 'chest', [sign * 2.65, 34.85, -2.72], [3.45, 0.55, 0.52], 'filigree', [0, -sign * 14, -sign * 12]);
    box('chest_pec_lower_' + sign, 'chest', [sign * 2.15, 30.65, -2.28], [2.85, 2.35, 0.85], 'bronze', [0, -sign * 14, sign * 8]);
    bar('chest_v_rail_' + sign, 'chest', [sign * 3.85, 34.9, -2.55], [sign * 1.45, 28.6, -2.65], 0.42, 0.42, 'filigree');
    for (let i = 0; i < 3; i++) {
      rivet('chest_rivet_' + sign + '_' + i, 'chest', sign * (3.35 - i * 0.45), 34.2 - i * 1.8, -2.85, 0.34, 0.26, 'porcelain');
    }
    for (let i = 0; i < 4; i++) {
      const ry = 29.2 + i * 1.25;
      const rx = 3.25 + i * 0.36;
      box('chest_rib_vent_' + sign + '_' + i, 'chest', [sign * rx, ry, 0.1], [0.85, 0.58, 3.2], 'chassis');
      box('chest_rib_plate_' + sign + '_' + i, 'chest', [sign * (rx + 0.32), ry + 0.35, 0.0], [0.55, 0.72, 3.55], i % 2 === 0 ? 'brassLight' : 'bronze', [0, 0, -sign * 12]);
    }
    box('chest_back_wing_' + sign, 'chest', [sign * 3.35, 32.8, 2.55], [1.65, 4.4, 0.85], 'brassLight', [-6, sign * 18, -sign * 12]);
    box('chest_back_fin_' + sign, 'chest', [sign * 3.75, 34.6, 2.95], [0.65, 2.8, 0.65], 'filigree', [-12, sign * 18, -sign * 18]);
    bar('chest_back_strut_' + sign, 'chest', [sign * 2.8, 29.0, 2.45], [sign * 3.6, 33.8, 2.3], 0.36, 0.36, 'chassisLight');
  }

  // =========================================================================
  // 7. GRAND DORSAL 12-TOOTH CLOCKWORK ESCAPEMENT GEAR (`gear`)
  // =========================================================================
  const gx = 0, gy = 31.4, gz = 3.35;
  box('gear_hub_back', 'gear', [gx, gy, gz - 0.15], [1.9, 1.9, 0.68], 'chassisDark');
  box('gear_hub_front', 'gear', [gx, gy, gz + 0.35], [1.55, 1.55, 0.55], 'filigree', [0, 0, 45]);
  box('gear_hub_jewel', 'gear', [gx, gy, gz + 0.66], [0.78, 0.78, 0.28], 'coreGlow', [0, 0, 45]);
  box('gear_spoke_v', 'gear', [gx, gy, gz + 0.12], [0.58, 5.4, 0.42], 'bronze');
  box('gear_spoke_h', 'gear', [gx, gy, gz + 0.12], [5.4, 0.58, 0.42], 'bronze');
  box('gear_spoke_d1', 'gear', [gx, gy, gz + 0.06], [0.4, 4.8, 0.34], 'chassisLight', [0, 0, 45]);
  box('gear_spoke_d2', 'gear', [gx, gy, gz + 0.06], [0.4, 4.8, 0.34], 'chassisLight', [0, 0, -45]);
  const gearR = 2.85;
  for (let i = 0; i < 12; i++) {
    const a = i * 30 * Math.PI / 180;
    const aNext = (i + 1) * 30 * Math.PI / 180;
    bar('gear_rim_' + i, 'gear',
      [gx + Math.cos(a) * gearR, gy + Math.sin(a) * gearR, gz + 0.08],
      [gx + Math.cos(aNext) * gearR, gy + Math.sin(aNext) * gearR, gz + 0.08],
      0.56, 0.62, i % 2 === 0 ? 'brass' : 'brassLight');
    const tx = gx + Math.cos(a) * (gearR + 0.55);
    const ty = gy + Math.sin(a) * (gearR + 0.55);
    box('gear_tooth_' + i, 'gear', [tx, ty, gz + 0.1], [0.64, 0.85, 0.56], 'filigree', [0, 0, i * 30 - 90]);
  }
  box('gear_key_stem', 'gear', [gx, gy, gz + 0.92], [0.42, 0.42, 0.72], 'chassisLight');
  box('gear_key_bow_l', 'gear', [gx - 0.95, gy, gz + 1.26], [1.35, 0.88, 0.28], 'filigree', [0, 0, 12]);
  box('gear_key_bow_r', 'gear', [gx + 0.95, gy, gz + 1.26], [1.35, 0.88, 0.28], 'filigree', [0, 0, -12]);

  // =========================================================================
  // 8. FACETED LUMINOUS CLOCKWORK HEART (`core`) & WASP-WAIST (`abdomen`, `pelvis`)
  // =========================================================================
  box('core_housing', 'core', [0, 26.75, -2.35], [2.75, 2.75, 0.75], 'chassisDark', [0, 0, 45]);
  box('core_bezel', 'core', [0, 26.75, -2.62], [2.25, 2.25, 0.45], 'filigree', [0, 0, 45]);
  box('core_recess', 'core', [0, 26.75, -2.76], [1.65, 1.65, 0.32], 'visor', [0, 0, 45]);
  box('core_crystal_outer', 'core', [0, 26.75, -2.92], [1.28, 1.28, 0.28], 'coreGlow', [0, 0, 45]);
  box('core_crystal_inner', 'core', [0, 26.75, -3.05], [0.68, 0.68, 0.22], 'coreHot', [0, 0, 45]);
  for (let i = 0; i < 4; i++) {
    const a = (i * 90 + 45) * Math.PI / 180;
    box('core_claw_' + i, 'core', [Math.cos(a) * 1.15, 26.75 + Math.sin(a) * 1.15, -2.88], [0.46, 0.3, 0.38], 'filigree', [0, 0, i * 90 + 45]);
  }

  box('abdomen_spine_core', 'abdomen', [0, 25.4, 0.1], [3.5, 4.8, 2.8], 'chassisDark');
  for (let i = 0; i < 2; i++) {
    const y = 24.1 + i * 2.1;
    const w = 4.6 + i * 0.65;
    box('abdomen_plate_' + i, 'abdomen', [0, y, -0.05], [w, 1.65, 3.65], i === 0 ? 'bronze' : 'brass');
    box('abdomen_lip_' + i, 'abdomen', [0, y + 0.72, -0.15], [w + 0.38, 0.42, 3.92], 'filigree');
    box('abdomen_spine_node_' + i, 'abdomen', [0, y, 2.02], [1.1, 1.1, 0.56], 'chassisLight');
  }

  box('pelvis_inner_block', 'pelvis', [0, 20.5, 0.0], [4.6, 3.8, 3.1], 'chassisDark');
  box('pelvis_belt_band', 'pelvis', [0, 21.85, -0.05], [5.55, 1.35, 3.98], 'brass');
  box('pelvis_belt_trim', 'pelvis', [0, 22.45, -0.15], [5.88, 0.42, 4.2], 'filigree');
  box('pelvis_buckle_frame', 'pelvis', [0, 21.6, -2.2], [1.98, 1.98, 0.55], 'filigree', [0, 0, 45]);
  box('pelvis_buckle_gem', 'pelvis', [0, 21.6, -2.44], [0.78, 0.78, 0.28], 'coreGlow', [0, 0, 45]);
  box('pelvis_lower_crotch', 'pelvis', [0, 19.65, -0.08], [4.1, 1.95, 3.35], 'bronze');
  box('pelvis_front_apron', 'pelvis', [0, 18.7, -1.88], [2.45, 2.65, 0.55], 'brassLight', [-8, 0, 0]);
  box('pelvis_front_apron_tip', 'pelvis', [0, 17.15, -2.05], [1.5, 1.5, 0.48], 'filigree', [-8, 0, 45]);
  box('pelvis_rear_plate', 'pelvis', [0, 20.4, 1.85], [4.2, 2.8, 0.75], 'bronze', [8, 0, 0]);

  // Sculpted dorsal bustle clasp on `skirt` seamlessly joining torso & coattails
  box('skirt_bustle_core', 'skirt', [0, 22.45, 2.15], [4.8, 2.15, 1.1], 'brass');
  box('skirt_bustle_trim', 'skirt', [0, 23.35, 2.38], [5.15, 0.48, 0.9], 'filigree');
  box('skirt_bustle_emblem', 'skirt', [0, 22.35, 2.82], [1.45, 1.45, 0.42], 'filigree', [0, 0, 45]);
  box('skirt_bustle_jewel', 'skirt', [0, 22.35, 3.04], [0.65, 0.65, 0.24], 'coreGlow', [0, 0, 45]);

  // =========================================================================
  // 9. SWEPT CATHEDRAL PAULDRONS, ARMS & ORNATE CRESCENT MANTIS-SCYTHES
  // =========================================================================
  for (const sign of [-1, 1]) {
    const s = sign < 0 ? 'left' : 'right';
    const sh = s + '_shoulder';
    const arm = s + '_arm';
    const elb = s + '_elbow';
    const wri = s + '_wrist';
    const bld = s + '_blade';

    // Layered swept gothic pauldrons
    box('pauldron_chassis_' + s, sh, [sign * 6.0, 33.9, 0.0], [2.9, 2.6, 4.4], 'chassisDark', [0, 0, sign * 12]);
    box('pauldron_shell_low_' + s, sh, [sign * 6.5, 32.8, 0.0], [3.6, 1.65, 4.9], 'bronze', [0, 0, -sign * 16]);
    box('pauldron_shell_mid_' + s, sh, [sign * 6.45, 34.4, 0.0], [4.1, 1.85, 5.2], 'brass', [0, 0, sign * 14]);
    box('pauldron_shell_top_' + s, sh, [sign * 6.3, 35.8, 0.0], [4.3, 1.2, 4.7], 'brassLight', [0, 0, sign * 28]);
    box('pauldron_spire_base_' + s, sh, [sign * 8.15, 36.8, 0.0], [1.65, 2.65, 3.2], 'brassLight', [0, 0, sign * 24]);
    box('pauldron_spire_tip_' + s, sh, [sign * 8.85, 38.4, 0.0], [0.92, 2.1, 1.65], 'filigree', [0, 0, sign * 30]);
    box('pauldron_front_rim_' + s, sh, [sign * 6.55, 34.5, -2.62], [3.9, 0.45, 0.42], 'filigree', [0, 0, sign * 14]);
    box('pauldron_back_rim_' + s, sh, [sign * 6.55, 34.5, 2.62], [3.9, 0.45, 0.42], 'filigree', [0, 0, sign * 14]);
    box('pauldron_crest_gem_' + s, sh, [sign * 6.6, 34.55, -2.78], [0.72, 0.72, 0.28], 'coreGlow', [0, 0, 45]);
    for (let i = 0; i < 2; i++) {
      box('pauldron_fringe_' + s + '_' + i, sh, [sign * (7.1 + i * 0.55), 31.6 - i * 0.55, 0.0], [1.6, 0.78, 3.85 - i * 0.6], i === 0 ? 'brass' : 'filigree', [0, 0, -sign * 22]);
    }

    // UPPER ARM (`*_arm`)
    bar('upper_arm_core_' + s, arm, [sign * 6.0, 33.6, 0.0], [sign * 11.1, 31.4, 0.0], 1.45, 1.55, 'chassisDark');
    bar('upper_arm_rerebrace_' + s, arm, [sign * 6.8, 33.4, -0.32], [sign * 10.3, 31.9, -0.32], 1.85, 1.35, 'brass');
    bar('upper_arm_filigree_' + s, arm, [sign * 7.0, 33.4, -1.02], [sign * 10.1, 32.0, -1.02], 0.52, 0.28, 'filigree');
    bar('upper_arm_piston_' + s, arm, [sign * 6.8, 33.1, 0.92], [sign * 10.6, 31.5, 0.92], 0.44, 0.44, 'chassisLight');

    // ELBOW & FOREARM (`*_elbow`)
    box('elbow_joint_core_' + s, elb, [sign * 11.1, 31.4, 0.0], [2.15, 2.15, 2.15], 'chassisDark');
    box('elbow_cap_front_' + s, elb, [sign * 11.1, 31.4, -1.18], [1.55, 1.55, 0.45], 'filigree', [0, 0, 45]);
    box('elbow_gear_back_' + s, elb, [sign * 11.1, 31.4, 1.15], [1.65, 1.65, 0.42], 'bronze', [0, 0, 45]);
    box('elbow_couter_fin_' + s, elb, [sign * 11.2, 30.1, 0.35], [1.25, 1.65, 1.6], 'brassLight', [15, 0, -sign * 20]);
    rivet('elbow_axle_' + s, elb, sign * 11.1, 31.4, -1.42, 0.48, 0.26, 'porcelain');

    bar('forearm_core_' + s, elb, [sign * 11.2, 31.4, -0.05], [sign * 15.9, 33.4, -0.15], 1.75, 1.85, 'chassis');
    bar('forearm_shell_' + s, elb, [sign * 11.9, 31.7, -0.2], [sign * 15.6, 33.3, -0.25], 2.15, 1.95, 'brass');
    bar('forearm_crest_' + s, elb, [sign * 12.1, 32.0, -1.15], [sign * 15.4, 33.5, -1.15], 0.72, 0.45, 'filigree');
    for (let i = 0; i < 3; i++) {
      const t = 0.25 + i * 0.25;
      const fx = sign * (11.5 + t * 4.2);
      const fy = 31.5 + t * 1.8;
      box('forearm_vent_' + s + '_' + i, elb, [fx, fy + 0.78, -0.15], [0.5, 0.48, 1.55], 'bronze', [0, 0, sign * 22]);
    }

    // WRIST & ARTICULATED GAUNTLET (`*_wrist`)
    const wx = sign * 16.0, wy = 33.4, wz = -0.15;
    box('wrist_cuff_' + s, wri, [wx - sign * 0.55, wy - 0.1, wz], [1.0, 2.35, 2.3], 'bronze', [0, 0, sign * 22]);
    box('wrist_cuff_trim_' + s, wri, [wx - sign * 0.25, wy + 0.02, wz], [0.45, 2.52, 2.48], 'filigree', [0, 0, sign * 22]);
    box('gauntlet_palm_' + s, wri, [wx + sign * 0.25, wy - 0.1, wz], [1.75, 1.85, 1.85], 'chassisDark');
    box('gauntlet_backplate_' + s, wri, [wx + sign * 0.3, wy + 0.05, wz - 0.98], [1.55, 1.55, 0.46], 'brassLight');
    box('gauntlet_knuckle_rim_' + s, wri, [wx + sign * 0.75, wy - 0.4, wz - 0.85], [0.85, 0.48, 0.82], 'filigree');
    for (let i = 0; i < 3; i++) {
      box('gauntlet_finger_' + s + '_' + i, wri, [wx + sign * (0.25 + i * 0.36), wy - 1.15, wz + 0.15], [0.34, 1.05, 1.15], i === 1 ? 'chassisLight' : 'chassis');
    }

    // ORNATE CRESCENT CLOCKWORK MANTIS-SCYTHES (`*_blade`) — consistent outward spine normals!
    box('blade_collar_' + s, bld, [sign * 16.8, 33.4, -0.25], [1.65, 1.9, 1.5], 'bronze');
    box('blade_guard_wing_' + s, bld, [sign * 16.4, 34.9, -0.25], [1.05, 2.5, 1.25], 'filigree', [0, 0, -sign * 18]);
    box('blade_guard_scroll_' + s, bld, [sign * 17.5, 34.6, -0.25], [1.55, 0.72, 1.0], 'brassLight', [0, 0, -sign * 24]);
    box('blade_lock_gem_' + s, bld, [sign * 16.95, 33.45, -1.04], [0.78, 0.78, 0.3], 'coreGlow', [0, 0, 45]);
    box('blade_lock_gem_back_' + s, bld, [sign * 16.95, 33.45, 0.54], [0.78, 0.78, 0.3], 'coreGlow', [0, 0, 45]);

    const bladePts = [
      [16.8, 33.45, 1.4],
      [19.2, 33.4,  1.72],
      [21.6, 32.15, 1.85],
      [23.7, 29.75, 1.65],
      [25.2, 26.5,  1.38],
      [26.0, 22.7,  1.05],
      [26.1, 18.7,  0.7],
      [25.1, 15.1,  0.3]
    ];
    for (let i = 0; i < bladePts.length - 1; i++) {
      const p0 = bladePts[i], p1 = bladePts[i + 1];
      const a = [sign * p0[0], p0[1], -0.28];
      const b = [sign * p1[0], p1[1], -0.28];
      const dx = b[0] - a[0], dy = b[1] - a[1];
      const len = Math.hypot(dx, dy);
      // Consistent outward normal pointing AWAY from the concave center of curvature (around [sign*17, 21])
      const mx = (a[0] + b[0]) * 0.5 - sign * 16.5;
      const my = (a[1] + b[1]) * 0.5 - 21.0;
      let nx = -dy / len, ny = dx / len;
      if (nx * mx + ny * my < 0) { nx = -nx; ny = -ny; }
      const w = (p0[2] + p1[2]) * 0.5;
      bar('blade_body_' + s + '_' + i, bld, a, b, w * 1.12, 0.56, 'bladeSteel');
      bar('blade_spine_' + s + '_' + i, bld,
        [a[0] + nx * w * 0.5, a[1] + ny * w * 0.5, -0.28],
        [b[0] + nx * w * 0.5, b[1] + ny * w * 0.5, -0.28],
        w * 0.42, 0.74, 'bladeDark');
      bar('blade_edge_' + s + '_' + i, bld,
        [a[0] - nx * w * 0.56, a[1] - ny * w * 0.56, -0.28],
        [b[0] - nx * w * 0.56, b[1] - ny * w * 0.56, -0.28],
        w * 0.34, 0.24, 'bladeEdge');
      if (i < 5) {
        bar('blade_filigree_' + s + '_' + i, bld,
          [a[0] + nx * w * 0.14, a[1] + ny * w * 0.14, -0.28],
          [b[0] + nx * w * 0.14, b[1] + ny * w * 0.14, -0.28],
          0.26, 0.68, i < 2 ? 'filigree' : 'coreGlow');
      }
    }
    box('blade_spur_' + s, bld, [sign * 22.8, 32.35, -0.28], [1.45, 0.68, 0.65], 'filigree', [0, 0, -sign * 32]);

    // =========================================================================
    // 10. SCULPTED BALLETIC ARMORED LEGS & SABATON BOOTS
    // =========================================================================
    const leg = s + '_leg', knee = s + '_knee', ank = s + '_ankle';

    // Seamless hip ball-joint housing + contoured cuisse thigh armor
    box('hip_joint_drum_' + s, leg, [sign * 2.15, 19.65, 0.0], [2.2, 1.9, 2.45], 'chassisDark');
    bar('thigh_chassis_' + s, leg, [sign * 2.25, 19.6, 0.0], [sign * 2.75, 11.2, -0.1], 1.75, 1.95, 'chassisDark');
    bar('thigh_cuisse_upper_' + s, leg, [sign * 2.38, 19.1, -0.45], [sign * 2.6, 15.2, -0.58], 2.55, 1.65, 'brass');
    bar('thigh_cuisse_lower_' + s, leg, [sign * 2.58, 15.6, -0.56], [sign * 2.74, 12.4, -0.68], 2.1, 1.35, 'bronze');
    bar('thigh_cuisse_ridge_' + s, leg, [sign * 2.36, 18.8, -1.32], [sign * 2.72, 12.6, -1.34], 0.56, 0.36, 'filigree');
    bar('thigh_cuisse_outer_' + s, leg, [sign * 3.35, 18.7, 0.0], [sign * 3.58, 13.0, -0.08], 0.72, 2.25, 'brassLight');
    box('thigh_upper_cuff_' + s, leg, [sign * 2.4, 18.5, -0.1], [2.75, 0.75, 2.55], 'filigree');
    bar('thigh_tendon_' + s, leg, [sign * 2.45, 18.4, 1.12], [sign * 2.85, 12.0, 1.02], 0.44, 0.44, 'chassisLight');

    // KNEE & GREAVE (`*_knee`)
    box('knee_core_' + s, knee, [sign * 2.75, 11.2, -0.1], [2.15, 2.1, 2.2], 'chassisDark');
    box('knee_poleyn_plate_' + s, knee, [sign * 2.75, 11.3, -1.25], [2.12, 2.12, 0.68], 'brassLight', [0, 0, 45]);
    box('knee_poleyn_trim_' + s, knee, [sign * 2.75, 11.3, -1.56], [1.22, 1.22, 0.34], 'filigree', [0, 0, 45]);
    box('knee_side_bolt_' + s, knee, [sign * 2.75, 11.2, -0.05], [2.65, 0.88, 0.88], 'bronze');

    bar('shin_chassis_' + s, knee, [sign * 2.75, 10.6, -0.1], [sign * 3.15, 2.7, -0.35], 1.5, 1.72, 'chassisDark');
    bar('shin_greave_main_' + s, knee, [sign * 2.8, 10.1, -0.45], [sign * 3.12, 3.6, -0.72], 2.02, 1.32, 'brass');
    bar('shin_greave_crest_' + s, knee, [sign * 2.82, 10.0, -1.16], [sign * 3.12, 3.8, -1.38], 0.56, 0.42, 'porcelain');
    box('shin_band_top_' + s, knee, [sign * 2.88, 8.6, -0.35], [2.32, 0.56, 2.22], 'filigree');
    box('shin_band_bot_' + s, knee, [sign * 3.04, 5.2, -0.45], [2.14, 0.52, 2.02], 'bronze');
    bar('shin_calf_piston_' + s, knee, [sign * 2.85, 9.8, 0.92], [sign * 3.15, 3.4, 0.68], 0.44, 0.44, 'chassisLight');

    // ANKLE & GOTHIC SABATON BOOT (`*_ankle`)
    const fx = sign * 3.15;
    box('ankle_cuff_' + s, ank, [fx, 2.85, -0.35], [2.2, 1.05, 2.2], 'bronze');
    box('ankle_cuff_rim_' + s, ank, [fx, 3.35, -0.35], [2.38, 0.32, 2.38], 'filigree');
    box('boot_heel_' + s, ank, [fx, 1.45, -0.15], [2.1, 2.25, 2.1], 'chassisDark');
    box('boot_heel_spur_' + s, ank, [fx, 1.25, 1.18], [0.56, 0.72, 0.96], 'filigree', [18, 0, 0]);
    box('boot_instep_a_' + s, ank, [fx, 1.68, -1.65], [2.28, 1.55, 1.88], 'brass', [14, 0, 0]);
    box('boot_instep_trim_' + s, ank, [fx, 2.32, -1.45], [2.4, 0.36, 0.85], 'filigree', [14, 0, 0]);
    box('boot_toecap_' + s, ank, [fx, 1.15, -2.95], [1.98, 1.05, 1.48], 'brassLight', [8, 0, 0]);
    box('boot_toepoint_' + s, ank, [fx, 0.92, -3.76], [1.22, 0.72, 0.76], 'porcelain');
    box('boot_sole_' + s, ank, [fx, 0.36, -1.45], [2.38, 0.72, 4.65], 'chassisDark');

    // =========================================================================
    // 11. SCALLOPED HIP TASSETS & S-CURVED SWALLOWTAIL COATTAILS
    // =========================================================================
    const hip = s + '_hip_plate';
    box('hip_underplate_' + s, hip, [sign * 3.35, 21.1, 0.45], [1.25, 3.85, 3.5], 'chassisDark', [0, 0, -sign * 22]);
    box('hip_main_plate_' + s, hip, [sign * 3.75, 21.1, 0.4], [0.88, 4.3, 3.9], 'brass', [0, 0, -sign * 24]);
    box('hip_lower_flange_' + s, hip, [sign * 4.55, 18.6, 0.45], [0.76, 2.55, 3.2], 'bronze', [0, 0, -sign * 32]);
    box('hip_trim_front_' + s, hip, [sign * 3.9, 21.0, -1.52], [0.95, 4.1, 0.45], 'filigree', [0, 0, -sign * 24]);
    box('hip_trim_top_' + s, hip, [sign * 3.4, 22.85, 0.4], [1.2, 0.48, 4.05], 'filigree', [0, 0, -sign * 24]);
    rivet('hip_clasp_' + s, hip, sign * 4.22, 21.3, -0.35, 0.56, 0.56, 'porcelain');

    const tailSpecs = [
      { bone: s + '_tail_0', x: sign * 2.25, y: 20.0, z: 2.15, w: 3.25, h: 4.95, mat: 'brass', trim: 'filigree' },
      { bone: s + '_tail_1', x: sign * 2.8,  y: 15.1, z: 2.52, w: 2.85, h: 4.9,  mat: 'bronze', trim: 'filigree' },
      { bone: s + '_tail_2', x: sign * 3.3,  y: 10.3, z: 2.88, w: 2.45, h: 4.55, mat: 'brass', trim: 'porcelain' }
    ];
    tailSpecs.forEach((sp, i) => {
      box('tail_lining_' + s + '_' + i, sp.bone, [sp.x, sp.y, sp.z - 0.35], [sp.w - 0.3, sp.h - 0.2, 0.42], 'chassisDark');
      box('tail_plate_' + s + '_' + i, sp.bone, [sp.x, sp.y, sp.z + 0.08], [sp.w, sp.h, 0.6], sp.mat);
      box('tail_lip_' + s + '_' + i, sp.bone, [sp.x, sp.y + sp.h / 2 - 0.25, sp.z + 0.34], [sp.w + 0.4, 0.58, 0.54], 'filigree');
      box('tail_spine_' + s + '_' + i, sp.bone, [sp.x, sp.y - 0.1, sp.z + 0.48], [0.48, sp.h - 0.45, 0.34], sp.trim);
      for (const edge of [-1, 1]) {
        box('tail_rail_' + s + '_' + i + '_' + edge, sp.bone, [sp.x + edge * (sp.w / 2 - 0.14), sp.y - 0.1, sp.z + 0.3], [0.34, sp.h - 0.35, 0.44], 'brassLight');
      }
      box('tail_pin_' + s + '_' + i, sp.bone, [sp.x, sp.y + sp.h / 2 - 0.25, sp.z + 0.64], [0.46, 0.46, 0.18], 'coreGlow', [0, 0, 45]);
    });
    const t2 = s + '_tail_2';
    box('tail_finial_core_' + s, t2, [sign * 3.35, 7.5, 2.98], [1.95, 2.25, 0.54], 'bronze');
    box('tail_finial_outer_' + s, t2, [sign * 4.08, 6.05, 3.06], [0.88, 2.8, 0.46], 'filigree', [0, 0, -sign * 14]);
    box('tail_finial_inner_' + s, t2, [sign * 2.82, 6.5, 3.06], [0.78, 2.2, 0.46], 'brassLight', [0, 0, sign * 10]);
    box('tail_finial_diamond_' + s, t2, [sign * 3.35, 8.5, 3.35], [0.98, 0.98, 0.26], 'porcelain', [0, 0, 45]);
  }

  globalThis.cogworkV2 = { bones, parts };
  return JSON.stringify({ parts: parts.length, bones: Object.keys(bones).length });
})()
