package com.gentv.fishlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** A local daily ledger, keyed by catch id so displaying a result never awards twice. */
public final class FishingJournal {
    public static final class Entry {
        public final FishingSession.Catch fish;public final long timestamp;
        Entry(FishingSession.Catch fish,long timestamp){this.fish=fish;this.timestamp=timestamp;}
    }
    private final SharedPreferences prefs;
    private String day="";
    private final ArrayList<Entry> entries=new ArrayList<>();
    public FishingJournal(Context c){prefs=c.getSharedPreferences("fishing-journal",Context.MODE_PRIVATE);refresh(System.currentTimeMillis());}
    public static String dayKey(long time){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(time));}
    public void refresh(long now){
        String current=dayKey(now);if(current.equals(day))return;day=current;entries.clear();
        if(!day.equals(prefs.getString("day","")))return;
        try{JSONArray a=new JSONArray(prefs.getString("catches","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);entries.add(new Entry(new FishingSession.Catch(o.getString("id"),o.getString("name"),o.getInt("grams"),o.getInt("merit"),o.getInt("skin")),o.getLong("time")));}}
        catch(JSONException error){android.util.Log.e("GenTV","Cannot read fishing journal",error);}
    }
    public boolean record(FishingSession.Catch fish,long now){
        refresh(now);for(Entry e:entries)if(e.fish.id.equals(fish.id))return false;
        entries.add(new Entry(fish,now));JSONArray a=new JSONArray();
        try{for(Entry e:entries){JSONObject o=new JSONObject();o.put("id",e.fish.id).put("name",e.fish.name).put("grams",e.fish.grams).put("merit",e.fish.merit).put("skin",e.fish.skin).put("time",e.timestamp);a.put(o);}}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
        prefs.edit().putString("day",day).putString("catches",a.toString()).apply();return true;
    }
    public int merit(){int total=0;for(Entry e:entries)total+=e.fish.merit;return total;}
    public List<Entry> entries(){return Collections.unmodifiableList(new ArrayList<>(entries));}
}
