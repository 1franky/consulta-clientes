package com.coppel.validador.ui;

import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.TablaListado;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Lo que devolvió cada procedimiento, sin interpretar, y los avisos de la consulta.
 * Sirve para revisar a mano el listado y para ajustar el mapeo de columnas en config.properties.
 */
public class PanelListados extends JPanel {

    private final JTabbedPane pestanas = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

    public PanelListados() {
        super(new BorderLayout());
        add(pestanas, BorderLayout.CENTER);
    }

    public void limpiar() {
        pestanas.removeAll();
    }

    public void mostrar(ExpedienteCliente exp) {
        limpiar();
        pestanas.addTab("Avisos (" + exp.avisos().size() + ")",
                new JScrollPane(new JList<>(exp.avisos().toArray(String[]::new))));
        for (TablaListado t : exp.listados()) {
            DefaultTableModel modelo = new DefaultTableModel(t.columnas().toArray(), 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            for (List<Object> fila : t.filas()) {
                modelo.addRow(fila.toArray());
            }
            JTable tabla = new JTable(modelo);
            tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            tabla.setAutoCreateRowSorter(true);
            pestanas.addTab(t.nombre() + (t.error() != null ? " (error)" : " (" + t.filas().size() + ")"),
                    new JScrollPane(tabla));
        }
    }
}
