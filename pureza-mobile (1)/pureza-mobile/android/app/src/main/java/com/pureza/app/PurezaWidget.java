package com.pureza.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class PurezaWidget extends AppWidgetProvider {

    static final String PREFS = "pureza";
    static final String KEY = "db";
    static final int[] HITOS = {1, 3, 7, 14, 21, 30, 60, 90, 180, 365};

    static String loadDb(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void saveDb(Context c, String db) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, db).apply();
    }

    /** Misma lógica que la app: todo día desde el inicio es limpio hasta la última recaída (hoy incluido). */
    static int calcRacha(JSONObject db) {
        try {
            String inicio = db.optString("inicio", "");
            if (inicio.isEmpty() || "null".equals(inicio)) return 0;
            JSONObject dias = db.optJSONObject("dias");
            if (dias == null) dias = new JSONObject();
            LocalDate hoy = LocalDate.now();
            if ("fail".equals(dias.optString(hoy.toString()))) return 0;
            List<String> ks = new ArrayList<>();
            Iterator<String> it = dias.keys();
            while (it.hasNext()) ks.add(it.next());
            Collections.sort(ks);
            String lastFail = null;
            for (String k : ks) if ("fail".equals(dias.optString(k))) lastFail = k;
            LocalDate start = lastFail != null ? LocalDate.parse(lastFail).plusDays(1) : LocalDate.parse(inicio);
            return (int) Math.max(0, ChronoUnit.DAYS.between(start, hoy) + 1);
        } catch (Exception e) {
            return 0;
        }
    }

    static JSONObject parse(Context c) {
        try {
            String s = loadDb(c);
            if (s != null && !s.isEmpty()) return new JSONObject(s);
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    static void updateAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, PurezaWidget.class));
        for (int id : ids) render(c, mgr, id);
    }

    static void render(Context c, AppWidgetManager mgr, int id) {
        int racha = calcRacha(parse(c));

        String info = "👟 " + String.format(java.util.Locale.forLanguageTag("es-MX"), "%,d", StepTracker.pasosHoy(c));
        for (int h : HITOS) {
            if (h > racha) { info += "  ·  🎁 en " + (h - racha) + " d"; break; }
        }

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_pureza);
        v.setTextViewText(R.id.w_num, String.valueOf(racha));
        v.setTextViewText(R.id.w_label, racha == 1 ? "DÍA LIMPIO" : "DÍAS LIMPIO");
        v.setTextViewText(R.id.w_info, info);
        v.setTextViewText(R.id.w_btn, "🆘 Tengo ganas");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, open, flags));

        Intent urge = new Intent(c, MainActivity.class).putExtra("accion", "urge");
        v.setOnClickPendingIntent(R.id.w_btn, PendingIntent.getActivity(c, 1, urge, flags));

        mgr.updateAppWidget(id, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(c, mgr, id);
    }

    @Override
    public void onReceive(final Context c, Intent intent) {
        super.onReceive(c, intent);
        // Cada actualización del widget aprovecha para leer los pasos del sensor.
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            final BroadcastReceiver.PendingResult pr = goAsync();
            StepTracker.sample(c, new Runnable() {
                @Override public void run() {
                    updateAll(c);
                    pr.finish();
                }
            });
        }
    }
}
