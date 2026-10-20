package com.coppel.validador;

import com.coppel.validador.config.Config;
import com.coppel.validador.config.Log;
import com.coppel.validador.service.DemoEstadoCuentaService;
import com.coppel.validador.service.EstadoCuentaService;
import com.coppel.validador.service.SqlServerEstadoCuentaService;
import com.coppel.validador.ui.Tema;
import com.coppel.validador.ui.VentanaPrincipal;
import com.coppel.validador.validacion.CalculadoraEstadoCuenta;
import com.coppel.validador.validacion.CatalogoBonificaciones;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de entrada y "contenedor" de la aplicación: aquí se crean los objetos y se conectan
 * entre sí (lo que en Spring haría la inyección de dependencias). Capas:
 *  - config      : config.properties y logs
 *  - service     : acceso a la base de datos (procedimientos)
 *  - validacion  : reglas del manual "Estado de Cuenta Correos" (sin BD ni pantallas)
 *  - ui          : pantallas Swing (sin SQL ni reglas)
 */
public class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        Log.iniciar();
        LOG.info("Iniciando Validador de estados de cuenta (Java " + System.getProperty("java.version")
                + ", logs en " + Log.carpeta() + ")");
        Tema.aplicarGuardado();

        SwingUtilities.invokeLater(() -> {
            try {
                Config config = Config.cargar();
                EstadoCuentaService service = config.modoDemo()
                        ? new DemoEstadoCuentaService()
                        : new SqlServerEstadoCuentaService(config);
                CalculadoraEstadoCuenta calculadora = new CalculadoraEstadoCuenta(CatalogoBonificaciones.cargar(config.bonificaciones()));
                LOG.info("Origen de datos: " + service.origen());

                new VentanaPrincipal(service, calculadora, config.logo())
                        .setVisible(true);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error al iniciar la aplicación", e);
                JOptionPane.showMessageDialog(null, e.getMessage(),
                        "Error de configuración", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
