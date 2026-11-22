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
import java.time.ZoneId;

public class PurezaWidget extends AppWidgetProvider {

    static final String PREFS = "pureza";
    static final String KEY = "db";
    static final String ACCION_MARCAR = "com.pureza.app.MARCAR_LIMPIO";
    static final int[] HITOS = {1, 3, 7, 14, 21, 30, 60, 90, 180, 365};

    static String loadDb(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void saveDb(Context c, String db) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, db).apply();
    }

    /** Momento desde el que se cuenta la racha: tu última recaída (fecha y hora). */
    static long baseMs(JSONObject db) {
        long ms = db.optLong("ultimaMs", 0);
        if (ms > 0) return ms;
        String inicio = db.optString("inicio", "");
        if (inicio.isEmpty() || "null".equals(inicio)) return 0;
        try {
            return LocalDate.parse(inicio).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Misma lógica que la app: días completos (24 h) desde la última recaída. */
    static int calcRacha(JSONObject db) {
        long base = baseMs(db);
        if (base <= 0) return 0;
        long diff = System.currentTimeMillis() - base;
        return diff < 0 ? 0 : (int) (diff / 86400000L);
    }

    static JSONObject parse(Context c) {
        try {
            String s = loadDb(c);
            if (s != null && !s.isEmpty()) return new JSONObject(s);
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    static boolean recaidaHoy(JSONObject db) {
        JSONObject dias = db.optJSONObject("dias");
        return dias != null && "fail".equals(dias.optString(LocalDate.now().toString()));
    }

    static boolean marcadoHoy(JSONObject db) {
        JSONObject chk = db.optJSONObject("checkins");
        return chk != null && chk.optBoolean(LocalDate.now().toString(), false);
    }

    /** Marca el día de hoy como limpio (desde el botón del widget). */
    static void marcarHoy(Context c) {
        try {
            JSONObject db = parse(c);
            if (recaidaHoy(db)) return;
            JSONObject chk = db.optJSONObject("checkins");
            if (chk == null) {
                chk = new JSONObject();
                db.put("checkins", chk);
            }
            chk.put(LocalDate.now().toString(), true);
            saveDb(c, db.toString());
        } catch (Exception ignored) { }
    }

    static void updateAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, PurezaWidget.class));
        for (int id : ids) render(c, mgr, id);
    }

    static void render(Context c, AppWidgetManager mgr, int id) {
        JSONObject db = parse(c);
        int racha = calcRacha(db);
        boolean fallo = recaidaHoy(db);
        boolean marcado = marcadoHoy(db);

        String info = "👟 " + String.format(java.util.Locale.forLanguageTag("es-MX"), "%,d", StepTracker.pasosHoy(c));
        for (int h : HITOS) {
            if (h > racha) { info += "\n🎁 en " + (h - racha) + " d"; break; }
        }

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_pureza);
        v.setTextViewText(R.id.w_num, String.valueOf(racha));
        v.setTextViewText(R.id.w_label, racha == 1 ? "DÍA LIMPIO" : "DÍAS LIMPIO");
        v.setTextViewText(R.id.w_info, info);
        v.setTextViewText(R.id.w_btn, "🆘 Tengo ganas");

        if (fallo) {
            v.setTextViewText(R.id.w_btn2, "💙 Mañana seguimos");
            v.setInt(R.id.w_btn2, "setBackgroundResource", R.drawable.widget_btn_done);
        } else if (marcado) {
            v.setTextViewText(R.id.w_btn2, "✅ Hoy marcado");
            v.setInt(R.id.w_btn2, "setBackgroundResource", R.drawable.widget_btn_done);
        } else {
            v.setTextViewText(R.id.w_btn2, "✓ Día limpio");
            v.setInt(R.id.w_btn2, "setBackgroundResource", R.drawable.widget_btn_ok);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, open, flags));

        Intent urge = new Intent(c, MainActivity.class).putExtra("accion", "urge");
        v.setOnClickPendingIntent(R.id.w_btn, PendingIntent.getActivity(c, 1, urge, flags));

        Intent marcar = new Intent(c, PurezaWidget.class).setAction(ACCION_MARCAR);
        v.setOnClickPendingIntent(R.id.w_btn2, PendingIntent.getBroadcast(c, 2, marcar, flags));

        mgr.updateAppWidget(id, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(c, mgr, id);
    }

    @Override
    public void onReceive(final Context c, Intent intent) {
        if (ACCION_MARCAR.equals(intent.getAction())) {
            marcarHoy(c);
            updateAll(c);
            return;
        }
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
