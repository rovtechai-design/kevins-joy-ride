package com.rovtech.joyride;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/** One ride: physics, traffic, scoring and the in-game HUD. */
public class Game {
    public interface Host { void vibrate(int ms); }

    public static final float MAX_KMH = 172f;
    private static final float U_PER_KMH = Road.UNITS_PER_M / 3.6f;
    private static final float MAX_SPEED = MAX_KMH * U_PER_KMH;
    private static final int[] GEAR_KMH = {0, 28, 55, 85, 115, 145, 180};
    private static final double CHECKPOINT = 2000 * Road.UNITS_PER_M;

    private final Road road;
    private final Sound sound;
    private final Host host;
    private final Random rnd = new Random();
    public final ArrayList<Vehicles.Car> cars = new ArrayList<>();

    public Rider rider;
    public int mode;

    // player
    public double z;
    public float x, speed, lean;
    public boolean braking;
    // run state
    public boolean crashed, finished, timeUp;
    public float crashT, runTime, timeLeft;
    public float score, topKmh, combo, comboT, oppTime, phase;
    public int nearMisses, overtakes, bestCombo;
    private double nextCheckpoint;
    private float spawnT, shake, flash, crashSlide, crashDir, offroadRumble;

    private static class Popup { String text; int color; float t, x, y, size; }
    private final ArrayList<Popup> popups = new ArrayList<>();
    private final float[][] sparks = new float[60][5]; // x,y,vx,vy,life

    // touch control rects (screen space), set in layout()
    public final RectF bLeft = new RectF(), bRight = new RectF(), bGas = new RectF(), bBrake = new RectF(), bPause = new RectF();
    private int w, h;
    private final Ui ui;

    public Game(Road road, Sound sound, Host host, Ui ui) {
        this.road = road;
        this.sound = sound;
        this.host = host;
        this.ui = ui;
    }

    public void layout(int width, int height) {
        w = width; h = height;
        float bs = w * 0.2f, pad = w * 0.035f, by = h - bs - h * 0.03f;
        bLeft.set(pad, by, pad + bs, by + bs);
        bRight.set(pad * 2 + bs, by, pad * 2 + bs * 2, by + bs);
        bGas.set(w - pad - bs, by - bs * 0.5f, w - pad, by + bs);
        bBrake.set(w - pad * 2 - bs * 2, by, w - pad * 2 - bs, by + bs);
        bPause.set(w - w * 0.13f, h * 0.035f, w - w * 0.03f, h * 0.035f + w * 0.1f);
    }

    public void start(int mode, Rider rider) {
        this.mode = mode;
        this.rider = rider;
        cars.clear();
        popups.clear();
        z = 0; x = 0.25f; speed = 0; lean = 0;
        crashed = finished = timeUp = false;
        crashT = runTime = 0;
        score = topKmh = combo = comboT = oppTime = 0;
        nearMisses = overtakes = bestCombo = 0;
        timeLeft = 60f;
        nextCheckpoint = CHECKPOINT;
        spawnT = 0; shake = flash = 0;
        phase = 0;
        for (float[] s : sparks) s[4] = 0;
        // a few cars already on the road
        for (int i = 0; i < 6; i++) spawn(4000 + i * 3500);
    }

    public float kmh() { return speed / U_PER_KMH; }
    public float km() { return (float) (z / Road.UNITS_PER_M / 1000.0); }

    public int gear() {
        float k = kmh();
        for (int g = 1; g < GEAR_KMH.length; g++) if (k < GEAR_KMH[g]) return g;
        return 6;
    }

    public float rpm() {
        int g = gear();
        float lo = GEAR_KMH[g - 1], hi = GEAR_KMH[g];
        float t = Math.max(0, Math.min(1, (kmh() - lo) / (hi - lo)));
        return speed < 1 ? 1500 : 3000 + t * 6200;
    }

    // ------------------------------------------------------------------ update

    public void update(float dt, boolean left, boolean right, boolean gas, boolean brake) {
        for (Iterator<Popup> it = popups.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            p.t += dt;
            p.y -= dt * h * 0.05f;
            if (p.t > 1.4f) it.remove();
        }
        for (float[] s : sparks) {
            if (s[4] <= 0) continue;
            s[0] += s[2] * dt; s[1] += s[3] * dt; s[3] += h * 1.5f * dt; s[4] -= dt;
        }
        shake = Math.max(0, shake - dt * 2.5f);
        flash = Math.max(0, flash - dt * 2f);

        if (crashed) {
            crashT += dt;
            speed = Math.max(0, speed - 5000 * dt);
            z += speed * dt;
            crashSlide += dt * crashDir * Math.max(0, 1 - crashT);
            moveTraffic(dt * 0.3f);
            sound.engineOn = false;
            if (crashT > 2.0f) finished = true;
            return;
        }

        runTime += dt;
        braking = brake;
        float sp = speed / MAX_SPEED;

        // throttle / brake / drag
        if (gas) speed += 1550f * (1 - sp * sp) * dt;
        else speed -= (220 + speed * 0.06f) * dt;
        if (brake) speed -= 4200 * dt;
        boolean offroad = Math.abs(x) > 1.0f;
        if (offroad) {
            float offMax = 55 * U_PER_KMH;
            if (speed > offMax) speed -= 3000 * dt;
            offroadRumble += dt;
            if (rnd.nextFloat() < 0.4f) shake = Math.max(shake, 0.12f);
        }
        speed = Math.max(0, Math.min(MAX_SPEED, speed));
        sp = speed / MAX_SPEED;

        // steering + centrifugal force on bends
        float steer = (right ? 1 : 0) - (left ? 1 : 0);
        if (speed > 50) x += steer * dt * (0.7f + 1.1f * sp);
        float curve = road.curveAt(z + Road.PLAYER_D);
        x -= dt * 2 * sp * sp * curve * 0.17f;
        x = Math.max(-1.9f, Math.min(1.9f, x));
        float targetLean = steer * (0.4f + 0.6f * sp) + curve * sp * 0.08f;
        lean += (targetLean - lean) * Math.min(1, dt * 8);

        z += speed * dt;
        float k = kmh();
        topKmh = Math.max(topKmh, k);
        phase = (km() / 3.5f) % 3f;

        // scoring
        boolean opp = x < 0 && k > 80;
        if (opp) oppTime += dt;
        float rate = k * 0.08f * (opp ? 2 : 1) * (k > 140 ? 1.5f : 1);
        score += rate * dt;
        if (comboT > 0) { comboT -= dt; if (comboT <= 0) combo = 0; }

        // time attack
        if (mode == Store.MODE_TIME) {
            timeLeft -= dt;
            if (z >= nextCheckpoint) {
                nextCheckpoint += CHECKPOINT;
                float bonus = Math.max(12, 25 - (float) (z / CHECKPOINT) * 1.5f);
                timeLeft += bonus;
                score += 500;
                popup("CHECKPOINT +" + (int) bonus + "s", 0xFF4CD964, h * 0.3f, 1.3f);
                sound.beep(1320);
            }
            if (timeLeft <= 0) {
                timeLeft = 0;
                timeUp = true;
                finished = true;
                sound.engineOn = false;
                return;
            }
        }

        // traffic
        spawnT -= dt;
        int target = (int) Math.min(22, 7 + km() * 2.2f);
        if (spawnT <= 0 && cars.size() < target) {
            spawn(Road.DRAW * Road.SEG * 0.92f);
            spawnT = 0.25f + rnd.nextFloat() * 0.5f;
        }
        moveTraffic(dt);
        checkTraffic(k);

        sound.engineOn = true;
        sound.rpm = rpm();
        sound.throttle = gas ? 1 : 0;
    }

    private void spawn(float ahead) {
        Vehicles.Car c = new Vehicles.Car();
        int lane = rnd.nextInt(4);
        c.oncoming = lane < 2;
        c.x = c.targetX = Road.LANES[lane];
        float t = rnd.nextFloat();
        c.type = t < 0.42f ? Vehicles.HATCH : t < 0.62f ? Vehicles.SUV : t < 0.78f ? Vehicles.TRUCK : t < 0.88f ? Vehicles.BUS : Vehicles.AUTO;
        c.color = c.type == Vehicles.TRUCK ? Vehicles.TRUCK_PAINT[rnd.nextInt(Vehicles.TRUCK_PAINT.length)]
                : c.type == Vehicles.BUS ? (rnd.nextBoolean() ? 0xFFD62828 : 0xFF1E63C6)
                : Vehicles.PAINT[rnd.nextInt(Vehicles.PAINT.length)];
        float kmhT = c.type == Vehicles.AUTO ? 30 + rnd.nextFloat() * 15
                : c.type >= Vehicles.TRUCK ? 40 + rnd.nextFloat() * 20 : 45 + rnd.nextFloat() * 35;
        c.speed = kmhT * U_PER_KMH * (c.oncoming ? -1 : 1);
        c.z = z + Road.PLAYER_D + ahead;
        for (Vehicles.Car o : cars) {
            if (Math.abs(o.x - c.x) < 0.3f && Math.abs(o.z - c.z) < 3200) return;
        }
        cars.add(c);
    }

    private void moveTraffic(float dt) {
        double pz = z + Road.PLAYER_D;
        for (Iterator<Vehicles.Car> it = cars.iterator(); it.hasNext(); ) {
            Vehicles.Car c = it.next();
            // keep a gap behind slower traffic in the same lane
            for (Vehicles.Car o : cars) {
                if (o == c || o.oncoming != c.oncoming || Math.abs(o.x - c.x) > 0.3f) continue;
                double gap = c.oncoming ? c.z - o.z : o.z - c.z;
                if (gap > 0 && gap < (c.length() + o.length()) * 0.6f + 500) {
                    if (Math.abs(c.speed) > Math.abs(o.speed)) c.speed = o.speed;
                }
            }
            // occasional lane changes among same-direction cars
            if (!c.oncoming && c.blink <= 0 && c.type <= Vehicles.SUV && rnd.nextFloat() < dt * 0.06f
                    && Math.abs(c.z - pz) > 3000) {
                float nx = c.x < 0.5f ? 0.75f : 0.25f;
                boolean free = true;
                for (Vehicles.Car o : cars) if (o != c && Math.abs(o.x - nx) < 0.3f && Math.abs(o.z - c.z) < 2200) free = false;
                if (free) { c.targetX = nx; c.blink = 2.4f; }
            }
            if (c.blink > 0) {
                c.blink -= dt;
                if (c.blink < 1.5f) {
                    float d = c.targetX - c.x;
                    c.x += Math.signum(d) * Math.min(Math.abs(d), 0.35f * dt);
                }
            }
            c.z += c.speed * dt;
            double rel = c.z - pz;
            if (rel < -2500 || rel > Road.DRAW * Road.SEG + 4000) it.remove();
        }
    }

    private void checkTraffic(float kmh) {
        double pz = z + Road.PLAYER_D;
        final float bikeHalfW = 55, bikeHalfL = 180;
        for (Vehicles.Car c : cars) {
            double dz = c.z - pz;
            float lat = Math.abs(c.x - x) * Road.RW - c.width() * 0.44f - bikeHalfW;
            if (Math.abs(dz) < c.length() / 2 + bikeHalfL && lat < 0) {
                crash(c);
                return;
            }
            if (!c.passed && dz < -(c.length() / 2)) {
                c.passed = true;
                if (!c.oncoming) { overtakes++; score += 10; }
                if (lat < 200 && kmh > 80) {
                    nearMisses++;
                    combo = Math.min(combo + 1, 10);
                    bestCombo = Math.max(bestCombo, (int) combo);
                    comboT = 3f;
                    int bonus = (int) (100 * combo * (c.oncoming ? 2 : 1));
                    score += bonus;
                    popup((c.oncoming ? "ONCOMING NEAR MISS " : "NEAR MISS ") + "+" + bonus,
                            c.oncoming ? 0xFFFF5252 : 0xFFFFEB3B, h * 0.32f, 1.0f);
                    sound.whoosh();
                    host.vibrate(15);
                }
            }
        }
    }

    private void crash(Vehicles.Car c) {
        crashed = true;
        crashT = 0;
        crashSlide = 0;
        crashDir = c.x > x ? -1 : 1;
        shake = 1f;
        flash = 1f;
        sound.crash();
        sound.engineOn = false;
        host.vibrate(400);
        float bx = w / 2f, by = road.playerScreenY() - h * 0.06f;
        for (float[] s : sparks) {
            s[0] = bx; s[1] = by;
            s[2] = (rnd.nextFloat() - 0.5f) * w * 1.6f;
            s[3] = -rnd.nextFloat() * h * 0.9f;
            s[4] = 0.4f + rnd.nextFloat() * 0.8f;
        }
    }

    private void popup(String t, int color, float y, float size) {
        Popup p = new Popup();
        p.text = t; p.color = color; p.x = w / 2f; p.y = y; p.size = size;
        popups.add(p);
    }

    // ------------------------------------------------------------------ render

    public void render(Canvas c, float time, boolean showControls, boolean[] held) {
        c.save();
        if (shake > 0) c.translate((rnd.nextFloat() - 0.5f) * shake * w * 0.04f, (rnd.nextFloat() - 0.5f) * shake * w * 0.04f);
        road.render(c, z, x, cars, phase, speed / MAX_SPEED);

        float bs = road.playerPxPerUnit() * 225;
        float by = road.playerScreenY();
        float bx = w / 2f + crashSlide * w * 0.5f;
        float bikeLean = crashed ? crashDir * -Math.min(5f, crashT * 9f) : lean;
        RiderArt.drawRear(c, rider, bx, by, bs, bikeLean, braking && !crashed, time, road.darkness);
        road.drawNightOverlay(c, cars, bx, by, bs);
        if (braking && road.darkness > 0.15f) {
            ui.p.setColor(0x55FF1744);
            c.drawCircle(bx, by - bs * 0.84f, bs * 0.25f, ui.p);
        }

        // speed streaks
        float sp = speed / MAX_SPEED;
        if (sp > 0.55f && !crashed) {
            ui.p.setShader(null);
            ui.p.setColor(Color.argb((int) ((sp - 0.55f) * 300), 255, 255, 255));
            ui.p.setStrokeWidth(3);
            for (int i = 0; i < 14; i++) {
                float a = (i * 0.618f + time * (2 + sp * 4)) % 1f;
                float side = i % 2 == 0 ? -1 : 1;
                float yy = road.horizonY() + (h - road.horizonY()) * ((i * 0.37f) % 1);
                float x0 = w / 2f + side * w * (0.32f + a * 0.6f);
                c.drawLine(x0, yy, x0 + side * w * 0.12f * sp, yy + h * 0.05f * a, ui.p);
            }
        }
        // sparks
        ui.p.setColor(0xFFFFC107);
        for (float[] s : sparks) if (s[4] > 0) c.drawCircle(s[0], s[1], w * 0.006f, ui.p);
        c.restore();

        if (flash > 0) {
            ui.p.setColor(Color.argb((int) (flash * 160), 255, 255, 255));
            c.drawRect(0, 0, w, h, ui.p);
        }
        drawHud(c, time, showControls, held);
    }

    private void drawHud(Canvas c, float time, boolean showControls, boolean[] held) {
        float u = w / 100f;
        // top bar gradient
        ui.p.setShader(new LinearGradient(0, 0, 0, h * 0.14f, 0xAA000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.14f, ui.p);
        ui.p.setShader(null);

        ui.text(c, "SCORE", u * 4, u * 7, u * 3.2f, 0xCCFFFFFF, Paint.Align.LEFT, Ui.BOLD);
        ui.shadowText(c, String.valueOf((int) score), u * 4, u * 15.5f, u * 8.5f, 0xFFFFFFFF, Paint.Align.LEFT, Ui.BLACK);
        ui.text(c, String.format("%.2f km", km()), u * 4, u * 21, u * 3.6f, 0xFFFFE0B2, Paint.Align.LEFT, Ui.BOLD);

        if (mode == Store.MODE_TIME) {
            int col = timeLeft < 10 && ((int) (time * 4)) % 2 == 0 ? 0xFFFF5252 : 0xFFFFFFFF;
            ui.text(c, "TIME", w / 2f, u * 7, u * 3.2f, 0xCCFFFFFF, Paint.Align.CENTER, Ui.BOLD);
            ui.shadowText(c, String.valueOf((int) Math.ceil(timeLeft)), w / 2f, u * 16, u * 9.5f, col, Paint.Align.CENTER, Ui.BLACK);
            float toCp = (float) ((nextCheckpoint - z) / Road.UNITS_PER_M / 1000);
            ui.text(c, String.format("checkpoint %.1f km", toCp), w / 2f, u * 21, u * 3.2f, 0xFFB9F6CA, Paint.Align.CENTER, Ui.BOLD);
        }

        // pause button
        ui.register(1, bPause);
        ui.p.setColor(0x66000000);
        c.drawRoundRect(bPause, u * 2.5f, u * 2.5f, ui.p);
        ui.p.setColor(0xFFFFFFFF);
        float pw = bPause.width();
        c.drawRect(bPause.left + pw * 0.32f, bPause.top + pw * 0.25f, bPause.left + pw * 0.44f, bPause.bottom - pw * 0.25f, ui.p);
        c.drawRect(bPause.left + pw * 0.56f, bPause.top + pw * 0.25f, bPause.left + pw * 0.68f, bPause.bottom - pw * 0.25f, ui.p);

        // combo + oncoming badge
        float k = kmh();
        if (combo >= 2) {
            ui.outlineText(c, "COMBO x" + (int) combo, w / 2f, h * 0.2f, u * 7f, 0xFFFFEB3B, 0xFF000000, Ui.BLACK);
            ui.p.setColor(0xFFFFEB3B);
            c.drawRect(w / 2f - u * 15, h * 0.21f, w / 2f - u * 15 + u * 30 * (comboT / 3f), h * 0.215f, ui.p);
        }
        if (x < 0 && k > 80 && !crashed) {
            RectF b = new RectF(w / 2f - u * 17, h * 0.235f, w / 2f + u * 17, h * 0.235f + u * 7);
            ui.p.setColor(((int) (time * 5)) % 2 == 0 ? 0xDDD32F2F : 0xDDB71C1C);
            c.drawRoundRect(b, u * 3, u * 3, ui.p);
            ui.text(c, "ONCOMING x2", w / 2f, b.centerY() + u * 1.4f, u * 4f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        }
        for (Popup p : popups) {
            int a = (int) (255 * Math.max(0, Math.min(1, 1.4f - p.t)));
            ui.outlineText(c, p.text, p.x, p.y, u * 5.2f * p.size, (p.color & 0x00FFFFFF) | (a << 24), a << 24, Ui.BLACK);
        }

        // speedometer
        float gr = u * 12, gx = w - u * 16, gy = bPause.bottom + u * 16;
        ui.p.setColor(0x99000000);
        c.drawCircle(gx, gy, gr * 1.08f, ui.p);
        ui.p.setStyle(Paint.Style.STROKE);
        ui.p.setStrokeCap(Paint.Cap.ROUND);
        ui.p.setStrokeWidth(u * 2.2f);
        RectF arc = new RectF(gx - gr * 0.85f, gy - gr * 0.85f, gx + gr * 0.85f, gy + gr * 0.85f);
        ui.p.setColor(0x44FFFFFF);
        c.drawArc(arc, 135, 270, false, ui.p);
        float frac = Math.min(1, k / MAX_KMH);
        ui.p.setColor(frac > 0.8f ? 0xFFFF5252 : frac > 0.55f ? 0xFFFFC107 : 0xFF4CD964);
        c.drawArc(arc, 135, 270 * frac, false, ui.p);
        ui.p.setStyle(Paint.Style.FILL);
        ui.text(c, String.valueOf((int) k), gx, gy + u * 2.5f, u * 7f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BLACK);
        ui.text(c, "km/h", gx, gy + u * 6.5f, u * 2.8f, 0xCCFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        ui.text(c, "G" + gear(), gx, gy - u * 7, u * 3.4f, 0xFFFFC107, Paint.Align.CENTER, Ui.BOLD);

        if (showControls) {
            ctrl(c, bLeft, "◀", held[0], 0xFF546E7A);
            ctrl(c, bRight, "▶", held[1], 0xFF546E7A);
            ctrl(c, bBrake, "BRAKE", held[3], 0xFFC62828);
            ctrl(c, bGas, "GAS", held[2], 0xFF2E7D32);
        }
    }

    private void ctrl(Canvas c, RectF r, String label, boolean on, int color) {
        ui.p.setColor(on ? color : (color & 0x00FFFFFF) | 0x77000000);
        c.drawRoundRect(r, r.width() * 0.2f, r.width() * 0.2f, ui.p);
        ui.stroke(c, r, r.width() * 0.2f, 3, on ? 0xFFFFFFFF : 0x66FFFFFF);
        float size = r.width() * (label.length() > 3 ? 0.2f : 0.3f);
        ui.text(c, label, r.centerX(), r.centerY() + size * 0.35f, size, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);
    }
}
