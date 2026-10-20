package com.coppel.validador.validacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Porcentajes de bonificación por cartera, plazo, días transcurridos y vigencia
 * (cat_bonificacionescarteras / cat_bonificacionesPrestamos), leídos de bonificaciones.csv.
 *
 * Se usa el archivo externo (junto a config.properties) si existe, para poder actualizar los
 * factores sin recompilar; si no, la copia incluida en el jar.
 */
public final class CatalogoBonificaciones {

    private static final Logger LOG = Logger.getLogger(CatalogoBonificaciones.class.getName());

    private static final String RECURSO = "/bonificaciones.csv";

    /** "MUEBLES|12" → vigente_desde → factores por día (índice = días transcurridos) */
    private final Map<String, NavigableMap<LocalDate, double[]>> factores = new HashMap<>();
    private final String origen;

    private CatalogoBonificaciones(String origen) {
        this.origen = origen;
    }

    /** Lee el archivo externo si existe; si es null o no existe, la copia incluida en el jar. */
    public static CatalogoBonificaciones cargar(Path externo) throws IOException {
        if (externo != null && Files.isRegularFile(externo)) {
            try (InputStream in = Files.newInputStream(externo)) {
                CatalogoBonificaciones c = leer(in, externo.toAbsolutePath().toString());
                LOG.info("Catálogo de bonificaciones cargado desde " + externo.toAbsolutePath());
                return c;
            }
        }
        try (InputStream in = CatalogoBonificaciones.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IOException("No se encontró el recurso " + RECURSO);
            }
            LOG.info("Catálogo de bonificaciones: copia incluida en el programa");
            return leer(in, "incluido en el programa");
        }
    }

    public static CatalogoBonificaciones cargar() throws IOException {
        return cargar(null);
    }

    /** De dónde se leyó el catálogo, para mostrarlo en pantalla. */
    public String origen() {
        return origen;
    }

    private static CatalogoBonificaciones leer(InputStream in, String origen) throws IOException {
        CatalogoBonificaciones catalogo = new CatalogoBonificaciones(origen);
        Map<String, Map<LocalDate, Map<Integer, Double>>> leidos = new HashMap<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String linea;
        int numero = 0;
        while ((linea = br.readLine()) != null) {
            numero++;
            linea = linea.replace("﻿", "").trim();
            if (linea.isEmpty() || linea.startsWith("#") || linea.toLowerCase().startsWith("cartera,")) {
                continue;
            }
            String[] c = linea.split(",");
            try {
                LocalDate desde = c.length > 4 && !c[4].isBlank() ? LocalDate.parse(c[4].trim()) : LocalDate.of(1901, 1, 1);
                leidos.computeIfAbsent(c[0].trim().toUpperCase() + "|" + Integer.parseInt(c[1].trim()), k -> new HashMap<>())
                        .computeIfAbsent(desde, k -> new HashMap<>())
                        .put(Integer.parseInt(c[2].trim()), Double.parseDouble(c[3].trim()));
            } catch (RuntimeException e) {
                throw new IOException("Renglón " + numero + " inválido en el catálogo de bonificaciones ("
                        + origen + "): " + linea, e);
            }
        }
        leidos.forEach((clave, bloques) -> {
            NavigableMap<LocalDate, double[]> porVigencia = new TreeMap<>();
            bloques.forEach((desde, porDia) -> {
                int max = porDia.keySet().stream().max(Integer::compare).orElse(0);
                double[] tabla = new double[max + 1];
                for (int d = 0; d <= max; d++) {
                    // Si falta un día se usa el anterior
                    tabla[d] = porDia.getOrDefault(d, d > 0 ? tabla[d - 1] : 0);
                }
                porVigencia.put(desde, tabla);
            });
            catalogo.factores.put(clave, porVigencia);
        });
        return catalogo;
    }

    /**
     * Factor de bonificación; vacío si la cartera o el plazo no están en el catálogo.
     * Se toma el bloque vigente más reciente con fecha &lt;= fecha de compra (fec_iniciobloque).
     * Pasado el último día del plazo la bonificación es 0.
     */
    public OptionalDouble factor(String cartera, int plazo, long dias, LocalDate fechaCompra) {
        NavigableMap<LocalDate, double[]> porVigencia = factores.get(cartera + "|" + plazo);
        if (porVigencia == null) {
            return OptionalDouble.empty();
        }
        Map.Entry<LocalDate, double[]> bloque = fechaCompra == null ? porVigencia.firstEntry()
                : porVigencia.floorEntry(fechaCompra);
        if (bloque == null) {
            return OptionalDouble.empty();
        }
        double[] tabla = bloque.getValue();
        if (dias < 0) {
            return OptionalDouble.of(tabla[0]);
        }
        return OptionalDouble.of(dias < tabla.length ? tabla[(int) dias] : 0);
    }
}
