package com.coppel.validador.ui;

import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.service.BitacoraValidacionService;
import com.coppel.validador.service.EstadoCuentaService;
import com.coppel.validador.validacion.CalculadoraEstadoCuenta;
import com.coppel.validador.validacion.EstadoCuentaEsperado;
import com.coppel.validador.validacion.GeneradorPuntosRevision;
import com.coppel.validador.validacion.PuntoRevision;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pantalla del validador: muestra impresa a la izquierda; a la derecha el estado de cuenta
 * esperado, los puntos a corroborar y los listados. No contiene SQL ni reglas de negocio:
 * recibe ya construidos el servicio de datos, la calculadora y la bitácora (ver App).
 */
public class VentanaPrincipal extends JFrame {

    private static final Logger LOG = Logger.getLogger(VentanaPrincipal.class.getName());

    private static final int ALTO_LOGO = 40;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EstadoCuentaService service;
    private final CalculadoraEstadoCuenta calculadora;
    private final BitacoraValidacionService bitacora;
    private final String leyendaMercadotecnia;

    private final JTextField txtCliente = new JTextField(12);
    private final JTextField txtCorte = new JTextField(9);
    private final JButton btnConsultar = new JButton("Consultar");
    private final JButton btnTema = new JButton();
    private final JLabel lblEstado = new JLabel(" ");
    private final JTabbedPane pestanas = new JTabbedPane();
    private final PanelMuestra panelMuestra = new PanelMuestra();
    private final PanelEstadoCuenta panelEstadoCuenta = new PanelEstadoCuenta();
    private final PanelPuntosRevision panelPuntos = new PanelPuntosRevision(this::guardar);
    private final PanelListados panelListados = new PanelListados();

    private ExpedienteCliente expediente;

    public VentanaPrincipal(EstadoCuentaService service, CalculadoraEstadoCuenta calculadora,
                            BitacoraValidacionService bitacora, String leyendaMercadotecnia, Path logo) {
        super("Validador de estados de cuenta");
        this.service = service;
        this.calculadora = calculadora;
        this.bitacora = bitacora;
        this.leyendaMercadotecnia = leyendaMercadotecnia;

        txtCorte.setText(FECHA.format(corteSugerido(LocalDate.now())));
        txtCorte.setToolTipText("Fecha de corte (dd/mm/aaaa)");

        JPanel formulario = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        formulario.add(new JLabel("Número de cliente:"));
        formulario.add(txtCliente);
        formulario.add(new JLabel("Corte:"));
        formulario.add(txtCorte);
        formulario.add(btnConsultar);

        btnTema.addActionListener(e -> cambiarTema());
        actualizarBotonTema();
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        derecha.add(btnTema);

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(formulario, BorderLayout.CENTER);
        arriba.add(derecha, BorderLayout.EAST);
        cargarLogo(logo, arriba);
        cargarIcono();

        pestanas.addTab("Estado de cuenta esperado", panelEstadoCuenta);
        pestanas.addTab("Puntos a revisar", panelPuntos);
        pestanas.addTab("Listados", panelListados);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelMuestra, pestanas);
        division.setResizeWeight(0.5);
        division.setContinuousLayout(true);

        lblEstado.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        lblEstado.setText("Origen de datos: " + service.origen() + " · Bonificaciones: " + calculadora.origenCatalogo());

        setLayout(new BorderLayout());
        add(arriba, BorderLayout.NORTH);
        add(division, BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);

        btnConsultar.addActionListener(e -> consultar());
        txtCliente.addActionListener(e -> consultar()); // Enter en el campo
        getRootPane().setDefaultButton(btnConsultar);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 650));
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
    }

    private void cambiarTema() {
        Tema.actual().contrario().aplicar();
        actualizarBotonTema();
    }

    private void actualizarBotonTema() {
        boolean oscuro = Tema.actual() == Tema.OSCURO;
        btnTema.setText(oscuro ? "Modo claro" : "Modo oscuro");
        btnTema.setToolTipText(oscuro ? "Cambiar a colores claros" : "Cambiar a colores oscuros");
    }

    /** Último corte al 20: si hoy es antes del 20, el del mes anterior. */
    static LocalDate corteSugerido(LocalDate hoy) {
        LocalDate corte = hoy.withDayOfMonth(20);
        return hoy.getDayOfMonth() >= 20 ? corte : corte.minusMonths(1);
    }

    private void consultar() {
        String texto = txtCliente.getText().trim();
        if (!texto.matches("\\d{1,12}")) {
            LOG.info("Número de cliente inválido: \"" + texto + "\"");
            lblEstado.setText("Ingresa un número de cliente válido (solo dígitos).");
            txtCliente.requestFocus();
            return;
        }
        long numeroCliente = Long.parseLong(texto);
        LocalDate corte;
        try {
            corte = LocalDate.parse(txtCorte.getText().trim(), FECHA);
        } catch (DateTimeParseException e) {
            lblEstado.setText("La fecha de corte debe tener el formato dd/mm/aaaa.");
            txtCorte.requestFocus();
            return;
        }

        btnConsultar.setEnabled(false);
        lblEstado.setText("Consultando cliente " + numeroCliente + " en " + service.origen() + "...");
        expediente = null;
        panelEstadoCuenta.limpiar();
        panelPuntos.mostrar(List.of());
        panelListados.limpiar();

        new SwingWorker<Resultado, Void>() {
            @Override
            protected Resultado doInBackground() throws Exception {
                ExpedienteCliente exp = service.consultar(numeroCliente, corte);
                EstadoCuentaEsperado esp = calculadora.calcular(exp);
                List<PuntoRevision> puntos = GeneradorPuntosRevision.generar(exp, esp, leyendaMercadotecnia);
                return new Resultado(exp, esp, puntos);
            }

            @Override
            protected void done() {
                btnConsultar.setEnabled(true);
                try {
                    Resultado r = get();
                    expediente = r.expediente();
                    panelEstadoCuenta.mostrar(r.expediente(), r.esperado());
                    panelPuntos.mostrar(r.puntos());
                    panelListados.mostrar(r.expediente());
                    int avisos = r.expediente().avisos().size();
                    lblEstado.setText("Cliente " + numeroCliente + " " + r.expediente().cliente().nombre() + ": "
                            + r.esperado().renglones().size() + " cuenta(s), " + r.puntos().size()
                            + " puntos a revisar" + (avisos > 0 ? ", " + avisos + " aviso(s) en la pestaña Listados" : "")
                            + ". Origen: " + service.origen());
                } catch (InterruptedException | ExecutionException | RuntimeException e) {
                    Throwable causa = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.SEVERE, "Error consultando el cliente " + numeroCliente, causa);
                    lblEstado.setText("Error: " + causa.getMessage());
                    JOptionPane.showMessageDialog(VentanaPrincipal.this, causa.getMessage(),
                            "Error al consultar", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void guardar(PanelPuntosRevision.Dictamen dictamen) {
        if (expediente == null) {
            return;
        }
        String muestra = panelMuestra.nombreMuestra();
        if (muestra.isEmpty()) {
            int r = JOptionPane.showConfirmDialog(this, "No hay una muestra abierta. ¿Guardar de todos modos?",
                    "Sin muestra", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            Path archivo = bitacora.guardar(expediente, dictamen.puntos(), dictamen.dictamen(),
                    dictamen.observacionGeneral(), muestra);
            lblEstado.setText("Validación " + dictamen.dictamen() + " guardada en " + archivo);
            JOptionPane.showMessageDialog(this, "Validación " + dictamen.dictamen() + " guardada en:\n" + archivo,
                    "Validación guardada", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo guardar la validación", e);
            JOptionPane.showMessageDialog(this, "No se pudo guardar la validación:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private record Resultado(ExpedienteCliente expediente, EstadoCuentaEsperado esperado, List<PuntoRevision> puntos) {
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
            lblLogo.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 4));
            arriba.add(lblLogo, BorderLayout.WEST);
        } catch (IOException e) {
            // Sin logo: la aplicación sigue funcionando igual
            LOG.log(Level.WARNING, "No se pudo leer el logo " + ruta, e);
        }
    }
}
