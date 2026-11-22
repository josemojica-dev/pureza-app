package com.pureza.app;

import android.Manifest;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

/** Puente JS <-> nativo: datos compartidos con el widget, pasos y acciones del widget. */
@CapacitorPlugin(
    name = "Pureza",
    permissions = {
        @Permission(strings = { Manifest.permission.ACTIVITY_RECOGNITION }, alias = "actividad")
    }
)
public class PurezaPlugin extends Plugin {

    @PluginMethod
    public void get(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("db", PurezaWidget.loadDb(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void set(PluginCall call) {
        String db = call.getString("db", "");
        PurezaWidget.saveDb(getContext(), db);
        PurezaWidget.updateAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getSteps(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 29 && getPermissionState("actividad") != PermissionState.GRANTED) {
            requestPermissionForAlias("actividad", call, "permisoCallback");
            return;
        }
        responderPasos(call);
    }

    @PermissionCallback
    private void permisoCallback(PluginCall call) {
        responderPasos(call);
    }

    private void responderPasos(final PluginCall call) {
        StepTracker.sample(getContext(), new Runnable() {
            @Override public void run() {
                JSObject ret = new JSObject();
                try {
                    ret.put("mapa", new JSObject(StepTracker.mapa(getContext())));
                } catch (Exception e) {
                    ret.put("mapa", new JSObject());
                }
                ret.put("hoy", StepTracker.pasosHoy(getContext()));
                call.resolve(ret);
                PurezaWidget.updateAll(getContext());
            }
        });
    }

    @PluginMethod
    public void consumeLaunch(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("accion", MainActivity.accionPendiente);
        MainActivity.accionPendiente = null;
        call.resolve(ret);
    }
}
