(() => {
  if (!globalThis.cogworkV2) throw new Error('Run build_cogwork_dancer_v2.js first');
  const { bones, parts } = globalThis.cogworkV2;
  const size = 1024;
  const density = 4;

  // Dual bespoke palettes sharing identical UV coordinates:
  // Gold (lead dancer): Venetian gilded brass, burnished bronze, midnight-slate, turquoise core.
  // Silver (follower): Moonlit sterling silver, frost pewter, obsidian-indigo, pale-gold filigree, aquamarine core.
  const goldMat = {
    brassDark:      { color: [118, 78, 36],  noise: 5, edge: 16, kind: 'metal' },
    brass:          { color: [194, 142, 68], noise: 5, edge: 22, kind: 'metal' },
    brassLight:     { color: [232, 192, 118],noise: 4, edge: 24, kind: 'metal' },
    filigree:       { color: [248, 218, 140],noise: 3, edge: 26, kind: 'gold'  },
    bronze:         { color: [154, 96, 48],  noise: 6, edge: 18, kind: 'metal' },
    chassisDark:    { color: [28, 36, 46],   noise: 4, edge: 10, kind: 'steel' },
    chassis:        { color: [54, 68, 80],   noise: 4, edge: 14, kind: 'steel' },
    chassisLight:   { color: [112, 128, 140],noise: 4, edge: 18, kind: 'steel' },
    porcelain:      { color: [236, 228, 210],noise: 3, edge: 14, kind: 'ivory' },
    visor:          { color: [14, 17, 24],   noise: 2, edge: 4,  kind: 'lacquer'},
    bladeDark:      { color: [46, 56, 68],   noise: 4, edge: 14, kind: 'blade' },
    bladeSteel:     { color: [176, 188, 198],noise: 4, edge: 20, kind: 'blade' },
    bladeEdge:      { color: [244, 247, 246],noise: 2, edge: 14, kind: 'blade' },
    eyeGlow:        { color: [255, 242, 206],noise: 1, edge: 0,  kind: 'glow'  },
    coreGlow:       { color: [74, 236, 224], noise: 2, edge: 0,  kind: 'glow'  },
    coreHot:        { color: [224, 255, 252],noise: 0, edge: 0,  kind: 'glow'  },
    haloGlow:       { color: [255, 234, 172],noise: 1, edge: 0,  kind: 'glow'  }
  };

  const silverMat = {
    brassDark:      { color: [74, 88, 106],  noise: 5, edge: 16, kind: 'metal' },
    brass:          { color: [152, 170, 188],noise: 5, edge: 22, kind: 'metal' },
    brassLight:     { color: [208, 222, 234],noise: 4, edge: 24, kind: 'metal' },
    filigree:       { color: [234, 214, 166],noise: 3, edge: 24, kind: 'gold'  },
    bronze:         { color: [108, 126, 148],noise: 6, edge: 18, kind: 'metal' },
    chassisDark:    { color: [22, 28, 40],   noise: 4, edge: 10, kind: 'steel' },
    chassis:        { color: [48, 60, 78],   noise: 4, edge: 14, kind: 'steel' },
    chassisLight:   { color: [118, 134, 152],noise: 4, edge: 18, kind: 'steel' },
    porcelain:      { color: [226, 236, 244],noise: 3, edge: 14, kind: 'ivory' },
    visor:          { color: [12, 15, 24],   noise: 2, edge: 4,  kind: 'lacquer'},
    bladeDark:      { color: [40, 52, 68],   noise: 4, edge: 14, kind: 'blade' },
    bladeSteel:     { color: [188, 202, 216],noise: 4, edge: 20, kind: 'blade' },
    bladeEdge:      { color: [242, 250, 255],noise: 2, edge: 14, kind: 'blade' },
    eyeGlow:        { color: [226, 248, 255],noise: 1, edge: 0,  kind: 'glow'  },
    coreGlow:       { color: [98, 216, 255], noise: 2, edge: 0,  kind: 'glow'  },
    coreHot:        { color: [232, 248, 255],noise: 0, edge: 0,  kind: 'glow'  },
    haloGlow:       { color: [204, 242, 255],noise: 1, edge: 0,  kind: 'glow'  }
  };

  // Pre-allocate deterministic packed UV rects across all unique (size, material, face, variant) tiles.
  const cache = new Map();
  const tiles = [];
  let px = 2, py = 2, rowH = 0, tileCounter = 0;

  function allocateTile(wUnits, hUnits, key, face) {
    const w = Math.max(2, Math.min(48, Math.ceil(wUnits * density)));
    const h = Math.max(2, Math.min(48, Math.ceil(hUnits * density)));
    const variant = tileCounter++ % 3;
    const id = `${w}_${h}_${key}_${face}_${variant}`;
    if (cache.has(id)) return cache.get(id);
    if (px + w + 2 > size) { px = 2; py += rowH + 2; rowH = 0; }
    if (py + h + 2 > size) throw new Error('Cogwork atlas overflow at y=' + py);
    const uv = [px, py, px + w, py + h];
    tiles.push({ u: px, v: py, w, h, key, face, seed: 42191 + tiles.length * 7919 });
    px += w + 2;
    rowH = Math.max(rowH, h);
    cache.set(id, uv);
    return uv;
  }

  for (const p of parts) {
    const d = [p.to[0] - p.from[0], p.to[1] - p.from[1], p.to[2] - p.from[2]];
    p.uv = {};
    for (const face of ['north', 'south', 'east', 'west', 'up', 'down']) {
      const dim = (face === 'north' || face === 'south') ? [d[0], d[1]]
        : (face === 'east' || face === 'west') ? [d[2], d[1]] : [d[0], d[2]];
      p.uv[face] = allocateTile(dim[0], dim[1], p.material, face);
    }
  }

  const clampByte = v => Math.max(0, Math.min(255, Math.round(v)));
  const rgb = a => `rgb(${clampByte(a[0])},${clampByte(a[1])},${clampByte(a[2])})`;

  function paintAtlas(name, palette) {
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#141922';
    ctx.fillRect(0, 0, size, size);

    const dirShade = { north: 1.03, south: 0.92, east: 0.97, west: 0.93, up: 1.08, down: 0.78 };

    for (const t of tiles) {
      const m = palette[t.key];
      if (!m) throw new Error('Unknown material: ' + t.key);
      let s = t.seed >>> 0;
      const rand = () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };

      const pw = Math.ceil(t.w / 3) + 1;
      const ph = Math.ceil(t.h / 3) + 1;
      const patches = Array.from({ length: ph }, () => Array.from({ length: pw }, () => rand() - 0.5));
      const shade = m.kind === 'glow' ? 1.0 : dirShade[t.face];

      for (let yy = 0; yy < t.h; yy++) {
        for (let xx = 0; xx < t.w; xx++) {
          let n = (rand() - 0.5) * m.noise * 1.7;
          const patch = patches[Math.floor(yy / 3)][Math.floor(xx / 3)];

          if (m.kind === 'glow') {
            // Luminous center bloom with gentle radiant edge
            const cx = (xx + 0.5) / t.w - 0.5;
            const cy = (yy + 0.5) / t.h - 0.5;
            const dist = Math.hypot(cx * 2, cy * 2);
            const boost = (1 - Math.min(1, dist)) * 34;
            const col = [m.color[0] + boost + n, m.color[1] + boost + n, m.color[2] + boost + n];
            ctx.fillStyle = rgb(col);
            ctx.fillRect(t.u + xx, t.v + yy, 1, 1);
            continue;
          }

          if (m.kind === 'metal' || m.kind === 'gold') {
            n += patch * 12;
            // Top-to-bottom anodized metallic sheen
            n += (0.5 - yy / Math.max(1, t.h - 1)) * 10;
            // Subtle brushed diagonal metallic glint
            if ((xx + yy) % 6 === 0) n += 3.5;
            if (t.w >= 4 && t.h >= 4) {
              if (yy === 0) n += m.edge;
              else if (yy === t.h - 1) n -= m.edge * 0.72;
              if (xx === 0) n += m.edge * 0.6;
              else if (xx === t.w - 1) n -= m.edge * 0.58;
              // Recessed inner filigree seam 1px inside border
              if (t.w >= 6 && t.h >= 6 && Math.min(xx, t.w - 1 - xx, yy, t.h - 1 - yy) === 1) {
                n -= 6.5;
              }
            }
            // Subtle clockwork patina & micro-glints
            const wear = rand();
            if (wear < 0.025) n -= 14;
            else if (wear > 0.982) n += 18;
          } else if (m.kind === 'steel') {
            n += patch * 8 + (0.45 - yy / Math.max(1, t.h - 1)) * 7;
            if (t.w >= 3 && t.h >= 3) {
              if (yy === 0) n += m.edge * 0.85;
              else if (yy === t.h - 1) n -= m.edge * 0.65;
              if (xx === 0 || xx === t.w - 1) n -= 4;
            }
            if (t.h >= 6 && yy % 3 === 0) n -= 3.5;
          } else if (m.kind === 'blade') {
            // Honed Damascus directional steel grain
            n += Math.sin((xx * 0.6 + yy * 0.9) * 0.7) * 6 + patch * 6;
            if (yy === 0 || xx === 0) n += m.edge * 0.8;
            if (yy === t.h - 1) n -= m.edge * 0.45;
          } else if (m.kind === 'ivory') {
            n += patch * 5 + (0.5 - yy / Math.max(1, t.h - 1)) * 6;
            if (t.w >= 4 && t.h >= 4 && (yy === 0 || xx === 0)) n += m.edge * 0.6;
            if (t.w >= 4 && t.h >= 4 && (yy === t.h - 1 || xx === t.w - 1)) n -= m.edge * 0.5;
          } else if (m.kind === 'lacquer') {
            n += patch * 3;
          }

          const col = m.color.map(c => c * shade + n);
          ctx.fillStyle = rgb(col);
          ctx.fillRect(t.u + xx, t.v + yy, 1, 1);
        }
      }
    }
    return new Texture({ name, render_mode: 'default' }).fromDataURL(canvas.toDataURL()).add(false);
  }

  // Clear previous textures and install the dual high-res pixel atlases
  [...Texture.all].forEach(t => t.remove());
  Project.texture_width = size;
  Project.texture_height = size;
  const goldTex = paintAtlas('cogwork_dancer', goldMat);
  paintAtlas('cogwork_dancer_silver', silverMat);

  for (const p of parts) {
    if (!bones[p.bone]) throw new Error('Missing bone ' + p.bone + ' for ' + p.name);
    const isGlow = goldMat[p.material].kind === 'glow';
    const cube = new Cube({
      name: p.name,
      from: p.from,
      to: p.to,
      origin: p.origin,
      rotation: p.rotation,
      box_uv: false,
      autouv: 0,
      shade: !isGlow
    });
    for (const face of Object.keys(p.uv)) {
      cube.faces[face].texture = goldTex.uuid;
      cube.faces[face].uv = p.uv[face];
    }
    cube.addTo(bones[p.bone]).init();
  }

  Modes.options.edit.select();
  unselectAll();
  Canvas.updateAll();
  return JSON.stringify({
    cubes: Cube.all.length,
    meshes: Mesh.all.length,
    bones: Group.all.length,
    textures: Texture.all.length,
    atlas: [size, size],
    atlasUsedHeight: py + rowH,
    uniqueTiles: tiles.length
  });
})()
