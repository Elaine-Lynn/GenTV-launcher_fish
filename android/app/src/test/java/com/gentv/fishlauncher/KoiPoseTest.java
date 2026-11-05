package com.gentv.fishlauncher;

public final class KoiPoseTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static PondWorld.Fish fish(){PondWorld.Fish f=new PondWorld.Fish();f.x=800;f.y=450;f.angle=0;f.length=96;f.depth=.3f;f.effort=.8f;f.pose=new KoiPose(f);return f;}
    private static void geometry(PondWorld.Fish f){
        KoiPose p=f.pose;
        for(int i=1;i<=KoiPose.SEGMENTS;i++)check(Math.abs(Math.hypot(p.x[i]-p.x[i-1],p.y[i]-p.y[i-1])-p.length/KoiPose.SEGMENTS)<.0002,"Spine must not stretch");
        for(int i=0;i<=KoiPose.SEGMENTS;i++){
            check(Math.abs(Math.hypot(p.normalX[i],p.normalY[i])-1)<.0001,"Unit normals");
            if(i>0&&i<KoiPose.SEGMENTS){float dx=p.x[i+1]-p.x[i-1],dy=p.y[i+1]-p.y[i-1];check(Math.abs(dx*p.normalX[i]+dy*p.normalY[i])<.0001,"Normal must match actual spine tangent");}
        }
        for(float v:p.vertices)check(Float.isFinite(v),"Finite mesh coordinates");
        for(int i=0;i<p.vertices.length;i+=4)check(p.vertices[i+2]>=0&&p.vertices[i+2]<=1&&p.vertices[i+3]>=0&&p.vertices[i+3]<=1,"UV bounds");
    }
    public static void main(String[] args){
        PondWorld.Fish f=fish();float headMax=0,tailMax=0;
        for(int i=0;i<180;i++){f.phase=i/180f*(float)Math.PI*2;f.pose.update(f);geometry(f);headMax=Math.max(headMax,Math.abs(f.pose.y[3]-f.y));tailMax=Math.max(tailMax,Math.abs(f.pose.y[31]-f.y));}
        check(headMax<.001,"Skull must stay rigid during propulsion");check(tailMax>2,"Tail must provide visible propulsion");
        f=fish();float startTailX=f.pose.x[32],startTailY=f.pose.y[32];f.angle=.12f;f.x+=.4f;f.y+=.05f;f.pose.update(f);
        check(Math.hypot(f.pose.x[32]-startTailX,f.pose.y[32]-startTailY)<3,"Turn must not rotate the whole body like a rigid sprite");
        for(int i=0;i<600;i++){float a=i*.004f;f.angle=a;f.x=800+(float)Math.sin(a)*110;f.y=450+(1-(float)Math.cos(a))*110;f.phase=i*.08f;f.finPhase=i*.04f;f.pose.update(f);geometry(f);}
        // Equal arc-length traces sampled at 30 / 60 Hz should produce equivalent body following.
        PondWorld.Fish a=fish(),b=fish();a.effort=b.effort=0;a.phase=b.phase=0;
        for(int i=1;i<=300;i++){float t=i/30f;a.angle=t*.12f;a.x=800+(float)Math.sin(t*.12f)*180;a.y=450+(1-(float)Math.cos(t*.12f))*180;a.pose.update(a);}
        for(int i=1;i<=600;i++){float t=i/60f;b.angle=t*.12f;b.x=800+(float)Math.sin(t*.12f)*180;b.y=450+(1-(float)Math.cos(t*.12f))*180;b.pose.update(b);}
        check(Math.hypot(a.pose.x[32]-b.pose.x[32],a.pose.y[32]-b.pose.y[32])<.4,"Pose must follow distance, not frame count");
        short[] indices=KoiPose.indices();check(indices.length==1536,"Grid triangle count");for(short index:indices)check(index>=0&&index<297,"Mesh index bounds");
        PondWorld world=new PondWorld(97);float minEffort=1,maxEffort=0;
        for(int i=0;i<900;i++){world.update(1/60f);PondWorld.Fish sample=world.fish.get(0);minEffort=Math.min(minEffort,sample.effort);maxEffort=Math.max(maxEffort,sample.effort);geometry(sample);}
        check(maxEffort-minEffort>.3,"Cruising must contain distinct stroke and glide periods");
        System.out.println("PASS: rigid skull, posterior propulsion, no rigid-body pivot, invariant spine length, matching normals, independent fin mesh, 30/60 Hz consistency and stroke/glide rhythms.");
    }
}
