package com.gentv.fishlauncher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.opengl.GLES20;
import java.util.concurrent.*;

/** On-device smoke test against real GLES rendering and native remote navigation. */
public final class LauncherSmoke extends Instrumentation {
    private MainActivity activity;
    private String failure;
    private boolean visualOnly,poseOnly,gridOnly,leafOnly;
    private interface WorldCheck { void run(PondWorld world); }
    private View findText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=findText(g.getChildAt(i),text);if(result!=null)return result;}}return null;}
    private PondSurface surface(View v){if(v instanceof PondSurface)return (PondSurface)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){PondSurface p=surface(g.getChildAt(i));if(p!=null)return p;}}return null;}
    private void capture(String name)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot unavailable");java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),name+".png");try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}image.recycle();}
    private void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    private void ui(Runnable task){runOnMainSync(task);waitForIdleSync();}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void checkFocusInside(String tag){View focused=activity.getCurrentFocus();while(focused!=null){if(tag.equals(focused.getTag()))return;focused=focused.getParent() instanceof View?(View)focused.getParent():null;}throw new AssertionError("Focus escaped "+tag+" overlay: "+activity.getCurrentFocus()+" touch="+activity.getWindow().getDecorView().isInTouchMode());}
    private void readWorld(PondSurface p,WorldCheck read)throws Exception{CountDownLatch done=new CountDownLatch(1);p.queueEvent(()->{try{read.run(p.renderer.world);}catch(Throwable error){failure=error.toString();}finally{done.countDown();}});check(done.await(5,TimeUnit.SECONDS),"GL thread not responding");check(failure==null,failure);}
    private void swimFishUnderBobber(PondSurface pond)throws Exception{
        readWorld(pond,w->{
            FishingSession f=pond.renderer.fishing;
            check(f.stage==FishingSession.Stage.WAITING,"Fishing must reach waiting");
            int count=w.fish.size();
            for(int trial=0;trial<32&&f.stage==FishingSession.Stage.WAITING;trial++){
                PondWorld.Fish fish=w.fish.get(0);
                fish.x=f.bobberX+70;fish.y=f.bobberY+32;f.update(0,w);
                fish.x=f.bobberX;f.update(0,w);
                if(f.stage==FishingSession.Stage.REELING){
                    check(w.fish.size()==count&&!w.fish.contains(fish),"Hooked fish replaced from pond edge");
                    check(f.result.skin==fish.skin,"Hooked fish keeps its appearance");
                }
            }
            check(f.stage==FishingSession.Stage.REELING,"A fish entered the bobber zone and hooked");
        });
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);leafOnly=arguments!=null&&"true".equals(arguments.getString("leaf"));gridOnly=arguments!=null&&"true".equals(arguments.getString("grid"));visualOnly=arguments!=null&&"true".equals(arguments.getString("visual"));poseOnly=arguments!=null&&"true".equals(arguments.getString("pose"));start();}
    @Override public void onStart(){
        Bundle report=new Bundle();
        try{
            if(!visualOnly&&!poseOnly&&!leafOnly&&!gridOnly)getTargetContext().getSharedPreferences("fishing-journal",0).edit().clear().commit();
            getTargetContext().getSharedPreferences("pond",0).edit().putInt("fishCount",20).commit();
            Intent intent=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),"com.gentv.fishlauncher.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(MainActivity)startActivitySync(intent);waitForIdleSync();PondSurface p=surface(activity.getWindow().getDecorView());check(p!=null,"Native surface missing");
            long deadline=SystemClock.uptimeMillis()+10000;while(p.renderer.measuredFps<1&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);check(p.renderer.measuredFps>1,"Renderer never drew frames");
            check(getTargetContext().getSharedPreferences("pond",0).getInt("quality",0)==720,"Optimized default resolution");
            if(leafOnly){
                readWorld(p,w->{
                    w.leaves.clear();PondWorld.Leaf leaf=new PondWorld.Leaf();
                    leaf.x=800;leaf.y=420;leaf.size=13;leaf.age=1;leaf.amber=true;
                    w.leaves.add(leaf);
                });
                SystemClock.sleep(200);capture("maple-water");
                android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
                int red=0;for(int y=(int)(image.getHeight()*.45);y<image.getHeight()*.48;y++)for(int x=(int)(image.getWidth()*.49);x<image.getWidth()*.51;x++){
                    int pixel=image.getPixel(x,y);
                    if(android.graphics.Color.red(pixel)-android.graphics.Color.green(pixel)>80)red++;
                }
                image.recycle();check(red>50,"Maple silhouette should render on the water");
                readWorld(p,w->check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"Leaf GLES error"));
                report.putString("stream","PASS: red maple leaf rendered on the water, GLES clean.\n");finish(Activity.RESULT_OK,report);return;
            }
            if(gridOnly){
                key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);
                View root=activity.getWindow().getDecorView();
                View first=root.findViewWithTag("dock-slot-0");
                View all=root.findViewWithTag("dock-all-apps");
                check(first!=null&&root.findViewWithTag("dock-slot-4")!=null&&root.findViewWithTag("dock-slot-5")==null&&all!=null,"Five editable positions and All Apps");
                ui(()->first.requestFocus());key(KeyEvent.KEYCODE_DPAD_DOWN);
                ui(()->check(activity.getCurrentFocus()==first,"Dock focus must not move down"));
                for(int i=0;i<5;i++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
                ui(()->{
                    check(activity.getCurrentFocus()==all,"All Apps is sixth");
                    check(((TextView)root.findViewWithTag("selected-app-name")).getText().toString().equals("全部应用"),"Shared focus name");
                });
                capture("dock-single-row");key(KeyEvent.KEYCODE_DPAD_CENTER);
                ui(()->{
                    checkFocusInside("app-library");
                    View firstCell=root.findViewWithTag("library-cell-0");
                    check(firstCell instanceof ViewGroup&&((ViewGroup)firstCell).getChildAt(0) instanceof ImageView&&((ViewGroup)firstCell).getChildAt(1) instanceof TextView,"Icon above name");
                    check(((ViewGroup)firstCell).getChildAt(0).getClipToOutline(),"Library icons are rounded");
                });
                SystemClock.sleep(230);capture("dock-all-apps");
                for(int i=0;i<5;i++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
                ui(()->check("library-cell-5".equals(activity.getCurrentFocus().getTag()),"Six columns in first row"));
                key(KeyEvent.KEYCODE_DPAD_RIGHT);
                ui(()->check("library-cell-5".equals(activity.getCurrentFocus().getTag()),"No row wrap"));
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                ui(()->check("library-cell-11".equals(activity.getCurrentFocus().getTag()),"Down keeps the column"));
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                ui(()->check(((TextView)root.findViewWithTag("app-library-page")).getText().toString().startsWith("2 /"),"Down at the second row turns the page"));
                SystemClock.sleep(230);capture("dock-all-apps-page-two");
                key(KeyEvent.KEYCODE_DPAD_UP);
                ui(()->check(((TextView)root.findViewWithTag("app-library-page")).getText().toString().startsWith("1 /"),"Up from the top row returns to the previous page"));
                key(KeyEvent.KEYCODE_BACK);ui(()->check(activity.getCurrentFocus()==all,"Back restores All Apps focus"));
                ui(()->{first.requestFocus();check(first.performLongClick(),"Pinned slot supports replacement");checkFocusInside("app-library");});
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                String selected=activity.getCurrentFocus().getContentDescription().toString();
                key(KeyEvent.KEYCODE_DPAD_CENTER);
                ui(()->{View replacement=root.findViewWithTag("dock-slot-0");check(selected.contentEquals(replacement.getContentDescription()),"Replace the selected slot");check(activity.getCurrentFocus()==replacement,"Replacement keeps slot focus");});
                String[] order=getTargetContext().getSharedPreferences("pond",0).getString("dock-pinned","").split(",");
                check(order.length==5,"Five slots persisted in order");
                ui(()->{
                    try{
                        java.lang.reflect.Field appsField=MainActivity.class.getDeclaredField("apps"),recentField=MainActivity.class.getDeclaredField("recentApps");
                        appsField.setAccessible(true);recentField.setAccessible(true);
                        java.util.List<?> entries=(java.util.List<?>)appsField.get(activity);
                        java.lang.reflect.Field pkg=entries.get(0).getClass().getDeclaredField("pkg");pkg.setAccessible(true);
                        java.util.List<String> recents=(java.util.List<String>)recentField.get(activity);
                        recents.clear();recents.add(order[0]);recents.add(order[1]);
                        java.util.List<String> unpinned=new java.util.ArrayList<>();
                        for(Object entry:entries){String name=(String)pkg.get(entry);if(!java.util.Arrays.asList(order).contains(name))unpinned.add(name);}
                        recents.addAll(unpinned.subList(0,3));
                        java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refreshDock");refresh.setAccessible(true);refresh.invoke(activity);
                        check(root.findViewWithTag("dock-recent-"+order[0])==null,"Pinned apps do not appear in recents");
                        check(root.findViewWithTag("dock-recent-"+unpinned.get(0))!=null&&root.findViewWithTag("dock-recent-"+unpinned.get(1))!=null&&root.findViewWithTag("dock-recent-"+unpinned.get(2))==null,"Two latest unpinned apps");
                    }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
                });
                SystemClock.sleep(350);
                ui(()->{View dock=root.findViewWithTag("app-dock");check(Math.abs(dock.getLeft()+dock.getRight()-root.getWidth())<=2,"Dock is centered with recents");});
                capture("dock-centered-recents");
                report.putString("stream","PASS: single row, down blocked, All Apps popup, focus restoration and persistent slot replacement, centered dock and deduplicated recents.\n");
                finish(Activity.RESULT_OK,report);return;
            }
            if(poseOnly){
                ui(()->activity.getWindow().getDecorView().findViewWithTag("fisher-fish").performClick());SystemClock.sleep(3000);
                swimFishUnderBobber(p);SystemClock.sleep(2050);
                capture("fishing-head-up-open");readWorld(p,w->{check(p.renderer.fishing.stage==FishingSession.Stage.LANDED,"Capture landed pose");check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"Fishing pose GLES error");});report.putString("stream","PASS: landed pose captured with no overlay and GLES clean.\n");finish(Activity.RESULT_OK,report);return;
            }
            if(visualOnly){
                readWorld(p,w->{w.setCount(0);w.ripples.clear();w.turtle.x=1400;w.turtle.y=800;w.time=0;});SystemClock.sleep(300);
                android.graphics.Bitmap a=getUiAutomation().takeScreenshot();capture("fresh-water-a");
                readWorld(p,w->w.time=1.3f);SystemClock.sleep(300);android.graphics.Bitmap b=getUiAutomation().takeScreenshot();capture("fresh-water-b");
                check(a!=null&&b!=null,"Visual probe screenshots");int changing=0,samples=0,dryChanges=0;
                for(int y=(int)(a.getHeight()*.34);y<a.getHeight()*.48;y+=3)for(int x=(int)(a.getWidth()*.52);x<a.getWidth()*.68;x+=3){int ca=a.getPixel(x,y),cb=b.getPixel(x,y);int delta=Math.abs(android.graphics.Color.red(ca)-android.graphics.Color.red(cb))+Math.abs(android.graphics.Color.green(ca)-android.graphics.Color.green(cb))+Math.abs(android.graphics.Color.blue(ca)-android.graphics.Color.blue(cb));if(delta>4)changing++;samples++;}
                for(int y=(int)(a.getHeight()*.82);y<a.getHeight()*.88;y+=3)for(int x=(int)(a.getWidth()*.08);x<a.getWidth()*.12;x+=3)if(a.getPixel(x,y)!=b.getPixel(x,y))dryChanges++;
                a.recycle();b.recycle();check(changing>samples*.03,"Water must show moving light/refraction");check(dryChanges==0,"Dry terrace must remain stationary");readWorld(p,w->{check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"Visual GLES error");w.setCount(20);});
                report.putString("stream","PASS: animated water changed "+changing+"/"+samples+" pixels; dry terrace changed "+dryChanges+" pixels; GLES clean.\n");finish(Activity.RESULT_OK,report);return;
            }
            for(int n:new int[]{0,1,2,3,4,12}){final int count=n;readWorld(p,w->{w.ripples.clear();for(int i=0;i<count;i++)w.ripple(500+i*20,400,.7f);});SystemClock.sleep(150);readWorld(p,w->check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"GL error for "+count+" ripples"));}
            capture("fishing-home");
            final PondWorld.Turtle[] resident=new PondWorld.Turtle[1];final float[] turtleStart=new float[2];
            readWorld(p,w->{check(w.turtle!=null&&w.turtle.pose!=null,"Resident turtle missing");resident[0]=w.turtle;turtleStart[0]=w.turtle.x;turtleStart[1]=w.turtle.y;for(float v:w.turtle.pose.vertices)check(!Float.isNaN(v)&&!Float.isInfinite(v),"Invalid rendered turtle mesh");});
            key(KeyEvent.KEYCODE_DPAD_DOWN); // Leave touch mode before testing D-pad focus.
            ui(()->activity.getWindow().getDecorView().findViewWithTag("dock-slot-0").requestFocus());
            key(KeyEvent.KEYCODE_DPAD_LEFT);ui(()->checkFocusInside("fisher-actions"));readWorld(p,w->check(!w.cursorVisible,"Desktop must not enter a water cursor mode"));
            for(int code:new int[]{KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP}){key(code);ui(()->{View v=activity.getCurrentFocus();boolean allowed=false;while(v!=null){if("app-dock".equals(v.getTag())||"fisher-actions".equals(v.getTag()))allowed=true;v=v.getParent() instanceof View?(View)v.getParent():null;}check(allowed,"Desktop focus escaped bottom controls");});}
            ui(()->{android.view.MotionEvent tap=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_UP,700,350,0);check(!p.onTouchEvent(tap),"Water must not accept position selection");tap.recycle();});readWorld(p,w->check(w.food.isEmpty()&&!w.cursorVisible,"Water tap must not feed or show a cursor"));
            key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_MENU);check(p.renderer.activityFpsLimit==15,"Panel must reduce render rate");ui(()->{View more=activity.getWindow().getDecorView().findViewWithTag("多五尾");check(more!=null,"Settings did not open");more.requestFocus();});key(KeyEvent.KEYCODE_DPAD_CENTER);readWorld(p,w->check(w.fish.size()==25,"Fish count change not applied"));
            ui(()->{View less=activity.getWindow().getDecorView().findViewWithTag("少五尾");less.requestFocus();});key(KeyEvent.KEYCODE_DPAD_CENTER);readWorld(p,w->{check(w.fish.size()==20,"Fish count restore failed");check(w.turtle==resident[0],"Fish count must preserve single resident turtle");check(Math.hypot(w.turtle.x-turtleStart[0],w.turtle.y-turtleStart[1])>1,"Resident turtle did not move");});
            check(getTargetContext().getSharedPreferences("pond",0).getInt("fishCount",0)==20,"Settings not persisted");key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_MENU);for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_LEFT);for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_UP);ui(()->checkFocusInside("settings"));key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_BACK);ui(()->checkFocusInside("app-dock"));
            check(p.renderer.activityFpsLimit==60,"Closing panel restores interactive render budget");
            ui(()->{View feed=activity.getWindow().getDecorView().findViewWithTag("fisher-feed");check(feed!=null,"Feed button missing");feed.requestFocus();feed.performClick();});
            readWorld(p,w->check(!w.food.isEmpty(),"Random feed must scatter food"));
            ui(()->{View notice=activity.getWindow().getDecorView().findViewWithTag("left-notice");check(notice!=null&&!notice.isFocusable(),"Left notice must not take focus");check(((TextView)notice).getText().length()>0,"Notice text must appear");});
            SystemClock.sleep(5500);ui(()->check(activity.getWindow().getDecorView().findViewWithTag("left-notice").getAlpha()<.01f,"Notice hides after five seconds"));
            ui(()->activity.getWindow().getDecorView().findViewWithTag("fisher-fish").performClick());
            readWorld(p,w->check(p.renderer.fishing.stage!=FishingSession.Stage.DOZING,"Fishing button must wake elder"));
            SystemClock.sleep(3000);capture("fishing-awake");
            ui(()->activity.getWindow().getDecorView().findViewWithTag("fisher-achievements").performClick());
            SystemClock.sleep(250);ui(()->checkFocusInside("achievements"));
            swimFishUnderBobber(p);
            long catchDeadline=SystemClock.uptimeMillis()+10000;boolean landed=false,caughtFrame=false;
            while(!landed&&SystemClock.uptimeMillis()<catchDeadline){if(!caughtFrame&&p.renderer.fishing.stage==FishingSession.Stage.LANDED){capture("fishing-landed");caughtFrame=true;}landed=!new FishingJournal(getTargetContext()).entries().isEmpty();SystemClock.sleep(100);}
            check(landed,"Fishing must record catch without settlement modal");ui(()->{checkFocusInside("achievements");check(activity.getWindow().getDecorView().findViewWithTag("catch-dismiss")==null,"No catch modal");});ui(()->check(activity.getWindow().getDecorView().findViewWithTag("left-notice").getBackground()==null,"Catch notice must have no box"));capture("fishing-toast");key(KeyEvent.KEYCODE_BACK);
            FishingJournal journal=new FishingJournal(getTargetContext());check(journal.entries().size()==1,"Exactly one catch recorded");FishingSession.Catch caught=journal.entries().get(0).fish;check(journal.merit()>=1&&journal.merit()<=3,"Catch awards 1–3 merit");
            check(!journal.record(caught,System.currentTimeMillis()),"Duplicate catch must not award again");
            java.util.Calendar tomorrow=java.util.Calendar.getInstance();tomorrow.add(java.util.Calendar.DAY_OF_YEAR,1);journal.refresh(tomorrow.getTimeInMillis());check(journal.entries().isEmpty()&&journal.merit()==0,"Daily rollover clears today's view");journal.refresh(System.currentTimeMillis());check(journal.entries().size()==1,"Today's saved ledger reloads");
            readWorld(p,w->check(p.renderer.fishing.stage!=FishingSession.Stage.DOZING,"Fishing continues automatically after catch"));
            ui(()->activity.getWindow().getDecorView().findViewWithTag("fisher-achievements").performClick());ui(()->{check(findText(activity.getWindow().getDecorView(),caught.name)!=null,"Catch list must show fish name");check(activity.getWindow().getDecorView().findViewWithTag("today-merit")!=null,"Today's merit missing");checkFocusInside("achievements");});capture("fishing-achievements");key(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(16900);
            ui(()->{for(String tag:new String[]{"app-dock","fisher-actions","pond-header"}){
                View layer=activity.getWindow().getDecorView().findViewWithTag(tag);
                check(layer!=null&&Math.abs(layer.getAlpha()-.30f)<.025f,"Idle layer stays visible at 30%: "+tag);
            }});check(p.renderer.activityFpsLimit==20,"Idle desktop must use 20fps budget: "+p.renderer.activityFpsLimit);
            key(KeyEvent.KEYCODE_DPAD_DOWN);check(p.renderer.activityFpsLimit==60,"Remote wake restores interactive budget");
            check(!Float.isNaN(p.renderer.measuredFps)&&!Float.isInfinite(p.renderer.measuredFps),"Invalid FPS");report.putString("stream","PASS: native GLES, single resident turtle with valid animated mesh, D-pad focus containment, random feed, no water cursor or position picking, back navigation, settings persistence, modal focus containment, desktop grid, ripple shader variants, idle/panel render budgets, continuous fisherman cycle, nonmodal catch, persistent daily ledger, duplicate protection, day rollover and wake. FPS="+p.renderer.measuredFps+" GPU="+p.renderer.rendererName+"\n");finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("stream","FAIL: "+error);finish(Activity.RESULT_CANCELED,report);}
    }
}
