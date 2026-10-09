package com.rovtech.joyride;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Menu, rider select, garage, leaderboard, pause and results screens. */
public class Screens {
    public static final int MENU = 0, SELECT = 1, CUSTOM = 2, PLAY = 3, PAUSE = 4, OVER = 5, BOARD = 6;

    private final GameView app;
    private final Ui ui;
    private final Store store;
    private int w, h;
    private float u;

    // menu
    private int menuFocus = 0;
    private static final int[] MENU_IDS = {10, 11, 12, 13, 14};
    private double cruiseZ = 0;
    private final List<Vehicles.Car> noCars = new ArrayList<>();
    // select
    public int selIdx;
    private float scroll, scrollVel;
    private boolean dragging, settling;
    private int selFocus = 0;
    // garage
    private int row = 0, view = 0;
    // board
    private int boardMode = 0, lastRank = -1, lastMode = -1;
    // results
    private int overFocus = 0, pauseFocus = 0;
    private int resultRank = -1;

    public Screens(GameView app, Ui ui, Store store) {
        this.app = app;
        this.ui = ui;
        this.store = store;
        selIdx = store.selected();
        scroll = selIdx;
    }

    public void layout(int width, int height) { w = width; h = height; u = w / 100f; }

    // ------------------------------------------------------------------ transitions

    public void openSelect() { selIdx = store.selected(); scroll = selIdx; app.screen = SELECT; }

    public void onRunFinished(Game g) {
        Store.Entry e = new Store.Entry();
        e.name = g.rider.name;
        e.rider = g.rider.index;
        e.score = (int) g.score;
        e.km = g.km();
        e.topSpeed = g.topKmh;
        e.time = System.currentTimeMillis();
        resultRank = store.submit(g.mode, e);
        lastRank = resultRank;
        lastMode = g.mode;
        overFocus = 0;
        app.screen = OVER;
    }

    // ------------------------------------------------------------------ input

    public void onDrag(float dx) {
        if (app.screen != SELECT) return;
        dragging = true;
        scroll -= dx / (w * 0.33f);
        scroll = Math.max(-0.4f, Math.min(store.riders.length - 0.6f, scroll));
    }

    public void onRelease(float vx) {
        if (app.screen != SELECT || !dragging) return;
        dragging = false;
        scrollVel = -vx / (w * 0.33f);
        settling = true;
    }

    public void onTap(int id) {
        if (id < 0) return;
        app.sound.beep(id >= 100 ? 990 : 660);
        switch (id) {
            // menu
            case 10: app.startRun(Store.MODE_ENDLESS); break;
            case 11: app.startRun(Store.MODE_TIME); break;
            case 12: case 15: openSelect(); break;
            case 13: boardMode = 0; app.screen = BOARD; break;
            case 14: store.setSound(!store.sound()); app.sound.enabled = store.sound(); break;
            // select
            case 20: store.setSelected(selIdx); row = 0; app.screen = CUSTOM; break;
            case 21: store.setSelected(selIdx); app.screen = MENU; break;
            case 22: app.screen = MENU; break;
            case 23: app.renameCustom(store.riders[selIdx]); break;
            // garage
            case 30: view = 0; break;
            case 31: view = 1; break;
            case 32: store.riders[selIdx].resetStyle(); store.saveRider(store.riders[selIdx]); break;
            case 33: app.screen = SELECT; break;
            case 34: view = 1 - view; break;
            // board
            case 40: boardMode = 0; break;
            case 41: boardMode = 1; break;
            case 42: app.screen = MENU; break;
            // results
            case 50: app.startRun(app.game.mode); break;
            case 51: boardMode = lastMode; app.screen = BOARD; break;
            case 52: app.screen = MENU; break;
            // pause
            case 60: app.screen = PLAY; break;
            case 61: app.startRun(app.game.mode); break;
            case 62: app.screen = MENU; break;
            default:
                if (id >= 100 && id < 200) { selIdx = id - 100; scroll = scroll * 0.5f + selIdx * 0.5f; }
                else if (id >= 200 && id < 300) {
                    int r = (id - 200) / 2;
                    row = r;
                    change(r, (id - 200) % 2 == 0 ? -1 : 1);
                } else if (id >= 300 && id < 400) row = id - 300;
        }
    }

    private void change(int slot, int dir) {
        Rider r = store.riders[selIdx];
        int n = Rider.optionCount(slot);
        r.v[slot] = ((r.v[slot] + dir) % n + n) % n;
        store.saveRider(r);
    }

    /** Keyboard navigation. Returns true if handled. */
    public boolean onKey(int code) {
        boolean up = code == KeyEvent.KEYCODE_W || code == KeyEvent.KEYCODE_DPAD_UP;
        boolean down = code == KeyEvent.KEYCODE_S || code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_A || code == KeyEvent.KEYCODE_DPAD_LEFT;
        boolean right = code == KeyEvent.KEYCODE_D || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean ok = code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_DPAD_CENTER;
        switch (app.screen) {
            case MENU:
                if (up) menuFocus = (menuFocus + MENU_IDS.length - 1) % MENU_IDS.length;
                if (down) menuFocus = (menuFocus + 1) % MENU_IDS.length;
                if (ok) onTap(MENU_IDS[menuFocus]);
                return true;
            case SELECT: {
                int[] ids = store.riders[selIdx].isCustom() ? new int[]{21, 20, 23, 22} : new int[]{21, 20, 22};
                if (left && selIdx > 0) { selIdx--; app.sound.beep(990); }
                if (right && selIdx < store.riders.length - 1) { selIdx++; app.sound.beep(990); }
                if (up) selFocus = (selFocus + ids.length - 1) % ids.length;
                if (down) selFocus = (selFocus + 1) % ids.length;
                selFocus = Math.min(selFocus, ids.length - 1);
                if (ok) onTap(ids[selFocus]);
                return true;
            }
            case CUSTOM:
                if (up) row = (row + Rider.SLOTS + 1) % (Rider.SLOTS + 2);
                if (down) row = (row + 1) % (Rider.SLOTS + 2);
                if (row < Rider.SLOTS) {
                    if (left) change(row, -1);
                    if (right) change(row, 1);
                } else if (ok) onTap(row == Rider.SLOTS ? 32 : 33);
                if (code == KeyEvent.KEYCODE_V || code == KeyEvent.KEYCODE_TAB) view = 1 - view;
                return true;
            case BOARD:
                if (left || right) boardMode = 1 - boardMode;
                if (ok) onTap(42);
                return true;
            case OVER: {
                int[] ids = {50, 51, 52};
                if (up || left) overFocus = (overFocus + 2) % 3;
                if (down || right) overFocus = (overFocus + 1) % 3;
                if (ok) onTap(ids[overFocus]);
                return true;
            }
            case PAUSE: {
                int[] ids = {60, 61, 62};
                if (up) pauseFocus = (pauseFocus + 2) % 3;
                if (down) pauseFocus = (pauseFocus + 1) % 3;
                if (ok) onTap(ids[pauseFocus]);
                return true;
            }
        }
        return false;
    }

    public boolean onBack() {
        switch (app.screen) {
            case SELECT: case BOARD: case OVER: app.screen = MENU; return true;
            case CUSTOM: app.screen = SELECT; return true;
            case PLAY: pauseFocus = 0; app.screen = PAUSE; return true;
            case PAUSE: app.screen = PLAY; return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ rendering

    public void update(float dt) {
        cruiseZ += dt * 2600;
        if (app.screen == SELECT && !dragging) {
            scroll += scrollVel * dt;
            scrollVel *= (float) Math.pow(0.02, dt);
            if (Math.abs(scrollVel) < 2f) {
                int snap = Math.round(Math.max(0, Math.min(store.riders.length - 1, scroll)));
                if (settling && Math.abs(scrollVel) < 0.5f) { selIdx = snap; settling = false; }
                scroll += (selIdx - scroll) * Math.min(1, dt * 10);
            }
            scroll = Math.max(-0.4f, Math.min(store.riders.length - 0.6f, scroll));
        }
    }

    private void scenicBackground(Canvas c, float dim) {
        app.road.render(c, cruiseZ, 0.45f, noCars, (float) ((cruiseZ / 900000.0) % 3), 0.4f);
        ui.p.setShader(new LinearGradient(0, 0, 0, h, (int) (dim * 255) << 24 | 0x0A1020,
                (int) (Math.min(1, dim + 0.25f) * 255) << 24 | 0x05070F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, ui.p);
        ui.p.setShader(null);
    }

    private void title(Canvas c, String t, float y) {
        ui.outlineText(c, t, w / 2f, y, u * 8, Ui.CREAM, 0xFF000000, Ui.BLACK);
    }

    public void renderMenu(Canvas c, float time) {
        scenicBackground(c, 0.45f);
        ui.outlineText(c, "KEVIN'S", w / 2f, h * 0.1f, u * 12, Ui.CREAM, 0xFF000000, Ui.BLACK);
        ui.outlineText(c, "JOY RIDE", w / 2f, h * 0.165f, u * 17, Ui.ORANGE, 0xFF000000, Ui.BLACK);
        ui.text(c, "HIMALAYAN 450  •  MOUNTAIN TRAFFIC", w / 2f, h * 0.195f, u * 3.6f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);

        // rider showcase
        Rider r = store.rider();
        RectF card = new RectF(u * 8, h * 0.225f, w - u * 8, h * 0.475f);
        ui.panel(c, card, 0x88000000);
        ui.register(15, card);
        float bob = (float) Math.sin(time * 3) * u * 0.3f;
        RiderArt.drawSide(c, r, w / 2f, card.bottom - u * 9 + bob, u * 17, time, true);
        ui.text(c, r.name.toUpperCase(), w / 2f, card.bottom - u * 3.2f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BLACK);
        ui.text(c, "tap to change rider", w / 2f, card.top + u * 5, u * 3f, 0xAAFFFFFF, Paint.Align.CENTER, Ui.BOLD);

        float bw = w - u * 20, bh = u * 14, x0 = u * 10, y0 = h * 0.5f, gap = u * 3.5f;
        String[] labels = {"ENDLESS RIDE", "TIME ATTACK", "RIDERS & GARAGE", "LEADERBOARD"};
        int[] styles = {Ui.GO, Ui.PRIMARY, Ui.SECONDARY, Ui.SECONDARY};
        for (int i = 0; i < 4; i++) {
            RectF b = new RectF(x0, y0 + i * (bh + gap), x0 + bw, y0 + i * (bh + gap) + bh);
            ui.button(c, MENU_IDS[i], b, labels[i], styles[i], menuFocus == i, false);
        }
        RectF sb = new RectF(w / 2f - u * 18, y0 + 4 * (bh + gap), w / 2f + u * 18, y0 + 4 * (bh + gap) + u * 10);
        ui.button(c, 14, sb, store.sound() ? "SOUND: ON" : "SOUND: OFF", Ui.GHOST, menuFocus == 4, false);

        ui.text(c, "BEST  endless " + store.best(0) + "   •   time attack " + store.best(1),
                w / 2f, h - u * 9, u * 3.3f, 0xFFFFE0B2, Paint.Align.CENTER, Ui.BOLD);
        ui.text(c, "W gas  •  S brake  •  A / D steer  •  ESC pause", w / 2f, h - u * 4.5f, u * 3f, 0x99FFFFFF, Paint.Align.CENTER, Ui.BOLD);
    }

    public void renderSelect(Canvas c, float time) {
        scenicBackground(c, 0.6f);
        title(c, "CHOOSE YOUR RIDER", h * 0.06f);
        Rider r = store.riders[selIdx];

        RectF stage = new RectF(u * 5, h * 0.08f, w - u * 5, h * 0.47f);
        ui.panel(c, stage, 0x99000000);
        float bob = (float) Math.sin(time * 3) * u * 0.3f;
        RiderArt.drawSide(c, r, w / 2f, stage.top + (stage.height()) * 0.66f + bob, u * 22, time, true);
        ui.outlineText(c, r.name.toUpperCase(), w / 2f, stage.bottom - u * 12, u * 9, Ui.ORANGE, 0xFF000000, Ui.BLACK);
        ui.text(c, r.tagline(), w / 2f, stage.bottom - u * 6.5f, u * 3.6f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        String chips = Rider.HELMETS[r.v[Rider.HELMET]] + "  •  " + Rider.PATTERNS[r.v[Rider.PATTERN]] + "  •  "
                + Rider.ACCESSORIES[r.v[Rider.ACCESSORY]];
        ui.text(c, chips, w / 2f, stage.bottom - u * 2.5f, u * 3f, 0xFFFFE0B2, Paint.Align.CENTER, Ui.BOLD);

        // carousel
        float cw = w * 0.3f, ch = h * 0.17f, cy = h * 0.49f;
        c.save();
        c.clipRect(0, cy - u * 2, w, cy + ch + u * 2);
        for (int i = 0; i < store.riders.length; i++) {
            float cx = w / 2f + (i - scroll) * (cw + u * 3);
            if (cx < -cw || cx > w + cw) continue;
            RectF card = new RectF(cx - cw / 2, cy, cx + cw / 2, cy + ch);
            boolean sel = i == selIdx;
            ui.panel(c, card, sel ? 0xEE24345A : 0xAA101828);
            if (sel) ui.stroke(c, card, cw * 0.08f, u * 0.9f, Ui.ORANGE);
            Rider ri = store.riders[i];
            RiderArt.drawRear(c, ri, cx, card.bottom - u * 8, u * 9.5f, 0, false, time, 0);
            ui.text(c, ri.name.toUpperCase(), cx, card.bottom - u * 2.5f, u * 3.6f, sel ? Ui.ORANGE : 0xFFFFFFFF, Paint.Align.CENTER, Ui.BLACK);
            ui.register(100 + i, card);
        }
        c.restore();
        ui.text(c, "◀  swipe or A / D  ▶", w / 2f, cy + ch + u * 5, u * 3.2f, 0xAAFFFFFF, Paint.Align.CENTER, Ui.BOLD);

        float bw = w - u * 20, bh = u * 13, x0 = u * 10, y0 = h * 0.71f, gap = u * 3;
        boolean custom = r.isCustom();
        ui.button(c, 21, new RectF(x0, y0, x0 + bw, y0 + bh), "RIDE AS " + r.name.toUpperCase(), Ui.GO, selFocus == 0, false);
        ui.button(c, 20, new RectF(x0, y0 + bh + gap, x0 + bw, y0 + 2 * bh + gap), "CUSTOMISE OUTFIT", Ui.PRIMARY, selFocus == 1, false);
        float y2 = y0 + 2 * (bh + gap);
        if (custom) {
            ui.button(c, 23, new RectF(x0, y2, x0 + bw / 2 - gap / 2, y2 + bh), "RENAME", Ui.SECONDARY, selFocus == 2, false);
            ui.button(c, 22, new RectF(x0 + bw / 2 + gap / 2, y2, x0 + bw, y2 + bh), "BACK", Ui.SECONDARY, selFocus == 3, false);
        } else {
            ui.button(c, 22, new RectF(x0, y2, x0 + bw, y2 + bh), "BACK", Ui.SECONDARY, selFocus == 2, false);
        }
    }

    public void renderCustom(Canvas c, float time) {
        scenicBackground(c, 0.7f);
        Rider r = store.riders[selIdx];
        title(c, "GARAGE  •  " + r.name.toUpperCase(), h * 0.055f);

        RectF stage = new RectF(u * 5, h * 0.07f, w - u * 5, h * 0.4f);
        ui.panel(c, stage, 0x99000000);
        ui.register(34, stage);
        // turntable floor
        ui.p.setColor(0x33FFFFFF);
        c.drawOval(new RectF(w / 2f - u * 30, stage.bottom - u * 14, w / 2f + u * 30, stage.bottom - u * 6), ui.p);
        if (view == 0) RiderArt.drawSide(c, r, w / 2f, stage.bottom - u * 10, u * 19, time, true);
        else RiderArt.drawRear(c, r, w / 2f, stage.bottom - u * 10, u * 22, (float) Math.sin(time) * 0.3f, false, time, 0);
        float tb = u * 9;
        ui.button(c, 30, new RectF(stage.left + u * 3, stage.top + u * 3, stage.left + u * 22, stage.top + u * 3 + tb), "SIDE", view == 0 ? Ui.PRIMARY : Ui.SECONDARY, false, false);
        ui.button(c, 31, new RectF(stage.left + u * 24, stage.top + u * 3, stage.left + u * 43, stage.top + u * 3 + tb), "REAR", view == 1 ? Ui.PRIMARY : Ui.SECONDARY, false, false);

        float top = h * 0.415f, rh = (h * 0.47f) / Rider.SLOTS;
        for (int i = 0; i < Rider.SLOTS; i++) {
            RectF rr = new RectF(u * 4, top + i * rh, w - u * 4, top + (i + 1) * rh - u * 0.6f);
            boolean f = row == i;
            ui.p.setColor(f ? 0xDD24345A : 0x99101828);
            c.drawRoundRect(rr, u * 2, u * 2, ui.p);
            if (f) ui.stroke(c, rr, u * 2, u * 0.5f, Ui.ORANGE);
            float ty = rr.centerY() + u * 1.3f;
            ui.text(c, Rider.SLOT_NAMES[i], rr.left + u * 3, ty, u * 3.6f, 0xFFFFFFFF, Paint.Align.LEFT, Ui.BOLD);
            String val = Rider.optionName(i, r.v[i]);
            float vx = rr.right - u * 31;
            ui.p.setColor(Rider.swatch(i, r.v[i]));
            if (Rider.isColorSlot(i) || i == Rider.BIKE) {
                c.drawRoundRect(new RectF(vx - u * 8, rr.centerY() - u * 2, vx - u * 3, rr.centerY() + u * 2), u, u, ui.p);
            }
            ui.text(c, val, vx + u * 12, ty, u * 3.4f, 0xFFFFE0B2, Paint.Align.CENTER, Ui.BOLD);
            ui.text(c, "◀", vx - u * 12, ty, u * 4.2f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);
            ui.text(c, "▶", rr.right - u * 3.5f, ty, u * 4.2f, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);
            ui.register(300 + i, rr);
            ui.register(200 + i * 2, new RectF(vx - u * 16, rr.top, vx + u * 6, rr.bottom));
            ui.register(201 + i * 2, new RectF(vx + u * 18, rr.top, rr.right, rr.bottom));
        }
        float by = h * 0.9f, bh = u * 12;
        ui.button(c, 32, new RectF(u * 6, by, w / 2f - u * 2, by + bh), "RESET", Ui.DANGER, row == Rider.SLOTS, false);
        ui.button(c, 33, new RectF(w / 2f + u * 2, by, w - u * 6, by + bh), "DONE", Ui.GO, row == Rider.SLOTS + 1, false);
    }

    public void renderBoard(Canvas c, float time) {
        scenicBackground(c, 0.7f);
        title(c, "LEADERBOARD", h * 0.07f);
        float tw = (w - u * 16) / 2;
        ui.button(c, 40, new RectF(u * 6, h * 0.09f, u * 6 + tw, h * 0.09f + u * 11), "ENDLESS", boardMode == 0 ? Ui.PRIMARY : Ui.SECONDARY, false, false);
        ui.button(c, 41, new RectF(w - u * 6 - tw, h * 0.09f, w - u * 6, h * 0.09f + u * 11), "TIME ATTACK", boardMode == 1 ? Ui.PRIMARY : Ui.SECONDARY, false, false);

        List<Store.Entry> list = store.board(boardMode);
        float top = h * 0.17f, rh = h * 0.062f;
        if (list.isEmpty()) {
            ui.text(c, "No runs yet. Go ride!", w / 2f, h * 0.35f, u * 5, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        }
        int[] medal = {0xFFFFD54F, 0xFFCFD8DC, 0xFFD7A26A};
        for (int i = 0; i < list.size(); i++) {
            Store.Entry e = list.get(i);
            RectF rr = new RectF(u * 5, top + i * rh, w - u * 5, top + (i + 1) * rh - u);
            boolean mine = boardMode == lastMode && i == lastRank;
            ui.panel(c, rr, mine ? 0xDDF77F00 : 0xAA101828);
            float cy = rr.centerY();
            ui.p.setColor(i < 3 ? medal[i] : 0x44FFFFFF);
            c.drawCircle(rr.left + u * 6, cy, u * 3.6f, ui.p);
            ui.text(c, String.valueOf(i + 1), rr.left + u * 6, cy + u * 1.5f, u * 4, i < 3 ? 0xFF000000 : 0xFFFFFFFF, Paint.Align.CENTER, Ui.BLACK);
            Rider ri = store.riders[Math.max(0, Math.min(store.riders.length - 1, e.rider))];
            c.save();
            c.clipRect(rr.left + u * 11, rr.top, rr.left + u * 20, rr.bottom);
            RiderArt.drawRear(c, ri, rr.left + u * 15.5f, rr.bottom + u * 2.5f, u * 3.6f, 0, false, 0, 0);
            c.restore();
            ui.text(c, e.name.toUpperCase(), rr.left + u * 21, cy - u * 0.3f, u * 4.2f, 0xFFFFFFFF, Paint.Align.LEFT, Ui.BLACK);
            ui.text(c, String.format("%.1f km  •  top %d km/h", e.km, (int) e.topSpeed), rr.left + u * 21, cy + u * 4.2f, u * 2.9f, 0xCCFFFFFF, Paint.Align.LEFT, Ui.BOLD);
            ui.text(c, String.valueOf(e.score), rr.right - u * 4, cy + u * 2, u * 6, mine ? 0xFFFFFFFF : Ui.ORANGE, Paint.Align.RIGHT, Ui.BLACK);
        }
        ui.text(c, "Top " + Store.BOARD_SIZE + " on this device", w / 2f, h * 0.81f, u * 3.2f, 0xAAFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        ui.button(c, 42, new RectF(u * 10, h * 0.86f, w - u * 10, h * 0.86f + u * 13), "BACK", Ui.SECONDARY, true, false);
    }

    public void renderOver(Canvas c, Game g, float time) {
        ui.p.setColor(0xCC05070F);
        c.drawRect(0, 0, w, h, ui.p);
        ui.outlineText(c, g.timeUp ? "TIME UP!" : "WIPEOUT!", w / 2f, h * 0.12f, u * 15, g.timeUp ? 0xFF4CD964 : 0xFFFF5252, 0xFF000000, Ui.BLACK);
        ui.text(c, Store.MODE_NAMES[g.mode] + "  •  " + g.rider.name.toUpperCase(), w / 2f, h * 0.16f, u * 3.8f, 0xFFFFE0B2, Paint.Align.CENTER, Ui.BOLD);
        ui.text(c, "SCORE", w / 2f, h * 0.22f, u * 4, 0xCCFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        ui.outlineText(c, String.valueOf((int) g.score), w / 2f, h * 0.3f, u * 18, 0xFFFFFFFF, 0xFF000000, Ui.BLACK);
        if (resultRank == 0) {
            float pulse = 1 + (float) Math.sin(time * 6) * 0.06f;
            ui.outlineText(c, "NEW RECORD!", w / 2f, h * 0.35f, u * 8 * pulse, 0xFFFFD54F, 0xFF000000, Ui.BLACK);
        } else if (resultRank > 0) {
            ui.text(c, "#" + (resultRank + 1) + " on the leaderboard", w / 2f, h * 0.35f, u * 5, 0xFFFFD54F, Paint.Align.CENTER, Ui.BOLD);
        } else {
            ui.text(c, "Best: " + store.best(g.mode), w / 2f, h * 0.35f, u * 5, 0xCCFFFFFF, Paint.Align.CENTER, Ui.BOLD);
        }
        String[][] stats = {
                {"DISTANCE", String.format("%.2f km", g.km())},
                {"TOP SPEED", (int) g.topKmh + " km/h"},
                {"NEAR MISSES", String.valueOf(g.nearMisses)},
                {"BEST COMBO", "x" + g.bestCombo},
                {"OVERTAKES", String.valueOf(g.overtakes)},
                {"ONCOMING", String.format("%.1f s", g.oppTime)},
        };
        float gx = u * 6, gy = h * 0.39f, cw = (w - u * 15) / 2, ch = h * 0.075f;
        for (int i = 0; i < stats.length; i++) {
            float x = gx + (i % 2) * (cw + u * 3), y = gy + (i / 2) * (ch + u * 2);
            RectF r = new RectF(x, y, x + cw, y + ch);
            ui.panel(c, r, 0xAA24345A);
            ui.text(c, stats[i][0], r.centerX(), r.top + ch * 0.36f, u * 3, 0xAAFFFFFF, Paint.Align.CENTER, Ui.BOLD);
            ui.text(c, stats[i][1], r.centerX(), r.top + ch * 0.8f, u * 6, 0xFFFFFFFF, Paint.Align.CENTER, Ui.BLACK);
        }
        float by = h * 0.68f, bh = u * 13, gap = u * 3, x0 = u * 10, bw = w - u * 20;
        ui.button(c, 50, new RectF(x0, by, x0 + bw, by + bh), "RIDE AGAIN", Ui.GO, overFocus == 0, false);
        ui.button(c, 51, new RectF(x0, by + bh + gap, x0 + bw, by + 2 * bh + gap), "LEADERBOARD", Ui.PRIMARY, overFocus == 1, false);
        ui.button(c, 52, new RectF(x0, by + 2 * (bh + gap), x0 + bw, by + 3 * bh + 2 * gap), "MAIN MENU", Ui.SECONDARY, overFocus == 2, false);
    }

    public void renderPause(Canvas c) {
        ui.p.setColor(0xAA05070F);
        c.drawRect(0, 0, w, h, ui.p);
        ui.outlineText(c, "PAUSED", w / 2f, h * 0.3f, u * 15, Ui.CREAM, 0xFF000000, Ui.BLACK);
        float by = h * 0.4f, bh = u * 14, gap = u * 4, x0 = u * 12, bw = w - u * 24;
        ui.button(c, 60, new RectF(x0, by, x0 + bw, by + bh), "RESUME", Ui.GO, pauseFocus == 0, false);
        ui.button(c, 61, new RectF(x0, by + bh + gap, x0 + bw, by + 2 * bh + gap), "RESTART", Ui.PRIMARY, pauseFocus == 1, false);
        ui.button(c, 62, new RectF(x0, by + 2 * (bh + gap), x0 + bw, by + 3 * bh + 2 * gap), "QUIT TO MENU", Ui.SECONDARY, pauseFocus == 2, false);
    }
}
