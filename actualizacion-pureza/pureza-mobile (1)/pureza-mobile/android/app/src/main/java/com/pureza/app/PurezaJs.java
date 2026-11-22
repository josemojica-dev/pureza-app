package com.pureza.app;

import android.content.Context;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/** Puente directo y sencillo entre la página y el teléfono (respaldo del plugin). */
public class PurezaJs {
    private final Context c;

    PurezaJs(Context c) {
        this.c = c.getApplicationContext();
    }

    @JavascriptInterface
    public String get() {
        return PurezaWidget.loadDb(c);
    }

    @JavascriptInterface
    public void set(String db) {
        PurezaWidget.saveDb(c, db == null ? "" : db);
        PurezaWidget.updateAll(c);
    }

    @JavascriptInterface
    public String steps() {
        try {
            JSONObject o = new JSONObject();
            o.put("mapa", new JSONObject(StepTracker.mapa(c)));
            o.put("hoy", StepTracker.pasosHoy(c));
            return o.toString();
        } catch (Exception e) {
            return "{\"mapa\":{},\"hoy\":0}";
        }
    }

    @JavascriptInterface
    public String consumeLaunch() {
        String a = MainActivity.accionPendiente;
        MainActivity.accionPendiente = null;
        return a == null ? "" : a;
    }
}
