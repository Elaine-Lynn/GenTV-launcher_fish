package com.gentv.fishlauncher;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.Choreographer;

public final class PondSurface extends GLSurfaceView {
    public final PondRenderer renderer;
    private boolean animating;
    private long nextRequestNs;
    private final Choreographer.FrameCallback frameCallback=new Choreographer.FrameCallback(){
        @Override public void doFrame(long timeNs){
            if(!animating)return;
            long interval=1_000_000_000L/Math.max(1,Math.min(renderer.targetFps,renderer.activityFpsLimit));
            if(nextRequestNs==0||timeNs+750_000L>=nextRequestNs){requestRender();nextRequestNs=nextRequestNs==0||timeNs-nextRequestNs>interval?timeNs+interval:nextRequestNs+interval;}
            else if(nextRequestNs-timeNs>interval)nextRequestNs=timeNs+interval;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };
    public PondSurface(Context context){
        super(context);setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,0,0,0);setPreserveEGLContextOnPause(true);
        renderer=new PondRenderer(context);setRenderer(renderer);setRenderMode(RENDERMODE_WHEN_DIRTY);setFocusable(false);setContentDescription("动态鱼池背景，使用下方喂鱼按钮随机投食");
    }
    @Override public void onResume(){super.onResume();if(!animating){animating=true;nextRequestNs=0;Choreographer.getInstance().postFrameCallback(frameCallback);}}
    @Override public void onPause(){animating=false;Choreographer.getInstance().removeFrameCallback(frameCallback);super.onPause();}
    @Override public boolean onTouchEvent(MotionEvent event){return false;}
    @Override protected void onDetachedFromWindow(){animating=false;Choreographer.getInstance().removeFrameCallback(frameCallback);super.onDetachedFromWindow();}
}
