package com.rovtech.joyride;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.List;
import java.util.Random;

/** Pseudo-3D (OutRun-style) mountain road: track, sky, scenery and traffic projection. */
public class Road {
    public static final int SEG = 200, N = 4000, DRAW = 170;
    public static final float RW = 1100f, CAM_H = 1000f, DEPTH = 0.84f, PLAYER_D = 600f;
    public static final float UNITS_PER_M = 150f;
    public static final float[] LANES = {-0.75f, -0.25f, 0.25f, 0.75f}; // first two are oncoming

    // scenery types
    static final byte NONE = 0, PINE = 1, ROCK = 2, FLAGS = 3, MILESTONE = 4, SIGN = 5, HUT = 6, CHEVRON = 7, PINE2 = 8;

    final float[] curve = new float[N];
    final byte[] scenL = new byte[N], scenR = new byte[N];
    final float[] scenLX = new float[N], scenRX = new float[N];

    private final float[] sx = new float[DRAW + 2], sy = new float[DRAW + 2], hw = new float[DRAW + 2], sc = new float[DRAW + 2];
    private final boolean[] ok = new boolean[DRAW + 2];

    private int w, h;
    private float horizon, kx, ky;
    private float skyOff;
    private final float[] mtnFar = new float[96], mtnNear = new float[96];
    private final float[] stars = new float[120];

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), path2 = new Path();
    private final RectF rf = new RectF();
    private final Typeface bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    // palette (lerped through the time-of-day cycle)
    private static final int[][] PAL = {
            // skyTop, skyBot, mtnFar, mtnNear, snow, grassA, grassB, fog
            {0xFF2F6FD0, 0xFFB8E4FA, 0xFF8EA6C2, 0xFF5D7390, 0xFFF5F9FF, 0xFF6B8E3D, 0xFF628339, 0xFFCFE6F5},
            {0xFF2D1B4E, 0xFFFF8C42, 0xFF8D5A7A, 0xFF4A3550, 0xFFFFC9A8, 0xFF56702F, 0xFF4E672B, 0xFFF0A070},
            {0xFF04060E, 0xFF1A2440, 0xFF2A3350, 0xFF161C30, 0xFF8D9BBF, 0xFF33452A, 0xFF2E4026, 0xFF1A2440},
    };
    private static final float[] DARK = {0f, 0.22f, 0.58f};
    private final int[] pal = new int[8];
    public float darkness;

    public Road(long seed) {
        Random r = new Random(seed);
        // ---- curves ----
        int i = 0;
        while (i < 80) curve[i++] = 0;
        while (i < N - 120) {
            int len = 60 + r.nextInt(160);
            boolean straight = r.nextFloat() < 0.3f;
            float amt = straight ? 0 : (r.nextFloat() * 2 - 1) * 5.5f;
            for (int k = 0; k < len && i < N - 120; k++, i++) {
                float t = (float) k / len;
                float ease = t < 0.25f ? t / 0.25f : (t > 0.75f ? (1 - t) / 0.25f : 1f);
                curve[i] = amt * ease * ease * (3 - 2 * ease);
            }
        }
        while (i < N) curve[i++] = 0;

        // ---- scenery ----
        for (i = 0; i < N; i++) {
            float roll = r.nextFloat();
            if (i % 750 == 0) { scenR[i] = MILESTONE; scenRX[i] = 1.12f; }
            else if (i % 420 == 210) { scenL[i] = SIGN; scenLX[i] = -1.75f; }
            else if (Math.abs(curve[i]) > 2.5f && i % 8 == 0) {
                if (curve[i] > 0) { scenL[i] = CHEVRON; scenLX[i] = -1.1f; }
                else { scenR[i] = CHEVRON; scenRX[i] = 1.1f; }
            } else if (i % 37 == 0) { scenL[i] = FLAGS; scenLX[i] = -1.2f - r.nextFloat() * 0.3f; }
            else if (roll < 0.03f) { scenR[i] = HUT; scenRX[i] = 1.9f + r.nextFloat(); }
            else if (roll < 0.09f) { scenL[i] = ROCK; scenLX[i] = -1.2f - r.nextFloat() * 1.5f; }
            if (scenL[i] == NONE && r.nextFloat() < 0.32f) {
                scenL[i] = r.nextBoolean() ? PINE : PINE2; scenLX[i] = -1.3f - r.nextFloat() * 2.2f;
            }
            if (scenR[i] == NONE && r.nextFloat() < 0.32f) {
                scenR[i] = r.nextBoolean() ? PINE : PINE2; scenRX[i] = 1.3f + r.nextFloat() * 2.2f;
            }
        }
        // ---- mountains + stars ----
        for (i = 0; i < mtnFar.length; i++) {
            mtnFar[i] = 0.35f + 0.65f * (float) Math.abs(Math.sin(i * 0.37) * Math.cos(i * 0.13)) + r.nextFloat() * 0.08f;
            mtnNear[i] = 0.2f + 0.5f * (float) Math.abs(Math.sin(i * 0.23 + 1) * Math.sin(i * 0.071)) + r.nextFloat() * 0.06f;
        }
        for (i = 0; i < stars.length; i++) stars[i] = r.nextFloat();
    }

    public void layout(int width, int height) {
        w = width; h = height;
        horizon = h * 0.40f;
        kx = w * 0.5f;
        ky = kx * 1.4f;
    }

    public float curveAt(double z) {
        int i = (int) Math.floor(z / SEG) % N;
        if (i < 0) i += N;
        return curve[i];
    }

    public float playerScreenY() { return horizon + (DEPTH / PLAYER_D) * CAM_H * ky; }
    /** pixels per world unit at the player's depth */
    public float playerPxPerUnit() { return DEPTH / PLAYER_D * kx; }
    public float horizonY() { return horizon; }

    private static int lerp(int a, int b, float t) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** phase in [0,3): 0 day, 1 dusk, 2 night, wraps back to day. */
    private void setPhase(float phase) {
        int a = (int) Math.floor(phase) % 3, b = (a + 1) % 3;
        float t = phase - (float) Math.floor(phase);
        t = t < 0.75f ? 0 : (t - 0.75f) / 0.25f; // hold each palette, then blend
        for (int i = 0; i < 8; i++) pal[i] = lerp(PAL[a][i], PAL[b][i], t);
        darkness = DARK[a] + (DARK[b] - DARK[a]) * t;
    }

    // ------------------------------------------------------------------

    /** Renders sky, road, scenery and cars. Returns nothing; car screen data is written into each car. */
    public void render(Canvas c, double camZ, float playerX, List<Vehicles.Car> cars, float phase, float speedFrac) {
        setPhase(phase);
        int base = (int) Math.floor(camZ / SEG);
        float pct = (float) (camZ / SEG - base);
        float camX = playerX * RW;

        // projection
        float x = 0, dx = -curve[Math.floorMod(base, N)] * pct;
        for (int k = 0; k <= DRAW; k++) {
            float z = (k - pct) * SEG;
            ok[k] = z > 20;
            float s = ok[k] ? DEPTH / z : 0;
            sc[k] = s;
            sx[k] = w / 2f + s * (x - camX) * kx;
            sy[k] = horizon + s * CAM_H * ky;
            hw[k] = s * RW * kx;
            x += dx;
            dx += curve[Math.floorMod(base + k, N)];
        }
        // sky scrolls with curvature
        skyOff += curve[Math.floorMod(base, N)] * speedFrac * 0.0025f;

        drawSky(c);

        int first = 0;
        while (first < DRAW && !ok[first]) first++;

        // grass stripes
        p.setShader(null);
        p.setColor(pal[5]);
        c.drawRect(0, horizon, w, h, p);
        p.setColor(pal[6]);
        for (int k = first; k < DRAW; k++) {
            if (((base + k) / 4) % 2 == 0) c.drawRect(0, sy[k + 1], w, sy[k] + 1, p);
        }
        // rumble strip
        strip(path, -1.12f, 1.12f, first, DRAW);
        p.setColor(0xFFEDEDED);
        c.drawPath(path, p);
        path.reset();
        for (int k = first; k < DRAW; k++) {
            if (((base + k) / 3) % 2 == 0) {
                quad(path, k, -1.12f, -1.0f);
                quad(path, k, 1.0f, 1.12f);
            }
        }
        p.setColor(0xFFC62828);
        c.drawPath(path, p);
        // asphalt
        strip(path, -1.0f, 1.0f, first, DRAW);
        p.setColor(0xFF4C5056);
        c.drawPath(path, p);
        path.reset();
        for (int k = first; k < DRAW; k++) if (((base + k) / 4) % 2 == 0) quad(path, k, -1f, 1f);
        p.setColor(0xFF484C52);
        c.drawPath(path, p);
        // edge lines, centre double yellow
        path.reset();
        addStrip(path, -0.96f, -0.93f, first, DRAW);
        addStrip(path, 0.93f, 0.96f, first, DRAW);
        p.setColor(0xFFF2F2F2);
        c.drawPath(path, p);
        path.reset();
        addStrip(path, -0.035f, -0.012f, first, DRAW);
        addStrip(path, 0.012f, 0.035f, first, DRAW);
        p.setColor(0xFFFFC107);
        c.drawPath(path, p);
        // dashed lane lines
        path.reset();
        for (int k = first; k < DRAW; k++) {
            if (((base + k) / 6) % 2 == 0) {
                quad(path, k, -0.515f, -0.495f);
                quad(path, k, 0.495f, 0.515f);
            }
        }
        p.setColor(0xFFF2F2F2);
        c.drawPath(path, p);

        // distance haze
        float hazeH = h * 0.09f;
        p.setShader(new LinearGradient(0, horizon, 0, horizon + hazeH, pal[7], pal[7] & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, horizon + hazeH, p);
        p.setShader(null);

        // project cars into segment slots
        for (Vehicles.Car car : cars) {
            double rel = car.z - camZ;
            car.visible = false;
            if (rel < 30 || rel >= (DRAW - 1) * SEG) continue;
            float kf = (float) (rel / SEG) + pct;
            int k = (int) kf;
            float t = kf - k;
            if (!ok[k] || !ok[k + 1]) continue;
            float s = sc[k] + (sc[k + 1] - sc[k]) * t;
            float cxs = sx[k] + (sx[k + 1] - sx[k]) * t;
            car.sx = cxs + s * car.x * RW * kx;
            car.sy = sy[k] + (sy[k + 1] - sy[k]) * t;
            car.scale = s * kx;
            car.slot = k;
            car.visible = true;
        }

        // sprites back to front
        for (int k = DRAW - 1; k >= first; k--) {
            int i = Math.floorMod(base + k, N);
            float px = sc[k] * kx;
            if (scenL[i] != NONE) drawScenery(c, scenL[i], sx[k] + scenLX[i] * RW * px, sy[k], px, i);
            if (scenR[i] != NONE) drawScenery(c, scenR[i], sx[k] + scenRX[i] * RW * px, sy[k], px, i);
            for (Vehicles.Car car : cars) {
                if (car.visible && car.slot == k) Vehicles.draw(c, car, darkness);
            }
        }
    }

    /** Darkens the ground for dusk/night; call after drawing the world and the bike. */
    public void drawNightOverlay(Canvas c, List<Vehicles.Car> cars, float bikeX, float bikeY, float bikeScale) {
        if (darkness <= 0.01f) return;
        p.setShader(null);
        p.setColor(Color.argb((int) (darkness * 255), 4, 6, 18));
        c.drawRect(0, horizon - 1, w, h, p);
        // bike headlight beam
        float a = Math.min(1f, darkness * 1.6f);
        path.reset();
        path.moveTo(bikeX - bikeScale * 0.3f, bikeY - bikeScale * 1.0f);
        path.lineTo(bikeX + bikeScale * 0.3f, bikeY - bikeScale * 1.0f);
        path.lineTo(w / 2f + w * 0.35f, horizon + h * 0.08f);
        path.lineTo(w / 2f - w * 0.35f, horizon + h * 0.08f);
        path.close();
        p.setShader(new LinearGradient(0, bikeY, 0, horizon + h * 0.08f,
                Color.argb((int) (110 * a), 255, 244, 200), Color.argb(0, 255, 244, 200), Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        for (Vehicles.Car car : cars) if (car.visible) Vehicles.drawLights(c, car, darkness);
    }

    // ---- road geometry helpers ----

    private void strip(Path pt, float a, float b, int from, int to) {
        pt.reset();
        addStrip(pt, a, b, from, to);
    }

    private void addStrip(Path pt, float a, float b, int from, int to) {
        pt.moveTo(sx[from] + a * hw[from], sy[from]);
        for (int k = from + 1; k <= to; k++) pt.lineTo(sx[k] + a * hw[k], sy[k]);
        for (int k = to; k >= from; k--) pt.lineTo(sx[k] + b * hw[k], sy[k]);
        pt.close();
    }

    private void quad(Path pt, int k, float a, float b) {
        pt.moveTo(sx[k] + a * hw[k], sy[k]);
        pt.lineTo(sx[k + 1] + a * hw[k + 1], sy[k + 1]);
        pt.lineTo(sx[k + 1] + b * hw[k + 1], sy[k + 1]);
        pt.lineTo(sx[k] + b * hw[k], sy[k]);
        pt.close();
    }

    // ---- sky ----

    private void drawSky(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, horizon, pal[0], pal[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon + 2, p);
        p.setShader(null);
        // stars at night
        if (darkness > 0.3f) {
            p.setColor(Color.argb((int) (Math.min(1, (darkness - 0.3f) * 3) * 220), 255, 255, 255));
            for (int i = 0; i < stars.length; i += 2) {
                float stx = ((stars[i] - skyOff * 0.05f) % 1 + 1) % 1 * w;
                c.drawCircle(stx, stars[i + 1] * horizon * 0.8f, 2.2f, p);
            }
        }
        // sun or moon
        float sunX = w * 0.72f - (skyOff * 0.1f % 1) * 0;
        if (darkness < 0.4f) {
            p.setColor(darkness > 0.1f ? 0xFFFFB067 : 0xFFFFF4C2);
            c.drawCircle(sunX, horizon * (0.35f + darkness * 1.2f), w * 0.07f, p);
        } else {
            p.setColor(0xFFE8ECF5);
            c.drawCircle(w * 0.25f, horizon * 0.3f, w * 0.05f, p);
            p.setColor(pal[0]);
            c.drawCircle(w * 0.25f + w * 0.022f, horizon * 0.3f - w * 0.01f, w * 0.045f, p);
        }
        drawMountains(c, mtnFar, 0.3f, h * 0.2f, pal[2], true);
        drawMountains(c, mtnNear, 0.6f, h * 0.11f, pal[3], false);
    }

    private void drawMountains(Canvas c, float[] ht, float parallax, float maxH, int col, boolean snow) {
        int n = ht.length;
        float span = w * 2.2f, step = span / n;
        float off = ((skyOff * parallax * w) % span + span) % span;
        path.reset();
        path.moveTo(-off - step, horizon + 2);
        if (snow) path2.reset();
        float snowLine = horizon - maxH * 0.62f;
        boolean inSnow = false;
        for (int rep = 0; rep < 2; rep++) {
            for (int i = 0; i <= n; i++) {
                float px = -off + rep * span + i * step;
                float py = horizon - ht[i % n] * maxH;
                path.lineTo(px, py);
                if (snow) {
                    if (py < snowLine) {
                        if (!inSnow) { path2.moveTo(px - step * 0.5f, snowLine); inSnow = true; }
                        path2.lineTo(px, py);
                    } else if (inSnow) {
                        path2.lineTo(px - step * 0.5f, snowLine);
                        path2.close();
                        inSnow = false;
                    }
                }
            }
        }
        if (inSnow) path2.close();
        path.lineTo(-off + span * 2 + step, horizon + 2);
        path.close();
        p.setColor(col);
        c.drawPath(path, p);
        if (snow) {
            p.setColor(pal[4]);
            c.drawPath(path2, p);
        }
    }

    // ---- scenery sprites ----

    private void drawScenery(Canvas c, byte type, float x, float y, float px, int seg) {
        switch (type) {
            case PINE: case PINE2: {
                float tw = 700 * px * (type == PINE2 ? 0.8f : 1f), th = 1700 * px * (type == PINE2 ? 1.25f : 1f);
                if (th < 2) return;
                p.setColor(0xFF4E342E);
                c.drawRect(x - tw * 0.06f, y - th * 0.18f, x + tw * 0.06f, y, p);
                int dk = type == PINE ? 0xFF1F4D2B : 0xFF173F2A, lt = type == PINE ? 0xFF2E6B3A : 0xFF235A38;
                for (int i = 0; i < 3; i++) {
                    float bot = y - th * (0.12f + i * 0.25f), top = bot - th * 0.45f, half = tw * (0.5f - i * 0.12f);
                    path.reset();
                    path.moveTo(x, top); path.lineTo(x + half, bot); path.lineTo(x - half, bot); path.close();
                    p.setColor(dk); c.drawPath(path, p);
                    path.reset();
                    path.moveTo(x, top); path.lineTo(x, bot); path.lineTo(x - half, bot); path.close();
                    p.setColor(lt); c.drawPath(path, p);
                }
                if (darkness < 0.3f && seg % 3 == 0) { // snow dusting
                    p.setColor(0xCCFFFFFF);
                    c.drawCircle(x, y - th * 0.95f, tw * 0.05f, p);
                }
                break;
            }
            case ROCK: {
                float rw = 650 * px, rh = 380 * px;
                path.reset();
                path.moveTo(x - rw / 2, y); path.lineTo(x - rw * 0.35f, y - rh * 0.8f);
                path.lineTo(x - rw * 0.05f, y - rh); path.lineTo(x + rw * 0.4f, y - rh * 0.6f);
                path.lineTo(x + rw / 2, y); path.close();
                p.setColor(0xFF7B8189); c.drawPath(path, p);
                path.reset();
                path.moveTo(x - rw * 0.05f, y - rh); path.lineTo(x + rw * 0.4f, y - rh * 0.6f);
                path.lineTo(x + rw / 2, y); path.lineTo(x, y); path.close();
                p.setColor(0xFF5F656C); c.drawPath(path, p);
                break;
            }
            case FLAGS: { // vertical prayer-flag banner on a pole
                float ph = 1700 * px, fw = 170 * px;
                p.setColor(0xFF5D4037);
                c.drawRect(x - fw * 0.12f, y - ph, x + fw * 0.12f, y, p);
                int[] cols = {0xFF1E63C6, 0xFFF2F2F2, 0xFFD62828, 0xFF2E9D4A, 0xFFF4C430};
                float fh = ph * 0.13f;
                for (int i = 0; i < 5; i++) {
                    p.setColor(cols[i]);
                    float top = y - ph + i * fh;
                    c.drawRect(x + fw * 0.12f, top, x + fw * 1.4f, top + fh * 0.92f, p);
                }
                break;
            }
            case MILESTONE: { // Indian highway kilometre stone
                float mw = 260 * px, mh = 380 * px;
                p.setColor(0xFFF5F5F5);
                c.drawRect(x - mw / 2, y - mh * 0.62f, x + mw / 2, y, p);
                p.setColor(0xFFFFC107);
                rf.set(x - mw / 2, y - mh, x + mw / 2, y - mh * 0.26f);
                c.drawArc(rf, 180, 180, true, p);
                if (mw > 18) {
                    p.setColor(0xFF111111);
                    p.setTypeface(bold);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(mw * 0.26f);
                    c.drawText("LEH", x, y - mh * 0.68f, p);
                    p.setTextSize(mw * 0.3f);
                    c.drawText(String.valueOf(seg / 750 * 1 + 7), x, y - mh * 0.25f, p);
                }
                break;
            }
            case SIGN: {
                float bw = 1500 * px, bh = 700 * px, post = 600 * px;
                p.setColor(0xFF616161);
                c.drawRect(x - bw * 0.35f, y - post, x - bw * 0.3f, y, p);
                c.drawRect(x + bw * 0.3f, y - post, x + bw * 0.35f, y, p);
                p.setColor(0xFF14213D);
                c.drawRect(x - bw / 2, y - post - bh, x + bw / 2, y - post, p);
                p.setColor(0xFFF77F00);
                c.drawRect(x - bw / 2, y - post - bh * 0.18f, x + bw / 2, y - post, p);
                if (bw > 40) {
                    p.setTypeface(bold);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setColor(0xFFFFFFFF);
                    p.setTextSize(bh * 0.28f);
                    c.drawText("KEVIN'S JOY RIDE", x, y - post - bh * 0.55f, p);
                    p.setTextSize(bh * 0.13f);
                    p.setColor(0xFF14213D);
                    c.drawText("HIMALAYAN 450", x, y - post - bh * 0.04f, p);
                }
                break;
            }
            case HUT: {
                float hw2 = 1200 * px, hh = 650 * px;
                p.setColor(0xFFE8DCC4);
                c.drawRect(x - hw2 / 2, y - hh, x + hw2 / 2, y, p);
                path.reset();
                path.moveTo(x - hw2 * 0.6f, y - hh); path.lineTo(x, y - hh * 1.6f); path.lineTo(x + hw2 * 0.6f, y - hh); path.close();
                p.setColor((seg & 1) == 0 ? 0xFFB23A48 : 0xFF2B59C3);
                c.drawPath(path, p);
                p.setColor(darkness > 0.3f ? 0xFFFFD54F : 0xFF5D4037);
                c.drawRect(x - hw2 * 0.12f, y - hh * 0.6f, x + hw2 * 0.12f, y, p);
                c.drawRect(x + hw2 * 0.22f, y - hh * 0.7f, x + hw2 * 0.4f, y - hh * 0.45f, p);
                break;
            }
            case CHEVRON: {
                float s = 340 * px;
                p.setColor(0xFF424242);
                c.drawRect(x - s * 0.04f, y - s * 1.3f, x + s * 0.04f, y, p);
                p.setColor(0xFFFFC107);
                c.drawRect(x - s / 2, y - s * 1.6f, x + s / 2, y - s * 0.9f, p);
                p.setColor(0xFF111111);
                boolean right = x < w / 2f; // sign on the left of a right-hand bend points right
                float d = right ? 1 : -1;
                path.reset();
                path.moveTo(x - d * s * 0.25f, y - s * 1.5f);
                path.lineTo(x + d * s * 0.2f, y - s * 1.25f);
                path.lineTo(x - d * s * 0.25f, y - s * 1.0f);
                path.lineTo(x - d * s * 0.1f, y - s * 1.25f);
                path.close();
                c.drawPath(path, p);
                break;
            }
        }
    }
}
