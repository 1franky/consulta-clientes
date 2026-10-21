package com.coppel.validador.service;

import com.coppel.validador.config.Config;
import com.coppel.validador.config.Config.ConexionDb;
import com.coppel.validador.model.FactorBonificacion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lee el catálogo de bonificaciones de la base (cat_bonificacionescarteras y cat_bonificacionesPrestamos)
 * con la sentencia sp.bonificaciones, que devuelve cartera, plazo, días, factor y vigencia.
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
}
