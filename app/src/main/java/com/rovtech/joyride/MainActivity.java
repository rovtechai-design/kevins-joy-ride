package com.rovtech.joyride;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class MainActivity extends Activity implements GameView.Host {
    private GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new GameView(this, this);
        setContentView(game);
        game.requestFocus();
        immersive();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0 && keyCode != KeyEvent.KEYCODE_BACK) {
            return game.onKey(keyCode, true) || super.onKeyDown(keyCode, event);
        }
        return game.onKey(keyCode, true) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return game.screen != Screens.MENU || super.onKeyUp(keyCode, event);
        }
        return game.onKey(keyCode, false) || super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.resume();
    }

    @Override
    public void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    @Override
    public void askName(String current, GameView.NameCallback cb) {
        runOnUiThread(() -> {
            EditText input = new EditText(this);
            input.setText(current);
            input.setSelectAllOnFocus(true);
            input.setSingleLine(true);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
            new AlertDialog.Builder(this)
                    .setTitle("Name your rider")
                    .setView(input)
                    .setPositiveButton("Save", (d, which) -> cb.onName(input.getText().toString()))
                    .setNegativeButton("Cancel", null)
                    .setOnDismissListener(d -> { immersive(); game.requestFocus(); })
                    .show();
        });
    }
}
