import assert from 'node:assert/strict';
import { FishSpine } from '../koi-pose.js';
const fish=()=>({x:800,y:450,a:0,size:70,depth:.3,effort:.8,phase:0});
let f=fish(),pose=new FishSpine(f),maxHead=0,maxTail=0;
for(let i=0;i<120;i++){
  f.phase=i/120*Math.PI*2;pose.update(f);
  maxHead=Math.max(maxHead,Math.abs(pose.points[2].y-f.y));maxTail=Math.max(maxTail,Math.abs(pose.points[23].y-f.y));
  for(let j=1;j<25;j++)assert.ok(Math.abs(Math.hypot(pose.points[j].x-pose.points[j-1].x,pose.points[j].y-pose.points[j-1].y)-f.size*(1-f.depth*.12)/24)<1e-8,'Constant spine length');
}
assert.ok(maxHead<1e-8,'Rigid skull');assert.ok(maxTail>2,'Posterior propulsion');
const oldTail={...pose.points[24]};f.a=.12;f.x+=.4;f.y+=.05;pose.update(f);assert.ok(Math.hypot(pose.points[24].x-oldTail.x,pose.points[24].y-oldTail.y)<3,'Body should follow the turn, not pivot rigidly');
function trace(hz){const f=fish(),p=new FishSpine(f);f.effort=0;for(let i=1;i<=hz*10;i++){const t=i/hz;f.a=t*.12;f.x=800+Math.sin(t*.12)*180;f.y=450+(1-Math.cos(t*.12))*180;p.update(f);}return p.points[24];}
const a=trace(30),b=trace(60);assert.ok(Math.hypot(a.x-b.x,a.y-b.y)<.4,'30/60 Hz consistency');
console.log('PASS: browser rigid skull, tail-led propulsion, constant body length, turn following and 30/60 Hz consistency.');
