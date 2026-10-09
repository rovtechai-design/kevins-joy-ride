package com.rovtech.joyride;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;

/** Minimal immediate-mode UI: buttons are drawn and registered each frame, then hit-tested. */
public class Ui {
    public static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    public static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    public static final Typeface COND = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    public static final int PRIMARY = 0, SECONDARY = 1, DANGER = 2, GHOST = 3, GO = 4;
    public static final int ORANGE = 0xFFF77F00, NAVY = 0xFF14213D, CREAM = 0xFFFFF4E0;

    public static class Btn {
        public final int id;
        public final RectF r;
        Btn(int id, RectF r) { this.id = id; this.r = r; }
    }

    public final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ArrayList<Btn> building = new ArrayList<>();
    private volatile ArrayList<Btn> live = new ArrayList<>();

    public Ui() {
        s.setStyle(Paint.Style.STROKE);
    }

    public void begin() { building = new ArrayList<>(); }
    public void end() { live = building; }

    public int hit(float x, float y) {
        ArrayList<Btn> l = live;
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).r.contains(x, y)) return l.get(i).id;
        return -1;
    }

    public void register(int id, RectF r) { building.add(new Btn(id, new RectF(r))); }

    public void button(Canvas c, int id, RectF r, String label, int style, boolean focused, boolean pressed) {
        register(id, r);
        float rad = r.height() * 0.28f;
        p.setShader(null);
        // drop shadow
        p.setColor(0x55000000);
        c.drawRoundRect(r.left, r.top + r.height() * 0.07f, r.right, r.bottom + r.height() * 0.07f, rad, rad, p);
        int top, bot, txt = 0xFFFFFFFF;
        switch (style) {
            case PRIMARY: top = 0xFFFF9E2C; bot = 0xFFE56A00; break;
            case GO: top = 0xFF4CD964; bot = 0xFF1E9E45; break;
            case DANGER: top = 0xFFEF5350; bot = 0xFFB71C1C; break;
            case GHOST: top = 0x33FFFFFF; bot = 0x22FFFFFF; break;
            default: top = 0xEE24345A; bot = 0xEE14213D;
        }
        if (pressed) { int t = top; top = bot; bot = t; }
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bot, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        s.setStrokeWidth(focused ? r.height() * 0.06f : r.height() * 0.025f);
        s.setColor(focused ? 0xFFFFFFFF : 0x55FFFFFF);
        c.drawRoundRect(r, rad, rad, s);
        float size = Math.min(r.height() * 0.38f, r.width() / Math.max(4, label.length()) * 1.7f);
        text(c, label, r.centerX(), r.centerY() + size * 0.36f, size, txt, Paint.Align.CENTER, BOLD);
    }

    public void text(Canvas c, String t, float x, float y, float size, int color, Paint.Align a, Typeface tf) {
        p.setShader(null);
        p.setTypeface(tf);
        p.setTextAlign(a);
        p.setTextSize(size);
        p.setColor(color);
        c.drawText(t, x, y, p);
    }

    public void shadowText(Canvas c, String t, float x, float y, float size, int color, Paint.Align a, Typeface tf) {
        text(c, t, x + size * 0.05f, y + size * 0.06f, size, 0x99000000, a, tf);
        text(c, t, x, y, size, color, a, tf);
    }

    public void outlineText(Canvas c, String t, float x, float y, float size, int fill, int outline, Typeface tf) {
        s.setTypeface(tf);
        s.setTextAlign(Paint.Align.CENTER);
        s.setTextSize(size);
        s.setStrokeWidth(size * 0.12f);
        s.setStrokeJoin(Paint.Join.ROUND);
        s.setColor(outline);
        c.drawText(t, x, y, s);
        text(c, t, x, y, size, fill, Paint.Align.CENTER, tf);
    }

    public void panel(Canvas c, RectF r, int color) {
        p.setShader(null);
        p.setColor(color);
        float rad = Math.min(r.width(), r.height()) * 0.08f;
        c.drawRoundRect(r, rad, rad, p);
        s.setStrokeWidth(2.5f);
        s.setColor(0x33FFFFFF);
        c.drawRoundRect(r, rad, rad, s);
    }

    public void stroke(Canvas c, RectF r, float rad, float width, int color) {
        s.setStrokeWidth(width);
        s.setColor(color);
        c.drawRoundRect(r, rad, rad, s);
    }
}
