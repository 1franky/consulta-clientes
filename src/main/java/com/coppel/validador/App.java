package com.coppel.validador;

import com.coppel.validador.config.Config;
import com.coppel.validador.config.Log;
import com.coppel.validador.model.FactorBonificacion;
import com.coppel.validador.model.ReglasMuebles;
import com.coppel.validador.service.DemoEstadoCuentaService;
import com.coppel.validador.service.EstadoCuentaService;
import com.coppel.validador.service.SqlServerBonificacionesService;
import com.coppel.validador.service.SqlServerEstadoCuentaService;
import com.coppel.validador.ui.Tema;
import com.coppel.validador.ui.VentanaPrincipal;
import com.coppel.validador.validacion.CalculadoraEstadoCuenta;
import com.coppel.validador.validacion.CatalogoBonificaciones;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
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
                CalculadoraEstadoCuenta calculadora = new CalculadoraEstadoCuenta(catalogo(config), reglasMuebles(config));
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

    /** Configuración de muebles por tipo de producto; sin base o si falla, el cálculo general de muebles. */
    private static ReglasMuebles reglasMuebles(Config config) {
        if (config.modoDemo()) {
            return ReglasMuebles.NINGUNA;
        }
        try {
            return new SqlServerBonificacionesService(config).leerReglasMuebles();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo leer la configuración de muebles: se usa el cálculo general", e);
            return ReglasMuebles.NINGUNA;
        }
    }

    /** Catálogo de bonificaciones de la base; si no hay conexión o las tablas están vacías, el CSV. */
    private static CatalogoBonificaciones catalogo(Config config) throws IOException {
        if (!config.modoDemo()) {
            SqlServerBonificacionesService bonificaciones = new SqlServerBonificacionesService(config);
            try {
                List<FactorBonificacion> factores = bonificaciones.leer();
                if (!factores.isEmpty()) {
                    return CatalogoBonificaciones.de(factores, bonificaciones.origen());
                }
                LOG.warning("Las tablas de bonificaciones están vacías: se usa bonificaciones.csv");
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "No se pudo leer el catálogo de bonificaciones de la base: se usa bonificaciones.csv", e);
            }
        }
        return CatalogoBonificaciones.cargar(config.bonificaciones());
    }
}
