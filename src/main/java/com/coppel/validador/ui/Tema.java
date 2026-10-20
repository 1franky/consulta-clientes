package com.coppel.validador.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Modo claro / oscuro con FlatLaf. La elección se recuerda por usuario de Windows
 * (java.util.prefs), así que cada persona abre la aplicación como la dejó.
 */
public enum Tema {
    CLARO, OSCURO;

    private static final Logger LOG = Logger.getLogger(Tema.class.getName());
    private static final String PREFERENCIA = "tema";

    /** Aplica el tema guardado (claro si no hay ninguno). Se llama antes de crear ventanas. */
    public static void aplicarGuardado() {
        Tema tema = CLARO;
        try {
            tema = valueOf(preferencias().get(PREFERENCIA, CLARO.name()));
        } catch (IllegalArgumentException | SecurityException e) {
            LOG.fine("Preferencia de tema inválida o no disponible: " + e);
        }
        tema.instalar();
    }

    public static Tema actual() {
        return FlatLaf.isLafDark() ? OSCURO : CLARO;
    }

    public Tema contrario() {
        return this == CLARO ? OSCURO : CLARO;
    }

    /** Cambia el tema de todas las ventanas abiertas y lo guarda. */
    public void aplicar() {
        instalar();
        FlatLaf.updateUI();
        try {
            preferencias().put(PREFERENCIA, name());
        } catch (SecurityException e) {
            LOG.warning("No se pudo guardar la preferencia de tema: " + e);
        }
        LOG.info("Tema " + name().toLowerCase());
    }

    private void instalar() {
        boolean ok = this == OSCURO ? FlatDarkLaf.setup() : FlatLightLaf.setup();
        if (!ok) {
            LOG.warning("No se pudo aplicar el tema " + name() + "; se usará el aspecto por defecto de Swing");
        }
    }

    private static Preferences preferencias() {
        return Preferences.userNodeForPackage(Tema.class);
    }
}
