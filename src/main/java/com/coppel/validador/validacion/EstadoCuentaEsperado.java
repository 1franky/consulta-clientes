package com.coppel.validador.validacion;

import com.coppel.validador.model.Cuenta;

import java.util.List;

/**
 * Cómo DEBERÍA verse el estado de cuenta según los listados y las reglas del manual
 * "Estado de Cuenta Correos". Es lo que el validador compara contra la muestra impresa.
 *
 * @param renglones          todas las cuentas que se cobran (el estado de cuenta pinta solo las primeras 5)
 * @param asteriscos         "*", "**", "***" o vacío [16]
 * @param favorPagarAntesDe  "INMEDIATO", la fecha [15], o vacío si no hay pase a pagar (se imprime "Fecha:")
 * @param facturas           tienda + factura * artículos [19]
 * @param interesesCartera   IR$..., IM$... [20]
 * @param citatorio          "CIT # n" o vacío [18]; el formato 2026 no lo imprime
 * @param domiciliada        "*_Domiciliada" bajo los totales [21]
 * @param marcaContacto      3MSC, NTE o NCE arriba de "AVISO IMPORTANTE", o vacío
 * @param avisoImportante    leyenda bajo "AVISO IMPORTANTE:"; vacía si no se pudo determinar
 * @param situacionEspecial  situación y causa que se imprimen junto al centro (I35), o vacío
 * @param datosConvenio      convenio vigente junto a las facturas: DDMM importe plazo subtipo ("0910 00335 2 2"), o vacío
 * @param alertas            situaciones que impiden o condicionan el estado de cuenta
 * @param notas              aclaraciones del cálculo que no impiden nada (abonos reconstruidos, etc.)
 */
public record EstadoCuentaEsperado(
        String soloIncluyeAbonosHasta,
        String favorPagarAntesDe,
        String explicacionFavorPagar,
        String asteriscos,
        String explicacionAsteriscos,
        List<Renglon> renglones,
        Totales totales,
        String facturas,
        String datosConvenio,
        String interesesCartera,
        String citatorio,
        String explicacionCitatorio,
        String marcaContacto,
        String avisoImportante,
        String explicacionAviso,
        String situacionEspecial,
        long saldoDineroElectronico,
        int monedasAbonoPuntual,
        boolean domiciliada,
        List<String> alertas,
        List<String> notas) {

    public static final int MAX_RENGLONES = 5;

    public List<Renglon> renglonesVisibles() {
        return renglones.subList(0, Math.min(MAX_RENGLONES, renglones.size()));
    }

    /** Un renglón de "Sus compras al detalle". */
    public record Renglon(
            Cuenta cuenta,
            String articulo,
            String fechaCompra,
            long ustedDebia,
            long menosAbonos,
            long masCompras,
            long interesAdicional,
            long ustedDebe,
            long pagueloConSolo,
            long abonoMensual,
            long atrasado,
            long paseAPagar,
            String fechaUltimoAbono,
            Bonificacion bonificacion) {
    }

    /**
     * Cálculo de la bonificación [7]: base × factor(días, plazo).
     *
     * @param dias  (fecha de corte + 1 mes) - fecha de compra
     * @param nota  explicación cuando no aplica o no se pudo calcular
     */
    public record Bonificacion(long base, long dias, int plazo, double factor, long importe, String nota) {
    }

    /** Fila "TOTALES $" [12]. */
    public record Totales(long ustedDebia, long menosAbonos, long masCompras, long interesAdicional, long ustedDebe,
                          long pagueloConSolo, long abonoMensual, long atrasado, long paseAPagar) {
    }
}
