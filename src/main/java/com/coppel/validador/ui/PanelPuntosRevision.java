package com.coppel.validador.ui;

import com.coppel.validador.validacion.PuntoRevision;
import com.coppel.validador.validacion.PuntoRevision.Resultado;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lista de puntos a corroborar. La persona compara cada valor esperado contra la muestra y marca
 * Correcto / Error / No aplica. Atajos: 1 = Correcto, 2 = Error, 3 = No aplica (y pasa al siguiente).
 */
public class PanelPuntosRevision extends JPanel {

    // Fondo de la fila según el resultado: {claro, oscuro}
    private static final Color[] VERDE = {new Color(0xDFF5E1), new Color(0x1F3D27)};
    private static final Color[] ROJO = {new Color(0xFDE2E2), new Color(0x4A2222)};
    private static final Color[] GRIS = {new Color(0xEEEEEE), new Color(0x3A3D44)};

    private static final int COL_RESULTADO = 4;
    private static final int COL_OBSERVACION = 5;

    private final Modelo modelo = new Modelo();
    private final JTable tabla = new JTable(modelo);
    private final JTextArea txtRegla = new JTextArea(3, 40);
    private final JLabel lblResumen = new JLabel(" ");
    private final JTextField txtObservacionGeneral = new JTextField(30);
    private final JButton btnGuardar = new JButton("Guardar validación");

    /** Recibe el dictamen y la observación general cuando la persona pulsa Guardar. */
    private final Consumer<Dictamen> alGuardar;

    public record Dictamen(String dictamen, String observacionGeneral, List<PuntoRevision> puntos) {
    }

    public PanelPuntosRevision(Consumer<Dictamen> alGuardar) {
        super(new BorderLayout());
        this.alGuardar = alGuardar;

        tabla.setRowHeight(24);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setDefaultRenderer(Object.class, new Renderizador());
        tabla.getColumnModel().getColumn(COL_RESULTADO).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(Resultado.values())));
        int[] anchos = {50, 140, 260, 260, 90, 200};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getSelectionModel().addListSelectionListener(e -> mostrarRegla());
        atajo("1", Resultado.OK);
        atajo("2", Resultado.ERROR);
        atajo("3", Resultado.NO_APLICA);

        txtRegla.setEditable(false);
        txtRegla.setLineWrap(true);
        txtRegla.setWrapStyleWord(true);
        txtRegla.setBorder(BorderFactory.createTitledBorder("Origen del valor esperado"));

        JSplitPane centro = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tabla), new JScrollPane(txtRegla));
        centro.setResizeWeight(0.85);

        JButton btnVisualesOk = new JButton("Marcar pendientes como Correcto");
        btnVisualesOk.setToolTipText("Solo después de revisar cada punto contra la muestra");
        btnVisualesOk.addActionListener(e -> marcarPendientes());
        btnGuardar.addActionListener(e -> guardar());

        JPanel ayuda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        ayuda.add(new JLabel("Atajos sobre la fila: 1 = Correcto · 2 = Error · 3 = No aplica"));
        ayuda.add(btnVisualesOk);

        JPanel abajo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        abajo.add(lblResumen);
        abajo.add(new JLabel("Observación general:"));
        abajo.add(txtObservacionGeneral);
        abajo.add(btnGuardar);

        JPanel sur = new JPanel(new BorderLayout());
        sur.add(ayuda, BorderLayout.NORTH);
        sur.add(abajo, BorderLayout.SOUTH);

        add(centro, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        mostrar(List.of());
    }

    /** Al cambiar de tema se recalcula el resumen (lleva color en HTML). */
    @Override
    public void updateUI() {
        super.updateUI();
        if (modelo != null) { // updateUI() también se llama desde el constructor de JPanel
            actualizarResumen();
        }
    }

    public void mostrar(List<PuntoRevision> puntos) {
        modelo.puntos = new ArrayList<>(puntos);
        modelo.fireTableDataChanged();
        txtObservacionGeneral.setText("");
        txtRegla.setText("");
        if (!puntos.isEmpty()) {
            tabla.setRowSelectionInterval(0, 0);
        }
        actualizarResumen();
    }

    private void atajo(String tecla, Resultado resultado) {
        String nombre = "marcar-" + resultado.name();
        tabla.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(tecla.charAt(0)), nombre);
        tabla.getActionMap().put(nombre, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int fila = tabla.getSelectedRow();
                if (fila < 0) {
                    return;
                }
                int i = tabla.convertRowIndexToModel(fila);
                modelo.setValueAt(resultado, i, COL_RESULTADO);
                if (fila + 1 < tabla.getRowCount()) {
                    tabla.setRowSelectionInterval(fila + 1, fila + 1);
                    tabla.scrollRectToVisible(tabla.getCellRect(fila + 1, 0, true));
                }
            }
        });
    }

    private void mostrarRegla() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= modelo.puntos.size()) {
            txtRegla.setText("");
            return;
        }
        PuntoRevision p = modelo.puntos.get(tabla.convertRowIndexToModel(fila));
        txtRegla.setText(p.clave() + " " + p.descripcion() + "\nEsperado: " + p.esperado()
                + "\n" + (p.visual() ? "Revisión visual. " : "") + p.regla());
        txtRegla.setCaretPosition(0);
    }

    private void marcarPendientes() {
        long pendientes = modelo.puntos.stream().filter(p -> p.resultado() == Resultado.PENDIENTE).count();
        if (pendientes == 0) {
            return;
        }
        int r = JOptionPane.showConfirmDialog(this, "¿Revisaste contra la muestra los " + pendientes
                        + " puntos pendientes?\nSe marcarán como Correcto.", "Confirmar", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            modelo.puntos.stream().filter(p -> p.resultado() == Resultado.PENDIENTE)
                    .forEach(p -> p.resultado(Resultado.OK));
            modelo.fireTableDataChanged();
            actualizarResumen();
        }
    }

    private void guardar() {
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }
        if (modelo.puntos.isEmpty()) {
            return;
        }
        long pendientes = contar(Resultado.PENDIENTE);
        if (pendientes > 0) {
            JOptionPane.showMessageDialog(this, "Faltan " + pendientes + " punto(s) por revisar.",
                    "Validación incompleta", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean sinObservacion = modelo.puntos.stream()
                .anyMatch(p -> p.resultado() == Resultado.ERROR && p.observacion().isBlank());
        if (sinObservacion) {
            JOptionPane.showMessageDialog(this, "Escribe una observación en cada punto marcado como Error.",
                    "Falta observación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        alGuardar.accept(new Dictamen(dictamen(), txtObservacionGeneral.getText().trim(), List.copyOf(modelo.puntos)));
    }

    private String dictamen() {
        return contar(Resultado.ERROR) > 0 ? "RECHAZADO" : "APROBADO";
    }

    private long contar(Resultado r) {
        return modelo.puntos.stream().filter(p -> p.resultado() == r).count();
    }

    private void actualizarResumen() {
        if (modelo.puntos.isEmpty()) {
            lblResumen.setText("Sin puntos");
            btnGuardar.setEnabled(false);
            return;
        }
        long pendientes = contar(Resultado.PENDIENTE);
        String rojo = FlatLaf.isLafDark() ? "#ff8080" : "#b00000";
        lblResumen.setText("<html>Correctos <b>" + contar(Resultado.OK) + "</b> · Errores <b><font color='" + rojo + "'>"
                + contar(Resultado.ERROR) + "</font></b> · No aplica " + contar(Resultado.NO_APLICA)
                + " · Pendientes <b>" + pendientes + "</b>"
                + (pendientes == 0 ? " → <b>" + dictamen() + "</b>" : "") + "</html>");
        btnGuardar.setEnabled(true);
    }

    private final class Modelo extends AbstractTableModel {
        private final String[] columnas = {"#", "Sección", "Punto", "Valor esperado", "Resultado", "Observación"};
        private List<PuntoRevision> puntos = new ArrayList<>();

        @Override
        public int getRowCount() {
            return puntos.size();
        }

        @Override
        public int getColumnCount() {
            return columnas.length;
        }

        @Override
        public String getColumnName(int c) {
            return columnas[c];
        }

        @Override
        public boolean isCellEditable(int fila, int c) {
            return c == COL_RESULTADO || c == COL_OBSERVACION;
        }

        @Override
        public Object getValueAt(int fila, int c) {
            PuntoRevision p = puntos.get(fila);
            return switch (c) {
                case 0 -> p.clave();
                case 1 -> p.seccion();
                case 2 -> p.descripcion();
                case 3 -> p.esperado();
                case 4 -> p.resultado();
                default -> p.observacion();
            };
        }

        @Override
        public void setValueAt(Object valor, int fila, int c) {
            PuntoRevision p = puntos.get(fila);
            if (c == COL_RESULTADO && valor instanceof Resultado r) {
                p.resultado(r);
            } else if (c == COL_OBSERVACION) {
                p.observacion(valor == null ? "" : valor.toString());
            }
            fireTableRowsUpdated(fila, fila);
            actualizarResumen();
        }
    }

    /** Colorea la fila según el resultado y resalta los puntos solo visuales. */
    private final class Renderizador extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object valor, boolean seleccionada, boolean foco,
                                                       int fila, int columna) {
            Component c = super.getTableCellRendererComponent(t, valor, seleccionada, foco, fila, columna);
            PuntoRevision p = modelo.puntos.get(t.convertRowIndexToModel(fila));
            if (!seleccionada) {
                int tema = FlatLaf.isLafDark() ? 1 : 0;
                c.setBackground(switch (p.resultado()) {
                    case OK -> VERDE[tema];
                    case ERROR -> ROJO[tema];
                    case NO_APLICA -> GRIS[tema];
                    case PENDIENTE -> t.getBackground();
                });
                c.setForeground(t.getForeground());
            }
            c.setFont(c.getFont().deriveFont(columna == 3 ? Font.BOLD : (p.visual() ? Font.ITALIC : Font.PLAIN)));
            setToolTipText(p.regla().isBlank() ? null : "<html><body style='width:400px'>" + p.regla() + "</body></html>");
            return c;
        }
    }
}
