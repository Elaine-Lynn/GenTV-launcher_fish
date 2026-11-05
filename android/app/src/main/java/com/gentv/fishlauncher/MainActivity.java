package com.gentv.fishlauncher;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int IVORY=0xffeef0dd,MUTED=0xffc4d1bc;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final ArrayList<AppEntry> apps=new ArrayList<>();
    private final HashSet<String> favorites=new HashSet<>();
    private FrameLayout root,overlay;
    private LinearLayout dock,header,fisherActions;
    private View feedAction,fishAction,achievementAction;
    private FishingJournal fishingJournal;
    private TextView time,date,status;
    private PondSurface pond;
    private SoundPool feedAudio;
    private int feedSound,feedStream;
    private boolean feedSoundReady;
    private SharedPreferences prefs;
    private View initialFocus;
    private TextView selectedAppName;
    private FrameLayout appViewport;
    private DockFocusFrame dockFocus;
    private static final int FOCUS_GUTTER_DP=12;
    private LinearLayout appRows;
    private final ArrayList<View> desktopTiles=new ArrayList<>();
    private static final int PINNED_SLOTS=5, RECENT_SLOTS=2, RECENT_HISTORY=24, DOCK_ICON_DP=72;
    private final ArrayList<String> pinnedApps=new ArrayList<>(),recentApps=new ArrayList<>();
    private Object returnDockFocus;
    private boolean asleep,resumed,receiverRegistered;
    private int fishCount,fps,quality;
    private float tempo;
    private long lastInput;
    private int wakeKey=-1;
    private final Runnable tick=new Runnable(){public void run(){if(!resumed)return;updateClock();if(overlay==null&&SystemClock.uptimeMillis()-lastInput>=15000)setAsleep(true);handler.postDelayed(this,1000);}};
    private final BroadcastReceiver packageReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){loadApps();}};
    private static final class AppEntry {String label,pkg;ComponentName component;Bitmap icon;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);fishingJournal=new FishingJournal(this);prefs=getSharedPreferences("pond",MODE_PRIVATE);
        pinnedApps.addAll(readOrder("dock-pinned"));recentApps.addAll(readOrder("dock-recent"));
        while(pinnedApps.size()>PINNED_SLOTS)pinnedApps.remove(pinnedApps.size()-1);
        if(!prefs.getBoolean("resourceProfile13",false)){prefs.edit().putInt("quality",720).putInt("fps",30).putBoolean("resourceProfile13",true).apply();}
        fishCount=prefs.getInt("fishCount",20);tempo=prefs.getFloat("tempo",1);fps=prefs.getInt("fps",30);quality=prefs.getInt("quality",720);
        favorites.addAll(prefs.getStringSet("favorites",Collections.emptySet()));
        immersive();root=new FrameLayout(this);root.setBackgroundColor(0xff183d38);setContentView(root);
        pond=new PondSurface(this);root.addView(pond,new FrameLayout.LayoutParams(-1,-1));pond.renderer.targetFps=fps;
        pond.queueEvent(()->{pond.renderer.world.setCount(fishCount);pond.renderer.world.tempo=tempo;});applyResolution();
        addShade();buildHeader();buildDock();buildStatus();buildFisherActions();updateClock();
        feedAudio=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        feedAudio.setOnLoadCompleteListener((pool,id,status)->{if(pool==feedAudio&&id==feedSound)feedSoundReady=status==0;});
        feedSound=feedAudio.load(this,R.raw.feed_plop,1);
        pond.renderer.feedImpactListener=()->handler.post(()->{
            if(resumed&&feedAudio!=null&&feedSoundReady)feedStream=feedAudio.play(feedSound,.55f,.55f,1,0,1);
        });
        pond.renderer.catchListener=fish->handler.post(()->{if(isDestroyed()||fish==null)return;if(fishingJournal.record(fish,System.currentTimeMillis())&&resumed)message(fish.name+" · "+fishWeight(fish.grams)+" · 功德 +"+fish.merit,true);});
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageReceiver,filter,Context.RECEIVER_EXPORTED);else registerReceiver(packageReceiver,filter);receiverRegistered=true;
        loadApps();lastInput=SystemClock.uptimeMillis();
    }
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);return t;}
    private GradientDrawable background(int color,float radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private void addShade(){View top=new View(this);top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x200a2422,0x000a2422}));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(155),Gravity.TOP);root.addView(top,tp);View bottom=new View(this);bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0x550b2925,0x180b2925,0x000b2925}));root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(210),Gravity.BOTTOM));}
    private void buildHeader(){
        header=new LinearLayout(this);header.setTag("pond-header");header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,dp(100),Gravity.TOP);hp.setMargins(dp(43),dp(15),dp(43),0);root.addView(header,hp);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);TextView name=text("GenTV",25,0xff163f36);name.setTypeface(Typeface.create("serif",0));brand.addView(name);TextView sub=text("静 水  ·  自 在",9,0xff163f36);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(8);brand.addView(sub,sp);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout clock=new LinearLayout(this);clock.setOrientation(1);clock.setGravity(Gravity.CENTER);time=text("",45,Color.WHITE);time.setShadowLayer(dp(4),0,dp(1),0xc0000000);time.setGravity(Gravity.CENTER);time.setTypeface(Typeface.create("sans-serif-thin",0));clock.addView(time);date=text("",12,Color.WHITE);date.setShadowLayer(dp(3),0,dp(1),0xc0000000);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-2,-2);dl.topMargin=dp(4);clock.addView(date,dl);header.addView(clock,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(new View(this),new LinearLayout.LayoutParams(0,-2,1));header.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private List<String> readOrder(String key){
        String value=prefs.getString(key,"");
        return value.isEmpty()?Collections.emptyList():Arrays.asList(value.split(",",-1));
    }
    private void saveOrder(String key,List<String> values){prefs.edit().putString(key,android.text.TextUtils.join(",",values)).apply();}
    private void buildDock(){
        dock=new LinearLayout(this);dock.setTag("app-dock");dock.setOrientation(1);
        dock.setClipChildren(false);dock.setClipToPadding(false);
        selectedAppName=text("",36,Color.WHITE);selectedAppName.setTag("selected-app-name");
        selectedAppName.setShadowLayer(dp(3),0,dp(1),0xe018251f);
        selectedAppName.setSingleLine(true);selectedAppName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        selectedAppName.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        dock.addView(selectedAppName,new LinearLayout.LayoutParams(-1,dp(52)));
        appViewport=new FrameLayout(this);appViewport.setTag("app-viewport");appViewport.setClipChildren(false);appViewport.setClipToPadding(false);
        dock.addView(appViewport,new LinearLayout.LayoutParams(-1,dp(86+FOCUS_GUTTER_DP)));
        appRows=new LinearLayout(this);appRows.setOrientation(0);appRows.setGravity(Gravity.BOTTOM|Gravity.START);
        appRows.setClipChildren(false);appRows.setClipToPadding(false);
        FrameLayout.LayoutParams rowsLayout=new FrameLayout.LayoutParams(-1,-1);rowsLayout.bottomMargin=dp(FOCUS_GUTTER_DP);
        appRows.setPadding(dp(FOCUS_GUTTER_DP),0,dp(FOCUS_GUTTER_DP),0);
        appViewport.addView(appRows,rowsLayout);
        selectedAppName.setPadding(dp(FOCUS_GUTTER_DP),0,dp(FOCUS_GUTTER_DP),0);
        dockFocus=new DockFocusFrame();
        appViewport.addView(dockFocus,new FrameLayout.LayoutParams(0,0));
        appRows.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateDockFocus(false));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(810),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin=dp(45);root.addView(dock,lp);
    }
    private AppEntry appFor(String pkg){for(AppEntry app:apps)if(app.pkg.equals(pkg))return app;return null;}
    private void fillPinnedDefaults(){
        if(apps.isEmpty()||prefs.contains("dock-pinned"))return;
        for(AppEntry a:apps)if(favorites.contains(a.pkg)&&pinnedApps.size()<PINNED_SLOTS)pinnedApps.add(a.pkg);
        for(AppEntry a:apps)if(pinnedApps.size()<PINNED_SLOTS&&!pinnedApps.contains(a.pkg))pinnedApps.add(a.pkg);
        saveOrder("dock-pinned",pinnedApps);
    }
    private void refreshDock(){
        if(dock==null)return;
        boolean restore=dock.hasFocus();Object previous=getCurrentFocus()==null?null:getCurrentFocus().getTag();
        appRows.removeAllViews();desktopTiles.clear();fillPinnedDefaults();
        for(int slot=0;slot<PINNED_SLOTS;slot++){
            final int index=slot;
            AppEntry app=slot<pinnedApps.size()?appFor(pinnedApps.get(slot)):null;
            addDockTile(app,app==null?"添加应用":app.label,"dock-slot-"+slot,LauncherIcon.ADD,
                ()->{if(app==null)showAppLibrary(index);else launch(app);},()->showAppLibrary(index));
        }
        addDockTile(null,"全部应用","dock-all-apps",LauncherIcon.APPS,()->showAppLibrary(-1),null);
        View divider=new View(this);divider.setTag("dock-divider");divider.setBackgroundColor(0x90ffffff);
        appRows.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(38)));
        Set<String> visiblePackages=new HashSet<>(pinnedApps);
        for(String pkg:recentApps){
            if(!visiblePackages.add(pkg))continue;
            AppEntry app=appFor(pkg);
            if(app!=null)addDockTile(app,app.label,"dock-recent-"+pkg,0,()->launch(app),null);
            if(desktopTiles.size()>=PINNED_SLOTS+1+RECENT_SLOTS)break;
        }
        initialFocus=desktopTiles.get(0);selectedAppName.setText("");linkFishingFocus();sizeDock();
        View target=previous==null?null:dock.findViewWithTag(previous);
        if((restore||getCurrentFocus()==null)&&overlay==null)(target==null?initialFocus:target).requestFocus();
    }
    private void addDockTile(AppEntry app,String name,String tag,int glyph,Runnable click,Runnable edit){
        FrameLayout tile=new FrameLayout(this);
        tile.setId(View.generateViewId());tile.setTag(tag);tile.setFocusable(true);tile.setContentDescription(name);
        tile.setClipChildren(false);tile.setClipToPadding(false);
        View art;
        if(app!=null)art=roundedAppIcon(app);
        else{LauncherIcon icon=new LauncherIcon(this,glyph);icon.setInk(Color.WHITE);art=icon;tile.setBackground(dockTileBackground(dp(DOCK_ICON_DP),false));}
        int size=app!=null?-1:dp(34);
        tile.addView(art,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
        if(app!=null)art.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->v.setPivotY(b-t));
        tile.setOnClickListener(v->click.run());
        if(edit!=null)tile.setOnLongClickListener(v->{edit.run();return true;});
        tile.setOnFocusChangeListener((v,focused)->{
            art.animate().scaleX(focused?1.04f:1f).scaleY(focused?1.04f:1f).setDuration(155).start();
            tile.setTranslationZ(focused?dp(4):0);
            if(app==null)tile.setBackground(dockTileBackground(tile.getLayoutParams().width,focused));
            selectedAppName.setText(focused?name:"");
            if(focused){updateDockFocus(true);if(!asleep)activity();}
            else appRows.post(()->{if(!desktopTiles.contains(getCurrentFocus()))updateDockFocus(false);});
        });
        desktopTiles.add(tile);appRows.addView(tile,new LinearLayout.LayoutParams(dp(DOCK_ICON_DP),dp(DOCK_ICON_DP)));
    }
    private void sizeDock(){
        if(root.getWidth()==0)return;
        int count=desktopTiles.size();
        boolean hasRecents=count>PINNED_SLOTS+1;
        int available=Math.round(root.getWidth()*.63f);
        int gap=Math.max(dp(3),(Math.round(root.getWidth()*.49f/6)-dp(48))/3);
        int dividerLeft=gap/2,dividerRight=gap+gap/2;
        int spacing=gap*Math.max(0,count-1)+(hasRecents?dp(1)+dividerLeft+dividerRight:0);
        int icon=Math.min(dp(DOCK_ICON_DP),Math.max(1,(available-spacing)/Math.max(1,count)));
        FrameLayout.LayoutParams d=(FrameLayout.LayoutParams)dock.getLayoutParams();
        d.width=icon*count+spacing+2*dp(FOCUS_GUTTER_DP);d.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        d.leftMargin=0;dock.setLayoutParams(d);
        for(int i=0;i<count;i++){
            View tile=desktopTiles.get(i);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)tile.getLayoutParams();
            lp.width=icon;lp.height=icon;lp.rightMargin=i<count-1?gap:0;tile.setLayoutParams(lp);
            if(((ViewGroup)tile).getChildAt(0) instanceof LauncherIcon)tile.setBackground(dockTileBackground(icon,tile.isFocused()));
        }
        appRows.post(()->updateDockFocus(false));
        View line=appRows.findViewWithTag("dock-divider");
        if(line!=null){
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)line.getLayoutParams();
            lp.leftMargin=dividerLeft;lp.rightMargin=dividerRight;line.setLayoutParams(lp);
            line.setVisibility(hasRecents?View.VISIBLE:View.GONE);
        }
    }
    private void updateDockFocus(boolean animated){
        View tile=getCurrentFocus();
        if(!desktopTiles.contains(tile)){
            dockFocus.animate().cancel();dockFocus.setAlpha(0);return;
        }
        if(tile.getWidth()==0){tile.post(()->{if(tile.isFocused()&&tile.getWidth()>0)updateDockFocus(false);});return;}
        int gutter=dp(FOCUS_GUTTER_DP),extent=tile.getWidth()+2*gutter;
        FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)dockFocus.getLayoutParams();
        if(bounds.width!=extent){bounds.width=extent;bounds.height=extent;dockFocus.setLayoutParams(bounds);}
        float x=appRows.getLeft()+tile.getLeft()-gutter,y=appRows.getTop()+tile.getTop()-gutter;
        boolean visible=dockFocus.getAlpha()>0;
        dockFocus.animate().cancel();
        if(animated&&visible) dockFocus.animate().translationX(x).translationY(y).alpha(1)
            .setDuration(255).setInterpolator(new android.view.animation.PathInterpolator(.22f,.65f,.22f,1f)).start();
        else{dockFocus.setTranslationX(x);dockFocus.setTranslationY(y);dockFocus.setAlpha(1);}
    }
    private final class DockFocusFrame extends View {
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF outline=new RectF();
        DockFocusFrame(){super(MainActivity.this);setTag("dock-focus-frame");setAlpha(0);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            float gutter=dp(FOCUS_GUTTER_DP),side=getWidth()-2*gutter,space=dp(3);
            outline.set(gutter-space,gutter-space,gutter+side+space,gutter+side+space);
            float corner=side*.20f+space;
            ink.setStyle(Paint.Style.STROKE);
            for(int ring=6;ring>=1;ring--){
                ink.setColor(Color.argb(5+(6-ring)*4,255,255,255));ink.setStrokeWidth(dp(ring+1));
                canvas.drawRoundRect(outline,corner,corner,ink);
            }
            ink.setColor(0xf5ffffff);ink.setStrokeWidth(dp(2));
            canvas.drawRoundRect(outline,corner,corner,ink);
        }
    }
    private GradientDrawable dockTileBackground(int size,boolean focused){
        GradientDrawable tile=actionDisc(focused);
        tile.setShape(GradientDrawable.RECTANGLE);tile.setCornerRadius(size*.20f);
        return tile;
    }
    private void alignDockBottom(){
        if(achievementAction==null||achievementAction.getHeight()==0||dock==null)return;
        View circle=((ViewGroup)achievementAction).getChildAt(0);
        int bottom=circle.getBottom();
        for(View parent=achievementAction;parent!=root;parent=(View)parent.getParent())bottom+=parent.getTop();
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)dock.getLayoutParams();
        int margin=Math.max(0,root.getHeight()-bottom-dp(FOCUS_GUTTER_DP));
        if(lp.bottomMargin!=margin){lp.bottomMargin=margin;dock.setLayoutParams(lp);}
    }
    private ImageView roundedAppIcon(AppEntry app){
        ImageView icon=new ImageView(this);icon.setImageBitmap(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,Outline outline){
                outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),
                    Math.min(view.getWidth(),view.getHeight())*.20f);
            }
        });
        icon.setClipToOutline(true);return icon;
    }
    private void showAppLibrary(int slot){
        View current=getCurrentFocus();Object opener=current==null?null:current.getTag();
        LinearLayout panel=openOverlay(slot<0?"全部应用":"替换第 "+(slot+1)+" 个应用",
            slot<0?"方向键选择 · 上下翻页 · 确认打开":"方向键选择 · 上下翻页 · 确认替换",false);
        overlay.setTag("app-library");returnDockFocus=opener;
        overlay.setBackgroundColor(0xa60d1110);
        GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xfb29312f,0xfc161d1b});
        surface.setCornerRadius(dp(28));surface.setStroke(dp(1),0x42ffffff);panel.setBackground(surface);
        int panelWidth=Math.min(dp(940),Math.round(root.getWidth()*.86f));
        int panelHeight=Math.min(dp(486),Math.round(root.getHeight()*.86f));
        FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)panel.getLayoutParams();
        bounds.width=panelWidth;bounds.height=panelHeight;panel.setLayoutParams(bounds);
        panel.setPadding(dp(30),dp(23),dp(30),dp(18));panel.setElevation(dp(12));
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(1);grid.setClipChildren(false);grid.setClipToPadding(false);
        panel.setClipChildren(false);panel.setClipToPadding(false);panel.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        TextView pageLabel=text("",12,0xffd7dfdc);pageLabel.setTag("app-library-page");pageLabel.setGravity(Gravity.CENTER);
        panel.addView(pageLabel,new LinearLayout.LayoutParams(-1,dp(25)));
        View close=((ViewGroup)panel.getChildAt(0)).getChildAt(1);
        new AppLibraryPages(new ArrayList<>(apps),grid,pageLabel,close,slot,panelWidth,panelHeight).show(0,0);
    }
    private final class AppLibraryPages {
        static final int COLUMNS=6,ROWS=2,PAGE_SIZE=COLUMNS*ROWS;
        final List<AppEntry> entries;
        final LinearLayout grid;
        final TextView pageLabel;
        final View close;
        final int slot,iconSize,pages;
        final ArrayList<View> cells=new ArrayList<>();
        int page,lastCell;
        AppLibraryPages(List<AppEntry> entries,LinearLayout grid,TextView pageLabel,View close,int slot,int width,int height){
            this.entries=entries;this.grid=grid;this.pageLabel=pageLabel;this.close=close;this.slot=slot;
            pages=Math.max(1,(entries.size()+PAGE_SIZE-1)/PAGE_SIZE);
            iconSize=Math.max(dp(24),Math.min(dp(94),Math.min((width-dp(60))/COLUMNS-dp(16),(height-dp(160))/ROWS-dp(26))));
            close.setOnKeyListener((v,key,e)->{
                if(key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
                if(e.getAction()==KeyEvent.ACTION_DOWN&&!cells.isEmpty())cells.get(lastCell).requestFocus();
                return true;
            });
        }
        void show(int page,int focus){
            this.page=page;grid.removeAllViews();cells.clear();
            pageLabel.setText((page+1)+" / "+pages+"  页");
            int start=page*PAGE_SIZE,count=Math.min(PAGE_SIZE,entries.size()-start);
            if(count==0){
                TextView empty=text("暂无可打开的应用",17,MUTED);empty.setGravity(Gravity.CENTER);
                grid.addView(empty,new LinearLayout.LayoutParams(-1,-1));close.requestFocus();return;
            }
            for(int row=0;row<ROWS;row++){
                LinearLayout line=new LinearLayout(MainActivity.this);line.setClipChildren(false);
                grid.addView(line,new LinearLayout.LayoutParams(-1,0,1));
                for(int col=0;col<COLUMNS;col++){
                    int index=row*COLUMNS+col;
                    View cell=index<count?cell(entries.get(start+index),index):new View(MainActivity.this);
                    line.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
                    if(index<count)cells.add(cell);
                }
            }
            cells.get(Math.min(focus,count-1)).requestFocus();
            grid.animate().cancel();grid.setAlpha(.78f);grid.animate().alpha(1f).setDuration(145).start();
        }
        View cell(AppEntry app,int index){
            LinearLayout cell=new LinearLayout(MainActivity.this);cell.setOrientation(1);
            cell.setTag("library-cell-"+index);cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(5),dp(3),dp(5),dp(3));cell.setClipChildren(false);cell.setClipToPadding(false);
            ImageView icon=roundedAppIcon(app);
            cell.addView(icon,new LinearLayout.LayoutParams(iconSize,iconSize));
            TextView name=text(app.label,13,0xffd6dfdb);name.setGravity(Gravity.CENTER);
            name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams caption=new LinearLayout.LayoutParams(-1,dp(22));caption.topMargin=dp(8);
            cell.addView(name,caption);cell.setFocusable(true);cell.setContentDescription(app.label);
            cell.setOnFocusChangeListener((v,focused)->{
                if(focused)lastCell=index;
                cell.animate().scaleX(focused?1.08f:1f).scaleY(focused?1.08f:1f).setDuration(155).start();
                cell.setTranslationZ(focused?dp(5):0);
                cell.setBackground(focused?background(0x2cffffff,16,0xccffffff):null);
                name.setTextColor(focused?Color.WHITE:0xffd6dfdb);
            });
            cell.setOnKeyListener((v,key,e)->navigate(index,key,e));
            cell.setOnClickListener(v->{
                if(slot<0){closeOverlay(true);launch(app);return;}
                while(pinnedApps.size()<PINNED_SLOTS)pinnedApps.add("");
                int old=pinnedApps.indexOf(app.pkg);
                if(old>=0&&old!=slot)pinnedApps.set(old,pinnedApps.get(slot));
                pinnedApps.set(slot,app.pkg);saveOrder("dock-pinned",pinnedApps);
                closeOverlay(true);refreshDock();
            });
            return cell;
        }
        boolean navigate(int index,int key,KeyEvent event){
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&
                key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
            int col=index%COLUMNS,target=index;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(col>0)target--;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(col<COLUMNS-1&&index+1<cells.size())target++;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(index/COLUMNS<ROWS-1&&cells.size()>COLUMNS)target=Math.min(index+COLUMNS,cells.size()-1);
                else if(page+1<pages){show(page+1,col);return true;}
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP){
                if(index>=COLUMNS)target=index-COLUMNS;
                else if(page>0){show(page-1,(ROWS-1)*COLUMNS+col);return true;}
                else{close.requestFocus();return true;}
            }
            if(target!=index)cells.get(target).requestFocus();return true;
        }
    }
    private void focusable(View v,int radius){
        v.setFocusable(true);v.setFocusableInTouchMode(false);v.setClickable(true);v.setBackground(background(0x00152e28,radius,0));
        v.setOnFocusChangeListener((view,has)->{view.animate().scaleX(has?1.07f:1).scaleY(has?1.07f:1).translationY(has?-dp(3):0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();view.setBackground(background(has?0x35d7dfbf:0x00152e28,radius,has?0xb8e7ecd4:0));if(has&&!asleep)activity();});
    }
    private void buildStatus(){
        status=text("",11,0xff254b40);status.setTag("left-notice");status.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);status.setAlpha(0);status.setPadding(dp(12),dp(9),dp(12),dp(9));status.setBackground(background(0xebf3efdc,9,0x70648d73));status.setMaxLines(3);FrameLayout.LayoutParams st=new FrameLayout.LayoutParams(dp(240),-2,Gravity.TOP|Gravity.LEFT);root.addView(status,st);
    }
    private void updateClock(){Date now=new Date();time.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));date.setText(new SimpleDateFormat("M月d日  EEEE",Locale.SIMPLIFIED_CHINESE).format(now));if(asleep){float drift=(float)Math.sin(SystemClock.uptimeMillis()/60000.0)*dp(6);header.setTranslationX(drift);}}
    private void message(String value){message(value,false);}
    private void message(String value,boolean bare){
        if(status==null)return;
        status.setBackground(bare?null:background(0xebf3efdc,9,0x70648d73));
        status.setTextColor(bare?Color.WHITE:0xff254b40);
        status.setShadowLayer(bare?dp(3):0,0,bare?dp(1):0,bare?0xe0142820:Color.TRANSPARENT);
        status.setText(value);status.bringToFront();status.animate().cancel();status.animate().alpha(1).setDuration(150).start();handler.removeCallbacks(hideMessage);handler.postDelayed(hideMessage,5000);}
    private final Runnable hideMessage=()->{if(status!=null)status.animate().alpha(0).setDuration(400).start();};
    private void activity(){lastInput=SystemClock.uptimeMillis();if(asleep)setAsleep(false);}
    private void updateRenderBudget(){if(pond!=null)pond.renderer.activityFpsLimit=overlay!=null?15:asleep?20:60;}
    private void setAsleep(boolean value){
        if(asleep==value)return;asleep=value;updateRenderBudget();
        float alpha=value?.30f:1f;
        for(View layer:new View[]{fisherActions,dock,header}){
            if(layer!=null){layer.animate().cancel();layer.animate().alpha(alpha).setDuration(600).start();}
        }
    }
    private GradientDrawable actionDisc(boolean focused){
        GradientDrawable disc=new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(focused?0xa82c433b:0x9031413b);
        disc.setStroke(dp(focused?2:1),focused?Color.WHITE:0x67ffffff);
        return disc;
    }
    private View fisherButton(int icon,String label,Runnable action){
        LinearLayout button=new LinearLayout(this);button.setOrientation(1);button.setGravity(Gravity.CENTER);
        FrameLayout disc=new FrameLayout(this);disc.setBackground(actionDisc(false));disc.setElevation(dp(1));LauncherIcon glyph=new LauncherIcon(this,icon);glyph.setInk(Color.WHITE);disc.addView(glyph,new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));button.addView(disc,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView title=text(label,11,Color.WHITE);title.setShadowLayer(dp(3),0,dp(1),0xcf142820);title.setGravity(Gravity.CENTER);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(4);button.addView(title,tp);
        button.setFocusable(true);button.setClickable(true);button.setContentDescription(label);button.setOnClickListener(v->action.run());button.setOnFocusChangeListener((v,focused)->{v.animate().scaleX(focused?1.07f:1).scaleY(focused?1.07f:1).translationY(focused?-dp(2):0).setDuration(180).start();disc.setBackground(actionDisc(focused));disc.setElevation(dp(focused?4:1));if(focused&&!asleep)activity();});return button;
    }
    private void buildFisherActions(){
        fisherActions=new LinearLayout(this);fisherActions.setOrientation(1);fisherActions.setTag("fisher-actions");
        achievementAction=fisherButton(LauncherIcon.ACHIEVEMENT,"成就",this::showAchievements);fishAction=fisherButton(LauncherIcon.FISHING,"钓鱼",this::startFishing);feedAction=fisherButton(LauncherIcon.FEED,"喂鱼",this::scatterFood);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setClipChildren(false);row.setClipToPadding(false);View[] actions={achievementAction,fishAction,feedAction};String[] tags={"fisher-achievements","fisher-fish","fisher-feed"};
        for(int i=0;i<actions.length;i++){View v=actions[i];v.setTag(tags[i]);v.setId(View.generateViewId());row.addView(v,new LinearLayout.LayoutParams(0,-1,1));}fisherActions.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(fisherActions,new FrameLayout.LayoutParams(dp(140),dp(80)));
        View circle=((ViewGroup)achievementAction).getChildAt(0);
        circle.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->alignDockBottom());
        fisherActions.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->alignDockBottom());
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)positionFisherActions();});root.post(this::positionFisherActions);linkFishingFocus();
    }
    private void positionFisherActions(){
        if(root.getWidth()==0)return;FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)fisherActions.getLayoutParams();p.width=Math.round(root.getWidth()*225f/1600);p.height=Math.round(root.getHeight()*105f/900);p.leftMargin=Math.round(root.getWidth()*50f/1600);p.topMargin=Math.round(root.getHeight()*730f/900);fisherActions.setLayoutParams(p);
        for(View button:new View[]{achievementAction,fishAction,feedAction}){View circle=((LinearLayout)button).getChildAt(0);int diameter=Math.round(root.getWidth()*60f/1600);circle.setLayoutParams(new LinearLayout.LayoutParams(diameter,diameter));}
        sizeDock();
        FrameLayout.LayoutParams notice=(FrameLayout.LayoutParams)status.getLayoutParams();notice.width=Math.round(root.getWidth()*390f/1600);notice.leftMargin=Math.round(root.getWidth()*35f/1600);notice.topMargin=Math.round(root.getHeight()*175f/900);status.setLayoutParams(notice);
    }
    private void linkFishingFocus(){
        if(feedAction==null||initialFocus==null)return;

        View[] actions={achievementAction,fishAction,feedAction};for(int i=0;i<actions.length;i++){actions[i].setNextFocusLeftId(actions[Math.max(0,i-1)].getId());actions[i].setNextFocusRightId(i<2?actions[i+1].getId():initialFocus.getId());actions[i].setNextFocusUpId(initialFocus.getId());actions[i].setNextFocusDownId(actions[i].getId());}
        for(int i=0;i<desktopTiles.size();i++){
            View item=desktopTiles.get(i);
            item.setNextFocusLeftId(i==0?feedAction.getId():desktopTiles.get(i-1).getId());
            item.setNextFocusRightId(i+1<desktopTiles.size()?desktopTiles.get(i+1).getId():item.getId());
            item.setNextFocusUpId(item.getId());item.setNextFocusDownId(item.getId());
        }
    }
    private void scatterFood(){activity();pond.queueEvent(()->{boolean accepted=pond.renderer.world.feedRandom();handler.post(()->message(accepted?"撒下一把鱼食，静候鱼来":"鱼食正慢慢落水"));});}
    private void startFishing(){activity();pond.queueEvent(()->{boolean started=pond.renderer.fishing.start();handler.post(()->message(started?"小猫醒来，抛下一竿闲意":"浮漂轻晃，且耐心候鱼"));});}
    private static String fishWeight(int grams){return String.format(Locale.CHINA,"%.2f kg",grams/1000f);}
    private void showAchievements(){
        fishingJournal.refresh(System.currentTimeMillis());List<FishingJournal.Entry> catches=fishingJournal.entries();
        LinearLayout panel=openOverlay("一日鱼缘","今日功德与渔获  ·  以这台设备的本地日期记账",false);overlay.setTag("achievements");
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER_VERTICAL);stats.setPadding(dp(20),dp(12),dp(20),dp(12));stats.setBackground(background(0x45232f24,16,0x3fd4c99c));
        TextView total=text(fishingJournal.merit()+"  今日功德",26,0xffdfc892);total.setTypeface(Typeface.create("serif",0));total.setTag("today-merit");stats.addView(total,new LinearLayout.LayoutParams(0,-2,1));stats.addView(text(catches.size()+" 尾 · 今日渔获",12,MUTED));panel.addView(stats,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setTag("catch-list");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(13);panel.addView(scroll,sp);LinearLayout list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);
        if(catches.isEmpty()){TextView empty=text("今日尚无渔获\n去池畔坐坐，静候一尾有缘鱼。",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(28),dp(20),dp(28));list.addView(empty);}
        else for(int i=catches.size()-1;i>=0;i--){FishingJournal.Entry entry=catches.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(9),dp(12),dp(9));LinearLayout desc=new LinearLayout(this);desc.setOrientation(1);desc.addView(text(entry.fish.name,14,IVORY));desc.addView(text(new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(entry.timestamp))+"  ·  "+fishWeight(entry.fish.grams),10,MUTED));row.addView(desc,new LinearLayout.LayoutParams(0,-2,1));row.addView(text("+"+entry.fish.merit+" 功德",13,0xffdfc892));focusable(row,9);row.setContentDescription(entry.fish.name+"，"+fishWeight(entry.fish.grams)+"，功德加"+entry.fish.merit);row.setBackground(background(0x1020392f,9,0x22c4ceae));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(5);list.addView(row,rp);}
        View close=panel.getChildAt(0);if(close instanceof ViewGroup&&((ViewGroup)close).getChildCount()>1)((ViewGroup)close).getChildAt(1).requestFocus();
    }
    private void loadApps(){
        if(loader.isShutdown())return;
        loader.execute(()->{
            try{
                PackageManager pm=getPackageManager();LinkedHashMap<String,ResolveInfo> found=new LinkedHashMap<>();
                for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                    Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
                    for(ResolveInfo ri:pm.queryIntentActivities(query,0)){String pkg=ri.activityInfo.packageName;if(!pkg.equals(getPackageName())&&!found.containsKey(pkg))found.put(pkg,ri);}
                }
                ArrayList<AppEntry> result=new ArrayList<>();
                for(ResolveInfo ri:found.values()){try{if(!ri.activityInfo.enabled||!ri.activityInfo.applicationInfo.enabled)continue;AppEntry app=new AppEntry();app.label=ri.loadLabel(pm).toString();app.pkg=ri.activityInfo.packageName;app.component=new ComponentName(app.pkg,ri.activityInfo.name);Drawable drawable=ri.loadIcon(pm);app.icon=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(app.icon);drawable.setBounds(0,0,96,96);drawable.draw(c);result.add(app);}catch(RuntimeException ignored){}}
                Collator collator=Collator.getInstance(Locale.getDefault());Collections.sort(result,(a,b)->collator.compare(a.label,b.label));
                handler.post(()->{if(isDestroyed())return;apps.clear();apps.addAll(result);refreshDock();});
            }catch(RuntimeException error){handler.post(()->message("应用列表暂时不可用，请稍后重试"));}
        });
    }
    private void launch(AppEntry app){
        activity();try{Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(i);
            recentApps.remove(app.pkg);recentApps.add(0,app.pkg);
            while(recentApps.size()>RECENT_HISTORY)recentApps.remove(recentApps.size()-1);
            saveOrder("dock-recent",recentApps);refreshDock();
        }catch(ActivityNotFoundException|SecurityException error){message("无法打开此应用，正在刷新列表");loadApps();}
    }
    private LinearLayout openOverlay(String title,String subtitle,boolean side){
        closeOverlay(false);overlay=new FrameLayout(this);updateRenderBudget();overlay.setBackgroundColor(0x8c092521);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(32),dp(26),dp(32),dp(24));panel.setBackground(background(0xf51b342e,side?0:22,0x28d4dfbd));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(side?dp(355):dp(760),side?-1:dp(435),side?Gravity.RIGHT:Gravity.CENTER);overlay.addView(panel,pp);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text(title,23,IVORY);titleRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));TextView close=smallButton("关闭",()->closeOverlay(true));titleRow.addView(close);panel.addView(titleRow);
        TextView sub=text(subtitle,10,0xb8d3ddc5);LinearLayout.LayoutParams sublp=new LinearLayout.LayoutParams(-1,-2);sublp.topMargin=dp(8);sublp.bottomMargin=dp(20);panel.addView(sub,sublp);dock.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);header.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);if(fisherActions!=null)fisherActions.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);return panel;
    }
    private TextView smallButton(String label,Runnable action){TextView b=text(label,11,IVORY);b.setTag(label);b.setGravity(Gravity.CENTER);b.setPadding(dp(13),dp(9),dp(13),dp(9));focusable(b,8);b.setOnClickListener(v->action.run());return b;}
    private void closeOverlay(boolean focus){
        if(overlay!=null){root.removeView(overlay);overlay=null;}
        updateRenderBudget();dock.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);header.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);if(fisherActions!=null)fisherActions.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        Object opener=returnDockFocus;returnDockFocus=null;
        if(focus&&initialFocus!=null){View target=opener==null?null:dock.findViewWithTag(opener);(target==null?initialFocus:target).requestFocus();}activity();
    }
    private void showSettings(){
        View previousFocus=getCurrentFocus();String focusTag=overlay!=null&&"settings".equals(overlay.getTag())&&previousFocus!=null&&previousFocus.getTag() instanceof String?(String)previousFocus.getTag():null;
        ScrollView oldScroll=overlay==null?null:overlay.findViewWithTag("settings-scroll");int scrollY=oldScroll==null?0:oldScroll.getScrollY();
        LinearLayout panel=openOverlay("池间小调","你的偏好会自动保存在这台设备。",true);overlay.setTag("settings");
        ScrollView scroll=new ScrollView(this);scroll.setTag("settings-scroll");panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);
        section(content,"锦鲤数量",fishCount+" 尾");LinearLayout countRow=row(content);countRow.addView(smallButton("少五尾",()->{fishCount=Math.max(5,fishCount-5);saveSimulation();showSettings();}));countRow.addView(smallButton("多五尾",()->{fishCount=Math.min(40,fishCount+5);saveSimulation();showSettings();}));
        section(content,"游动节奏",tempo<.9f?"缓缓":tempo>1.1f?"轻快":"悠然");LinearLayout speedRow=row(content);for(int i=0;i<3;i++){final float t=new float[]{.7f,1,1.3f}[i];speedRow.addView(smallButton(new String[]{"缓缓","悠然","轻快"}[i],()->{tempo=t;saveSimulation();showSettings();}));}
        section(content,"画面精细度",quality==1080?"1080p · 更细腻":"720p · 更省资源");LinearLayout resolution=row(content);resolution.addView(smallButton("1080p",()->{quality=1080;saveQuality();showSettings();}));resolution.addView(smallButton("720p",()->{quality=720;saveQuality();showSettings();}));
        section(content,"画面流畅度",fps+" 帧 / 秒");LinearLayout frames=row(content);frames.addView(smallButton("30 帧 · 标准",()->{fps=30;saveQuality();showSettings();}));frames.addView(smallButton("60 帧 · 流畅",()->{fps=60;saveQuality();showSettings();}));
        section(content,"系统","");TextView home=smallButton("设为默认桌面",this::requestHome);content.addView(home,new LinearLayout.LayoutParams(-1,-2));content.addView(smallButton("Android 系统设置",this::systemSettings),new LinearLayout.LayoutParams(-1,-2));
        TextView about=text("GenTV  1.11.0\n锦鲤与一只小乌龟\n完全离线 · 无广告 · 无账号\n应用切换后暂停鱼池，返回继续游动。",10,0x99c8d6bd);about.setLineSpacing(dp(5),1);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(24);content.addView(about,ap);
        View next=focusTag==null?null:content.findViewWithTag(focusTag);if(next!=null)next.requestFocus();else if(!content.getFocusables(View.FOCUS_FORWARD).isEmpty())content.getFocusables(View.FOCUS_FORWARD).get(0).requestFocus();if(focusTag!=null)scroll.post(()->scroll.scrollTo(0,scrollY));
    }
    private void section(LinearLayout parent,String title,String value){LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(title,12,IVORY);heading.addView(t,new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(value,10,0xb8ccdabf));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);lp.bottomMargin=dp(9);parent.addView(heading,lp);}
    private LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(this);r.setOrientation(0);parent.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;}
    private void saveSimulation(){prefs.edit().putInt("fishCount",fishCount).putFloat("tempo",tempo).apply();pond.queueEvent(()->{pond.renderer.world.setCount(fishCount);pond.renderer.world.tempo=tempo;});}
    private void saveQuality(){prefs.edit().putInt("fps",fps).putInt("quality",quality).apply();pond.renderer.targetFps=fps;applyResolution();}
    private void applyResolution(){int height=quality==720?720:1080;int screenH=getResources().getDisplayMetrics().heightPixels;height=Math.min(height,screenH);pond.getHolder().setFixedSize(height*16/9,height);}
    private void requestHome(){
        try{if(Build.VERSION.SDK_INT>=29){RoleManager role=getSystemService(RoleManager.class);if(role!=null&&role.isRoleAvailable(RoleManager.ROLE_HOME)){if(role.isRoleHeld(RoleManager.ROLE_HOME)){message("GenTV已经是默认桌面");return;}startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_HOME),101);return;}}startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}
        catch(ActivityNotFoundException|SecurityException ex){message("此固件未开放默认桌面选择，请在系统设置中检查");systemSettings();}
    }
    private void systemSettings(){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(ActivityNotFoundException e){message("此设备没有可用的系统设置入口");}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int key=e.getKeyCode();boolean wasAsleep=asleep;if(e.getAction()==KeyEvent.ACTION_DOWN)activity();
        if(wasAsleep&&e.getAction()==KeyEvent.ACTION_DOWN){wakeKey=key;return true;}
        if(key==wakeKey){if(e.getAction()==KeyEvent.ACTION_UP)wakeKey=-1;return true;}
        if(overlay!=null){if(key==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)closeOverlay(true);return true;}return super.dispatchKeyEvent(e);}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
            View focused=getCurrentFocus();int index=desktopTiles.indexOf(focused);
            if(index>=0){
                if(e.getAction()==KeyEvent.ACTION_DOWN){int id=key==KeyEvent.KEYCODE_DPAD_DOWN?focused.getNextFocusDownId():key==KeyEvent.KEYCODE_DPAD_UP?focused.getNextFocusUpId():key==KeyEvent.KEYCODE_DPAD_LEFT?focused.getNextFocusLeftId():focused.getNextFocusRightId();View next=root.findViewById(id);if(next!=null)next.requestFocus();}
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN)return true;
            if(focused==null&&initialFocus!=null){if(e.getAction()==KeyEvent.ACTION_DOWN)initialFocus.requestFocus();return true;}
        }
        if(key==KeyEvent.KEYCODE_MENU){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)showSettings();return true;}
        if(key==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP&&initialFocus!=null){initialFocus.requestFocus();}return true;}
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onResume(){super.onResume();resumed=true;immersive();pond.onResume();pond.queueEvent(()->pond.renderer.resetClock());activity();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);if(feedAudio!=null&&feedStream!=0){feedAudio.stop(feedStream);feedStream=0;}pond.onPause();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(feedAudio!=null){feedAudio.release();feedAudio=null;}loader.shutdownNow();if(receiverRegistered)unregisterReceiver(packageReceiver);super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);closeOverlay(true);immersive();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
}
