package com.empresa.consulta;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Lee config.properties desde FUERA del ejecutable. Orden de búsqueda:
 *  1. Ruta indicada con -Dconfig=C:\ruta\config.properties
 *  2. Carpeta donde está ConsultaClientes.exe
 *  3. Carpeta de trabajo actual (útil al ejecutar el jar en desarrollo)
 */
public final class Config {

    static final String NOMBRE_ARCHIVO = "config.properties";

    /** Prefijos de las bases definidas en el archivo de configuración. */
    private static final String[] BASES = {"sqlserver"};

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
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
                return new Config(p, ruta);
            }
        }
        String rutas = candidatos.stream().map(Path::toString).collect(Collectors.joining("\n  "));
        throw new IOException("No se encontró " + NOMBRE_ARCHIVO + ". Se buscó en:\n  " + rutas);
    }

    private static List<Path> candidatos() {
        List<Path> lista = new ArrayList<>();
        String explicita = System.getProperty("config");
        if (explicita != null && !explicita.isBlank()) {
            lista.add(Paths.get(explicita));
        }
        // jpackage define esta propiedad con la ruta del .exe
        String exe = System.getProperty("jpackage.app-path");
        if (exe != null) {
            lista.add(Paths.get(exe).toAbsolutePath().getParent().resolve(NOMBRE_ARCHIVO));
        }
        lista.add(Paths.get(System.getProperty("user.dir")).resolve(NOMBRE_ARCHIVO));
        return lista;
    }

    public List<ConexionDb> conexiones() {
        List<ConexionDb> lista = new ArrayList<>();
        for (String prefijo : BASES) {
            lista.add(new ConexionDb(
                    props.getProperty(prefijo + ".nombre", prefijo).trim(),
                    requerido(prefijo + ".url"),
                    requerido(prefijo + ".usuario"),
                    props.getProperty(prefijo + ".password", ""),
                    requerido(prefijo + ".consulta")));
        }
        return lista;
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

    public record ConexionDb(String nombre, String url, String usuario, String password, String consulta) {
    }
}
