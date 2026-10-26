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
 * @param interesPrimerMes   InteresAdicionalPrimerMes: R1/M1/E1 en el área de intereses [20]
 * @param tipoProducto       TipoProducto de muebles: decide la tabla de bonificación (55 = moto)
 * @param importeVenta       importe y enganche de la venta, último movimiento y convenio: con ellos la emisión
 *                           decide la leyenda "recoger" / "DEFINIR CON PAGOS" (fnPc2GeDeterminaLeyRecogerDefinirPagos)
 * @param minimoAPagar       ropa y tiempo aire (MinimoaPagarMesAct): base del pase a pagar; null si el listado no lo trae
 * @param enganchesMes       ropa y tiempo aire: la emisión los suma a "Menos abonos" junto con la bonificación otorgada
 * @param saldoMesAnterior   cuentas perdidas: saldo en diezmilésimos de salario mínimo (SaldoMesAnterior; en ropa
 *                           Saldo20MesAnterior)
 * @param salarioCuentasPerdidas cuentas perdidas: SalMinZonaC; pesos = saldoMesAnterior × salario / 10000
 * @param importeUltimoMovimiento ImporteUltimoMov: con datos posteriores al corte reemplaza a AbonoDelMes, que el
 *                           cierre del día 20 deja en 0
 * @param tipoConvenio       TipoDeConvenio y SubtipoDeConvenio: con fecha, importe y plazo arman la clave del convenio
 *                           vigente que se imprime junto a las facturas
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
        boolean domiciliada,
        long interesPrimerMes,
        long importeVenta,
        long enganche,
        String tipoUltimoMovimiento,
        LocalDate fechaConvenio,
        long importeConvenio,
        int plazoConvenio,
        int tipoProducto,
        Long minimoAPagar,
        long enganchesMes,
        long bonificacionOtorgada,
        long saldoMesAnterior,
        long salarioCuentasPerdidas,
        long importeUltimoMovimiento,
        String tipoConvenio,
        String subtipoConvenio) {

    /** Sin el importe del último movimiento ni el tipo de convenio. */
    public Cuenta(TipoCartera tipo, boolean cuentaPerdida, long numeroTienda, long numeroFactura, String articulo,
                  int numeroArticulos, LocalDate fechaCompra, int plazo, long interesSobreCompra, long abonoMensual,
                  long abonosMes, long comprasMes, long saldoFecha, long interesAdicionalMesAnterior,
                  long interesAdicional, long interesAdicionalMes, long vencido, long saldaCon,
                  long saldaCon20SiguienteMes, long bonificacionMes, LocalDate fechaUltimoMovimiento,
                  LocalDate fechaSinSaldo, boolean convenioActivo, boolean convenioDevolucion, boolean domiciliada,
                  long interesPrimerMes, long importeVenta, long enganche, String tipoUltimoMovimiento,
                  LocalDate fechaConvenio, long importeConvenio, int plazoConvenio, int tipoProducto, Long minimoAPagar,
                  long enganchesMes, long bonificacionOtorgada, long saldoMesAnterior, long salarioCuentasPerdidas) {
        this(tipo, cuentaPerdida, numeroTienda, numeroFactura, articulo, numeroArticulos, fechaCompra, plazo,
                interesSobreCompra, abonoMensual, abonosMes, comprasMes, saldoFecha, interesAdicionalMesAnterior,
                interesAdicional, interesAdicionalMes, vencido, saldaCon, saldaCon20SiguienteMes, bonificacionMes,
                fechaUltimoMovimiento, fechaSinSaldo, convenioActivo, convenioDevolucion, domiciliada,
                interesPrimerMes, importeVenta, enganche, tipoUltimoMovimiento, fechaConvenio, importeConvenio,
                plazoConvenio, tipoProducto, minimoAPagar, enganchesMes, bonificacionOtorgada, saldoMesAnterior,
                salarioCuentasPerdidas, 0, "", "");
    }

    /** Sin los datos de venta y convenio (modo demo y pruebas). */
    public Cuenta(TipoCartera tipo, boolean cuentaPerdida, long numeroTienda, long numeroFactura, String articulo,
                  int numeroArticulos, LocalDate fechaCompra, int plazo, long interesSobreCompra, long abonoMensual,
                  long abonosMes, long comprasMes, long saldoFecha, long interesAdicionalMesAnterior,
                  long interesAdicional, long interesAdicionalMes, long vencido, long saldaCon,
                  long saldaCon20SiguienteMes, long bonificacionMes, LocalDate fechaUltimoMovimiento,
                  LocalDate fechaSinSaldo, boolean convenioActivo, boolean convenioDevolucion, boolean domiciliada) {
        this(tipo, cuentaPerdida, numeroTienda, numeroFactura, articulo, numeroArticulos, fechaCompra, plazo,
                interesSobreCompra, abonoMensual, abonosMes, comprasMes, saldoFecha, interesAdicionalMesAnterior,
                interesAdicional, interesAdicionalMes, vencido, saldaCon, saldaCon20SiguienteMes, bonificacionMes,
                fechaUltimoMovimiento, fechaSinSaldo, convenioActivo, convenioDevolucion, domiciliada,
                0, 0, 0, "", null, 0, 0, 0, null, 0, 0, 0, 0);
    }
}
