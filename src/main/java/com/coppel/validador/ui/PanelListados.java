package com.coppel.validador.ui;

import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.TablaListado;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.List;

/**
 * Los listados del cliente con el acomodo de los impresos de VbnListadoCarteras (ver {@link HojasListados}),
 * los avisos de la consulta y, en "Datos crudos", lo que devolvió cada procedimiento sin interpretar
 * (sirve para ajustar el mapeo de columnas en config.properties).
 */
public class PanelListados extends JPanel {

    private final JTabbedPane pestanas = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private ExpedienteCliente expediente;

    public PanelListados() {
        super(new BorderLayout());
        add(pestanas, BorderLayout.CENTER);
    }

    public void limpiar() {
        expediente = null;
        pestanas.removeAll();
    }

    public void mostrar(ExpedienteCliente exp) {
        expediente = exp;
        pintar();
    }

    /** Al cambiar de tema (FlatLaf.updateUI) se vuelven a armar las hojas con los colores nuevos. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (pestanas != null && expediente != null) { // updateUI() también se llama desde el constructor de JPanel
            pintar();
        }
    }

    private void pintar() {
        int seleccionada = pestanas.getSelectedIndex();
        pestanas.removeAll();
        ExpedienteCliente exp = expediente;
        pestanas.addTab("Avisos (" + exp.avisos().size() + ")",
                new JScrollPane(new JList<>(exp.avisos().toArray(String[]::new))));

        HojasListados.Colores colores = FlatLaf.isLafDark() ? HojasListados.Colores.OSCUROS : HojasListados.Colores.CLAROS;
        for (HojasListados.Hoja hoja : HojasListados.armar(exp, LocalDate.now(), colores)) {
            JEditorPane html = new JEditorPane("text/html", hoja.html());
            html.setEditable(false);
            html.setCaretPosition(0);
            pestanas.addTab(hoja.titulo(), new JScrollPane(html));
        }

        JTabbedPane crudos = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
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
            crudos.addTab(t.nombre() + (t.error() != null ? " (error)" : " (" + t.filas().size() + ")"),
                    new JScrollPane(tabla));
        }
        pestanas.addTab("Datos crudos", crudos);
        if (seleccionada >= 0 && seleccionada < pestanas.getTabCount()) {
            pestanas.setSelectedIndex(seleccionada);
        }
    }
}
