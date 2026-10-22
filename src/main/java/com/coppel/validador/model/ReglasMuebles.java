package com.coppel.validador.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Configuración que usa proc_calculabonificacionmuebles para escoger la tabla de bonificación de cada venta de
 * muebles y ajustar su fecha de compra.
 *
 * @param diasAjuste              cat_configuracion_muebles_beneficios: días que se recorre la fecha de compra por
 *                                tipo de producto (0 = tabla de beneficios, más de 0 = tabla de motos)
 * @param plazosVentaFutura       cat_plazosmuebles con clv_flagventafuturo = 1
 * @param tiposNegocioAfiliado    catnegocios con TipoCta = 2: tipos de producto de negocios afiliados
 * @param inicioBonificacionPropia catTipoProducto con clv_bonifMuebles = 1: desde esa fecha el tipo deja de usar la
 *                                tabla de negocios afiliados
 * @param arranqueNegocioAfiliado FECHA_BONIFICACION_NA_MP: ventas desde esta fecha usan cat_bonificacionescoppelpay
 */
public record ReglasMuebles(
        Map<Integer, Integer> diasAjuste,
        Set<Integer> plazosVentaFutura,
        Set<Integer> tiposNegocioAfiliado,
        Map<Integer, LocalDate> inicioBonificacionPropia,
        LocalDate arranqueNegocioAfiliado) {

    /** Tipo de producto de las motos. */
    public static final int TIPO_MOTO = 55;

    /** Sin base de datos: todas las ventas usan el cálculo general de muebles. */
    public static final ReglasMuebles NINGUNA = new ReglasMuebles(Map.of(), Set.of(), Set.of(), Map.of(), null);

    /** clv_NegAfi de proc_calculabonificacionmuebles, ya con la fecha de arranque. */
    public boolean negocioAfiliado(int tipoProducto, LocalDate fechaVenta) {
        if (arranqueNegocioAfiliado == null || fechaVenta == null || fechaVenta.isBefore(arranqueNegocioAfiliado)
                || !tiposNegocioAfiliado.contains(tipoProducto)) {
            return false;
        }
        LocalDate inicio = inicioBonificacionPropia.get(tipoProducto);
        return inicio == null || fechaVenta.isBefore(inicio);
    }
}
