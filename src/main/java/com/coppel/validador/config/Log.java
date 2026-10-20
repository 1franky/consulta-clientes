package com.coppel.validador.config;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Configura java.util.logging para escribir en archivos .log. Ubicación:
 *  1. Carpeta logs\ junto a ValidadorEdoCta.exe
 *  2. Carpeta logs\ en la carpeta de trabajo actual (desarrollo)
 *  3. Si no se puede escribir ahí: %USERPROFILE%\.validador-edocta\logs
 *
 * Se generan dos archivos, que rotan al llegar a 5 MB y se guardan hasta 5 de cada uno
 * (el -0 es el más reciente):
 *  - validador-0.log: todos los eventos
 *  - errores-0.log: solo advertencias y errores, para revisarlos rápido
 */
public final class Log {

    private static final String NOMBRE_ARCHIVO = "validador-%g.log";
    private static final String NOMBRE_ERRORES = "errores-%g.log";
    private static final int TAMANO_MAXIMO = 5 * 1024 * 1024;
    private static final int CANTIDAD_ARCHIVOS = 5;

    private static Path carpeta;

    private Log() {
    }

    public static void iniciar() {
        Logger raiz = Logger.getLogger("");
        for (Path candidata : new Path[]{Config.carpetaAplicacion().resolve("logs"),
                Paths.get(System.getProperty("user.home"), ".validador-edocta", "logs")}) {
            try {
                Files.createDirectories(candidata);
                FileHandler archivo = archivo(candidata.resolve(NOMBRE_ARCHIVO), Level.ALL);
                FileHandler errores;
                try {
                    errores = archivo(candidata.resolve(NOMBRE_ERRORES), Level.WARNING);
                } catch (IOException | SecurityException e) {
                    archivo.close();
                    throw e;
                }
                for (Handler h : raiz.getHandlers()) {
                    h.setFormatter(new Formato());
                }
                raiz.addHandler(archivo);
                raiz.addHandler(errores);
                carpeta = candidata;
                break;
            } catch (IOException | SecurityException e) {
                // Se intenta con la siguiente carpeta
                System.err.println("No se pudo crear el log en " + candidata + ": " + e);
            }
        }
        if (carpeta == null) {
            System.err.println("No se pudo crear ningún archivo de log; solo se registrará en consola.");
        }

        Thread.setDefaultUncaughtExceptionHandler((hilo, e) ->
                Logger.getLogger(Log.class.getName())
                        .log(Level.SEVERE, "Error no controlado en el hilo " + hilo.getName(), e));
    }

    private static FileHandler archivo(Path patron, Level nivel) throws IOException {
        FileHandler handler = new FileHandler(patron.toString(), TAMANO_MAXIMO, CANTIDAD_ARCHIVOS, true);
        handler.setEncoding("UTF-8");
        handler.setLevel(nivel);
        handler.setFormatter(new Formato());
        return handler;
    }

    /** Carpeta donde se guardan los logs, o null si no se pudo crear ninguna. */
    public static Path carpeta() {
        return carpeta;
    }

    /** Una línea por evento: 2026-10-19 14:03:21.512 INFO    [ConsultaService] mensaje */
    private static final class Formato extends Formatter {

        private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public String format(LogRecord r) {
            String clase = r.getLoggerName() == null ? "" : r.getLoggerName();
            clase = clase.substring(clase.lastIndexOf('.') + 1);

            StringBuilder sb = new StringBuilder()
                    .append(FECHA.format(LocalDateTime.ofInstant(r.getInstant(), ZoneId.systemDefault())))
                    .append(' ')
                    .append(String.format("%-7s", r.getLevel().getName()))
                    .append(" [").append(clase).append("] ")
                    .append(formatMessage(r))
                    .append(System.lineSeparator());
            if (r.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                r.getThrown().printStackTrace(pw);
                // SQL Server puede encadenar varios errores en una misma SQLException
                if (r.getThrown() instanceof SQLException sql) {
                    for (SQLException sig = sql.getNextException(); sig != null; sig = sig.getNextException()) {
                        pw.print("Siguiente error SQL: ");
                        sig.printStackTrace(pw);
                    }
                }
                pw.flush();
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
