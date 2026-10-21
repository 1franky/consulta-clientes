package com.coppel.validador.validacion;

import com.coppel.validador.model.Cliente;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Formatos tal como se imprimen en el estado de cuenta. */
public final class Formato {

    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private static final ThreadLocal<DecimalFormat> IMPORTE = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)));

    private Formato() {
    }

    /** 1,234 ; vacío cuando es 0, igual que en las columnas del estado de cuenta. */
    public static String importe(long valor) {
        return valor == 0 ? "" : IMPORTE.get().format(valor);
    }

    /** 1,234 ; muestra 0. */
    public static String importeConCero(long valor) {
        return IMPORTE.get().format(valor);
    }

    /** 03-Ene-26 */
    public static String fechaCorta(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return String.format("%02d-%s-%02d", fecha.getDayOfMonth(), MESES[fecha.getMonthValue() - 1].substring(0, 3),
                fecha.getYear() % 100);
    }

    /** 20-Septiembre-2026 */
    public static String fechaLarga(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return String.format("%02d-%s-%d", fecha.getDayOfMonth(), MESES[fecha.getMonthValue() - 1], fecha.getYear());
    }

    /** Puntualidad, puntualidad activa, estado civil, tipo de casa, sexo, ingreso (2) y antigüedad (2): AACPF0322 */
    public static String cadenaCobranza(Cliente c) {
        String ingreso = c.ingresoMensual().matches("\\d+")
                ? String.format("%02d", Integer.parseInt(c.ingresoMensual())) : c.ingresoMensual();
        return c.puntualidad() + c.puntualidadActiva() + c.estadoCivil() + c.tipoCasa() + c.sexo() + ingreso
                + anio(c.antiguedad());
    }

    /** Ancho del renglón del domicilio en el estado de cuenta. */
    static final int ANCHO_DOMICILIO = 50;
    /** Lo que sigue después del corte se imprime hasta este largo; si no cabe se trunca con "*". */
    static final int ANCHO_CONTINUACION = 45;

    private static final Pattern TELEFONO = Pattern.compile("(^|\\s)\\d{10}(?=\\s|$)");

    /**
     * Domicilio como se imprime: calle # casa depto rumbo entre-calles complemento, y colonia + municipio.
     * Cada uno se parte en dos renglones si pasa de {@value #ANCHO_DOMICILIO} caracteres (ver {@link #partir}).
     * Las partes vacías conservan su espacio, igual que en el impreso ("# 110   loc").
     */
    public static List<String> domicilio(Cliente c) {
        String linea = (c.calle().trim() + " # " + c.numeroCasa().trim() + " " + c.departamento().trim() + " "
                + c.rumbo().trim() + " " + c.entreCalles().trim() + " " + sinTelefono(c.complemento().trim())).stripTrailing();
        List<String> renglones = new ArrayList<>(partir(linea));
        renglones.addAll(partir((c.colonia().trim() + " " + c.municipio().trim()).strip()));
        return renglones;
    }

    /**
     * Igual que el impresor (ImpresionXCentro, locationn): si pasa de 50 caracteres se corta en el último espacio
     * dentro de los primeros 50; el resto va en el renglón siguiente, hasta 45 caracteres y con "*" si no cabe.
     */
    static List<String> partir(String linea) {
        if (linea.length() <= ANCHO_DOMICILIO) {
            return List.of(linea);
        }
        int corte = linea.substring(0, ANCHO_DOMICILIO).lastIndexOf(' ');
        if (corte <= 0) {
            corte = ANCHO_DOMICILIO;
        }
        String resto = linea.substring(corte).strip();
        if (resto.length() > ANCHO_CONTINUACION) {
            resto = resto.substring(0, ANCHO_CONTINUACION) + "*";
        }
        return List.of(linea.substring(0, corte).stripTrailing(), resto);
    }

    /** El complemento no imprime números de teléfono (10 dígitos) que se hayan capturado ahí. */
    static String sinTelefono(String texto) {
        return TELEFONO.matcher(texto).replaceAll("").strip();
    }

    /** 500001 → 5000-01 */
    public static String centro(String centro) {
        String s = centro.trim();
        return s.length() > 2 && s.matches("\\d+") ? s.substring(0, s.length() - 2) + "-" + s.substring(s.length() - 2) : s;
    }

    /** "100 - 200" → 100-0200 */
    public static String zona(String zona) {
        String[] partes = zona.split("-");
        if (partes.length == 2 && partes[1].trim().matches("\\d+")) {
            return partes[0].trim() + "-" + String.format("%04d", Integer.parseInt(partes[1].trim()));
        }
        return zona.trim();
    }

    /** Últimos dos dígitos del año: 2022 → 22 */
    public static String anio(LocalDate fecha) {
        return fecha == null ? "" : String.format("%02d", fecha.getYear() % 100);
    }
}
