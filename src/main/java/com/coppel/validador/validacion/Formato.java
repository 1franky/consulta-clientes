package com.coppel.validador.validacion;

import com.coppel.validador.model.Cliente;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;

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

    /**
     * Primer renglón del domicilio como se imprime: calle # casa, departamento, rumbo, entre calles
     * y complemento. Ej.: "HIDALGO # 1 0 entre juarez y morelos CASA VERDE"
     */
    public static String domicilio(Cliente c) {
        StringBuilder sb = new StringBuilder(c.calle().trim());
        if (!c.numeroCasa().isBlank()) {
            sb.append(" # ").append(c.numeroCasa().trim());
        }
        for (String parte : new String[]{c.departamento(), c.rumbo(), c.entreCalles(), c.complemento()}) {
            if (parte != null && !parte.isBlank()) {
                sb.append(' ').append(parte.trim());
            }
        }
        return sb.toString();
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
