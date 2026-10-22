package com.coppel.validador.validacion;

import java.time.LocalDate;
import java.util.Set;

/**
 * Funciones con las que la emisión de correos (spPc2GeCar*VdoCalEdoCta) arma los importes de cada renglón del
 * detalle. Cada método replica la función SQL del mismo nombre; no consulta la base.
 */
final class FuncionesEmision {

    /** Abono mínimo que pide la tienda (fnGeCalculaSaldo40). */
    static final long MINIMO_TIENDA = 40;
    /** Plazo de ropa cuando la cuenta no trae idu_Plazo (cat_parametrosprocesos 38). */
    static final int PLAZO_ROPA = 12;
    /** fnPcGeMinimoAbonoSdoTeoricoEmision divide el saldo de tiempo aire entre 5. */
    static final int PLAZO_TIEMPO_AIRE = 5;

    /** Abono base (abono mensual impreso) y mínimo a pagar. */
    record AbonoMinimo(long abonoBase, long minimo) {
    }

    private FuncionesEmision() {
    }

    /**
     * Las funciones de la emisión preguntan si el corte es el del día 20 (24 en diciembre). La condición SQL deja
     * fuera también el 20 de diciembre: {@code (DAY<>20 AND MONTH<>12) OR (DAY<>24 AND MONTH=12)}.
     */
    static boolean corteDia20(LocalDate corte) {
        int dia = corte.getDayOfMonth();
        int mes = corte.getMonthValue();
        return !((dia != 20 && mes != 12) || (dia != 24 && mes == 12));
    }

    /** fnPc2GeComprasDelMes: la compra cae del 21 del mes anterior al 20 del mes del corte. */
    static boolean compraDelMes(LocalDate venta, LocalDate corte) {
        if (venta == null) {
            return false;
        }
        if (venta.getDayOfMonth() >= 21 && corte.getDayOfMonth() >= 21
                && venta.getMonthValue() == corte.getMonthValue() && venta.getYear() == corte.getYear()) {
            return true;
        }
        LocalDate mesAnterior = corte.minusMonths(1);
        return (venta.getDayOfMonth() >= 21 && venta.getMonthValue() == mesAnterior.getMonthValue()
                && venta.getYear() == mesAnterior.getYear() && corte.getDayOfMonth() <= 20)
                || (venta.getDayOfMonth() <= 20 && corte.getDayOfMonth() <= 20
                && venta.getMonthValue() == corte.getMonthValue() && venta.getYear() == corte.getYear());
    }

    /** fnPc2GeDeterminaComprasDelMes: el importe si la compra es del mes, si no 0. */
    static long comprasDelMes(LocalDate venta, LocalDate corte, long importe) {
        return compraDelMes(venta, corte) ? importe : 0;
    }

    /** fnGeInteresAdicional: el interés adicional negativo se toma como 0. */
    static long interesAdicional(long interes) {
        return Math.max(0, interes);
    }

    /** fnGeVencidoEmision: el atrasado impreso es el vencido más el interés adicional. */
    static long vencidoEmision(long vencido, long interesAdicional) {
        return vencido + interesAdicional;
    }

    /** fnPc2GeUstedDebiaMuebles (también Deuda Bancoppel y reestructura). */
    static long ustedDebiaMuebles(long saldo, long interesAdicional, long compras, long abonos, long moratorio,
                                  LocalDate corte) {
        long debia = saldo + interesAdicional - compras + abonos;
        return corteDia20(corte) ? debia - moratorio : debia;
    }

    /** fnPc2GeUstedDebiaRopa (también tiempo aire); los abonos ya incluyen enganches y bonificación otorgada. */
    static long ustedDebiaRopa(long saldo20, long interesAdicional, long compras, long abonos, long moratorio,
                               LocalDate corte) {
        return corteDia20(corte) ? saldo20 + interesAdicional - compras + abonos - moratorio
                : saldo20 + interesAdicional;
    }

    /** fnPc2GeUstedDebiaPrestamos: 0 si el préstamo es del mes. */
    static long ustedDebiaPrestamos(long saldo, long interesAdicional, long abonos, long moratorio,
                                    LocalDate fechaPrestamo, LocalDate corte) {
        if (compraDelMes(fechaPrestamo, corte)) {
            return 0;
        }
        long debia = saldo + interesAdicional + abonos;
        return corteDia20(corte) ? debia - moratorio : debia;
    }

    /**
     * fnGePaseAPagarMuebles: vencido + abono, nunca menos que el abono ni más que usted debe; si el vencido es todo
     * lo que debe, el pase es el vencido. Devuelve el abono sin cambios y el pase como mínimo.
     */
    static AbonoMinimo paseAPagarMuebles(long vencido, long abono, long debe) {
        long pase = vencido + abono;
        if (pase < abono) {
            pase = abono;
        }
        if (pase > debe) {
            pase = debe;
        }
        if (vencido == debe) {
            pase = vencido;
        }
        return new AbonoMinimo(abono, pase);
    }

    /** fnGeCalculaSaldo40: si debe $40 o menos se le pide todo; si el mínimo no pasa de $40, abono y mínimo son $40. */
    static AbonoMinimo saldo40(long debe, AbonoMinimo am) {
        long abono = am.abonoBase();
        long minimo = am.minimo();
        if (debe > 0) {
            if (debe == MINIMO_TIENDA) {
                abono = MINIMO_TIENDA;
                minimo = MINIMO_TIENDA;
            } else if (debe < MINIMO_TIENDA) {
                abono = debe;
                minimo = debe;
            } else if (minimo <= MINIMO_TIENDA) {
                abono = MINIMO_TIENDA;
                minimo = MINIMO_TIENDA;
            }
        }
        return new AbonoMinimo(abono, minimo);
    }

    /** CAST(saldo AS FLOAT) / plazo + .5 asignado a INT: redondeo al entero más cercano. */
    private static long abonoCalculado(long saldo, int plazo) {
        return (long) ((double) saldo / plazo + 0.5);
    }

    /**
     * fun_minimoabonosaldoteoricoemision: mínimo a pagar y abono base de ropa. No se replica el recálculo del abono
     * base (saldo / plazo + 0.5 cuando es mayor o hubo compras): una muestra de ropa (compra de $527 a 12 meses)
     * imprime abono 43 y el recálculo daría 44, así que en producción el abono base es el del listado.
     */
    static AbonoMinimo minimoAbonoRopa(long minimo, long abonosDelMes, long abonoBase, long saldo, LocalDate corte,
                                       int plazo) {
        long calculado = abonoCalculado(saldo, plazo > 0 ? plazo : PLAZO_ROPA);
        if (!corteDia20(corte)) {
            minimo -= abonosDelMes;
            if (saldo == 0) {
                minimo = 0;
            }
            if (minimo > saldo) {
                minimo = saldo;
            }
        } else {
            long teorico = saldo > 0 ? saldo - minimo : 0;
            if (teorico == 0 && minimo == 0 && abonoBase == 0) {
                abonoBase = calculado;
            }
        }
        return topes(minimo, abonoBase, saldo, calculado);
    }

    /** fnPcGeMinimoAbonoSdoTeoricoEmision: mínimo a pagar y abono base de tiempo aire (saldo entre 5). */
    static AbonoMinimo minimoAbonoTiempoAire(long minimo, long abonosDelMes, long abonoBase, long saldo,
                                             LocalDate corte) {
        long calculado = abonoCalculado(saldo, PLAZO_TIEMPO_AIRE);
        if (!corteDia20(corte)) {
            minimo -= abonosDelMes;
            if (saldo == 0) {
                minimo = 0;
            }
            if (minimo > saldo) {
                minimo = saldo;
            }
        } else {
            if (minimo > saldo) {
                minimo = saldo;
            }
            long teorico = saldo > 0 ? saldo - minimo : 0;
            if (teorico == 0 && minimo == 0 && abonoBase == 0) {
                abonoBase = calculado;
            }
        }
        if (calculado >= abonoBase) {
            minimo += calculado - abonoBase;
            abonoBase = calculado;
        }
        return topes(minimo, abonoBase, saldo, calculado);
    }

    private static AbonoMinimo topes(long minimo, long abonoBase, long saldo, long calculado) {
        if (minimo < 0) {
            minimo = 0;
        }
        if (minimo < abonoBase) {
            minimo = abonoBase;
        }
        if (minimo > saldo) {
            minimo = saldo;
        }
        if (abonoBase == 0 && minimo == 0) {
            abonoBase = calculado;
            minimo = abonoBase;
        }
        return new AbonoMinimo(abonoBase, minimo);
    }

    /** fnGePaseAPagarRopa: el mínimo más el interés adicional; si el vencido es todo lo que debe, el vencido. */
    static long paseAPagarRopa(long minimo, long interesAdicional, long debe, long vencido) {
        return vencido == debe ? vencido : minimo + interesAdicional;
    }

    /**
     * fnPc2GeFechaUltimoMov: la fecha solo se imprime si el último movimiento es un abono de la cartera. No se aplica
     * fnPc2GeValidaFechaUltimoMov (borrar la fecha si es el día de la compra): una muestra imprime el abono de
     * ropa hecho el mismo día de su compra. Sin TipoUltimoMov (modo demo) se borra solo si hubo
     * compras en el mes y la fecha es la de la compra.
     */
    static LocalDate fechaUltimoAbono(LocalDate fechaUltimoMov, String tipoUltimoMov, Set<String> tiposAbono,
                                      LocalDate fechaVenta, long compras) {
        if (fechaUltimoMov == null) {
            return null;
        }
        String tipo = tipoUltimoMov == null ? "" : tipoUltimoMov.strip();
        if (tipo.isEmpty()) {
            return compras > 0 && fechaUltimoMov.equals(fechaVenta) ? null : fechaUltimoMov;
        }
        return tiposAbono.contains(tipo) ? fechaUltimoMov : null;
    }
}
