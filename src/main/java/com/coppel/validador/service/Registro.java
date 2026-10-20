package com.coppel.validador.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Una fila devuelta por un procedimiento, con búsqueda tolerante de columnas.
 *
 * Todavía no se conocen los nombres exactos de las columnas de cada procedimiento, así que cada
 * campo se busca por varios alias, comparando sin acentos, mayúsculas, espacios ni guiones bajos
 * (NUM_FACTURA, "Num Factura" y numFactura se consideran iguales). Si ningún alias coincide,
 * se fuerza el nombre real en config.properties:
 *   columna.muebles.saldoFecha=imp_SaldoFecha
 * Los campos que no se encuentran se reportan en "avisos" para ajustar el mapeo.
 */
final class Registro {

    private static final LocalDate FECHA_VACIA = LocalDate.of(1900, 1, 1);

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            formato("d-MMM-yyyy"), formato("d-MMM-yy"), formato("MM/dd/yyyy"), formato("yyyy-MM-dd"),
            formato("dd/MM/yyyy"), formato("yyyyMMdd"));

    private final String listado;
    private final Map<String, Object> valores;
    /** columna normalizada → nombre original */
    private final Map<String, String> indice = new LinkedHashMap<>();
    private final Map<String, String> forzadas;
    private final List<String> avisos;

    Registro(String listado, Map<String, Object> valores, Map<String, String> forzadas, List<String> avisos) {
        this.listado = listado;
        this.valores = valores;
        this.forzadas = forzadas;
        this.avisos = avisos;
        for (String columna : valores.keySet()) {
            indice.putIfAbsent(normalizar(columna), columna);
        }
    }

    Map<String, Object> valores() {
        return valores;
    }

    /** Valor del campo lógico buscando primero la columna forzada y luego los alias. */
    Object valor(String campo, String... alias) {
        String forzada = forzadas.get(listado + "." + campo);
        if (forzada != null) {
            String columna = indice.get(normalizar(forzada));
            if (columna == null) {
                avisar("La columna forzada '" + forzada + "' para " + listado + "." + campo + " no existe");
                return null;
            }
            return valores.get(columna);
        }
        for (String a : alias) {
            String columna = indice.get(normalizar(a));
            if (columna != null) {
                return valores.get(columna);
            }
        }
        avisar("No se encontró la columna para " + listado + "." + campo
                + " (definir columna." + listado + "." + campo + " en config.properties)");
        return null;
    }

    /** Igual que valor() pero sin avisar si no existe: para campos opcionales. */
    Object opcional(String campo, String... alias) {
        int antes = avisos.size();
        Object v = valor(campo, alias);
        while (avisos.size() > antes) {
            avisos.remove(avisos.size() - 1);
        }
        return v;
    }

    String texto(String campo, String... alias) {
        return aTexto(valor(campo, alias));
    }

    long entero(String campo, String... alias) {
        return aEntero(valor(campo, alias));
    }

    LocalDate fecha(String campo, String... alias) {
        return aFecha(valor(campo, alias));
    }

    private void avisar(String mensaje) {
        if (!avisos.contains(mensaje)) {
            avisos.add(mensaje);
        }
    }

    // ---------- Conversión de valores ----------

    static String aTexto(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return v.toString().trim();
    }

    /** Importe redondeado a pesos; acepta texto con comas o "$". */
    static long aEntero(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return Math.round(n.doubleValue());
        }
        String s = v.toString().replace(",", "").replace("$", "").trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean aBooleano(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = v.toString().trim().toUpperCase(Locale.ROOT);
        return s.equals("S") || s.equals("SI") || s.equals("1") || s.equals("TRUE") || s.equals("Y");
    }

    /** Fecha o null; 01-ene-1900 se trata como vacía, igual que en los listados. */
    static LocalDate aFecha(Object v) {
        LocalDate f = null;
        if (v instanceof java.sql.Date d) {
            f = d.toLocalDate();
        } else if (v instanceof java.sql.Timestamp t) {
            f = t.toLocalDateTime().toLocalDate();
        } else if (v instanceof LocalDate d) {
            f = d;
        } else if (v instanceof LocalDateTime d) {
            f = d.toLocalDate();
        } else if (v instanceof java.util.Date d) {
            f = new java.sql.Date(d.getTime()).toLocalDate();
        } else if (v != null) {
            String s = v.toString().trim();
            if (s.length() > 10 && s.charAt(4) == '-') {
                s = s.substring(0, 10);
            }
            for (DateTimeFormatter formato : FORMATOS_FECHA) {
                try {
                    f = LocalDate.parse(s, formato);
                    break;
                } catch (DateTimeParseException e) {
                    // se intenta con el siguiente formato
                }
            }
        }
        return f == null || !f.isAfter(FECHA_VACIA) ? null : f;
    }

    static String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static DateTimeFormatter formato(String patron) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(patron)
                .toFormatter(Locale.ENGLISH);
    }
}
