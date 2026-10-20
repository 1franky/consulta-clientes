package com.coppel.validador.ui;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Visor de la muestra escaneada (PDF o imagen) para compararla contra lo esperado. */
public class PanelMuestra extends JPanel {

    private static final Logger LOG = Logger.getLogger(PanelMuestra.class.getName());

    private static final float DPI_BASE = 72f;
    private static final float[] ZOOMS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f};

    private final JLabel lblImagen = new JLabel("Abre la muestra escaneada (PDF o imagen) con \"Abrir muestra\"",
            SwingConstants.CENTER);
    private final JScrollPane scroll = new JScrollPane(lblImagen);
    private final JLabel lblPagina = new JLabel(" ");
    private final JButton btnAnterior = new JButton("◀");
    private final JButton btnSiguiente = new JButton("▶");
    private final JButton btnMenos = new JButton("−");
    private final JButton btnMas = new JButton("+");

    private PDDocument documento;
    private PDFRenderer renderer;
    private BufferedImage imagenSuelta;
    private File archivo;
    private int pagina;
    private int zoom = 4; // 1.5x
    private boolean renderizando;

    public PanelMuestra() {
        super(new BorderLayout());
        JButton btnAbrir = new JButton("Abrir muestra...");
        btnAbrir.addActionListener(e -> elegirArchivo());
        btnAnterior.addActionListener(e -> irA(pagina - 1));
        btnSiguiente.addActionListener(e -> irA(pagina + 1));
        btnMenos.addActionListener(e -> cambiarZoom(-1));
        btnMas.addActionListener(e -> cambiarZoom(1));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        barra.add(btnAbrir);
        barra.add(btnAnterior);
        barra.add(lblPagina);
        barra.add(btnSiguiente);
        barra.add(new JLabel("  Zoom:"));
        barra.add(btnMenos);
        barra.add(btnMas);

        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        setBorder(BorderFactory.createTitledBorder("Muestra impresa"));
        add(barra, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        actualizarControles();
    }

    /** Nombre del archivo abierto (para la bitácora), o vacío. */
    public String nombreMuestra() {
        return archivo == null ? "" : archivo.getName() + (documento != null ? " p." + (pagina + 1) : "");
    }

    private void elegirArchivo() {
        JFileChooser chooser = new JFileChooser(archivo != null ? archivo.getParentFile() : null);
        chooser.setFileFilter(new FileNameExtensionFilter("Muestras (PDF, PNG, JPG)", "pdf", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            abrir(chooser.getSelectedFile());
        }
    }

    public void abrir(File nuevo) {
        cerrar();
        try {
            if (nuevo.getName().toLowerCase().endsWith(".pdf")) {
                documento = Loader.loadPDF(nuevo);
                renderer = new PDFRenderer(documento);
            } else {
                imagenSuelta = ImageIO.read(nuevo);
                if (imagenSuelta == null) {
                    throw new IOException("Formato de imagen no soportado");
                }
            }
            archivo = nuevo;
            LOG.info("Muestra abierta: " + nuevo.getAbsolutePath());
            irA(0);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo abrir la muestra " + nuevo, e);
            JOptionPane.showMessageDialog(this, "No se pudo abrir " + nuevo.getName() + ":\n" + e.getMessage(),
                    "Muestra", JOptionPane.ERROR_MESSAGE);
            cerrar();
        }
    }

    private void cerrar() {
        if (documento != null) {
            try {
                documento.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Error cerrando el PDF", e);
            }
        }
        documento = null;
        renderer = null;
        imagenSuelta = null;
        archivo = null;
        pagina = 0;
        lblImagen.setIcon(null);
        actualizarControles();
    }

    private int totalPaginas() {
        if (documento != null) {
            return documento.getNumberOfPages();
        }
        return imagenSuelta != null ? 1 : 0;
    }

    private void cambiarZoom(int delta) {
        zoom = Math.max(0, Math.min(ZOOMS.length - 1, zoom + delta));
        irA(pagina);
    }

    private void irA(int nueva) {
        if (totalPaginas() == 0 || nueva < 0 || nueva >= totalPaginas() || renderizando) {
            return;
        }
        pagina = nueva;
        float escala = ZOOMS[zoom];
        if (imagenSuelta != null) {
            mostrar(escalar(imagenSuelta, escala * 0.5f));
            return;
        }
        renderizando = true;
        actualizarControles();
        PDFRenderer r = renderer;
        int p = pagina;
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws IOException {
                return r.renderImageWithDPI(p, DPI_BASE * escala, ImageType.RGB);
            }

            @Override
            protected void done() {
                renderizando = false;
                try {
                    mostrar(get());
                } catch (InterruptedException | ExecutionException e) {
                    LOG.log(Level.WARNING, "No se pudo mostrar la página " + (p + 1), e);
                    lblImagen.setIcon(null);
                    lblImagen.setText("No se pudo mostrar la página: " + e.getMessage());
                }
                actualizarControles();
            }
        }.execute();
    }

    private void mostrar(BufferedImage imagen) {
        lblImagen.setText(null);
        lblImagen.setIcon(new ImageIcon(imagen));
        actualizarControles();
    }

    private static BufferedImage escalar(BufferedImage original, float escala) {
        int w = Math.max(1, Math.round(original.getWidth() * escala));
        int h = Math.max(1, Math.round(original.getHeight() * escala));
        BufferedImage destino = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        return destino;
    }

    private void actualizarControles() {
        int total = totalPaginas();
        lblPagina.setText(total == 0 ? "Sin muestra" : "Página " + (pagina + 1) + " de " + total);
        btnAnterior.setEnabled(!renderizando && pagina > 0);
        btnSiguiente.setEnabled(!renderizando && pagina < total - 1);
        btnMenos.setEnabled(total > 0 && zoom > 0);
        btnMas.setEnabled(total > 0 && zoom < ZOOMS.length - 1);
    }
}
