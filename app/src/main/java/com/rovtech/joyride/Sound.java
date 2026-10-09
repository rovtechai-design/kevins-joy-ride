package com.rovtech.joyride;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/** Tiny software synth: single-cylinder engine thump that follows RPM, plus a few effects. */
public class Sound implements Runnable {
    private static final int RATE = 22050, BUF = 512;

    public volatile boolean enabled = true;
    public volatile boolean engineOn = false;
    public volatile float rpm = 1500f, throttle = 0f;

    private volatile float crash, whoosh, beep, beepFreq = 880f;
    private volatile boolean running;
    private Thread thread;
    private AudioTrack track;
    private final Random rnd = new Random();

    public void start() {
        if (running) return;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, BUF * 4), AudioTrack.MODE_STREAM);
            track.play();
        } catch (Exception e) {
            track = null;
            return;
        }
        running = true;
        thread = new Thread(this, "sound");
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) { try { thread.join(300); } catch (InterruptedException ignored) {} }
        if (track != null) { try { track.stop(); track.release(); } catch (Exception ignored) {} track = null; }
    }

    public void crash() { crash = 1f; }
    public void whoosh() { whoosh = 1f; }
    public void beep(float freq) { beepFreq = freq; beep = 1f; }

    @Override
    public void run() {
        short[] out = new short[BUF];
        double phase = 0, bphase = 0, wphase = 0;
        float lp = 0, smoothRpm = rpm, vol = 0;
        while (running) {
            float targetVol = (enabled && engineOn) ? 0.22f + throttle * 0.12f : 0f;
            for (int i = 0; i < BUF; i++) {
                smoothRpm += (rpm - smoothRpm) * 0.002f;
                vol += (targetVol - vol) * 0.001f;
                // firing pulses: a 4-stroke single fires every 2 revolutions; doubled for audibility
                double f = smoothRpm / 60.0;
                phase += f / RATE;
                if (phase >= 1) phase -= 1;
                float pulse = phase < 0.18 ? 1f : -0.22f;
                float body = (float) Math.sin(phase * Math.PI * 2) * 0.5f;
                float noise = (rnd.nextFloat() * 2 - 1) * (0.12f + throttle * 0.18f);
                float raw = pulse * 0.6f + body + noise * (phase < 0.3 ? 1f : 0.3f);
                float cutoff = 0.08f + Math.min(0.5f, smoothRpm / 22000f);
                lp += (raw - lp) * cutoff;
                float s = lp * vol;

                if (enabled && crash > 0.001f) {
                    s += (rnd.nextFloat() * 2 - 1) * crash * 0.7f;
                    crash *= 0.99985f;
                } else crash = 0;
                if (enabled && whoosh > 0.001f) {
                    wphase += (300 + 1500 * whoosh) / (double) RATE;
                    s += (float) Math.sin(wphase * Math.PI * 2) * (rnd.nextFloat()) * whoosh * 0.25f;
                    whoosh *= 0.99975f;
                } else whoosh = 0;
                if (enabled && beep > 0.001f) {
                    bphase += beepFreq / RATE;
                    s += (float) Math.sin(bphase * Math.PI * 2) * beep * 0.25f;
                    beep *= 0.9993f;
                } else beep = 0;

                if (s > 1) s = 1; else if (s < -1) s = -1;
                out[i] = (short) (s * 30000);
            }
            if (track != null) track.write(out, 0, BUF);
        }
    }
}
