import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

// Run the actual steering/feeding code deterministically; rendering is tested in the browser.
const elements = new Map();
const context = new Proxy({}, {get:()=>()=>({addColorStop(){}}),set:()=>true});
function element(selector){if(!elements.has(selector))elements.set(selector,{textContent:'',value:20,checked:true,classList:{add(){},remove(){},toggle(){}},getContext:()=>context,addEventListener(){},setAttribute(){},focus(){},matches:()=>false});return elements.get(selector);}
const sandbox={console,Math,Date,Number,Float32Array,Infinity,innerWidth:1600,innerHeight:900,devicePixelRatio:1,setTimeout:()=>0,clearTimeout(){},setInterval(){},requestAnimationFrame(){},document:{querySelector:element,createElement:()=>({getContext:()=>context}),addEventListener(){},body:element('body')},addEventListener(){}};
sandbox.window=sandbox;
vm.createContext(sandbox);
let source=await readFile(new URL('../pond.js',import.meta.url),'utf8');
const poseSource=(await readFile(new URL('../koi-pose.js',import.meta.url),'utf8')).replace('export class FishSpine','class FishSpine');
source=source.replace("import { FishSpine } from './koi-pose.js';",poseSource);
source=source.replace('buildPond();setCount(20);resize();requestAnimationFrame(frame);','setCount(20);resize();');
vm.runInContext(source+'\nwindow.testAPI={update,feedAt,waterRipple,scatter,setCount,fish,foods,ripples};',sandbox);
const api=sandbox.testAPI;
const initial=sandbox.pondDiagnostics();assert.equal(initial.fish,20);assert.ok(initial.finite);
for(let i=0;i<600;i++)api.update(1/60);
const roaming=sandbox.pondDiagnostics();assert.ok(roaming.finite);assert.ok(roaming.positions.some((p,i)=>Math.hypot(p.x-initial.positions[i].x,p.y-initial.positions[i].y)>50));
const centerFish=api.fish[0];api.feedAt(centerFish.x+55,centerFish.y);assert.equal(api.foods.length,12);
for(let i=0;i<120;i++)api.update(1/60);
assert.ok(sandbox.pondDiagnostics().feeding>0,'Fish must respond to food');
for(let i=0;i<1500;i++)api.update(1/60);
assert.ok(sandbox.pondDiagnostics().eaten>0,'Fish must eat food, not just orbit it');
const f=api.fish[0];api.scatter(f.x,f.y);assert.ok(f.fear>0);api.update(1/60);assert.equal(f.mode,'flee');
api.setCount(40);assert.equal(api.fish.length,40);api.setCount(5);assert.equal(api.fish.length,5);
for(let i=0;i<100;i++){api.update(.7);api.feedAt(800,450);}assert.ok(api.foods.length<=96);assert.ok(api.ripples.length<=45);
for(let i=0;i<60*180;i++)api.update(1/60);
const final=sandbox.pondDiagnostics();assert.ok(final.finite);assert.equal(final.food,0);assert.ok(final.ripples<=5);assert.ok(final.positions.every(p=>p.x>=12&&p.x<=1588&&p.y>=12&&p.y<=888));
api.setCount(0);for(let i=0;i<300;i++)api.update(1/60);assert.equal(api.ripples.length,0);
console.log(`PASS: roaming, feeding (${final.eaten} pellets eaten), flee, count control, resource caps, cleanup and 180-second stability.`);
