package com.pureza.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cuenta pasos con el sensor del teléfono (TYPE_STEP_COUNTER).
 * El sensor acumula pasos desde el último reinicio, incluso con la app cerrada;
 * aquí se lee cada vez que se abre la app o se actualiza el widget y se reparte por día.
 */
public class StepTracker {

    private static final String PREFS = "pureza_steps";

    static boolean permitido(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    static String mapa(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("map", "{}");
    }

    static long pasosHoy(Context c) {
        try {
            return new JSONObject(mapa(c)).optLong(LocalDate.now().toString(), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Lee el sensor una vez, guarda y avisa. Siempre llama a done (aunque no haya sensor o permiso). */
    static void sample(final Context c, final Runnable done) {
        if (!permitido(c)) { done.run(); return; }
        final SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) { done.run(); return; }

        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] fin = {false};
        final SensorEventListener[] listener = new SensorEventListener[1];

        final Runnable finish = new Runnable() {
            @Override public void run() {
                if (fin[0]) return;
                fin[0] = true;
                sm.unregisterListener(listener[0]);
                done.run();
            }
        };

        listener[0] = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                if (fin[0]) return;
                record(c, (long) e.values[0]);
                h.removeCallbacks(finish);
                finish.run();
            }
            @Override public void onAccuracyChanged(Sensor s, int a) { }
        };

        sm.registerListener(listener[0], sensor, SensorManager.SENSOR_DELAY_NORMAL);
        h.postDelayed(finish, 3000);
    }

    private static void record(Context c, long total) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long last = p.getLong("last", -1);
            String hoy = LocalDate.now().toString();
            JSONObject m = new JSONObject(p.getString("map", "{}"));
            if (last >= 0) {
                // Si el contador bajó, el teléfono se reinició: lo que hay ahora son pasos nuevos.
                long delta = total >= last ? total - last : total;
                m.put(hoy, m.optLong(hoy, 0) + delta);
            } else if (!m.has(hoy)) {
                m.put(hoy, 0);
            }
            // conserva solo los últimos 90 días
            String limite = LocalDate.now().minusDays(90).toString();
            List<String> borrar = new ArrayList<>();
            Iterator<String> it = m.keys();
            while (it.hasNext()) { String k = it.next(); if (k.compareTo(limite) < 0) borrar.add(k); }
            for (String k : borrar) m.remove(k);
            p.edit().putLong("last", total).putString("map", m.toString()).apply();
        } catch (Exception ignored) { }
    }
}
