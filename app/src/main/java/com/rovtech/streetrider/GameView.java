package com.rovtech.streetrider;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/** Top-down endless traffic riding game. W/S = gas/brake, A/D = steer. */
public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {

    private static final int LANES = 4;
    private static final float MAX_SPEED = 2.2f;   // screen-heights per second at top speed
    private static final float ACCEL = 0.9f;
    private static final float BRAKE = 2.4f;
    private static final float DRAG = 0.25f;
    private static final float STEER = 1.6f;       // road-widths per second

    private static final int[] CAR_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFFFFFFFF,
            0xFF8E24AA, 0xFFFF7043, 0xFF26C6DA, 0xFF455A64
    };

    private Thread thread;
    private volatile boolean running;
    private final SurfaceHolder holder;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final SharedPreferences prefs;

    // input
    private volatile boolean kLeft, kRight, kGas, kBrake;
    private boolean tLeft, tRight, tGas, tBrake;

    // world state (x in road units 0..1, y in screen-height units)
    private float bikeX = 0.625f, bikeTilt = 0f;
    private float speed = 0f, scroll = 0f, distance = 0f;
    private int score = 0, best;
    private boolean crashed = false, started = false;
    private float spawnTimer = 0f, crashTimer = 0f, nearMissFlash = 0f;
    private final ArrayList<Car> cars = new ArrayList<>();

    private int w, h;
    private float roadL, roadW;
    private RectF btnLeft, btnRight, btnGas, btnBrake;

    private static class Car {
        float x, y, speed, len;
        int color, type; // 0 car, 1 truck, 2 bus
        boolean passed;
    }

    public GameView(Context ctx) {
        super(ctx);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        prefs = ctx.getSharedPreferences("streetrider", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
    }

    // ---------- input ----------

    public boolean setKey(int code, boolean down) {
        switch (code) {
            case KeyEvent.KEYCODE_A: case KeyEvent.KEYCODE_DPAD_LEFT: kLeft = down; break;
            case KeyEvent.KEYCODE_D: case KeyEvent.KEYCODE_DPAD_RIGHT: kRight = down; break;
            case KeyEvent.KEYCODE_W: case KeyEvent.KEYCODE_DPAD_UP: kGas = down; break;
            case KeyEvent.KEYCODE_S: case KeyEvent.KEYCODE_DPAD_DOWN: kBrake = down; break;
            case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_ENTER:
                if (down && crashed && crashTimer > 0.8f) reset();
                break;
            default: return false;
        }
        if (down && !crashed) started = true;
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean l = false, r = false, g = false, b = false;
        int action = e.getActionMasked();
        for (int i = 0; i < e.getPointerCount(); i++) {
            if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP)
                    && i == e.getActionIndex()) continue;
            float x = e.getX(i), y = e.getY(i);
            if (btnLeft != null) {
                if (btnLeft.contains(x, y)) l = true;
                if (btnRight.contains(x, y)) r = true;
                if (btnGas.contains(x, y)) g = true;
                if (btnBrake.contains(x, y)) b = true;
            }
        }
        if (action == MotionEvent.ACTION_CANCEL) { l = r = g = b = false; }
        tLeft = l; tRight = r; tGas = g; tBrake = b;
        if (action == MotionEvent.ACTION_DOWN) {
            if (crashed && crashTimer > 0.8f) reset();
            else started = true;
        }
        return true;
    }

    // ---------- loop ----------

    @Override public void surfaceCreated(SurfaceHolder sh) { resume(); }
    @Override public void surfaceChanged(SurfaceHolder sh, int f, int width, int height) { layout(width, height); }
    @Override public void surfaceDestroyed(SurfaceHolder sh) { pause(); }

    private void layout(int width, int height) {
        w = width; h = height;
        roadW = Math.min(w * 0.86f, h * 0.6f);
        roadL = (w - roadW) / 2f;
        float bs = Math.min(w, h) * 0.17f;
        float pad = bs * 0.25f, by = h - bs - pad * 3;
        btnLeft = new RectF(pad, by, pad + bs, by + bs);
        btnRight = new RectF(pad * 2 + bs, by, pad * 2 + bs * 2, by + bs);
        btnGas = new RectF(w - pad - bs, by - bs * 0.4f, w - pad, by + bs);
        btnBrake = new RectF(w - pad * 2 - bs * 2, by, w - pad * 2 - bs, by + bs);
    }

    public void resume() {
        if (running) return;
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
        kLeft = kRight = kGas = kBrake = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
        }
    }

    private void reset() {
        cars.clear();
        bikeX = 0.625f; bikeTilt = 0; speed = 0; distance = 0; score = 0;
        crashed = false; crashTimer = 0; spawnTimer = 0; started = true;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, 0.05f);
            last = now;
            if (w > 0) update(dt);
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null && w > 0) render(c);
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
            long spent = (System.nanoTime() - now) / 1_000_000;
            if (spent < 16) { try { Thread.sleep(16 - spent); } catch (InterruptedException ignored) {} }
        }
    }

    // ---------- update ----------

    private void update(float dt) {
        if (crashed) {
            crashTimer += dt;
            speed = Math.max(0, speed - BRAKE * 2 * dt);
            scroll += speed * dt;
            return;
        }
        boolean left = kLeft || tLeft, right = kRight || tRight;
        boolean gas = kGas || tGas, brake = kBrake || tBrake;

        if (gas) speed += ACCEL * (1f - speed / MAX_SPEED * 0.6f) * dt;
        else speed -= DRAG * dt;
        if (brake) speed -= BRAKE * dt;
        speed = Math.max(0, Math.min(MAX_SPEED, speed));

        float steerAmt = STEER * (0.35f + 0.65f * Math.min(1f, speed / 0.6f));
        float targetTilt = 0;
        if (left) { bikeX -= steerAmt * dt * 0.25f; targetTilt = -1; }
        if (right) { bikeX += steerAmt * dt * 0.25f; targetTilt = 1; }
        bikeTilt += (targetTilt - bikeTilt) * Math.min(1, dt * 10);
        bikeX = Math.max(0.04f, Math.min(0.96f, bikeX));

        scroll += speed * dt;
        distance += speed * dt * 120f; // metres-ish
        if (nearMissFlash > 0) nearMissFlash -= dt;

        // spawn traffic
        if (started) {
            spawnTimer -= dt;
            if (spawnTimer <= 0) {
                spawnCar();
                float density = 0.9f - Math.min(0.55f, distance / 8000f);
                spawnTimer = density * (0.6f + rnd.nextFloat() * 0.7f);
            }
        }

        // move traffic (relative to player)
        Iterator<Car> it = cars.iterator();
        while (it.hasNext()) {
            Car car = it.next();
            car.y += (speed - car.speed) * dt;
            if (car.y > 1.6f || car.y < -1.5f) { it.remove(); continue; }
            if (!car.passed && car.y - car.len / 2 > bikeY() + 0.05f) {
                car.passed = true;
                score += 10;
                float laneGap = Math.abs(car.x - bikeX);
                if (laneGap < 0.2f && speed > 1.0f) { score += 40; nearMissFlash = 0.6f; }
            }
            if (collides(car)) crash();
        }
        score += (int) (speed * dt * 20);
    }

    private float bikeY() { return 0.72f; }

    private void spawnCar() {
        Car car = new Car();
        int lane = rnd.nextInt(LANES);
        car.x = (lane + 0.5f) / LANES;
        float t = rnd.nextFloat();
        car.type = t < 0.7f ? 0 : (t < 0.88f ? 1 : 2);
        car.len = car.type == 0 ? 0.11f : (car.type == 1 ? 0.18f : 0.2f);
        car.color = CAR_COLORS[rnd.nextInt(CAR_COLORS.length)];
        car.speed = 0.35f + rnd.nextFloat() * 0.45f;
        // spawn ahead if we're faster, behind if traffic would catch us
        if (speed < car.speed * 0.5f) return; // only spawn traffic ahead that we can catch
        car.y = -0.25f;
        for (Car o : cars) {
            if (Math.abs(o.x - car.x) < 0.01f && Math.abs(o.y - car.y) < 0.35f) return;
        }
        cars.add(car);
    }

    private boolean collides(Car car) {
        float bw = 0.035f, bh = 0.045f; // bike half-extents (road units, screen-height units)
        float cw = (car.type == 0 ? 0.075f : 0.085f);
        float ch = car.len / 2 * 0.92f;
        return Math.abs(car.x - bikeX) < (bw + cw) && Math.abs(car.y - bikeY()) < (bh + ch);
    }

    private void crash() {
        crashed = true;
        crashTimer = 0;
        if (score > best) {
            best = score;
            prefs.edit().putInt("best", best).apply();
        }
    }

    // ---------- draw ----------

    private float rx(float x) { return roadL + x * roadW; }

    private void render(Canvas c) {
        // grass + sidewalk
        c.drawColor(0xFF2E7D32);
        float off = (scroll * h) % (h * 0.2f);
        p.setColor(0xFF388E3C);
        for (float y = -h * 0.2f + off; y < h; y += h * 0.2f) {
            c.drawRect(0, y, roadL - 30, y + h * 0.1f, p);
            c.drawRect(roadL + roadW + 30, y, w, y + h * 0.1f, p);
        }
        // trees
        float treeOff = (scroll * h) % (h * 0.35f);
        for (float y = -h * 0.35f + treeOff; y < h + 40; y += h * 0.35f) {
            drawTree(c, (roadL - 30) / 2f, y);
            drawTree(c, roadL + roadW + 30 + (w - roadL - roadW - 30) / 2f, y + h * 0.17f);
        }
        // kerbs
        p.setColor(0xFF9E9E9E);
        c.drawRect(roadL - 30, 0, roadL, h, p);
        c.drawRect(roadL + roadW, 0, roadL + roadW + 30, h, p);
        float kOff = (scroll * h) % 60;
        for (float y = -60 + kOff; y < h; y += 60) {
            p.setColor(0xFFD32F2F);
            c.drawRect(roadL - 14, y, roadL, y + 30, p);
            c.drawRect(roadL + roadW, y, roadL + roadW + 14, y + 30, p);
            p.setColor(Color.WHITE);
            c.drawRect(roadL - 14, y + 30, roadL, y + 60, p);
            c.drawRect(roadL + roadW, y + 30, roadL + roadW + 14, y + 60, p);
        }
        // asphalt
        p.setColor(0xFF37474F);
        c.drawRect(roadL, 0, roadL + roadW, h, p);
        // edge lines
        p.setColor(Color.WHITE);
        c.drawRect(roadL + 6, 0, roadL + 12, h, p);
        c.drawRect(roadL + roadW - 12, 0, roadL + roadW - 6, h, p);
        // dashed lane markings
        float dash = h * 0.07f, gap = h * 0.06f;
        float dOff = (scroll * h) % (dash + gap);
        for (int i = 1; i < LANES; i++) {
            float x = rx((float) i / LANES);
            if (i == LANES / 2) {
                // centre double yellow
                p.setColor(0xFFFFC107);
                c.drawRect(x - 9, 0, x - 3, h, p);
                c.drawRect(x + 3, 0, x + 9, h, p);
                continue;
            }
            p.setColor(Color.WHITE);
            for (float y = -dash - gap + dOff; y < h; y += dash + gap) {
                c.drawRect(x - 4, y, x + 4, y + dash, p);
            }
        }

        // traffic
        for (Car car : cars) drawCar(c, car);

        // bike
        drawBike(c, rx(bikeX), bikeY() * h);

        drawHud(c);
        drawButtons(c);

        if (!started) {
            overlay(c, "STREET RIDER", "W / tap GAS to ride\nA D steer  •  S brake");
        } else if (crashed) {
            overlay(c, "CRASHED!", "Score " + score + "   Best " + best
                    + (crashTimer > 0.8f ? "\nTap or press SPACE to ride again" : ""));
        }
    }

    private void drawTree(Canvas c, float x, float y) {
        float r = Math.min(roadL * 0.35f, 46);
        if (r < 8) return;
        p.setColor(0x55000000);
        c.drawCircle(x + 6, y + 6, r, p);
        p.setColor(0xFF1B5E20);
        c.drawCircle(x, y, r, p);
        p.setColor(0xFF43A047);
        c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.6f, p);
    }

    private void drawCar(Canvas c, Car car) {
        float cx = rx(car.x), cy = car.y * h;
        float cw = roadW / LANES * (car.type == 0 ? 0.56f : 0.66f);
        float ch = car.len * h;
        RectF body = new RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
        // shadow
        p.setColor(0x66000000);
        c.drawRoundRect(new RectF(body.left + 6, body.top + 8, body.right + 6, body.bottom + 8), 14, 14, p);
        // wheels
        p.setColor(0xFF111111);
        float ww = cw * 0.12f, wh = ch * 0.16f;
        c.drawRect(body.left - ww * 0.5f, body.top + ch * 0.14f, body.left + ww, body.top + ch * 0.14f + wh, p);
        c.drawRect(body.right - ww, body.top + ch * 0.14f, body.right + ww * 0.5f, body.top + ch * 0.14f + wh, p);
        c.drawRect(body.left - ww * 0.5f, body.bottom - ch * 0.14f - wh, body.left + ww, body.bottom - ch * 0.14f, p);
        c.drawRect(body.right - ww, body.bottom - ch * 0.14f - wh, body.right + ww * 0.5f, body.bottom - ch * 0.14f, p);
        // body
        p.setColor(car.color);
        c.drawRoundRect(body, cw * 0.25f, cw * 0.25f, p);
        if (car.type == 0) {
            // windscreen (top = front, traffic faces up the road)
            p.setColor(0xFF263238);
            c.drawRoundRect(new RectF(body.left + cw * 0.14f, body.top + ch * 0.2f, body.right - cw * 0.14f, body.top + ch * 0.38f), 8, 8, p);
            c.drawRoundRect(new RectF(body.left + cw * 0.16f, body.bottom - ch * 0.24f, body.right - cw * 0.16f, body.bottom - ch * 0.12f), 8, 8, p);
            p.setColor(0x33FFFFFF);
            c.drawRect(body.left + cw * 0.18f, body.top + ch * 0.4f, body.right - cw * 0.18f, body.bottom - ch * 0.28f, p);
        } else if (car.type == 1) {
            // truck: cab + cargo box
            p.setColor(0xFF263238);
            c.drawRect(body.left + cw * 0.15f, body.top + ch * 0.08f, body.right - cw * 0.15f, body.top + ch * 0.18f, p);
            p.setColor(0xFFECEFF1);
            c.drawRoundRect(new RectF(body.left + 2, body.top + ch * 0.28f, body.right - 2, body.bottom - 2), 6, 6, p);
            p.setColor(0xFFB0BEC5);
            for (int i = 1; i < 5; i++) {
                float y = body.top + ch * 0.28f + (ch * 0.72f) * i / 5f;
                c.drawRect(body.left + 4, y - 1.5f, body.right - 4, y + 1.5f, p);
            }
        } else {
            // bus: roof with windows strip
            p.setColor(0xFF263238);
            c.drawRect(body.left + cw * 0.12f, body.top + ch * 0.05f, body.right - cw * 0.12f, body.top + ch * 0.12f, p);
            p.setColor(0x44FFFFFF);
            c.drawRect(body.left + cw * 0.2f, body.top + ch * 0.18f, body.right - cw * 0.2f, body.bottom - ch * 0.06f, p);
        }
        // tail lights (bottom = rear, facing the player)
        p.setColor(0xFFFF1744);
        c.drawRect(body.left + 4, body.bottom - 8, body.left + cw * 0.28f, body.bottom - 2, p);
        c.drawRect(body.right - cw * 0.28f, body.bottom - 8, body.right - 4, body.bottom - 2, p);
    }

    private void drawBike(Canvas c, float x, float y) {
        float s = roadW / LANES * 0.42f; // bike width scale
        float len = h * 0.1f;
        c.save();
        c.rotate(bikeTilt * 12f, x, y);
        // shadow
        p.setColor(0x66000000);
        c.drawOval(new RectF(x - s * 0.4f + 6, y - len / 2 + 8, x + s * 0.4f + 6, y + len / 2 + 8), p);
        // tyres
        p.setColor(0xFF111111);
        c.drawRoundRect(new RectF(x - s * 0.12f, y - len * 0.52f, x + s * 0.12f, y - len * 0.22f), 6, 6, p);
        c.drawRoundRect(new RectF(x - s * 0.14f, y + len * 0.2f, x + s * 0.14f, y + len * 0.52f), 6, 6, p);
        // handlebar
        p.setColor(0xFF212121);
        c.drawRect(x - s * 0.5f, y - len * 0.3f, x + s * 0.5f, y - len * 0.25f, p);
        p.setColor(0xFF424242);
        c.drawCircle(x - s * 0.5f, y - len * 0.275f, s * 0.07f, p);
        c.drawCircle(x + s * 0.5f, y - len * 0.275f, s * 0.07f, p);
        // body / tank
        p.setColor(0xFFFF6D00);
        c.drawRoundRect(new RectF(x - s * 0.22f, y - len * 0.3f, x + s * 0.22f, y + len * 0.28f), s * 0.2f, s * 0.2f, p);
        // rider: shoulders + helmet
        p.setColor(0xFF1A237E);
        c.drawRoundRect(new RectF(x - s * 0.34f, y - len * 0.08f, x + s * 0.34f, y + len * 0.12f), s * 0.15f, s * 0.15f, p);
        p.setColor(0xFFFAFAFA);
        c.drawCircle(x, y - len * 0.08f, s * 0.2f, p);
        p.setColor(0xFF000000);
        c.drawRect(x - s * 0.13f, y - len * 0.17f, x + s * 0.13f, y - len * 0.13f, p);
        // headlight beam
        p.setColor(0x30FFF59D);
        c.drawOval(new RectF(x - s * 0.5f, y - len * 1.4f, x + s * 0.5f, y - len * 0.5f), p);
        // brake light
        if (kBrake || tBrake) {
            p.setColor(0xFFFF1744);
            c.drawRect(x - s * 0.12f, y + len * 0.27f, x + s * 0.12f, y + len * 0.31f, p);
        }
        c.restore();
    }

    private void drawHud(Canvas c) {
        float ts = Math.min(w, h) * 0.05f;
        p.setColor(0xAA000000);
        c.drawRoundRect(new RectF(16, 16, w - 16, 16 + ts * 2.2f), 18, 18, p);
        p.setColor(Color.WHITE);
        p.setTextSize(ts);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.LEFT);
        int kmh = Math.round(speed / MAX_SPEED * 240);
        c.drawText(kmh + " km/h", 36, 16 + ts * 1.45f, p);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.format("%.1f km", distance / 1000f), w / 2f, 16 + ts * 1.45f, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Score " + score, w - 36, 16 + ts * 1.45f, p);
        if (nearMissFlash > 0) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFEB3B);
            p.setTextSize(ts * 1.2f);
            c.drawText("NEAR MISS +40", w / 2f, h * 0.45f, p);
        }
        p.setFakeBoldText(false);
    }

    private void drawButtons(Canvas c) {
        drawBtn(c, btnLeft, "◀", kLeft || tLeft, 0xFF607D8B);
        drawBtn(c, btnRight, "▶", kRight || tRight, 0xFF607D8B);
        drawBtn(c, btnBrake, "BRAKE", kBrake || tBrake, 0xFFC62828);
        drawBtn(c, btnGas, "GAS", kGas || tGas, 0xFF2E7D32);
    }

    private void drawBtn(Canvas c, RectF r, String label, boolean pressed, int color) {
        p.setColor(pressed ? color : (color & 0x00FFFFFF) | 0x88000000);
        c.drawRoundRect(r, 24, 24, p);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(r.width() * (label.length() > 3 ? 0.2f : 0.3f));
        c.drawText(label, r.centerX(), r.centerY() + p.getTextSize() * 0.35f, p);
        p.setFakeBoldText(false);
    }

    private void overlay(Canvas c, String title, String sub) {
        p.setColor(0xAA000000);
        c.drawRect(0, h * 0.3f, w, h * 0.55f, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFF6D00);
        p.setFakeBoldText(true);
        p.setTextSize(Math.min(w, h) * 0.1f);
        c.drawText(title, w / 2f, h * 0.39f, p);
        p.setColor(Color.WHITE);
        p.setFakeBoldText(false);
        p.setTextSize(Math.min(w, h) * 0.045f);
        float y = h * 0.45f;
        for (String line : sub.split("\n")) {
            c.drawText(line, w / 2f, y, p);
            y += p.getTextSize() * 1.4f;
        }
    }
}
