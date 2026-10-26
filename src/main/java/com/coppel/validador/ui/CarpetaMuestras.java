package com.coppel.validador.ui;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Miniaturas de las muestras de una carpeta (y de sus subcarpetas por cliente, p. ej. muestras/&lt;cliente&gt;/*.pdf)
 * para pasar de una a otra sin abrir cada archivo. Las miniaturas se generan en segundo plano.
 */
class CarpetaMuestras extends JPanel {

    private static final Logger LOG = Logger.getLogger(CarpetaMuestras.class.getName());

    static final int PROFUNDIDAD = 3;
    private static final int ANCHO_MINIATURA = 110;
    private static final int ALTO_MINIATURA = 142; // carta vertical
    private static final float DPI_MINIATURA = 24f;

    /** Una muestra de la carpeta; el nombre es la ruta relativa (carpeta del cliente / archivo). */
    record Muestra(File archivo, String nombre) {
    }

    private final DefaultListModel<Muestra> modelo = new DefaultListModel<>();
    private final JList<Muestra> lista = new JList<>(modelo);
    private final JLabel lblCarpeta = new JLabel(" ");
    private final Map<File, ImageIcon> miniaturas = new HashMap<>();
    private SwingWorker<Void, Muestra> generador;

    CarpetaMuestras(Consumer<File> alElegir) {
        super(new BorderLayout());
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setCellRenderer(new Celda());
        // celdas de tamaño fijo: la miniatura llega después y JList no recalcula la altura de cada renglón
        lista.setFixedCellWidth(ANCHO_MINIATURA + 60);
        lista.setFixedCellHeight(ALTO_MINIATURA + 60);
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lista.getSelectedValue() != null) {
                alElegir.accept(lista.getSelectedValue().archivo());
            }
        });
        lblCarpeta.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        JScrollPane scroll = new JScrollPane(lista);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(lblCarpeta, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(ANCHO_MINIATURA + 90, 0));
    }

    /** Carga las muestras de la carpeta; regresa cuántas encontró. */
    int cargar(File carpeta) throws IOException {
        if (generador != null) {
            generador.cancel(true);
        }
        List<Muestra> muestras = buscar(carpeta.toPath());
        modelo.clear();
        miniaturas.clear();
        muestras.forEach(modelo::addElement);
        lblCarpeta.setText(carpeta.getName() + " (" + muestras.size() + ")");
        lblCarpeta.setToolTipText(carpeta.getAbsolutePath());
        generador = new Generador(muestras);
        generador.execute();
        return muestras.size();
    }

    /** Marca en la lista el archivo abierto (si es de la carpeta); con null o uno de fuera, quita la marca. */
    void seleccionar(File archivo) {
        for (int i = 0; i < modelo.size(); i++) {
            if (modelo.get(i).archivo().equals(archivo)) {
                if (lista.getSelectedIndex() != i) {
                    lista.setSelectedIndex(i);
                }
                lista.ensureIndexIsVisible(i);
                return;
            }
        }
        lista.clearSelection();
    }

    void seleccionarPrimera() {
        if (!modelo.isEmpty()) {
            lista.setSelectedIndex(0);
        }
    }

    /** PDF e imágenes de la carpeta y sus subcarpetas, ordenados por ruta relativa. */
    static List<Muestra> buscar(Path carpeta) throws IOException {
        try (Stream<Path> rutas = Files.walk(carpeta, PROFUNDIDAD)) {
            return rutas.filter(Files::isRegularFile)
                    .filter(p -> esMuestra(p.getFileName().toString()))
                    .map(p -> new Muestra(p.toFile(), carpeta.relativize(p).toString().replace(File.separatorChar, '/')))
                    .sorted(Comparator.comparing(Muestra::nombre, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    static boolean esMuestra(String nombre) {
        String n = nombre.toLowerCase();
        return n.endsWith(".pdf") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /** Primera página (o la imagen) reducida para caber en la miniatura. */
    private static BufferedImage miniatura(File archivo) throws IOException {
        BufferedImage imagen;
        if (archivo.getName().toLowerCase().endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(archivo)) {
                imagen = new PDFRenderer(doc).renderImageWithDPI(0, DPI_MINIATURA, ImageType.RGB);
            }
        } else {
            imagen = ImageIO.read(archivo);
            if (imagen == null) {
                throw new IOException("Formato de imagen no soportado");
            }
        }
        double escala = Math.min((double) ANCHO_MINIATURA / imagen.getWidth(), (double) ALTO_MINIATURA / imagen.getHeight());
        int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(imagen.getHeight() * escala));
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(imagen, 0, 0, ancho, alto, null);
        g.dispose();
        return destino;
    }

    private class Generador extends SwingWorker<Void, Muestra> {
        private final List<Muestra> muestras;
        private final Map<File, ImageIcon> listas = new HashMap<>();

        Generador(List<Muestra> muestras) {
            this.muestras = muestras;
        }

        @Override
        protected Void doInBackground() {
            for (Muestra m : muestras) {
                if (isCancelled()) {
                    break;
                }
                ImageIcon icono = null; // null = no se pudo generar
                try {
                    icono = new ImageIcon(miniatura(m.archivo()));
                } catch (IOException | RuntimeException e) {
                    LOG.log(Level.FINE, "Sin miniatura para " + m.archivo(), e);
                }
                synchronized (listas) {
                    listas.put(m.archivo(), icono);
                }
                publish(m);
            }
            return null;
        }

        @Override
        protected void process(List<Muestra> hechas) {
            if (generador != this) {
                return; // se cargó otra carpeta
            }
            synchronized (listas) {
                hechas.forEach(m -> miniaturas.put(m.archivo(), listas.get(m.archivo())));
            }
            lista.repaint();
        }
    }

    private class Celda extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            Muestra m = (Muestra) value;
            // el espacio tras "/" deja partir el renglón entre la carpeta del cliente y el archivo
            String nombre = m.nombre().replace("&", "&amp;").replace("<", "&lt;").replace("/", "/ ");
            String aviso = !miniaturas.containsKey(m.archivo()) ? "<i>cargando…</i><br>"
                    : miniaturas.get(m.archivo()) == null ? "<i>sin vista previa</i><br>" : "";
            super.getListCellRendererComponent(list, "<html><div style='text-align:center;width:"
                    + (ANCHO_MINIATURA + 30) + "px'>" + aviso + nombre + "</div></html>", index, isSelected, cellHasFocus);
            setIcon(miniaturas.get(m.archivo()));
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
            setToolTipText(m.archivo().getAbsolutePath());
            return this;
        }
    }
}
