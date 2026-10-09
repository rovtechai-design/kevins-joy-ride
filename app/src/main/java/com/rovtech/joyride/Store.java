package com.rovtech.joyride;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persistent settings, rider outfits and the local leaderboards. */
public class Store {

    public static final int MODE_ENDLESS = 0, MODE_TIME = 1;
    public static final String[] MODE_NAMES = {"ENDLESS", "TIME ATTACK"};
    public static final int BOARD_SIZE = 10;

    public static class Entry {
        public String name;
        public int rider, score;
        public float km, topSpeed;
        public long time;
    }

    private final SharedPreferences prefs;
    public final Rider[] riders = new Rider[Rider.NAMES.length];

    public Store(Context ctx) {
        prefs = ctx.getSharedPreferences("joyride", Context.MODE_PRIVATE);
        for (int i = 0; i < riders.length; i++) {
            riders[i] = new Rider(i);
            riders[i].decode(prefs.getString("rider_" + i, null));
        }
        riders[riders.length - 1].name = prefs.getString("custom_name", "Custom");
    }

    public int selected() { return Math.max(0, Math.min(riders.length - 1, prefs.getInt("selected", 0))); }
    public void setSelected(int i) { prefs.edit().putInt("selected", i).apply(); }
    public Rider rider() { return riders[selected()]; }

    public void saveRider(Rider r) {
        prefs.edit().putString("rider_" + r.index, r.encode()).apply();
        if (r.isCustom()) prefs.edit().putString("custom_name", r.name).apply();
    }

    public boolean sound() { return prefs.getBoolean("sound", true); }
    public void setSound(boolean on) { prefs.edit().putBoolean("sound", on).apply(); }

    public List<Entry> board(int mode) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("board_" + mode, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.name = o.optString("name");
                e.rider = o.optInt("rider");
                e.score = o.optInt("score");
                e.km = (float) o.optDouble("km");
                e.topSpeed = (float) o.optDouble("top");
                e.time = o.optLong("time");
                out.add(e);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Inserts the run and returns its rank (0-based), or -1 if it didn't make the board. */
    public int submit(int mode, Entry e) {
        List<Entry> list = board(mode);
        int rank = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (e.score > list.get(i).score) { rank = i; break; }
        }
        if (rank >= BOARD_SIZE) return -1;
        list.add(rank, e);
        while (list.size() > BOARD_SIZE) list.remove(list.size() - 1);
        JSONArray arr = new JSONArray();
        try {
            for (Entry x : list) {
                JSONObject o = new JSONObject();
                o.put("name", x.name);
                o.put("rider", x.rider);
                o.put("score", x.score);
                o.put("km", x.km);
                o.put("top", x.topSpeed);
                o.put("time", x.time);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString("board_" + mode, arr.toString()).apply();
        return rank;
    }

    public int best(int mode) {
        List<Entry> b = board(mode);
        return b.isEmpty() ? 0 : b.get(0).score;
    }
}
