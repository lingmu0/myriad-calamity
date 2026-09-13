(() => {
  Project.texture_width=256; Project.texture_height=256;
  const palettes=[['#f0c37b','#9b602f'],['#fff0c7','#af8d54'],['#526264','#18272c'],['#f2ebd6','#9ea8a1'],['#ffffff','#dcfbff'],['#53ded6','#247b85'],['#231d20','#080d13'],['#e59850','#85502c']];
  function texture(name,silver) {
    const c=document.createElement('canvas'); c.width=256;c.height=256;const ctx=c.getContext('2d');
    ctx.fillStyle='#28353b';ctx.fillRect(0,0,256,256);
    palettes.forEach((pair,i)=>{
      const x=(i%4)*64,y=Math.floor(i/4)*64;
      const colors=silver&&i===0?['#d9e0d5','#7d969e']:pair;
      const g=ctx.createLinearGradient(x,y,x+56,y+64);g.addColorStop(0,colors[0]);g.addColorStop(0.35,colors[0]);g.addColorStop(1,colors[1]);
      ctx.fillStyle=g;ctx.fillRect(x,y,64,64);
      const pixels=ctx.getImageData(x,y,64,64);
      for(let py=0;py<64;py+=8)for(let px=0;px<64;px+=8){const k=((py+4)*64+px+4)*4;ctx.fillStyle=`rgb(${pixels.data[k]},${pixels.data[k+1]},${pixels.data[k+2]})`;ctx.fillRect(x+px,y+py,8,8);}
      if(i!==4){ctx.fillStyle='rgba(255,255,255,0.13)';ctx.fillRect(x+5,y+4,2,56);ctx.fillStyle='rgba(0,0,0,0.12)';ctx.fillRect(x+57,y+4,2,56);}
    });
    return new Texture({name,render_mode:'default'}).fromDataURL(c.toDataURL()).add(false);
  }
  const tex=texture('cogwork_dancer',false); texture('cogwork_dancer_silver',true);
  const groups={};
  function group(name,origin,parent){const g=new Group({name,origin}).addTo(parent?groups[parent]:'root').init();groups[name]=g;return g;}
  group('body',[0,25,0]);group('head',[0,37,0],'body');group('left_arm',[-6.5,32,0],'body');group('right_arm',[6.5,32,0],'body');
  group('left_leg',[-2,19,0],'body');group('right_leg',[2,19,0],'body');group('skirt',[0,24,1],'body');group('gear',[0,29,2],'body');group('halo',[0,40,3],'head');
  Canvas.updateAll();
  return JSON.stringify({rig:Group.all.length,textures:Texture.all.length});
})()