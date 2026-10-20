package com.coppel.validador.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Lee config.properties desde FUERA del ejecutable. Orden de búsqueda:
 *  1. Ruta indicada con -Dconfig=C:\ruta\config.properties
 *  2. Carpeta donde está ValidadorEdoCta.exe
 *  3. Carpeta de trabajo actual (útil al ejecutar el jar en desarrollo)
 *
 * Las sentencias de los listados (sp.*) vienen en procedimientos.properties, dentro del jar;
 * config.properties puede redefinir cualquiera con la misma clave.
 */
public final class Config {

    private static final Logger LOG = Logger.getLogger(Config.class.getName());

    static final String NOMBRE_ARCHIVO = "config.properties";
    private static final String PROCEDIMIENTOS = "/procedimientos.properties";

    private final Properties props;
    private final Path origen;

    private Config(Properties props, Path origen) {
        this.props = props;
        this.origen = origen;
    }

    public static Config cargar() throws IOException {
        List<Path> candidatos = candidatos();
        for (Path ruta : candidatos) {
            if (Files.isRegularFile(ruta)) {
                Properties p = new Properties(procedimientos());
                try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
                LOG.info("Configuración cargada desde " + ruta.toAbsolutePath());
                return new Config(p, ruta);
            }
        }
        String rutas = candidatos.stream().map(Path::toString).collect(Collectors.joining("\n  "));
        throw new IOException("No se encontró " + NOMBRE_ARCHIVO + ". Se buscó en:\n  " + rutas);
    }

    /** Sentencias por omisión incluidas en el programa. */
    private static Properties procedimientos() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Config.class.getResourceAsStream(PROCEDIMIENTOS)) {
            if (in == null) {
                throw new IOException("Falta " + PROCEDIMIENTOS + " dentro del programa");
            }
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static List<Path> candidatos() {
        List<Path> lista = new ArrayList<>();
        String explicita = System.getProperty("config");
        if (explicita != null && !explicita.isBlank()) {
            lista.add(Paths.get(explicita));
        }
        lista.add(carpetaAplicacion().resolve(NOMBRE_ARCHIVO));
        return lista;
    }

    /** Carpeta del .exe (jpackage define jpackage.app-path) o, en desarrollo, la carpeta de trabajo. */
    public static Path carpetaAplicacion() {
        String exe = System.getProperty("jpackage.app-path");
        if (exe != null) {
            return Paths.get(exe).toAbsolutePath().getParent();
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    /** true = datos de ejemplo sin base de datos (modo=demo). */
    public boolean modoDemo() {
        return "demo".equalsIgnoreCase(props.getProperty("modo", "sqlserver").trim());
    }

    public ConexionDb conexion() {
        return new ConexionDb(
                props.getProperty("sqlserver.nombre", "Carteras").trim(),
                requerido("sqlserver.url"),
                requerido("sqlserver.usuario"),
                props.getProperty("sqlserver.password", ""));
    }

    /** Sentencia configurada para un listado (sp.&lt;clave&gt;), o null si está vacía o no existe. */
    public String procedimiento(String clave) {
        String valor = props.getProperty("sp." + clave);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Nombres de columna forzados: columna.&lt;listado&gt;.&lt;campo&gt;=NombreReal. */
    public Map<String, String> columnas() {
        Map<String, String> mapa = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String clave : props.stringPropertyNames()) {
            if (clave.startsWith("columna.") && !props.getProperty(clave).isBlank()) {
                mapa.put(clave.substring("columna.".length()), props.getProperty(clave).trim());
            }
        }
        return mapa;
    }

    /**
     * Catálogo de bonificaciones externo (bonificaciones.archivo, por omisión bonificaciones.csv junto a
     * este archivo). Si no existe se usa la copia incluida en el programa.
     */
    public Path bonificaciones() {
        String valor = props.getProperty("bonificaciones.archivo", "bonificaciones.csv").trim();
        Path ruta = Paths.get(valor);
        return ruta.isAbsolute() ? ruta : origen.toAbsolutePath().getParent().resolve(ruta);
    }

    /**
     * Logo opcional, ubicado en la misma carpeta que config.properties.
     * Devuelve null si no existe para que la ventana se muestre sin logo.
     */
    public Path logo() {
        String nombre = props.getProperty("logo.archivo", "logo.png").trim();
        Path ruta = origen.toAbsolutePath().getParent().resolve(nombre);
        return Files.isRegularFile(ruta) ? ruta : null;
    }

    private String requerido(String clave) {
        String valor = props.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la clave '" + clave + "' en " + origen);
        }
        return valor.trim();
    }

    public record ConexionDb(String nombre, String url, String usuario, String password) {
    }
}
