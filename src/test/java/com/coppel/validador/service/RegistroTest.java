package com.coppel.validador.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistroTest {

    @Test
    void buscaColumnasSinImportarFormato() {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("SALDO_A_LA_FECHA", new java.math.BigDecimal("664.00"));
        valores.put("Fec Ult Movto", "01-Sep-2026");
        List<String> avisos = new ArrayList<>();
        Registro r = new Registro("muebles", valores, Map.of(), avisos);

        assertEquals(664, r.entero("saldoFecha", "SaldoALaFecha"));
        assertEquals(LocalDate.of(2026, 9, 1), r.fecha("fechaUltimoMovimiento", "FecUltMovto"));
        assertTrue(avisos.isEmpty());
    }

    @Test
    void columnaForzadaYAvisoDeFaltante() {
        Map<String, Object> valores = Map.of("imp_Raro", 5);
        List<String> avisos = new ArrayList<>();
        Registro r = new Registro("ropa", valores, Map.of("ropa.abonoMensual", "imp_Raro"), avisos);

        assertEquals(5, r.entero("abonoMensual", "AbonoBase"));
        assertEquals(0, r.entero("vencido", "Vencido"));
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("columna.ropa.vencido"));
    }

    @Test
    void fechaVaciaDe1900() {
        assertNull(Registro.aFecha("01-Jan-1900"));
        assertNull(Registro.aFecha(java.sql.Date.valueOf("1900-01-01")));
        assertEquals(LocalDate.of(2026, 9, 13), Registro.aFecha("09/13/2026"));
    }
}
