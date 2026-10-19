package com.empresa.consulta;

import com.empresa.consulta.Config.ConexionDb;

import javax.swing.table.DefaultTableModel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConsultaService {

    private static final Logger LOG = Logger.getLogger(ConsultaService.class.getName());

    private static final int TIMEOUT_CONEXION_SEG = 10;
    private static final int TIMEOUT_CONSULTA_SEG = 30;
    private static final int MAX_FILAS = 1000;

    static {
        DriverManager.setLoginTimeout(TIMEOUT_CONEXION_SEG);
    }

    private ConsultaService() {
    }

    /** Ejecuta la consulta configurada reemplazando el "?" por el número de cliente. */
    public static DefaultTableModel consultar(ConexionDb db, long numeroCliente) throws SQLException {
        LOG.info("[" + db.nombre() + "] Consultando cliente " + numeroCliente);
        long inicio = System.nanoTime();
        try (Connection con = DriverManager.getConnection(db.url(), db.usuario(), db.password())) {
            con.setReadOnly(true);
            try (PreparedStatement ps = con.prepareStatement(db.consulta())) {
                ps.setQueryTimeout(TIMEOUT_CONSULTA_SEG);
                ps.setMaxRows(MAX_FILAS);
                ps.setLong(1, numeroCliente);
                try (ResultSet rs = ps.executeQuery()) {
                    DefaultTableModel modelo = aModelo(rs);
                    LOG.info("[" + db.nombre() + "] Cliente " + numeroCliente + ": " + modelo.getRowCount()
                            + " fila(s) en " + milisDesde(inicio) + " ms");
                    if (modelo.getRowCount() == MAX_FILAS) {
                        LOG.warning("[" + db.nombre() + "] Se alcanzó el límite de " + MAX_FILAS + " filas");
                    }
                    return modelo;
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[" + db.nombre() + "] Error consultando el cliente " + numeroCliente
                    + " tras " + milisDesde(inicio) + " ms (SQLState " + e.getSQLState()
                    + ", código " + e.getErrorCode() + ")", e);
            throw e;
        }
    }

    private static long milisDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private static DefaultTableModel aModelo(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnas = meta.getColumnCount();
        String[] nombres = new String[columnas];
        for (int i = 0; i < columnas; i++) {
            nombres[i] = meta.getColumnLabel(i + 1);
        }

        DefaultTableModel modelo = modeloSoloLectura(nombres);
        while (rs.next()) {
            Object[] fila = new Object[columnas];
            for (int i = 0; i < columnas; i++) {
                fila[i] = rs.getObject(i + 1);
            }
            modelo.addRow(fila);
        }
        return modelo;
    }

    public static DefaultTableModel modeloSoloLectura(Object[] columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
