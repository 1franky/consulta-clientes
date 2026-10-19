package com.empresa.consulta;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        Log.iniciar();
        LOG.info("Iniciando Consulta de clientes (Java " + System.getProperty("java.version")
                + ", logs en " + Log.carpeta() + ")");
        if (!FlatLightLaf.setup()) {
            LOG.warning("No se pudo aplicar el tema FlatLaf; se usará el aspecto por defecto de Swing");
        }

        SwingUtilities.invokeLater(() -> {
            Config config;
            try {
                config = Config.cargar();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error al cargar la configuración", e);
                JOptionPane.showMessageDialog(null, e.getMessage(),
                        "Error de configuración", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            try {
                new VentanaPrincipal(config).setVisible(true);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error al abrir la ventana principal", e);
                JOptionPane.showMessageDialog(null, e.getMessage(),
                        "Error de configuración", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
