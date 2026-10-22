package com.coppel.validador.service;

import com.coppel.validador.config.Config;
import com.coppel.validador.config.Config.ConexionDb;
import com.coppel.validador.model.LeyendaCorreo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lee catLeyendasCorreos con la sentencia sp.leyendasCorreos (letra, número, Leyenda1, Leyenda2, ClaveLeyenda):
 * el catálogo del que la emisión toma la leyenda de "AVISO IMPORTANTE".
 */
public final class SqlServerLeyendasService {

    private static final Logger LOG = Logger.getLogger(SqlServerLeyendasService.class.getName());

    private static final int TIMEOUT_CONSULTA_SEG = 60;

    private final Config config;
    private final ConexionDb db;

    public SqlServerLeyendasService(Config config) {
        this.config = config;
        this.db = config.conexion();
    }

    /** Nombre para la barra de estado. */
    public String origen() {
        return "catLeyendasCorreos de " + db.nombre();
    }

    /** Renglones del catálogo; lista vacía si la sentencia no está configurada. */
    public List<LeyendaCorreo> leer() throws SQLException {
        String sql = config.procedimiento("leyendasCorreos");
        List<LeyendaCorreo> leyendas = new ArrayList<>();
        if (sql == null) {
            return leyendas;
        }
        try (Connection con = DriverManager.getConnection(db.url(), db.usuario(), db.password());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setQueryTimeout(TIMEOUT_CONSULTA_SEG);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    leyendas.add(new LeyendaCorreo(texto(rs.getString(1)), rs.getInt(2), texto(rs.getString(5)),
                            texto(rs.getString(3)), texto(rs.getString(4))));
                }
            }
        }
        LOG.info("[" + db.nombre() + "] Catálogo de leyendas: " + leyendas.size() + " renglones");
        return leyendas;
    }

    private static String texto(String s) {
        return s == null ? "" : s.stripTrailing();
    }
}
