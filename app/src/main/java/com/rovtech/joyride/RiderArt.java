package com.rovtech.joyride;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Procedural art for the Himalayan 450 and its rider. All coordinates are in "units":
 * ground is y=0, up is negative, one unit is roughly the bike's wheelbase / 1.3.
 */
public final class RiderArt {
    private RiderArt() {}

    private static final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final RectF rf = new RectF();
    private static float X, Y0, S;

    static {
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setStrokeJoin(Paint.Join.ROUND);
    }

    // ---------- helpers ----------
    private static float x(float u) { return X + u * S; }
    private static float y(float u) { return Y0 + u * S; }

    private static void line(Canvas c, float x1, float y1, float x2, float y2, float w, int col) {
        st.setColor(col);
        st.setStrokeWidth(w * S);
        c.drawLine(x(x1), y(y1), x(x2), y(y2), st);
    }

    private static void poly(Canvas c, int col, float... p) {
        path.reset();
        path.moveTo(x(p[0]), y(p[1]));
        for (int i = 2; i < p.length; i += 2) path.lineTo(x(p[i]), y(p[i + 1]));
        path.close();
        f.setColor(col);
        c.drawPath(path, f);
    }

    private static void circ(Canvas c, float ux, float uy, float r, int col) {
        f.setColor(col);
        c.drawCircle(x(ux), y(uy), r * S, f);
    }

    private static void ring(Canvas c, float ux, float uy, float r, float w, int col) {
        st.setColor(col);
        st.setStrokeWidth(w * S);
        c.drawCircle(x(ux), y(uy), r * S, st);
    }

    private static void rrect(Canvas c, float l, float t, float r, float b, float rad, int col) {
        f.setColor(col);
        rf.set(x(l), y(t), x(r), y(b));
        c.drawRoundRect(rf, rad * S, rad * S, f);
    }

    public static int shade(int col, float k) {
        int r = Math.min(255, (int) (Color.red(col) * k));
        int g = Math.min(255, (int) (Color.green(col) * k));
        int b = Math.min(255, (int) (Color.blue(col) * k));
        return Color.argb(Color.alpha(col), r, g, b);
    }

    private static boolean dark(int col) {
        return Color.red(col) * 0.3 + Color.green(col) * 0.59 + Color.blue(col) * 0.11 < 90;
    }

    /** Jacket pattern, drawn inside the current clip over the unit-rect l,t,r,b. */
    private static void pattern(Canvas c, int pat, float l, float t, float r, float b, int jacket, int accent, boolean rear) {
        float w = r - l, h = b - t;
        switch (pat) {
            case 1: // racing stripes
                if (rear) {
                    rrect(c, -0.07f, t, -0.025f, b, 0, accent);
                    rrect(c, 0.025f, t, 0.07f, b, 0, accent);
                } else {
                    line(c, l, b - h * 0.3f, r, t + h * 0.15f, 0.05f, accent);
                    line(c, l, b - h * 0.1f, r, t + h * 0.35f, 0.035f, accent);
                }
                break;
            case 2: { // camo
                int a = shade(jacket, 0.7f), b2 = shade(jacket, 1.35f), d = shade(accent, 0.8f);
                float[][] blobs = {{0.2f, 0.15f, 0.12f}, {0.7f, 0.3f, 0.14f}, {0.35f, 0.55f, 0.13f},
                        {0.8f, 0.75f, 0.12f}, {0.15f, 0.85f, 0.1f}, {0.55f, 0.9f, 0.11f}, {0.5f, 0.1f, 0.08f}};
                for (int i = 0; i < blobs.length; i++) {
                    int col = i % 3 == 0 ? a : (i % 3 == 1 ? b2 : d);
                    float bx = l + w * blobs[i][0], by = t + h * blobs[i][1], br = Math.max(w, h) * blobs[i][2];
                    f.setColor(col);
                    rf.set(x(bx - br * 1.3f), y(by - br * 0.8f), x(bx + br * 1.3f), y(by + br * 0.8f));
                    c.drawOval(rf, f);
                }
                break;
            }
            case 3: // chevrons
                for (int i = 0; i < 3; i++) {
                    float cy = t + h * (0.3f + i * 0.22f);
                    if (rear) {
                        poly(c, accent, l, cy, 0, cy + h * 0.12f, r, cy, r, cy + h * 0.07f, 0, cy + h * 0.19f, l, cy + h * 0.07f);
                    } else {
                        line(c, l, cy, l + w * 0.5f, cy - h * 0.1f, 0.04f, accent);
                    }
                }
                break;
            case 4: { // flames from the bottom
                int y1 = 0xFFFFC107, o = 0xFFF77F00;
                for (int i = 0; i < 5; i++) {
                    float fx = l + w * (0.1f + i * 0.2f);
                    float fh = h * (0.45f + (i % 2) * 0.25f);
                    poly(c, o, fx - w * 0.12f, b, fx + w * 0.05f, b - fh, fx + w * 0.12f, b);
                    poly(c, y1, fx - w * 0.06f, b, fx + w * 0.03f, b - fh * 0.55f, fx + w * 0.07f, b);
                }
                poly(c, accent, l, t + h * 0.02f, r, t + h * 0.02f, r, t + h * 0.12f, l, t + h * 0.12f);
                break;
            }
            case 5: // hi-vis reflective bands
                rrect(c, l, t + h * 0.45f, r, t + h * 0.53f, 0, 0xFFDDE3E8);
                rrect(c, l, t + h * 0.72f, r, t + h * 0.8f, 0, 0xFFDDE3E8);
                if (rear) {
                    rrect(c, -0.06f, t, -0.02f, t + h * 0.45f, 0, 0xFFDDE3E8);
                    rrect(c, 0.02f, t, 0.06f, t + h * 0.45f, 0, 0xFFDDE3E8);
                }
                break;
            default:
                // plain: subtle accent panel
                if (rear) rrect(c, l, t, r, t + h * 0.12f, 0, accent);
                else line(c, l + w * 0.2f, t + h * 0.05f, l + w * 0.2f, b, 0.03f, accent);
        }
    }

    // =====================================================================
    // SIDE VIEW (bike facing right)
    // =====================================================================

    public static void drawSide(Canvas c, Rider r, float cx, float gy, float s, float t, boolean withRider) {
        X = cx; Y0 = gy; S = s;
        int[] bk = r.bike();
        int tank = bk[0], acc = bk[1], frame = bk[2];
        int accCol = r.col(Rider.ACC_COL);
        int acs = r.v[Rider.ACCESSORY];
        float wave = (float) Math.sin(t * 9);

        // ground shadow
        f.setColor(0x44000000);
        rf.set(x(-1.15f), y(-0.04f), x(1.15f), y(0.05f));
        c.drawOval(rf, f);

        // cape flows behind everything
        if (withRider && acs == 3) {
            poly(c, shade(accCol, 0.8f), -0.2f, -1.62f, -0.95f, -1.5f + wave * 0.05f, -1.15f, -1.25f + wave * 0.08f,
                    -0.85f, -1.0f + wave * 0.05f, -0.3f, -1.1f);
        }

        // wheels
        drawWheel(c, -0.62f, -0.33f, 0.33f, t);
        drawWheel(c, 0.68f, -0.36f, 0.36f, t);
        ring(c, 0.68f, -0.36f, 0.13f, 0.03f, 0xFF9E9E9E); // front disc
        ring(c, -0.62f, -0.33f, 0.1f, 0.025f, 0xFF9E9E9E);

        // swingarm + shock
        line(c, -0.62f, -0.33f, -0.08f, -0.5f, 0.08f, frame);
        line(c, -0.32f, -0.45f, -0.18f, -0.82f, 0.06f, 0xFFE0B526);

        // exhaust
        line(c, 0.2f, -0.45f, -0.2f, -0.38f, 0.06f, 0xFF707070);
        line(c, -0.2f, -0.38f, -0.4f, -0.58f, 0.06f, 0xFF707070);
        line(c, -0.42f, -0.6f, -0.72f, -0.75f, 0.13f, 0xFF4A4A4A);
        line(c, -0.45f, -0.62f, -0.7f, -0.74f, 0.06f, 0xFF8A8A8A);

        // engine + skid plate
        rrect(c, -0.12f, -0.75f, 0.32f, -0.3f, 0.06f, 0xFF3A3A3A);
        for (int i = 0; i < 4; i++) line(c, 0.04f, -0.7f + i * 0.08f, 0.28f, -0.66f + i * 0.08f, 0.015f, 0xFF5A5A5A);
        circ(c, 0.02f, -0.45f, 0.08f, 0xFF555555);
        poly(c, 0xFFB0B0B0, -0.1f, -0.33f, 0.4f, -0.4f, 0.36f, -0.28f, -0.05f, -0.25f);

        // frame
        line(c, 0.42f, -1.05f, -0.08f, -0.5f, 0.05f, frame);
        line(c, 0.42f, -1.05f, 0.0f, -0.8f, 0.05f, frame);
        line(c, -0.1f, -0.95f, -0.8f, -0.97f, 0.05f, frame);
        line(c, -0.08f, -0.5f, -0.5f, -0.95f, 0.04f, frame);

        // forks (USD: gold upper, black lower)
        line(c, 0.68f, -0.36f, 0.53f, -0.78f, 0.08f, 0xFF222222);
        line(c, 0.53f, -0.78f, 0.42f, -1.12f, 0.1f, 0xFFC9A55C);

        // rear tail section + tail light
        poly(c, tank, -0.35f, -0.97f, -0.98f, -0.93f, -0.98f, -0.86f, -0.45f, -0.87f);
        rrect(c, -1.0f, -0.92f, -0.92f, -0.86f, 0.02f, 0xFFE53935);
        line(c, -0.85f, -0.86f, -0.95f, -0.62f, 0.04f, 0xFF222222); // hugger
        line(c, -0.5f, -1.03f, -0.95f, -1.0f, 0.03f, frame);       // rack

        // panniers
        if (acs == 5) {
            rrect(c, -1.02f, -1.02f, -0.45f, -0.6f, 0.05f, accCol);
            rrect(c, -1.02f, -1.02f, -0.45f, -0.95f, 0.03f, 0xFFB0B7BF);
            line(c, -0.98f, -0.78f, -0.5f, -0.78f, 0.02f, shade(accCol, 0.7f));
        }

        // seat
        poly(c, 0xFF2B2420, -0.78f, -0.96f, -0.4f, -1.03f, -0.05f, -1.02f, 0.02f, -0.95f, -0.78f, -0.9f);

        // tank + crash cage + graphic
        poly(c, tank, -0.05f, -0.98f, 0.12f, -1.17f, 0.44f, -1.13f, 0.44f, -0.93f, 0.12f, -0.82f, -0.05f, -0.86f);
        poly(c, acc, 0.05f, -1.02f, 0.4f, -1.06f, 0.4f, -1.0f, 0.05f, -0.96f);
        poly(c, shade(tank, 1.25f), 0.12f, -1.15f, 0.42f, -1.12f, 0.42f, -1.09f, 0.14f, -1.11f);
        line(c, 0.0f, -0.85f, 0.26f, -1.2f, 0.035f, frame);
        line(c, 0.26f, -1.2f, 0.46f, -1.12f, 0.035f, frame);

        // beak fender + headlight + screen + dash
        poly(c, acc, 0.48f, -1.0f, 0.82f, -0.9f, 0.8f, -0.86f, 0.52f, -0.9f);
        poly(c, 0x6690A4AE, 0.44f, -1.18f, 0.38f, -1.48f, 0.47f, -1.5f, 0.55f, -1.2f);
        circ(c, 0.52f, -1.1f, 0.1f, 0xFF1A1A1A);
        circ(c, 0.53f, -1.1f, 0.07f, 0xFFFFF8E1);
        ring(c, 0.53f, -1.1f, 0.05f, 0.012f, 0xFF90CAF9);
        circ(c, 0.4f, -1.23f, 0.045f, 0xFF111111);
        circ(c, 0.4f, -1.23f, 0.03f, 0xFF4CC9F0);

        // handlebar + handguard
        line(c, 0.38f, -1.15f, 0.3f, -1.27f, 0.04f, 0xFF222222);
        poly(c, acc, 0.24f, -1.3f, 0.36f, -1.32f, 0.38f, -1.24f, 0.28f, -1.24f);

        if (withRider) drawSideRider(c, r, t, wave);
    }

    private static void drawWheel(Canvas c, float ux, float uy, float rad, float t) {
        circ(c, ux, uy, rad, 0xFF151515);
        // knobbies
        f.setColor(0xFF2A2A2A);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20 + t * 8;
            float kx = ux + (float) Math.cos(a) * rad * 0.93f, ky = uy + (float) Math.sin(a) * rad * 0.93f;
            c.drawCircle(x(kx), y(ky), rad * 0.07f * S, f);
        }
        circ(c, ux, uy, rad * 0.76f, 0xFF262626);
        ring(c, ux, uy, rad * 0.74f, 0.025f, 0xFFB8B8B8);
        st.setColor(0xFF9A9A9A);
        st.setStrokeWidth(0.008f * S);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + t * 8;
            c.drawLine(x(ux), y(uy), x(ux + (float) Math.cos(a) * rad * 0.72f), y(uy + (float) Math.sin(a) * rad * 0.72f), st);
        }
        circ(c, ux, uy, rad * 0.16f, 0xFF777777);
    }

    private static void drawSideRider(Canvas c, Rider r, float t, float wave) {
        int jacket = r.col(Rider.JACKET), accent = r.col(Rider.ACCENT), pants = r.col(Rider.PANTS);
        int boots = r.col(Rider.BOOTS), gloves = r.col(Rider.GLOVES), skin = r.col(Rider.SKIN);
        int helm = r.col(Rider.HELMET_COL), stripe = r.col(Rider.STRIPE), visor = r.col(Rider.VISOR);
        int accCol = r.col(Rider.ACC_COL);
        int acs = r.v[Rider.ACCESSORY];

        // backpack / bottle pack behind torso
        if (acs == 1 || acs == 4) {
            if (acs == 4) {
                rrect(c, -0.42f, -1.82f, -0.35f, -1.5f, 0.02f, 0xAA4CC9F0);
                rrect(c, -0.33f, -1.78f, -0.26f, -1.5f, 0.02f, 0xAA8BE0A4);
                circ(c, -0.385f, -1.84f, 0.025f, 0xFFF2F2F2);
                circ(c, -0.295f, -1.8f, 0.025f, 0xFFF2F2F2);
            }
            rrect(c, -0.47f, -1.62f, -0.2f, -1.12f, 0.06f, accCol);
            rrect(c, -0.47f, -1.4f, -0.3f, -1.18f, 0.04f, shade(accCol, 0.75f));
        }

        // legs
        line(c, -0.25f, -1.05f, 0.12f, -0.84f, 0.2f, pants);
        line(c, 0.12f, -0.84f, 0.0f, -0.6f, 0.16f, pants);
        line(c, 0.04f, -0.78f, -0.01f, -0.6f, 0.17f, boots);
        rrect(c, -0.1f, -0.64f, 0.14f, -0.5f, 0.04f, boots);

        // torso with pattern
        path.reset();
        path.moveTo(x(-0.36f), y(-1.05f));
        path.lineTo(x(-0.22f), y(-1.66f));
        path.lineTo(x(0.04f), y(-1.62f));
        path.lineTo(x(-0.06f), y(-1.0f));
        path.close();
        f.setColor(jacket);
        c.drawPath(path, f);
        c.save();
        c.clipPath(path);
        pattern(c, r.v[Rider.PATTERN], -0.36f, -1.66f, 0.04f, -1.0f, jacket, accent, false);
        c.restore();
        rrect(c, -0.38f, -1.12f, -0.04f, -1.0f, 0.03f, shade(jacket, 0.7f)); // belt line

        // arm
        line(c, -0.1f, -1.56f, 0.12f, -1.4f, 0.13f, jacket);
        line(c, 0.12f, -1.4f, 0.3f, -1.28f, 0.11f, jacket);
        line(c, 0.0f, -1.5f, 0.12f, -1.41f, 0.03f, accent);
        circ(c, 0.31f, -1.27f, 0.06f, gloves);

        // scarf
        if (acs == 2) {
            rrect(c, -0.16f, -1.72f, 0.02f, -1.64f, 0.03f, accCol);
            path.reset();
            path.moveTo(x(-0.14f), y(-1.68f));
            for (int i = 1; i <= 8; i++) {
                float px = -0.14f - i * 0.08f;
                float py = -1.68f + (float) Math.sin(t * 12 + i * 0.9f) * 0.03f * i / 4f + i * 0.01f;
                path.lineTo(x(px), y(py));
            }
            st.setColor(accCol);
            st.setStrokeWidth(0.06f * S);
            c.drawPath(path, st);
        }

        // head / helmet
        int helmetType = r.v[Rider.HELMET];
        line(c, -0.08f, -1.62f, -0.06f, -1.7f, 0.08f, skin);
        circ(c, -0.04f, -1.85f, 0.17f, helm);
        // stripe over the crown
        st.setColor(stripe);
        st.setStrokeWidth(0.04f * S);
        rf.set(x(-0.19f), y(-2.0f), x(0.11f), y(-1.7f));
        c.drawArc(rf, 200, 110, false, st);
        if (helmetType == 2) {
            // retro: open face, goggles, small brim
            circ(c, 0.06f, -1.8f, 0.09f, skin);
            rrect(c, -0.05f, -1.9f, 0.16f, -1.84f, 0.02f, 0xFF333333);
            rrect(c, 0.04f, -1.9f, 0.15f, -1.83f, 0.03f, visor);
            poly(c, helm, -0.08f, -1.98f, 0.16f, -1.97f, 0.16f, -1.94f, -0.06f, -1.95f);
            line(c, 0.02f, -1.74f, 0.1f, -1.74f, 0.012f, shade(skin, 0.6f));
        } else {
            // chin bar + visor
            poly(c, helm, 0.02f, -1.78f, 0.15f, -1.76f, 0.12f, -1.68f, -0.02f, -1.7f);
            rrect(c, 0.0f, -1.93f, 0.14f, -1.79f, 0.04f, visor);
            line(c, 0.04f, -1.9f, 0.11f, -1.9f, 0.012f, 0x88FFFFFF);
            if (helmetType == 1) {
                poly(c, stripe, -0.04f, -1.99f, 0.25f, -1.97f, 0.22f, -1.92f, -0.02f, -1.95f);
                poly(c, helm, 0.1f, -1.74f, 0.2f, -1.72f, 0.12f, -1.68f);
            }
        }
    }

    // =====================================================================
    // REAR VIEW (riding away from the camera)
    // =====================================================================

    private static Typeface bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    public static void drawRear(Canvas c, Rider r, float cx, float gy, float s, float lean, boolean brake, float t, float night) {
        X = cx; Y0 = gy; S = s;
        int[] bk = r.bike();
        int tank = bk[0], acc = bk[1];
        int jacket = r.col(Rider.JACKET), accent = r.col(Rider.ACCENT), pants = r.col(Rider.PANTS);
        int boots = r.col(Rider.BOOTS), gloves = r.col(Rider.GLOVES);
        int helm = r.col(Rider.HELMET_COL), stripe = r.col(Rider.STRIPE);
        int accCol = r.col(Rider.ACC_COL);
        int acs = r.v[Rider.ACCESSORY];
        float wave = (float) Math.sin(t * 14);

        // shadow (unrotated)
        f.setColor(0x55000000);
        rf.set(x(-0.45f), y(-0.06f), x(0.45f), y(0.06f));
        c.drawOval(rf, f);

        c.save();
        c.rotate(lean * 18f, cx, gy);

        // tyre
        rrect(c, -0.12f, -0.64f, 0.12f, 0.0f, 0.07f, 0xFF141414);
        for (int i = 0; i < 6; i++) {
            float ty = -0.58f + ((i * 0.1f + t * 3f) % 0.6f);
            rrect(c, -0.1f, ty, 0.1f, ty + 0.03f, 0.01f, 0xFF2A2A2A);
        }

        // exhaust (right side)
        rrect(c, 0.13f, -0.8f, 0.3f, -0.5f, 0.06f, 0xFF4A4A4A);
        circ(c, 0.215f, -0.56f, 0.05f, 0xFF1A1A1A);

        // panniers
        if (acs == 5) {
            rrect(c, -0.58f, -1.02f, -0.2f, -0.52f, 0.05f, accCol);
            rrect(c, 0.2f, -1.02f, 0.58f, -0.52f, 0.05f, accCol);
            rrect(c, -0.58f, -1.02f, -0.2f, -0.95f, 0.03f, 0xFFB0B7BF);
            rrect(c, 0.2f, -1.02f, 0.58f, -0.95f, 0.03f, 0xFFB0B7BF);
        }

        // tank sides visible past the rider's knees
        f.setColor(tank);
        rf.set(x(-0.42f), y(-1.2f), x(-0.16f), y(-0.92f));
        c.drawOval(rf, f);
        rf.set(x(0.16f), y(-1.2f), x(0.42f), y(-0.92f));
        c.drawOval(rf, f);

        // fender, plate, tail light
        poly(c, 0xFF222222, -0.15f, -0.95f, 0.15f, -0.95f, 0.1f, -0.66f, -0.1f, -0.66f);
        rrect(c, -0.13f, -0.74f, 0.13f, -0.62f, 0.015f, 0xFFF5F5F5);
        f.setColor(0xFF222222);
        f.setTextAlign(Paint.Align.CENTER);
        f.setTypeface(bold);
        f.setTextSize(0.075f * S);
        c.drawText("KJR 450", x(0), y(-0.655f), f);
        int tail = brake ? 0xFFFF1744 : 0xFFB71C1C;
        if (brake || night > 0.3f) {
            f.setColor(brake ? 0x66FF1744 : 0x33FF1744);
            c.drawCircle(x(0), y(-0.84f), (brake ? 0.3f : 0.18f) * S, f);
        }
        rrect(c, -0.11f, -0.88f, 0.11f, -0.8f, 0.03f, tail);
        circ(c, -0.2f, -0.84f, 0.03f, 0xFFFF9800);
        circ(c, 0.2f, -0.84f, 0.03f, 0xFFFF9800);
        rrect(c, -0.17f, -1.04f, 0.17f, -0.95f, 0.03f, 0xFF2B2420); // seat tail

        // handlebar, mirrors, handguards
        line(c, -0.58f, -1.33f, 0.58f, -1.33f, 0.04f, 0xFF222222);
        line(c, -0.5f, -1.35f, -0.62f, -1.62f, 0.02f, 0xFF333333);
        line(c, 0.5f, -1.35f, 0.62f, -1.62f, 0.02f, 0xFF333333);
        f.setColor(0xFF222222);
        rf.set(x(-0.72f), y(-1.72f), x(-0.54f), y(-1.6f)); c.drawOval(rf, f);
        rf.set(x(0.54f), y(-1.72f), x(0.72f), y(-1.6f)); c.drawOval(rf, f);
        rrect(c, -0.68f, -1.4f, -0.5f, -1.27f, 0.04f, acc);
        rrect(c, 0.5f, -1.4f, 0.68f, -1.27f, 0.04f, acc);

        // legs + boots
        line(c, -0.16f, -1.05f, -0.33f, -0.86f, 0.18f, pants);
        line(c, 0.16f, -1.05f, 0.33f, -0.86f, 0.18f, pants);
        line(c, -0.33f, -0.86f, -0.3f, -0.6f, 0.15f, pants);
        line(c, 0.33f, -0.86f, 0.3f, -0.6f, 0.15f, pants);
        rrect(c, -0.39f, -0.72f, -0.21f, -0.48f, 0.04f, boots);
        rrect(c, 0.21f, -0.72f, 0.39f, -0.48f, 0.04f, boots);

        // arms (behind torso edges)
        line(c, -0.27f, -1.55f, -0.46f, -1.42f, 0.13f, jacket);
        line(c, -0.46f, -1.42f, -0.55f, -1.34f, 0.11f, jacket);
        line(c, 0.27f, -1.55f, 0.46f, -1.42f, 0.13f, jacket);
        line(c, 0.46f, -1.42f, 0.55f, -1.34f, 0.11f, jacket);
        circ(c, -0.57f, -1.33f, 0.065f, gloves);
        circ(c, 0.57f, -1.33f, 0.065f, gloves);

        // torso
        path.reset();
        path.moveTo(x(-0.22f), y(-1.0f));
        path.lineTo(x(-0.32f), y(-1.58f));
        path.quadTo(x(0), y(-1.7f), x(0.32f), y(-1.58f));
        path.lineTo(x(0.22f), y(-1.0f));
        path.close();
        f.setColor(jacket);
        c.drawPath(path, f);
        c.save();
        c.clipPath(path);
        pattern(c, r.v[Rider.PATTERN], -0.32f, -1.68f, 0.32f, -1.0f, jacket, accent, true);
        rrect(c, -0.32f, -1.08f, 0.32f, -1.0f, 0, shade(jacket, 0.7f));
        c.restore();
        rrect(c, -0.33f, -1.62f, -0.2f, -1.48f, 0.05f, accent); // shoulder armour
        rrect(c, 0.2f, -1.62f, 0.33f, -1.48f, 0.05f, accent);

        boolean pack = acs == 1 || acs == 4;
        if (!pack && acs != 3) backName(c, r.name, -1.32f, dark(jacket) ? 0xFFFFFFFF : 0xFF111111, accent);

        // helmet
        line(c, 0, -1.62f, 0, -1.68f, 0.12f, shade(jacket, 0.8f));
        circ(c, 0, -1.83f, 0.2f, helm);
        c.save();
        f.setColor(0);
        path.reset();
        path.addCircle(x(0), y(-1.83f), 0.2f * S, Path.Direction.CW);
        c.clipPath(path);
        rrect(c, -0.045f, -2.05f, 0.045f, -1.6f, 0, stripe);
        if (r.v[Rider.HELMET] == 2) rrect(c, -0.25f, -1.86f, 0.25f, -1.81f, 0, 0xFF333333); // goggle strap
        f.setColor(0x33FFFFFF);
        c.drawCircle(x(-0.07f), y(-1.92f), 0.07f * S, f);
        c.restore();
        if (r.v[Rider.HELMET] == 1) {
            poly(c, stripe, -0.2f, -2.0f, 0.2f, -2.0f, 0.14f, -2.08f, -0.14f, -2.08f);
        }

        // accessories on top
        if (pack) {
            if (acs == 4) {
                rrect(c, -0.3f, -1.78f, -0.2f, -1.48f, 0.03f, 0xCC4CC9F0);
                rrect(c, 0.2f, -1.76f, 0.3f, -1.48f, 0.03f, 0xCC8BE0A4);
                circ(c, -0.25f, -1.8f, 0.035f, 0xFFF2F2F2);
                circ(c, 0.25f, -1.78f, 0.035f, 0xFFF2F2F2);
            }
            rrect(c, -0.21f, -1.58f, 0.21f, -1.08f, 0.07f, accCol);
            rrect(c, -0.16f, -1.3f, 0.16f, -1.12f, 0.04f, shade(accCol, 0.75f));
            backName(c, r.name, -1.4f, dark(accCol) ? 0xFFFFFFFF : 0xFF111111, 0);
        } else if (acs == 3) {
            path.reset();
            path.moveTo(x(-0.3f), y(-1.6f));
            path.quadTo(x(0), y(-1.66f), x(0.3f), y(-1.6f));
            path.lineTo(x(0.42f + wave * 0.04f), y(-0.92f));
            path.quadTo(x(0.1f), y(-0.86f + wave * 0.04f), x(-0.42f + wave * 0.04f), y(-0.92f));
            path.close();
            f.setColor(accCol);
            c.drawPath(path, f);
            backName(c, r.name, -1.3f, dark(accCol) ? 0xFFFFFFFF : 0xFF111111, 0);
        } else if (acs == 2) {
            rrect(c, -0.12f, -1.68f, 0.12f, -1.6f, 0.03f, accCol);
            line(c, 0.05f, -1.64f, 0.2f + wave * 0.03f, -1.4f, 0.06f, accCol);
            line(c, 0.0f, -1.64f, 0.1f - wave * 0.03f, -1.36f, 0.05f, shade(accCol, 0.85f));
        }
        c.restore();
    }

    private static void backName(Canvas c, String name, float uy, int col, int outline) {
        String n = name.toUpperCase();
        f.setTypeface(bold);
        f.setTextAlign(Paint.Align.CENTER);
        float size = Math.min(0.13f, 0.5f / Math.max(4, n.length()) * 1.6f);
        f.setTextSize(size * S);
        if (outline != 0) {
            st.setColor(outline);
            st.setStrokeWidth(0.02f * S);
            st.setTypeface(bold);
            st.setTextAlign(Paint.Align.CENTER);
            st.setTextSize(size * S);
            c.drawText(n, x(0), y(uy), st);
        }
        f.setColor(col);
        c.drawText(n, x(0), y(uy), f);
    }
}
