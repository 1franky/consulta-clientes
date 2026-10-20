package com.coppel.validador.model;

import java.time.LocalDate;

/**
 * Una cuenta del cliente en alguna cartera, tal como la entrega el listado de operación.
 * Los importes van en pesos enteros, igual que en los listados.
 *
 * @param interesSobreCompra base para la bonificación (Int S/Compra; en préstamos InteresFinanciamiento)
 * @param interesAdicionalMes interés adicional cargado en el mes (NuevoInteresCargado): columna
 *                           "Interés adicional" del estado de cuenta
 * @param saldaCon           "Salda Con" del listado (referencia; el estado de cuenta se recalcula)
 * @param fechaSinSaldo      solo ropa (fec_sinsaldo): si se quedó sin saldo hace 30 días o menos se
 *                           bonifica el interés completo
 * @param bonificacionMes    solo tiempo aire: bonificación fija por mes
 */
public record Cuenta(
        TipoCartera tipo,
        boolean cuentaPerdida,
        long numeroTienda,
        long numeroFactura,
        String articulo,
        int numeroArticulos,
        LocalDate fechaCompra,
        int plazo,
        long interesSobreCompra,
        long abonoMensual,
        long abonosMes,
        long comprasMes,
        long saldoFecha,
        long interesAdicionalMesAnterior,
        long interesAdicional,
        long interesAdicionalMes,
        long vencido,
        long saldaCon,
        long saldaCon20SiguienteMes,
        long bonificacionMes,
        LocalDate fechaUltimoMovimiento,
        LocalDate fechaSinSaldo,
        boolean convenioActivo,
        boolean convenioDevolucion,
        boolean domiciliada) {
}
