package com.rovtech.joyride;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Traffic: data + procedural rear/front views. */
public final class Vehicles {
    private Vehicles() {}

    public static final int HATCH = 0, SUV = 1, TRUCK = 2, BUS = 3, AUTO = 4;
    /** width, height, length in world units */
    public static final float[][] SIZE = {
            {380, 300, 900}, {410, 380, 1000}, {440, 620, 1700}, {450, 580, 2000}, {320, 360, 620}
    };
    public static final int[] PAINT = {
            0xFFD62828, 0xFFF2F2F2, 0xFF1C1C1E, 0xFFB0B7BF, 0xFF2B59C3, 0xFF8D99AE, 0xFF7F5539, 0xFF2E7D32, 0xFFF77F00
    };
    public static final int[] TRUCK_PAINT = {0xFFF77F00, 0xFF1E88E5, 0xFFD62828, 0xFF2E9D4A, 0xFFFCBF49};

    public static class Car {
        public double z;
        public float x, targetX, speed; // speed in world units/sec, negative = oncoming
        public int type, color;
        public boolean oncoming, passed, nearDone;
        public float blink;
        // screen projection (filled by Road)
        public boolean visible;
        public float sx, sy, scale;
        public int slot;

        public float width() { return SIZE[type][0]; }
        public float length() { return SIZE[type][2]; }
    }

    private static final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final RectF rf = new RectF();
    private static final Typeface bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    private static void rect(Canvas c, float l, float t, float r, float b, float rad, int col) {
        p.setColor(col);
        rf.set(l, t, r, b);
        if (rad > 0) c.drawRoundRect(rf, rad, rad, p); else c.drawRect(rf, p);
    }

    public static void draw(Canvas c, Car car, float dark) {
        float s = car.scale;
        float W = SIZE[car.type][0] * s, H = SIZE[car.type][1] * s;
        if (W < 2) return;
        float x = car.sx, y = car.sy, l = x - W / 2, r = x + W / 2;
        boolean front = car.oncoming;

        // shadow + wheels
        p.setColor(0x55000000);
        rf.set(l - W * 0.05f, y - H * 0.06f, r + W * 0.05f, y + H * 0.05f);
        c.drawOval(rf, p);
        rect(c, l + W * 0.04f, y - H * 0.16f, l + W * 0.2f, y, W * 0.03f, 0xFF111111);
        rect(c, r - W * 0.2f, y - H * 0.16f, r - W * 0.04f, y, W * 0.03f, 0xFF111111);

        switch (car.type) {
            case HATCH: case SUV: {
                int body = car.color;
                float beltY = y - H * (car.type == SUV ? 0.55f : 0.5f);
                rect(c, l, beltY, r, y - H * 0.08f, W * 0.1f, body);
                path.reset();
                path.moveTo(l + W * 0.06f, beltY + 2);
                path.lineTo(l + W * 0.17f, y - H);
                path.lineTo(r - W * 0.17f, y - H);
                path.lineTo(r - W * 0.06f, beltY + 2);
                path.close();
                p.setColor(body);
                c.drawPath(path, p);
                // glass
                path.reset();
                path.moveTo(l + W * 0.12f, beltY - H * 0.03f);
                path.lineTo(l + W * 0.2f, y - H * 0.93f);
                path.lineTo(r - W * 0.2f, y - H * 0.93f);
                path.lineTo(r - W * 0.12f, beltY - H * 0.03f);
                path.close();
                p.setColor(front ? 0xFF6E8CA3 : 0xFF263238);
                c.drawPath(path, p);
                if (front) {
                    p.setColor(0x55FFFFFF);
                    path.reset();
                    path.moveTo(l + W * 0.25f, y - H * 0.93f);
                    path.lineTo(l + W * 0.38f, y - H * 0.93f);
                    path.lineTo(l + W * 0.24f, beltY - H * 0.03f);
                    path.lineTo(l + W * 0.14f, beltY - H * 0.03f);
                    path.close();
                    c.drawPath(path, p);
                    rect(c, l + W * 0.3f, y - H * 0.38f, r - W * 0.3f, y - H * 0.24f, W * 0.02f, 0xFF222222); // grille
                    rect(c, l + W * 0.05f, y - H * 0.42f, l + W * 0.26f, y - H * 0.32f, W * 0.03f, 0xFFFFF8E1);
                    rect(c, r - W * 0.26f, y - H * 0.42f, r - W * 0.05f, y - H * 0.32f, W * 0.03f, 0xFFFFF8E1);
                } else {
                    rect(c, l + W * 0.04f, y - H * 0.44f, l + W * 0.24f, y - H * 0.34f, W * 0.02f, 0xFFC62828);
                    rect(c, r - W * 0.24f, y - H * 0.44f, r - W * 0.04f, y - H * 0.34f, W * 0.02f, 0xFFC62828);
                }
                rect(c, x - W * 0.13f, y - H * 0.26f, x + W * 0.13f, y - H * 0.17f, 0, 0xFFF5F5F5); // plate
                rect(c, l, y - H * 0.14f, r, y - H * 0.06f, W * 0.04f, 0xFF2A2A2A);              // bumper
                break;
            }
            case TRUCK: {
                if (front) {
                    rect(c, l + W * 0.03f, y - H, r - W * 0.03f, y - H * 0.62f, W * 0.04f, 0xFF8D6E63); // cargo behind cab
                    rect(c, l, y - H * 0.7f, r, y - H * 0.1f, W * 0.08f, car.color);
                    rect(c, l + W * 0.08f, y - H * 0.66f, r - W * 0.08f, y - H * 0.42f, W * 0.03f, 0xFF6E8CA3);
                    rect(c, l + W * 0.3f, y - H * 0.36f, r - W * 0.3f, y - H * 0.18f, 0, 0xFF333333);
                    rect(c, l + W * 0.06f, y - H * 0.32f, l + W * 0.22f, y - H * 0.22f, W * 0.04f, 0xFFFFF8E1);
                    rect(c, r - W * 0.22f, y - H * 0.32f, r - W * 0.06f, y - H * 0.22f, W * 0.04f, 0xFFFFF8E1);
                    rect(c, l + W * 0.1f, y - H * 0.74f, r - W * 0.1f, y - H * 0.69f, 0, 0xFFFCBF49);
                } else {
                    rect(c, l, y - H, r, y - H * 0.1f, W * 0.03f, car.color);
                    p.setColor(Color.argb(70, 0, 0, 0));
                    for (int i = 1; i < 6; i++) c.drawRect(l, y - H + H * 0.15f * i, r, y - H + H * 0.15f * i + Math.max(1, H * 0.012f), p);
                    rect(c, l + W * 0.06f, y - H * 0.5f, r - W * 0.06f, y - H * 0.3f, 0, 0xFFFCBF49);
                    if (W > 60) {
                        p.setColor(0xFFD62828);
                        p.setTypeface(bold);
                        p.setTextAlign(Paint.Align.CENTER);
                        p.setTextSize(H * 0.09f);
                        c.drawText("HORN OK PLEASE", x, y - H * 0.37f, p);
                    }
                    rect(c, l + W * 0.04f, y - H * 0.24f, l + W * 0.18f, y - H * 0.16f, 0, 0xFFC62828);
                    rect(c, r - W * 0.18f, y - H * 0.24f, r - W * 0.04f, y - H * 0.16f, 0, 0xFFC62828);
                    rect(c, l, y - H * 0.12f, r, y - H * 0.07f, 0, 0xFF222222);
                }
                break;
            }
            case BUS: {
                rect(c, l, y - H, r, y - H * 0.08f, W * 0.06f, car.color);
                if (front) {
                    rect(c, l + W * 0.06f, y - H * 0.9f, r - W * 0.06f, y - H * 0.45f, W * 0.04f, 0xFF6E8CA3);
                    rect(c, l + W * 0.15f, y - H * 0.97f, r - W * 0.15f, y - H * 0.91f, 0, 0xFF111111);
                    if (W > 50) {
                        p.setColor(0xFFFF9800);
                        p.setTypeface(bold);
                        p.setTextAlign(Paint.Align.CENTER);
                        p.setTextSize(H * 0.055f);
                        c.drawText("MANALI - LEH", x, y - H * 0.92f, p);
                    }
                    rect(c, l + W * 0.06f, y - H * 0.3f, l + W * 0.22f, y - H * 0.22f, W * 0.03f, 0xFFFFF8E1);
                    rect(c, r - W * 0.22f, y - H * 0.3f, r - W * 0.06f, y - H * 0.22f, W * 0.03f, 0xFFFFF8E1);
                } else {
                    rect(c, l + W * 0.1f, y - H * 0.88f, r - W * 0.1f, y - H * 0.62f, W * 0.03f, 0xFF263238);
                    rect(c, l, y - H * 0.55f, r, y - H * 0.45f, 0, 0xFFF2F2F2);
                    rect(c, l + W * 0.05f, y - H * 0.32f, l + W * 0.16f, y - H * 0.2f, 0, 0xFFC62828);
                    rect(c, r - W * 0.16f, y - H * 0.32f, r - W * 0.05f, y - H * 0.2f, 0, 0xFFC62828);
                }
                rect(c, l, y - H * 0.14f, r, y - H * 0.08f, 0, 0xFF222222);
                break;
            }
            case AUTO: {
                rect(c, l + W * 0.05f, y - H * 0.6f, r - W * 0.05f, y - H * 0.1f, W * 0.12f, 0xFF7BC950);
                rect(c, l + W * 0.02f, y - H, r - W * 0.02f, y - H * 0.58f, W * 0.25f, 0xFF1C1C1E);
                rect(c, l + W * 0.02f, y - H * 0.66f, r - W * 0.02f, y - H * 0.58f, 0, 0xFFFCBF49);
                if (front) {
                    rect(c, l + W * 0.14f, y - H * 0.9f, r - W * 0.14f, y - H * 0.66f, W * 0.05f, 0xFF6E8CA3);
                    p.setColor(0xFFFFF8E1);
                    c.drawCircle(x, y - H * 0.35f, W * 0.08f, p);
                } else {
                    rect(c, l + W * 0.14f, y - H * 0.56f, r - W * 0.14f, y - H * 0.3f, W * 0.04f, 0xFF263238);
                    rect(c, l + W * 0.08f, y - H * 0.24f, l + W * 0.2f, y - H * 0.16f, 0, 0xFFC62828);
                    rect(c, r - W * 0.2f, y - H * 0.24f, r - W * 0.08f, y - H * 0.16f, 0, 0xFFC62828);
                }
                break;
            }
        }
        // indicator while changing lanes
        if (car.blink > 0 && ((int) (car.blink * 6)) % 2 == 0) {
            float side = car.targetX > car.x ? 1 : -1;
            p.setColor(0xFFFFA000);
            c.drawCircle(x + side * W * 0.46f, y - H * 0.4f, Math.max(2, W * 0.05f), p);
        }
    }

    /** Glowing head/tail lights, drawn above the night overlay. */
    public static void drawLights(Canvas c, Car car, float dark) {
        if (dark < 0.15f) return;
        float s = car.scale;
        float W = SIZE[car.type][0] * s, H = SIZE[car.type][1] * s;
        if (W < 3) return;
        float y = car.sy - H * (car.type == TRUCK && !car.oncoming ? 0.2f : 0.35f);
        float a = Math.min(1f, dark * 1.8f);
        int col = car.oncoming ? Color.argb((int) (230 * a), 255, 248, 220) : Color.argb((int) (220 * a), 255, 40, 40);
        float rad = W * (car.oncoming ? 0.32f : 0.2f);
        if (car.type == AUTO && car.oncoming) {
            glow(c, car.sx, y, rad * 1.3f, col);
            return;
        }
        glow(c, car.sx - W * 0.36f, y, rad, col);
        glow(c, car.sx + W * 0.36f, y, rad, col);
    }

    private static void glow(Canvas c, float x, float y, float r, int col) {
        p.setShader(new RadialGradient(x, y, r, col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }
}
