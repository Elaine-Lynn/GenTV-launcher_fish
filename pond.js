import { FishSpine } from './koi-pose.js';
// Original, dependency-free pond prototype. Coordinates use a fixed landscape world.
const canvas = document.querySelector('#pond');
const ctx = canvas.getContext('2d', { alpha: false });
const W = 1600, H = 900, TAU = Math.PI * 2;
let viewW, viewH, scale, offsetX, offsetY, dpr;
let bounds={left:0,right:W,top:0,bottom:H};
let seed = 68431;
const rand = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
const between = (a,b) => a + rand() * (b-a);
const clamp = (x,a,b) => Math.max(a,Math.min(b,x));
const angleDiff = (a,b) => Math.atan2(Math.sin(a-b),Math.cos(a-b));
const layer = () => {const c=document.createElement('canvas');c.width=W;c.height=H;return c;};
const bed=layer(), bedCtx=bed.getContext('2d');
const shore=layer(), shoreCtx=shore.getContext('2d');
const fish=[], foods=[], ripples=[], leaves=[];
let simTime=0, speed=1, previous=0, cursor={x:W*.55,y:H*.5,visible:false};
let lastFeed=-100, lastWake=0, totalEaten=0, toastTimer;

function stone(c,x,y,r,shade,land=false){
  const points=[], n=7+Math.floor(rand()*4), squash=between(.55,.95),rot=rand()*TAU;
  for(let i=0;i<n;i++){let a=i/n*TAU+rot,s=r*between(.8,1.13);points.push([x+Math.cos(a)*s,y+Math.sin(a)*s*squash]);}
  c.beginPath();c.moveTo((points[0][0]+points[n-1][0])/2,(points[0][1]+points[n-1][1])/2);
  points.forEach((p,i)=>{const next=points[(i+1)%n];c.quadraticCurveTo(p[0],p[1],(p[0]+next[0])/2,(p[1]+next[1])/2);});c.closePath();
  const g=c.createLinearGradient(x-r,y-r,x+r*.7,y+r);g.addColorStop(0,`hsl(${land?72:124} 9% ${shade+12}%)`);g.addColorStop(.5,`hsl(${land?76:135} 10% ${shade}%)`);g.addColorStop(1,`hsl(148 17% ${shade-7}%)`);c.fillStyle=g;
  c.shadowColor=land?'#051e1bb0':'#052e2980';c.shadowBlur=land?15:5;c.shadowOffsetY=land?9:3;c.fill();c.shadowBlur=0;c.shadowOffsetY=0;
  c.strokeStyle=land?'#d4d6b640':'#a9c4a31b';c.lineWidth=1;c.stroke();
  // Fracture planes and mineral grain give the riverbed relief without a repeating tile.
  c.save();c.clip();
  for(let k=0;k<7;k++){const px=x+between(-r,r),py=y+between(-r,r),gr=c.createRadialGradient(px,py,0,px,py,r*.6);gr.addColorStop(0,k%2?'#071e2429':'#dadcc220');gr.addColorStop(1,'#a0b2a000');c.fillStyle=gr;c.fillRect(px-r,py-r,r*2,r*2);}
  for(let j=0;j<r*3;j++){let px=x+between(-r,r),py=y+between(-r,r);c.fillStyle=rand()>.5?'#e0dfba16':'#09272125';c.fillRect(px,py,between(.5,2.2),between(.5,1.5));}
  c.restore();
}

function buildPond(){
  const c=bedCtx;c.fillStyle='#254d43';c.fillRect(0,0,W,H);
  // A loose, irregular tessellation of broad submerged slabs and smaller pebbles.
  for(let row=-1;row<12;row++)for(let col=-1;col<17;col++){
    const x=col*110+between(-35,35)+(row%2)*50,y=row*100+between(-27,27);
    stone(c,x,y,between(58,100),between(23,39));
  }
  for(let i=0;i<1700;i++){let x=rand()*W,y=rand()*H,r=between(1,7);c.fillStyle=`rgba(11,38,31,${between(.07,.3)})`;c.beginPath();c.ellipse(x,y,r,r*.65,rand()*TAU,0,TAU);c.fill();}
  // Soft sediment and depth gradients tie the stones together beneath the water.
  for(let i=0;i<35;i++){let x=rand()*W,y=rand()*H,r=between(80,300),g=c.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,i%2?'#66866a21':'#063f4433');g.addColorStop(1,'#204b4200');c.fillStyle=g;c.fillRect(x-r,y-r,r*2,r*2);}
  mineralTexture(c);
  const depth=c.createRadialGradient(W*.52,H*.45,70,W*.52,H*.45,1000);depth.addColorStop(0,'#0d535f3d');depth.addColorStop(.65,'#1c666142');depth.addColorStop(1,'#071f2455');c.fillStyle=depth;c.fillRect(0,0,W,H);
  // Land is restricted to corners to preserve a spacious swimming area.
  const s=shoreCtx;
  for(let i=0;i<42;i++){
    const left=i<21,x=left?between(-70,115):between(W-100,W+60),y=left?between(H-250,H+90):between(-80,200);
    stone(s,x,y,between(22,76),between(37,55),true);
  }
  reeds(s,35,H-32,1);reeds(s,W-13,30,-1);
  for(let i=0;i<11;i++){
    const x=i<6?between(-20,175):between(W-170,W+30),y=i<6?between(-20,145):between(H-170,H+20);
    leaves.push({x,y,r:between(24,48),a:rand()*TAU,phase:rand()*TAU});
  }
}

function mineralTexture(c){
  // Multiscale value noise adds stone pores, silt and submerged algae to the bed.
  const grids=[4,13,37,91].map(cell=>{const cols=Math.ceil(W/cell)+2,rows=Math.ceil(H/cell)+2;return{cell,cols,data:Float32Array.from({length:cols*rows},()=>rand())};});
  const pixels=c.getImageData(0,0,W,H),data=pixels.data;
  for(let y=0;y<H;y++)for(let x=0;x<W;x++){
    let variation=0,algae=0;
    for(let k=0;k<grids.length;k++){
      const g=grids[k],xx=x/g.cell,yy=y/g.cell,ix=xx|0,iy=yy|0,fx=xx-ix,fy=yy-iy,sx=fx*fx*(3-2*fx),sy=fy*fy*(3-2*fy),i=iy*g.cols+ix;
      const v=(g.data[i]*(1-sx)+g.data[i+1]*sx)*(1-sy)+(g.data[i+g.cols]*(1-sx)+g.data[i+g.cols+1]*sx)*sy;
      variation+=(v-.5)*[9,16,23,16][k];if(k===2)algae=Math.max(0,v-.5)*21;
    }
    const i=(y*W+x)*4,grain=(rand()-.5)*10;
    data[i]=clamp(data[i]+variation+grain-algae*.65,0,255);data[i+1]=clamp(data[i+1]+variation+grain+algae*.35,0,255);data[i+2]=clamp(data[i+2]+variation*.8+grain-algae*.5,0,255);
  }c.putImageData(pixels,0,0);
}

function reeds(c,x,y,dir){
  for(let i=0;i<28;i++){
    const dx=between(-110,150)*dir,dy=-between(65,230)*dir,base=x+between(-40,40),by=y+between(-18,18);
    c.beginPath();c.moveTo(base,by);c.quadraticCurveTo(base+dx*.2,by+dy*.75,base+dx,by+dy);c.quadraticCurveTo(base+dx*.45,by+dy*.7,base+between(2,7),by);c.fillStyle=['#697b59','#9caa80','#b9bd91','#4b6650'][i%4];c.shadowColor='#092a23';c.shadowBlur=5;c.fill();c.shadowBlur=0;
  }
}

// Each fish has its own skin, spine, cruising rhythm, preferred depth and response delay.
function createSkin(type){
  const skin=document.createElement('canvas');skin.width=256;skin.height=80;const c=skin.getContext('2d');
  c.fillStyle=['#eee5c7','#e9dcb9','#d9a331','#d2c7ab','#293630'][type];c.fillRect(0,0,256,80);
  if(type!==2){
    for(let i=0;i<9;i++){
      c.fillStyle=type===4?(i%3?'#bda976':'#d06930'):(i%3===0?'#25352e':'#bf4927');
      c.beginPath();c.ellipse(between(20,235),between(8,72),between(8,36),between(9,27),between(-.6,.6),0,TAU);c.fill();
    }
  }
  const g=c.createLinearGradient(0,0,0,80);g.addColorStop(0,'#062d287d');g.addColorStop(.27,'#ffffff05');g.addColorStop(.48,'#fff8cc45');g.addColorStop(.72,'#ffffff00');g.addColorStop(1,'#062c287d');c.fillStyle=g;c.fillRect(0,0,256,80);
  c.lineWidth=.6;c.strokeStyle='#635a3c30';
  for(let x=0;x<256;x+=9)for(let y=0;y<85;y+=8){c.beginPath();c.arc(x+(y%16?4.5:0),y,4.5,-1.1,1.1);c.stroke();}
  return skin;
}
function addFish(){
  const type=Math.floor(between(0,5)),a=rand()*TAU;
  const f={x:between(220,W-200),y:between(180,H-150),a,turn:0,v:between(20,34),cruise:between(22,37),size:between(45,73),phase:rand()*TAU,finPhase:rand()*TAU,effort:.65,beatHz:1,stroking:rand()>.5,motionTimer:between(.5,2.5),depth:between(.15,.95),targetDepth:between(.15,.95),wander:a,wanderTimer:0,response:between(.15,1.5),mode:'cruise',target:null,skin:createSkin(type),type,fear:0};
  f.pose=new FishSpine(f);fish.push(f);
}
function setCount(n){while(fish.length<n)addFish();fish.length=n;document.querySelector('#fish-count').textContent=`${n} 尾锦鲤，自在游弋`;document.querySelector('#count-output').value=n;}
function waterRipple(x,y,power=1){ripples.push({x,y,age:0,power});if(ripples.length>45)ripples.shift();}
function feedAt(x,y){
  if(simTime-lastFeed<.65)return;
  lastFeed=simTime;cursor.x=x;cursor.y=y;
  waterRipple(x,y,.6);
  for(let i=0;i<12;i++){let a=rand()*TAU,r=between(2,24);foods.push({x:clamp(x+Math.cos(a)*r,25,W-25),y:clamp(y+Math.sin(a)*r,25,H-25),age:0,phase:rand()*TAU});}
  if(foods.length>96)foods.splice(0,foods.length-96);
  toast('鱼食落水，静候鱼来');
}
function toast(text){const el=document.querySelector('#toast');el.textContent=text;el.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('visible'),2300);}

function update(dt){
  simTime+=dt;
  for(const f of fish){
    f.wanderTimer-=dt;f.fear=Math.max(0,f.fear-dt*.45);
    if(f.wanderTimer<=0&&f.fear<=0){f.wander=f.a+between(-.7,.7);f.wanderTimer=between(2.4,5);f.targetDepth=between(.15,.95);}
    let ax=Math.cos(f.wander)*.85,ay=Math.sin(f.wander)*.85;
    let nearest=Infinity;
    if(f.target&&!foods.includes(f.target))f.target=null;
    if(!f.target){for(const food of foods){const d=Math.hypot(food.x-f.x,food.y-f.y);if(food.age>f.response && d<nearest){nearest=d;f.target=food;}}}
    else nearest=Math.hypot(f.target.x-f.x,f.target.y-f.y);
    f.mode=f.fear>0?'flee':f.target?'feed':'cruise';
    f.motionTimer-=dt;if(f.motionTimer<=0){f.stroking=!f.stroking;f.motionTimer=f.stroking?between(.7,1.5):between(1.2,3.2);}
    let effort=f.stroking?.72:.11,desiredV=f.cruise*(f.stroking?1.02:.70);
    if(f.target && f.fear<=0){
      const dx=f.target.x-f.x,dy=f.target.y-f.y;
      // Arrival steering lets the fish slow down rather than orbiting the food endlessly.
      ax=dx/Math.max(nearest,1)*2.2;ay=dy/Math.max(nearest,1)*2.2;
      desiredV=nearest<65?Math.max(9,nearest*.6):f.cruise*1.5;
      effort=nearest<70?.32:.85;
      if(nearest<14+f.size*.07){const index=foods.indexOf(f.target);if(index>=0){foods.splice(index,1);totalEaten++;waterRipple(f.x,f.y,.23);}f.target=null;}
      f.targetDepth=.1;
    }
    if(f.fear>0){ax=Math.cos(f.wander)*4;ay=Math.sin(f.wander)*4;desiredV=f.cruise*2.1;effort=1;}
    for(const other of fish){if(f===other)continue;let dx=f.x-other.x,dy=f.y-other.y,d=Math.hypot(dx,dy),separation=f.mode==='feed'?28:52;
      if(d<separation&&d>.01){let force=(separation-d)/separation*1.9;ax+=dx/d*force;ay+=dy/d*force;}
      else if(d<180 && f.mode==='cruise'){ax+=(other.x-f.x)/d*.045+Math.cos(other.a)*.055;ay+=(other.y-f.y)/d*.045+Math.sin(other.a)*.055;}
    }
    // Soft boundaries and stone-bank avoidance; no teleporting at screen edges.
    const mx=95,my=95;
    if(f.x<bounds.left+mx)ax+=(bounds.left+mx-f.x)/mx*5;if(f.x>bounds.right-mx)ax-=(f.x-bounds.right+mx)/mx*5;
    if(f.y<bounds.top+my)ay+=(bounds.top+my-f.y)/my*5;if(f.y>bounds.bottom-my)ay-=(f.y-bounds.bottom+my)/my*5;
    for(const bank of [{x:35,y:H-40},{x:W-20,y:20}]){let dx=f.x-bank.x,dy=f.y-bank.y,d=Math.hypot(dx,dy);if(d<240){ax+=dx/Math.max(d,1)*(240-d)/80;ay+=dy/Math.max(d,1)*(240-d)/80;}}
    const wanted=Math.atan2(ay,ax),delta=angleDiff(wanted,f.a),maxTurn=f.v/(f.size*.72)+.12+(f.target?.12:0)+f.fear*.24;
    f.turn+=(clamp(delta*1.6,-maxTurn,maxTurn)-f.turn)*(1-Math.exp(-dt*2.8));
    desiredV*=1-clamp(Math.abs(delta)/Math.PI,0,1)*.6;
    f.a=angleDiff(f.a+f.turn*dt,0);f.v+=(desiredV-f.v)*(1-Math.exp(-dt*.9));
    f.x=clamp(f.x+Math.cos(f.a)*f.v*dt,12,W-12);f.y=clamp(f.y+Math.sin(f.a)*f.v*dt,12,H-12);
    f.depth+=(f.targetDepth-f.depth)*dt*.18;
    f.effort+=(effort-f.effort)*(1-Math.exp(-dt*2.2));
    f.beatHz+=(.32+f.v/f.size*.9+f.effort*.72-f.beatHz)*(1-Math.exp(-dt*2));
    f.phase=(f.phase+dt*TAU*f.beatHz)%TAU;f.finPhase=(f.finPhase+dt*(1.6+f.effort*1.3))%TAU;
    f.pose.update(f);
  }
  for(let i=foods.length-1;i>=0;i--){const food=foods[i];food.age+=dt;food.x+=Math.sin(simTime*.4+food.phase)*dt*.7;food.y+=Math.cos(simTime*.3+food.phase)*dt*.6;if(food.age>35)foods.splice(i,1);}
  for(let i=ripples.length-1;i>=0;i--){ripples[i].age+=dt;if(ripples[i].age>4.2)ripples.splice(i,1);}
  if(simTime-lastWake>1){lastWake=simTime;for(const f of fish)if(f.depth<.28&&rand()<.35)waterRipple(f.x,f.y,.12);}
}

function spineFor(f){
  return f.pose.points;
}
const widths=[.053,.09,.115,.128,.132,.131,.126,.12,.111,.102,.092,.082,.073,.065,.058,.051,.044,.037,.031,.025,.02,.017,.013,.009,.005];
function drawFish(f,shadow=false){
  const p=spineFor(f),size=f.size*(1-f.depth*.12),c=ctx;
  c.save();
  if(shadow){c.translate(9+f.depth*17,12+f.depth*19);c.globalAlpha=.17-f.depth*.06;}
  else c.globalAlpha=1-f.depth*.18;
  const left=[],right=[];
  for(let i=0;i<p.length;i++){const prev=p[Math.max(0,i-1)],next=p[Math.min(p.length-1,i+1)],a=Math.atan2(next.y-prev.y,next.x-prev.x),w=widths[i]*size;left.push({x:p[i].x-Math.sin(a)*w,y:p[i].y+Math.cos(a)*w});right.push({x:p[i].x+Math.sin(a)*w,y:p[i].y-Math.cos(a)*w});}
  // Translucent pectoral fins beat independently of the tail.
  for(const side of [-1,1]){
    const base=p[6],a=Math.atan2(p[5].y-p[7].y,p[5].x-p[7].x)+side*(1.05+Math.sin(f.finPhase+side*.35)*.13)-f.turn*.12,fx=Math.cos(a),fy=Math.sin(a);
    c.beginPath();c.moveTo(base.x,base.y);c.quadraticCurveTo(base.x+fx*size*.19,base.y+fy*size*.19,base.x+fx*size*.22-Math.cos(f.a)*size*.2,base.y+fy*size*.22-Math.sin(f.a)*size*.2);c.quadraticCurveTo(base.x-Math.cos(f.a)*size*.21,base.y-Math.sin(f.a)*size*.21,base.x,base.y);c.fillStyle=shadow?'#001b19':f.type===2?'#c0a34270':'#ddd9bb60';c.fill();
    if(!shadow){c.strokeStyle='#d4dbb134';c.lineWidth=.6;c.stroke();}
  }
  // Forked tail follows the terminal spine tangent.
  const tail=p[24],prev=p[22],ta=Math.atan2(tail.y-prev.y,tail.x-prev.x),tlen=size*.25,tw=size*.16;
  const local=(x,y)=>[tail.x+Math.cos(ta)*x-Math.sin(ta)*y,tail.y+Math.sin(ta)*x+Math.cos(ta)*y];
  c.beginPath();c.moveTo(...local(-size*.035,0));c.quadraticCurveTo(...local(tlen*.5,-tw*.3),...local(tlen,-tw));c.quadraticCurveTo(...local(tlen*.9,-tw*.1),...local(tlen*.6,0));c.quadraticCurveTo(...local(tlen*.9,tw*.1),...local(tlen,tw));c.quadraticCurveTo(...local(tlen*.5,tw*.3),...local(-size*.035,0));c.fillStyle=shadow?'#001b19':f.type===2?'#d0a846ac':'#d4d8bda0';c.fill();
  if(!shadow){c.strokeStyle='#e8e5c546';c.lineWidth=.5;for(let k=-3;k<=3;k++){c.beginPath();c.moveTo(...local(0,0));c.lineTo(...local(tlen*.85,k/3*tw*.83));c.stroke();}}
  c.beginPath();left.forEach((v,i)=>i?c.lineTo(v.x,v.y):c.moveTo(v.x,v.y));for(let i=right.length-1;i>=0;i--)c.lineTo(right[i].x,right[i].y);c.closePath();
  if(shadow){c.fillStyle='#001b19';c.fill();c.restore();return;}
  c.save();c.clip();
  // Textured strips deform along the spine, keeping each koi's markings attached to its body.
  for(let i=0;i<24;i++){
    const a=p[i],b=p[i+1],dx=b.x-a.x,dy=b.y-a.y,segment=Math.hypot(dx,dy),nx=-dy/segment,ny=dx/segment;
    c.save();c.transform(dx/segment,dy/segment,nx,ny,a.x,a.y);c.drawImage(f.skin,i/24*256,0,256/24+1,80,-.5,-size*.135,segment+1,size*.27);c.restore();
  }
  c.restore();
  // Dorsal ridge, gill covers, black eyes and tiny highlights.
  c.beginPath();c.moveTo(p[6].x,p[6].y);for(let i=7;i<17;i++)c.lineTo(p[i].x,p[i].y);c.strokeStyle='#efeaca5c';c.lineWidth=size*.019;c.stroke();
  for(const side of [-1,1]){const eye=p[2],nx=-Math.sin(f.a)*side,ny=Math.cos(f.a)*side;c.beginPath();c.arc(eye.x+nx*size*.086,eye.y+ny*size*.086,size*.018,0,TAU);c.fillStyle='#122c27';c.fill();c.beginPath();c.arc(eye.x+nx*size*.09+.4,eye.y+ny*size*.09-.4,size*.005,0,TAU);c.fillStyle='#faf4db';c.fill();}
  // A veil of water softens deeper fish, rather than changing size alone.
  c.globalAlpha=f.depth*.19;c.fillStyle='#286967';c.beginPath();left.forEach((v,i)=>i?c.lineTo(v.x,v.y):c.moveTo(v.x,v.y));for(let i=right.length-1;i>=0;i--)c.lineTo(right[i].x,right[i].y);c.closePath();c.fill();
  c.restore();
}

function drawLeaf(l){
  const c=ctx,x=l.x+Math.sin(simTime*.2+l.phase)*2,y=l.y+Math.cos(simTime*.17+l.phase)*2;
  c.save();c.translate(x,y);c.rotate(l.a+Math.sin(simTime*.13+l.phase)*.025);c.shadowColor='#052a26b0';c.shadowBlur=9;c.shadowOffsetX=5;c.shadowOffsetY=9;
  c.beginPath();c.moveTo(0,0);c.arc(0,0,l.r,.16,TAU-.16);c.closePath();const g=c.createRadialGradient(-l.r*.3,-l.r*.4,0,0,0,l.r);g.addColorStop(0,'#a1af7c');g.addColorStop(.6,'#7e9468');g.addColorStop(1,'#536e4e');c.fillStyle=g;c.fill();c.shadowBlur=0;c.shadowOffsetX=0;c.shadowOffsetY=0;c.strokeStyle='#b0b99466';c.lineWidth=.8;c.stroke();
  for(let a=.3;a<TAU-.2;a+=.3){c.beginPath();c.moveTo(0,0);c.quadraticCurveTo(Math.cos(a+.1)*l.r*.5,Math.sin(a+.1)*l.r*.5,Math.cos(a)*l.r*.94,Math.sin(a)*l.r*.94);c.strokeStyle='#c5ca9b25';c.lineWidth=.6;c.stroke();}c.restore();
}

function draw(){
  ctx.setTransform(dpr,0,0,dpr,0,0);ctx.fillStyle='#163e3e';ctx.fillRect(0,0,viewW,viewH);ctx.translate(offsetX,offsetY);ctx.scale(scale,scale);
  ctx.drawImage(bed,0,0);
  // Moving, broken light ribbons suggest shallow-water caustics at very low contrast.
  ctx.save();ctx.globalCompositeOperation='screen';ctx.lineWidth=1.3;
  for(let j=0;j<21;j++){
    ctx.beginPath();for(let i=0;i<=40;i++){let x=i*44-60+Math.sin(j*3.1+simTime*.15)*20,y=j*57+Math.sin(i*.6+j*1.7+simTime*.22)*16+Math.sin(i*1.2-j+simTime*.14)*7;i?ctx.lineTo(x,y):ctx.moveTo(x,y);}ctx.strokeStyle=`rgba(153,195,151,${.022+Math.sin(j+simTime*.3)*.012})`;ctx.stroke();
  }ctx.restore();
  // Fish depth controls painter order. Their shadows stay on the pond bed.
  const sorted=[...fish].sort((a,b)=>b.depth-a.depth);for(const f of sorted)drawFish(f,true);for(const f of sorted)drawFish(f);
  for(const food of foods){const fade=Math.min(1,(35-food.age)/3);ctx.globalAlpha=fade;ctx.beginPath();ctx.ellipse(food.x+1,food.y+2,2.7,1.7,0,0,TAU);ctx.fillStyle='#122e2680';ctx.fill();ctx.beginPath();ctx.arc(food.x,food.y,1.8,0,TAU);ctx.fillStyle='#c4a66d';ctx.fill();ctx.beginPath();ctx.arc(food.x-.5,food.y-.5,.65,0,TAU);ctx.fillStyle='#eee2ac';ctx.fill();}ctx.globalAlpha=1;
  // Broad reflected light glides over the water; it never obscures the fish.
  let gx=W*.65+Math.sin(simTime*.06)*90,gy=H*.22+Math.cos(simTime*.07)*45;
  let reflection=ctx.createRadialGradient(gx,gy,5,gx,gy,580);reflection.addColorStop(0,'#cbdac30b');reflection.addColorStop(.5,'#b9d5c309');reflection.addColorStop(1,'#b9d5c300');ctx.fillStyle=reflection;ctx.fillRect(0,0,W,H);
  for(const r of ripples){const fade=Math.pow(1-r.age/4.2,2),radius=6+r.age*43*(.6+r.power*.4);for(let k=0;k<3;k++){const rr=radius-k*9;if(rr<2)continue;ctx.beginPath();ctx.ellipse(r.x,r.y,rr,rr*.83,0,0,TAU);ctx.lineWidth=k===0?1.2:.6;ctx.strokeStyle=`rgba(210,231,209,${fade*(.13+r.power*.15)*(1-k*.22)})`;ctx.stroke();ctx.beginPath();ctx.ellipse(r.x,r.y+2,rr+2,rr*.83+1,0,0,TAU);ctx.strokeStyle=`rgba(3,37,38,${fade*.13})`;ctx.stroke();}}
  ctx.drawImage(shore,0,0);for(const l of leaves)drawLeaf(l);
  // Few airborne motes, with deliberately restrained brightness.
  for(let i=0;i<26;i++){let x=(i*347.29+Math.sin(simTime*.08+i)*18)%W,y=(i*197.17-simTime*(.9+i%3)+H*100)%H;ctx.beginPath();ctx.arc(x,y,.7+i%3*.3,0,TAU);ctx.fillStyle=`rgba(230,237,208,${.12+Math.sin(simTime*.4+i)*.08})`;ctx.fill();}
  const vignette=ctx.createRadialGradient(W*.5,H*.5,H*.2,W*.5,H*.5,W*.64);vignette.addColorStop(0,'#061f2000');vignette.addColorStop(1,'#061f2080');ctx.fillStyle=vignette;ctx.fillRect(0,0,W,H);
  if(cursor.visible){ctx.save();ctx.translate(cursor.x,cursor.y);ctx.strokeStyle='#eee9c99c';ctx.lineWidth=1.2;ctx.beginPath();ctx.arc(0,0,12+Math.sin(simTime*2)*1.5,0,TAU);ctx.stroke();ctx.beginPath();ctx.moveTo(-20,0);ctx.lineTo(-16,0);ctx.moveTo(20,0);ctx.lineTo(16,0);ctx.moveTo(0,-20);ctx.lineTo(0,-16);ctx.moveTo(0,20);ctx.lineTo(0,16);ctx.stroke();ctx.restore();}
}

function resize(){
  viewW=innerWidth;viewH=innerHeight;dpr=Math.min(devicePixelRatio||1,1.6);canvas.width=Math.round(viewW*dpr);canvas.height=Math.round(viewH*dpr);
  // Fill the screen; steer within the visible water even on a portrait preview.
  scale=Math.max(viewW/W,viewH/H);offsetX=(viewW-W*scale)/2;offsetY=(viewH-H*scale)/2;
  bounds={left:-offsetX/scale,right:(viewW-offsetX)/scale,top:-offsetY/scale,bottom:(viewH-offsetY)/scale};
  for(const f of fish){f.x=clamp(f.x,bounds.left+60,bounds.right-60);f.y=clamp(f.y,bounds.top+60,bounds.bottom-60);}
}
const worldPoint=e=>({x:clamp((e.clientX-offsetX)/scale,25,W-25),y:clamp((e.clientY-offsetY)/scale,25,H-25)});
let press=null,longTimer;
canvas.addEventListener('pointerdown',e=>{const p=worldPoint(e);canvas.setPointerCapture(e.pointerId);press={...p,id:e.pointerId,long:false};longTimer=setTimeout(()=>{if(press){press.long=true;waterRipple(p.x,p.y,1.5);scatter(p.x,p.y);toast('一圈涟漪，惊起游鱼');}},500);});
canvas.addEventListener('pointerup',e=>{clearTimeout(longTimer);if(press&&press.id===e.pointerId&&!press.long){const p=worldPoint(e);feedAt(p.x,p.y);}press=null;cursor.visible=false;});
canvas.addEventListener('pointercancel',()=>{clearTimeout(longTimer);press=null;});
function scatter(x,y){for(const f of fish){if(Math.hypot(f.x-x,f.y-y)<240){f.wander=Math.atan2(f.y-y,f.x-x);f.fear=1;}}}
document.querySelector('#feed').onclick=()=>feedAt(cursor.x,cursor.y);
document.querySelector('#ripple').onclick=()=>{waterRipple(cursor.x,cursor.y,1.5);scatter(cursor.x,cursor.y);};
const panel=document.querySelector('#settings'),toggle=document.querySelector('#settings-toggle');
function showSettings(show){panel.hidden=!show;toggle.setAttribute('aria-expanded',String(show));if(show)document.querySelector('#count').focus();else toggle.focus();}
toggle.onclick=()=>showSettings(panel.hidden);document.querySelector('#close-settings').onclick=()=>showSettings(false);
document.querySelector('#count').oninput=e=>setCount(Number(e.target.value));
document.querySelector('#speed').oninput=e=>{speed=Number(e.target.value);document.querySelector('#speed-output').value=speed<.8?'缓缓':speed>1.2?'轻快':'悠然';};
document.querySelector('#show-ui').onchange=e=>document.body.classList.toggle('quiet',!e.target.checked);
async function fullscreen(){try{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();}catch{toast('当前窗口不支持全屏，可在浏览器中打开');}}
document.querySelector('#fullscreen').onclick=fullscreen;
window.addEventListener('keydown',e=>{
  if(e.target.matches('input,button'))return;
  const moves={ArrowLeft:[-32,0],ArrowRight:[32,0],ArrowUp:[0,-32],ArrowDown:[0,32]};
  if(moves[e.key]){e.preventDefault();cursor.visible=true;cursor.x=clamp(cursor.x+moves[e.key][0],40,W-40);cursor.y=clamp(cursor.y+moves[e.key][1],40,H-40);}
  if(e.key==='Enter'||e.key===' '){e.preventDefault();feedAt(cursor.x,cursor.y);}
  if(e.key.toLowerCase()==='r'){waterRipple(cursor.x,cursor.y,1.5);scatter(cursor.x,cursor.y);}
  if(e.key.toLowerCase()==='h'){const quiet=document.body.classList.toggle('quiet');document.querySelector('#show-ui').checked=!quiet;}
  if(e.key.toLowerCase()==='f')fullscreen();
  if(e.key==='Escape'&&!panel.hidden)showSettings(false);
});
function clock(){const now=new Date();document.querySelector('#time').textContent=now.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});document.querySelector('#date').textContent=now.toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'long'});}
clock();setInterval(clock,15000);
window.addEventListener('resize',resize);
document.addEventListener('visibilitychange',()=>{previous=0;});
function frame(now){if(!document.hidden){const dt=previous?Math.min((now-previous)/1000,.05):0;previous=now;update(dt*speed);draw();}requestAnimationFrame(frame);}
buildPond();setCount(20);resize();requestAnimationFrame(frame);
// Read-only diagnostics for repeatable browser smoke checks.
window.pondDiagnostics=()=>({fish:fish.length,food:foods.length,eaten:totalEaten,ripples:ripples.length,time:simTime,feeding:fish.filter(f=>f.mode==='feed').length,positions:fish.map(f=>({x:f.x,y:f.y,angle:f.a})),finite:fish.every(f=>[f.x,f.y,f.a,f.v,f.depth].every(Number.isFinite))});
