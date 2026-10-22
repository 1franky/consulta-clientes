package com.coppel.validador.service;

import com.coppel.validador.config.Config;
import com.coppel.validador.config.Config.ConexionDb;
import com.coppel.validador.model.FactorBonificacion;
import com.coppel.validador.model.ReglasMuebles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Lee el catálogo de bonificaciones de la base (cat_bonificacionescarteras, cat_bonificacionesPrestamos y las
 * tablas de muebles por tipo de producto) con la sentencia sp.bonificaciones, que devuelve cartera, plazo, días,
 * factor y vigencia; y la configuración de proc_calculabonificacionmuebles (sp.muebles*).
 */
public final class SqlServerBonificacionesService {

    private static final Logger LOG = Logger.getLogger(SqlServerBonificacionesService.class.getName());

    private static final int TIMEOUT_CONSULTA_SEG = 60;

    private final Config config;
    private final ConexionDb db;

    public SqlServerBonificacionesService(Config config) {
        this.config = config;
        this.db = config.conexion();
    }

    /** Nombre para la barra de estado. */
    public String origen() {
        return "base " + db.nombre();
    }

    /** Renglones del catálogo; lista vacía si la sentencia no está configurada o las tablas están vacías. */
    public List<FactorBonificacion> leer() throws SQLException {
        String sql = config.procedimiento("bonificaciones");
        List<FactorBonificacion> factores = new ArrayList<>();
        if (sql == null) {
            return factores;
        }
        try (Connection con = DriverManager.getConnection(db.url(), db.usuario(), db.password());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setQueryTimeout(TIMEOUT_CONSULTA_SEG);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    factores.add(new FactorBonificacion(rs.getString(1).trim().toUpperCase(), rs.getInt(2),
                            rs.getInt(3), rs.getDouble(4), rs.getTimestamp(5).toLocalDateTime().toLocalDate()));
                }
            }
        }
        LOG.info("[" + db.nombre() + "] Catálogo de bonificaciones: " + factores.size() + " renglones");
        return factores;
    }

    /** Configuración de proc_calculabonificacionmuebles; NINGUNA si las sentencias no están configuradas. */
    public ReglasMuebles leerReglasMuebles() throws SQLException {
        Map<Integer, Integer> diasAjuste = new HashMap<>();
        for (Object[] f : filas("mueblesDiasAjuste")) {
            diasAjuste.put(((Number) f[0]).intValue(), ((Number) f[1]).intValue());
        }
        Set<Integer> ventaFutura = new HashSet<>();
        for (Object[] f : filas("mueblesVentaFutura")) {
            ventaFutura.add(((Number) f[0]).intValue());
        }
        Set<Integer> negocioAfiliado = new HashSet<>();
        for (Object[] f : filas("mueblesNegocioAfiliado")) {
            negocioAfiliado.add(((Number) f[0]).intValue());
        }
        Map<Integer, LocalDate> bonificacionPropia = new HashMap<>();
        for (Object[] f : filas("mueblesBonificacionPropia")) {
            bonificacionPropia.put(((Number) f[0]).intValue(), Registro.aFecha(f[1]));
        }
        LocalDate arranque = null;
        for (Object[] f : filas("mueblesArranqueNegocioAfiliado")) {
            arranque = LocalDate.parse(f[0].toString().strip().substring(0, 10));
        }
        LOG.info("[" + db.nombre() + "] Reglas de muebles: " + diasAjuste.size() + " tipos con ajuste, "
                + negocioAfiliado.size() + " tipos de negocio afiliado, arranque " + arranque);
        return new ReglasMuebles(diasAjuste, ventaFutura, negocioAfiliado, bonificacionPropia, arranque);
    }

    private List<Object[]> filas(String clave) throws SQLException {
        String sql = config.procedimiento(clave);
        List<Object[]> filas = new ArrayList<>();
        if (sql == null) {
            return filas;
        }
        try (Connection con = DriverManager.getConnection(db.url(), db.usuario(), db.password());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setQueryTimeout(TIMEOUT_CONSULTA_SEG);
            try (ResultSet rs = ps.executeQuery()) {
                int columnas = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] fila = new Object[columnas];
                    for (int i = 0; i < columnas; i++) {
                        fila[i] = rs.getObject(i + 1);
                    }
                    filas.add(fila);
                }
            }
        }
        return filas;
    }
}
