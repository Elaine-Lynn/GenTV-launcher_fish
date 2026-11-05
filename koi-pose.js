// Browser counterpart of Android KoiPose: distance-following, length-constrained spine.
export class FishSpine {
  static segments = 24;
  constructor(f) {
    this.spacing=f.size/48;this.trailX=new Float64Array(96);this.trailY=new Float64Array(96);
    this.points=Array.from({length:25},()=>({x:0,y:0}));
    this.desired=Array.from({length:25},()=>({x:0,y:0}));
    this.reset(f);this.update(f);
  }
  static smooth(a,b,value){const t=Math.max(0,Math.min(1,(value-a)/(b-a)));return t*t*(3-2*t);}
  static angle(a){return Math.atan2(Math.sin(a),Math.cos(a));}
  reset(f){this.newest=0;for(let i=0;i<96;i++){const k=(96-i)%96;this.trailX[k]=f.x-Math.cos(f.a)*i*this.spacing;this.trailY[k]=f.y-Math.sin(f.a)*i*this.spacing;}}
  update(f){
    const length=f.size*(1-f.depth*.12),smooth=FishSpine.smooth,wrap=FishSpine.angle;
    let dx=f.x-this.trailX[this.newest],dy=f.y-this.trailY[this.newest],distance=Math.hypot(dx,dy);
    if(distance>f.size*2){this.reset(f);distance=0;}
    while(distance>=this.spacing){const x=this.trailX[this.newest]+dx/distance*this.spacing,y=this.trailY[this.newest]+dy/distance*this.spacing;this.newest=(this.newest+1)%96;this.trailX[this.newest]=x;this.trailY[this.newest]=y;dx=f.x-x;dy=f.y-y;distance=Math.hypot(dx,dy);}
    let fromX=f.x,fromY=f.y,toX=this.trailX[this.newest],toY=this.trailY[this.newest],walked=0,offset=0,span=Math.hypot(toX-fromX,toY-fromY);
    for(let i=0;i<=24;i++){
      const u=i/24,target=u*length;
      while(walked+span<target&&offset<95){walked+=span;fromX=toX;fromY=toY;offset++;const k=(this.newest-offset+96)%96;toX=this.trailX[k];toY=this.trailY[k];span=Math.hypot(toX-fromX,toY-fromY);}
      const mix=span>1e-8?Math.max(0,Math.min(1,(target-walked)/span)):0;
      const nx=span>1e-8?(toY-fromY)/span:-Math.sin(f.a),ny=span>1e-8?-(toX-fromX)/span:Math.cos(f.a);
      const envelope=smooth(.16,.94,u)**2,wave=Math.sin(u*6.1-f.phase)*length*(.003+.064*f.effort)*envelope;
      this.desired[i].x=fromX+(toX-fromX)*mix+nx*wave;this.desired[i].y=fromY+(toY-fromY)*mix+ny*wave;
    }
    this.points[0].x=f.x;this.points[0].y=f.y;
    const backwards=f.a+Math.PI,step=length/24;let previous=backwards;
    for(let i=1;i<=24;i++){
      const u=i/24,target=backwards+wrap(Math.atan2(this.desired[i].y-this.desired[i-1].y,this.desired[i].x-this.desired[i-1].x)-backwards)*smooth(.12,.34,u);
      const maxBend=(.055+.055*smooth(.25,.95,u))*32/24;
      const direction=previous+Math.max(-maxBend,Math.min(maxBend,wrap(target-previous)));
      this.points[i].x=this.points[i-1].x+Math.cos(direction)*step;this.points[i].y=this.points[i-1].y+Math.sin(direction)*step;previous=direction;
    }
  }
}
