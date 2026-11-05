package com.gentv.fishlauncher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small consistent native line icons, independent of emoji or icon-font support. */
public final class LauncherIcon extends View {
    public static final int APPS=0,WATER=1,SETTINGS=2,HOME=3,INFO=4,FEED=5,FISHING=6,ACHIEVEMENT=7,ADD=8;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    private int ink=0xffe3e7cf;
    public void setInk(int color){ink=color;invalidate();}
    public LauncherIcon(Context c,int kind){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        float s=Math.min(getWidth(),getHeight())/32f;canvas.save();canvas.translate((getWidth()-32*s)/2,(getHeight()-32*s)/2);canvas.scale(s,s);paint.setColor(ink);paint.setStrokeWidth(1.4f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        switch(kind){
            case ADD:paint.setStrokeWidth(2);canvas.drawLine(9,16,23,16,paint);canvas.drawLine(16,9,16,23,paint);break;
            case APPS:for(int y=0;y<2;y++)for(int x=0;x<2;x++)canvas.drawRoundRect(5+x*13,5+y*13,13+x*13,13+y*13,1.8f,1.8f,paint);break;
            case WATER:canvas.drawOval(3,15,29,27,paint);canvas.drawOval(10,19,22,23,paint);canvas.drawLine(16,3,16,15,paint);canvas.drawLine(12,11,16,15,paint);canvas.drawLine(20,11,16,15,paint);break;
            case SETTINGS:canvas.drawLine(4,10,28,10,paint);canvas.drawLine(4,23,28,23,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff294b42);canvas.drawCircle(11,10,4,paint);canvas.drawCircle(22,23,4,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(ink);canvas.drawCircle(11,10,4,paint);canvas.drawCircle(22,23,4,paint);break;
            case HOME:Path p=new Path();p.moveTo(4,14);p.lineTo(16,4);p.lineTo(28,14);p.moveTo(8,12);p.lineTo(8,28);p.lineTo(24,28);p.lineTo(24,12);p.moveTo(13,28);p.lineTo(13,20);p.lineTo(19,20);p.lineTo(19,28);canvas.drawPath(p,paint);break;
            case FEED: {
                paint.setStrokeWidth(2);
                Path wave=new Path();wave.moveTo(4,19);wave.quadTo(8,15,12,19);wave.quadTo(16,23,20,19);wave.quadTo(24,15,28,19);
                wave.moveTo(8,25);wave.quadTo(12,22,16,25);wave.quadTo(20,28,24,25);
                canvas.drawPath(wave,paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(10,10,1.65f,paint);canvas.drawCircle(16,6,1.8f,paint);canvas.drawCircle(22,12,1.6f,paint);
                break;
            }
            case FISHING: {
                paint.setStrokeWidth(2.2f);
                Path hook=new Path();hook.moveTo(18,8);hook.lineTo(18,21);
                hook.cubicTo(18,29,7,29,7,21);hook.lineTo(7,17);
                hook.moveTo(7,17);hook.lineTo(11,21);
                canvas.drawPath(hook,paint);canvas.drawCircle(18,5.8f,2.4f,paint);
                canvas.drawLine(23,9,27,12,paint);canvas.drawLine(25,5,28,5,paint);
                break;
            }
            case ACHIEVEMENT: {
                paint.setStrokeWidth(2);
                canvas.drawCircle(16,12,7.8f,paint);
                Path ribbons=new Path();ribbons.moveTo(10.5f,18);ribbons.lineTo(7.5f,27);ribbons.lineTo(12.3f,25.4f);ribbons.lineTo(15.4f,29);ribbons.lineTo(16,21);
                ribbons.moveTo(21.5f,18);ribbons.lineTo(24.5f,27);ribbons.lineTo(19.7f,25.4f);ribbons.lineTo(16.6f,29);
                canvas.drawPath(ribbons,paint);
                paint.setStyle(Paint.Style.FILL);Path star=new Path();
                for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?4:1.8f;float x=16+r*(float)Math.cos(a),y=12+r*(float)Math.sin(a);if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}
                star.close();canvas.drawPath(star,paint);break;
            }
            default:canvas.drawCircle(16,16,12,paint);canvas.drawLine(16,14,16,23,paint);canvas.drawPoint(16,9,paint);
        }canvas.restore();
    }
}
