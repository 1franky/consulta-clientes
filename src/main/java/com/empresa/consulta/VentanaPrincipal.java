package com.empresa.consulta;

import com.empresa.consulta.Config.ConexionDb;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VentanaPrincipal extends JFrame {

    private static final Logger LOG = Logger.getLogger(VentanaPrincipal.class.getName());

    private static final int ALTO_LOGO = 48;

    private final List<ConexionDb> conexiones;
    private final List<JTable> tablas = new ArrayList<>();
    private final JTextField txtCliente = new JTextField(15);
    private final JButton btnConsultar = new JButton("Consultar");
    private final JLabel lblEstado = new JLabel(" ");
    private final JTabbedPane pestanas = new JTabbedPane();
    private int pendientes;

    public VentanaPrincipal(Config config) {
        super("Consulta de clientes");
        this.conexiones = config.conexiones();

        JPanel formulario = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        formulario.add(new JLabel("Número de cliente:"));
        formulario.add(txtCliente);
        formulario.add(btnConsultar);

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(formulario, BorderLayout.CENTER);
        cargarLogo(config.logo(), arriba);
        cargarIcono();

        for (ConexionDb db : conexiones) {
            JTable tabla = new JTable(ConsultaService.modeloSoloLectura(new Object[0]));
            tabla.setAutoCreateRowSorter(true);
            tablas.add(tabla);
            pestanas.addTab(db.nombre(), new JScrollPane(tabla));
        }

        lblEstado.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        setLayout(new BorderLayout());
        add(arriba, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);

        btnConsultar.addActionListener(e -> consultar());
        txtCliente.addActionListener(e -> consultar()); // Enter en el campo
        getRootPane().setDefaultButton(btnConsultar);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);
    }

    /** Ícono de la ventana y la barra de tareas, incluido dentro del jar. */
    private void cargarIcono() {
        URL recurso = getClass().getResource("/icono.png");
        if (recurso != null) {
            setIconImage(new ImageIcon(recurso).getImage());
        }
    }

    /** Muestra el logo arriba a la izquierda. */
    private void cargarLogo(Path ruta, JPanel arriba) {
        if (ruta == null) {
            return;
        }
        try {
            BufferedImage imagen = ImageIO.read(ruta.toFile());
            if (imagen == null) {
                LOG.warning("Formato de logo no soportado: " + ruta);
                return;
            }
            int ancho = imagen.getWidth() * ALTO_LOGO / imagen.getHeight();
            Image escalada = imagen.getScaledInstance(ancho, ALTO_LOGO, Image.SCALE_SMOOTH);
            JLabel lblLogo = new JLabel(new ImageIcon(escalada));
            lblLogo.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 4));
            arriba.add(lblLogo, BorderLayout.WEST);
        } catch (IOException e) {
            // Sin logo: la aplicación sigue funcionando igual
            LOG.log(Level.WARNING, "No se pudo leer el logo " + ruta, e);
        }
    }

    private void consultar() {
        String texto = txtCliente.getText().trim();
        long numeroCliente;
        try {
            if (!texto.matches("\\d+")) {
                throw new NumberFormatException();
            }
            numeroCliente = Long.parseLong(texto);
        } catch (NumberFormatException e) {
            LOG.info("Número de cliente inválido: \"" + texto + "\"");
            lblEstado.setText("Ingresa un número de cliente válido (solo dígitos).");
            txtCliente.requestFocus();
            return;
        }

        btnConsultar.setEnabled(false);
        lblEstado.setText("Consultando cliente " + numeroCliente + "...");
        pendientes = conexiones.size();

        for (int i = 0; i < conexiones.size(); i++) {
            ejecutar(i, numeroCliente);
        }
    }

    private void ejecutar(int indice, long numeroCliente) {
        ConexionDb db = conexiones.get(indice);
        pestanas.setTitleAt(indice, db.nombre() + " (...)");

        new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                return ConsultaService.consultar(db, numeroCliente);
            }

            @Override
            protected void done() {
                DefaultTableModel modelo;
                String titulo;
                try {
                    modelo = get();
                    titulo = db.nombre() + " (" + modelo.getRowCount() + ")";
                } catch (InterruptedException | ExecutionException | RuntimeException e) {
                    Throwable causa = e.getCause() != null ? e.getCause() : e;
                    if (!(causa instanceof SQLException)) { // los SQLException ya se registran en ConsultaService
                        LOG.log(Level.SEVERE, "[" + db.nombre() + "] Error inesperado", causa);
                    }
                    modelo = ConsultaService.modeloSoloLectura(new Object[]{"Error"});
                    modelo.addRow(new Object[]{causa.getMessage()});
                    titulo = db.nombre() + " (error)";
                }
                tablas.get(indice).setModel(modelo);
                pestanas.setTitleAt(indice, titulo);

                if (--pendientes == 0) {
                    btnConsultar.setEnabled(true);
                    lblEstado.setText("Consulta terminada para el cliente " + numeroCliente + ".");
                }
            }
        }.execute();
    }
}
