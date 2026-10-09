package com.rovtech.joyride;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Owns the render thread, routes input, and switches between screens. */
public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {

    public interface Host extends Game.Host {
        void askName(String current, NameCallback cb);
    }

    public interface NameCallback { void onName(String name); }

    private static final int EV_TAP = 0, EV_KEY = 1, EV_BACK = 2, EV_DRAG = 3, EV_RELEASE = 4;

    public final Store store;
    public final Sound sound = new Sound();
    public final Road road = new Road(450L);
    public final Ui ui = new Ui();
    public final Game game;
    public final Screens screens;
    public volatile int screen = Screens.MENU;

    private final Host host;
    private final SurfaceHolder holder;
    private Thread thread;
    private volatile boolean running;
    private int w, h;
    private float time;

    // held inputs
    private volatile boolean kLeft, kRight, kGas, kBrake;
    private volatile boolean usedKeyboard;
    private final float[][] pointers = new float[10][2];
    private volatile int pointerCount;
    private final ConcurrentLinkedQueue<float[]> events = new ConcurrentLinkedQueue<>();

    // gesture tracking (UI thread)
    private float downX, downY, lastX, lastT, velX;
    private boolean moved;

    public GameView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        store = new Store(ctx);
        sound.enabled = store.sound();
        game = new Game(road, sound, host, ui);
        screens = new Screens(this, ui, store);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    // ------------------------------------------------------------------ actions

    public void startRun(int mode) {
        game.start(mode, store.rider());
        screen = Screens.PLAY;
    }

    public void renameCustom(final Rider r) {
        host.askName(r.name, name -> {
            String n = name.trim();
            if (n.isEmpty()) return;
            if (n.length() > 12) n = n.substring(0, 12);
            r.name = n;
            store.saveRider(r);
        });
    }

    // ------------------------------------------------------------------ input (UI thread)

    public boolean onKey(int code, boolean down) {
        boolean held = true;
        switch (code) {
            case KeyEvent.KEYCODE_A: case KeyEvent.KEYCODE_DPAD_LEFT: kLeft = down; break;
            case KeyEvent.KEYCODE_D: case KeyEvent.KEYCODE_DPAD_RIGHT: kRight = down; break;
            case KeyEvent.KEYCODE_W: case KeyEvent.KEYCODE_DPAD_UP: kGas = down; break;
            case KeyEvent.KEYCODE_S: case KeyEvent.KEYCODE_DPAD_DOWN: kBrake = down; break;
            default: held = false;
        }
        if (held) usedKeyboard = true;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_P) {
            if (screen == Screens.MENU && code != KeyEvent.KEYCODE_P) return false; // let the activity close
            if (down) events.add(new float[]{EV_BACK, 0, 0});
            return true;
        }
        if (down) events.add(new float[]{EV_KEY, code, 0});
        return held || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_SPACE
                || code == KeyEvent.KEYCODE_V || code == KeyEvent.KEYCODE_TAB;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        // held pointers for in-game controls
        synchronized (pointers) {
            int n = 0;
            for (int i = 0; i < e.getPointerCount() && n < pointers.length; i++) {
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == e.getActionIndex()) continue;
                if (action == MotionEvent.ACTION_CANCEL) continue;
                pointers[n][0] = e.getX(i);
                pointers[n][1] = e.getY(i);
                n++;
            }
            pointerCount = n;
        }
        float x = e.getX(), y = e.getY();
        long now = e.getEventTime();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x; downY = y; lastT = now; velX = 0; moved = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = x - lastX;
                if (Math.abs(x - downX) > w * 0.03f || Math.abs(y - downY) > w * 0.03f) moved = true;
                if (moved) events.add(new float[]{EV_DRAG, dx, 0});
                float dt = Math.max(1, now - lastT) / 1000f;
                velX = velX * 0.5f + (dx / dt) * 0.5f;
                lastX = x; lastT = now;
                break;
            }
            case MotionEvent.ACTION_UP:
                if (!moved) events.add(new float[]{EV_TAP, x, y});
                else events.add(new float[]{EV_RELEASE, velX, 0});
                break;
        }
        return true;
    }

    private boolean touching(android.graphics.RectF r) {
        synchronized (pointers) {
            for (int i = 0; i < pointerCount; i++) if (r.contains(pointers[i][0], pointers[i][1])) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ loop

    @Override public void surfaceCreated(SurfaceHolder sh) { resume(); }
    @Override public void surfaceDestroyed(SurfaceHolder sh) { pause(); }
    @Override public void surfaceChanged(SurfaceHolder sh, int f, int width, int height) {
        w = width; h = height;
        road.layout(w, h);
        game.layout(w, h);
        screens.layout(w, h);
    }

    public void resume() {
        if (running) return;
        running = true;
        sound.start();
        thread = new Thread(this, "game");
        thread.start();
    }

    public void pause() {
        running = false;
        kLeft = kRight = kGas = kBrake = false;
        if (screen == Screens.PLAY) screen = Screens.PAUSE;
        if (thread != null) { try { thread.join(500); } catch (InterruptedException ignored) {} }
        sound.engineOn = false;
        sound.stop();
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, 0.05f);
            last = now;
            if (w == 0) { sleep(16); continue; }
            time += dt;
            processEvents();
            update(dt);
            Canvas c = null;
            try {
                c = Build.VERSION.SDK_INT >= 23 ? holder.lockHardwareCanvas() : holder.lockCanvas();
                if (c != null) render(c);
            } catch (Exception ignored) {
            } finally {
                if (c != null) { try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) {} }
            }
            long spent = (System.nanoTime() - now) / 1_000_000;
            if (spent < 16) sleep(16 - spent);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void processEvents() {
        float[] ev;
        while ((ev = events.poll()) != null) {
            switch ((int) ev[0]) {
                case EV_TAP:
                    if (screen == Screens.PLAY) {
                        if (ui.hit(ev[1], ev[2]) == 1) screens.onBack();
                    } else screens.onTap(ui.hit(ev[1], ev[2]));
                    break;
                case EV_KEY:
                    if (screen != Screens.PLAY) screens.onKey((int) ev[1]);
                    break;
                case EV_BACK: screens.onBack(); break;
                case EV_DRAG: screens.onDrag(ev[1]); break;
                case EV_RELEASE: screens.onRelease(ev[1]); break;
            }
        }
    }

    private final boolean[] held = new boolean[4];

    private void update(float dt) {
        screens.update(dt);
        if (screen == Screens.PLAY) {
            held[0] = kLeft || touching(game.bLeft);
            held[1] = kRight || touching(game.bRight);
            held[2] = kGas || touching(game.bGas);
            held[3] = kBrake || touching(game.bBrake);
            game.update(dt, held[0], held[1], held[2], held[3]);
            if (game.finished) screens.onRunFinished(game);
        } else {
            sound.engineOn = false;
        }
    }

    private void render(Canvas c) {
        ui.begin();
        switch (screen) {
            case Screens.MENU: screens.renderMenu(c, time); break;
            case Screens.SELECT: screens.renderSelect(c, time); break;
            case Screens.CUSTOM: screens.renderCustom(c, time); break;
            case Screens.BOARD: screens.renderBoard(c, time); break;
            case Screens.PLAY: game.render(c, time, true, held); break;
            case Screens.PAUSE:
                game.render(c, time, false, held);
                ui.begin();
                screens.renderPause(c);
                break;
            case Screens.OVER:
                game.render(c, time, false, held);
                ui.begin();
                screens.renderOver(c, game, time);
                break;
        }
        ui.end();
    }
}
