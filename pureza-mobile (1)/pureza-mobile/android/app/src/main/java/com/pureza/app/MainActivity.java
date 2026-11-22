package com.pureza.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    /** Acción pedida desde el widget (por ejemplo "urge"). La app web la consume al abrirse. */
    static String accionPendiente = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PurezaPlugin.class);
        super.onCreate(savedInstanceState);
        leerAccion(getIntent());
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
