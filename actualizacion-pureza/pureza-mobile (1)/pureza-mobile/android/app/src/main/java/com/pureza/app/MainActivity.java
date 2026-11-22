package com.pureza.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    /** Acción pedida desde el widget (por ejemplo "urge"). La app web la consume al abrirse. */
    static String accionPendiente = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PurezaPlugin.class);
        super.onCreate(savedInstanceState);
        try {
            getBridge().getWebView().addJavascriptInterface(new PurezaJs(this), "PurezaNative");
        } catch (Exception ignored) { }
        leerAccion(getIntent());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Pide el permiso de pasos una sola vez y refresca el widget.
        try {
            SharedPreferences p = getSharedPreferences("pureza_ui", Context.MODE_PRIVATE);
            if (Build.VERSION.SDK_INT >= 29 && !StepTracker.permitido(this) && !p.getBoolean("pidioPasos", false)) {
                p.edit().putBoolean("pidioPasos", true).apply();
                requestPermissions(new String[]{ Manifest.permission.ACTIVITY_RECOGNITION }, 77);
            } else {
                final Context ctx = getApplicationContext();
                StepTracker.sample(ctx, new Runnable() {
                    @Override public void run() { PurezaWidget.updateAll(ctx); }
                });
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        leerAccion(intent);
    }

    private void leerAccion(Intent intent) {
        if (intent != null && intent.hasExtra("accion")) {
            accionPendiente = intent.getStringExtra("accion");
            intent.removeExtra("accion");
        }
    }
}
