package com.coppel.validador.service;

import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.validacion.PuntoRevision;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * Guarda el resultado de cada validación en un CSV mensual (validaciones-AAAA-MM.csv) que se
 * abre en Excel: una fila por punto revisado, con quién, cuándo, el valor esperado y el dictamen.
 */
public final class BitacoraValidacionService {

    private static final Logger LOG = Logger.getLogger(BitacoraValidacionService.class.getName());

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ENCABEZADO = "Fecha,Usuario,Cliente,Nombre,Corte,Muestra,Dictamen,"
            + "Clave,Seccion,Punto,Esperado,Resultado,Observacion,ObservacionGeneral";

    private final Path carpeta;

    public BitacoraValidacionService(Path carpeta) {
        this.carpeta = carpeta;
    }

    /** Agrega la validación al CSV del mes y devuelve la ruta del archivo. */
    public Path guardar(ExpedienteCliente exp, List<PuntoRevision> puntos, String dictamen, String observacionGeneral,
                        String muestra) throws IOException {
        LocalDateTime ahora = LocalDateTime.now();
        Files.createDirectories(carpeta);
        Path archivo = carpeta.resolve("validaciones-" + MES.format(ahora) + ".csv");
        boolean nuevo = !Files.exists(archivo);

        try (Writer w = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (nuevo) {
                w.write('﻿'); // BOM para que Excel respete los acentos
                w.write(ENCABEZADO);
                w.write("\r\n");
            }
            String comun = String.join(",",
                    csv(FECHA_HORA.format(ahora)),
                    csv(System.getProperty("user.name")),
                    csv(String.valueOf(exp.cliente().numeroCliente())),
                    csv(exp.cliente().nombre()),
                    csv(exp.fechaCorte().toString()),
                    csv(muestra),
                    csv(dictamen));
            for (PuntoRevision p : puntos) {
                w.write(String.join(",", comun, csv(p.clave()), csv(p.seccion()), csv(p.descripcion()),
                        csv(p.esperado()), csv(p.resultado().toString()), csv(p.observacion()), csv(observacionGeneral)));
                w.write("\r\n");
            }
        }
        LOG.info("Validación del cliente " + exp.cliente().numeroCliente() + " guardada en " + archivo
                + " (" + dictamen + ", " + puntos.size() + " puntos)");
        return archivo;
    }

    private static String csv(String valor) {
        String v = valor == null ? "" : valor.replace("\r", " ").replace("\n", " ");
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
