package com.empresa.consulta;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class App {

    public static void main(String[] args) {
        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> {
            Config config;
            try {
                config = Config.cargar();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage(),
                        "Error de configuración", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            new VentanaPrincipal(config).setVisible(true);
        });
    }
}
