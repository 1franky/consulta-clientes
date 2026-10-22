package com.coppel.validador.validacion;

import com.coppel.validador.validacion.FuncionesEmision.AbonoMinimo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuncionesEmisionTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 9, 20);

    @Test
    void elCorteDel20DeDiciembreNoCuentaComoDia20() {
        assertTrue(FuncionesEmision.corteDia20(CORTE));
        assertTrue(FuncionesEmision.corteDia20(LocalDate.of(2026, 12, 24)));
        assertFalse(FuncionesEmision.corteDia20(LocalDate.of(2026, 12, 20)));
        assertFalse(FuncionesEmision.corteDia20(LocalDate.of(2026, 10, 18)));
    }

    @Test
    void compraDelMesEsDel21AnteriorAl20DelCorte() {
        assertTrue(FuncionesEmision.compraDelMes(LocalDate.of(2026, 8, 21), CORTE));
        assertTrue(FuncionesEmision.compraDelMes(LocalDate.of(2026, 9, 20), CORTE));
        assertFalse(FuncionesEmision.compraDelMes(LocalDate.of(2026, 8, 20), CORTE));
        assertFalse(FuncionesEmision.compraDelMes(LocalDate.of(2026, 9, 21), CORTE));
        assertEquals(1573, FuncionesEmision.comprasDelMes(LocalDate.of(2026, 8, 31), CORTE, 1573));
        assertEquals(0, FuncionesEmision.comprasDelMes(LocalDate.of(2026, 7, 28), CORTE, 1999));
    }

    @Test
    void paseAPagarMueblesConTopes() {
        // vencido + abono, sin pasar de lo que debe
        assertEquals(new AbonoMinimo(110, 898), FuncionesEmision.paseAPagarMuebles(788, 110, 1008));
        assertEquals(new AbonoMinimo(374, 1561), FuncionesEmision.paseAPagarMuebles(1300, 374, 1561));
        // si el vencido es todo lo que debe, el pase es el vencido
        assertEquals(new AbonoMinimo(522, 4985), FuncionesEmision.paseAPagarMuebles(4985, 522, 4985));
    }

    @Test
    void minimoDeCuarentaPesos() {
        // debe $40 o menos: se pide todo
        assertEquals(new AbonoMinimo(35, 35), FuncionesEmision.saldo40(35, new AbonoMinimo(20, 20)));
        assertEquals(new AbonoMinimo(40, 40), FuncionesEmision.saldo40(40, new AbonoMinimo(20, 20)));
        // debe más de $40 pero el mínimo no pasa de $40: abono y mínimo de $40
        assertEquals(new AbonoMinimo(40, 40), FuncionesEmision.saldo40(300, new AbonoMinimo(25, 25)));
        // mínimo mayor a $40: sin cambio
        assertEquals(new AbonoMinimo(43, 527), FuncionesEmision.saldo40(527, new AbonoMinimo(43, 527)));
    }

    @Test
    void ropaComoLaMuestra() {
        // Saldo 1,440, abono base 161, mínimo 1,110, interés 37 (19 del mes), vencido 949
        AbonoMinimo minimo = FuncionesEmision.minimoAbonoRopa(1110, 0, 161, 1440, CORTE, 12);
        long debe = 1440 + 37;
        long atrasado = FuncionesEmision.vencidoEmision(949, 37);
        assertEquals(161, FuncionesEmision.saldo40(debe, minimo).abonoBase());
        assertEquals(986, atrasado);
        assertEquals(1147, FuncionesEmision.paseAPagarRopa(minimo.minimo(), 37, debe, atrasado));
        assertEquals(1458, FuncionesEmision.ustedDebiaRopa(1440, 37, 0, 0, 19, CORTE));
    }

    @Test
    void ropaSinMinimoNiAbonoTomaSaldoEntrePlazo() {
        assertEquals(new AbonoMinimo(120, 120), FuncionesEmision.minimoAbonoRopa(0, 0, 0, 1440, CORTE, 12));
    }

    @Test
    void usteDebiaDePrestamoDelMesEsCero() {
        assertEquals(0, FuncionesEmision.ustedDebiaPrestamos(5000, 0, 0, 0, LocalDate.of(2026, 9, 1), CORTE));
        assertEquals(1215, FuncionesEmision.ustedDebiaPrestamos(1215, 20, 0, 20, LocalDate.of(2025, 9, 23), CORTE));
    }

    @Test
    void fechaDeUltimoAbonoSoloSiElMovimientoEsAbono() {
        LocalDate fecha = LocalDate.of(2026, 8, 31);
        // Abono de ropa el mismo día de la compra: se imprime (como la muestra)
        assertEquals(fecha, FuncionesEmision.fechaUltimoAbono(fecha, "5", Set.of("5"), fecha, 1573));
        // Deuda Bancoppel con movimiento E: no es abono (como la muestra)
        assertNull(FuncionesEmision.fechaUltimoAbono(LocalDate.of(2024, 4, 24), "E", Set.of("1"),
                LocalDate.of(2024, 4, 24), 0));
        // Muebles con movimiento 3 (como la muestra)
        assertNull(FuncionesEmision.fechaUltimoAbono(fecha, "3", Set.of("1"), LocalDate.of(2025, 4, 16), 0));
        // Sin tipo (modo demo): se borra si es la compra del mes
        assertNull(FuncionesEmision.fechaUltimoAbono(fecha, "", Set.of("5"), fecha, 527));
    }
}
