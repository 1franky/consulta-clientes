package com.coppel.validador.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VentanaPrincipalTest {

    @Test
    void elCorteEsElUltimoDia20QueNoPasaDeLaFecha() {
        assertEquals(LocalDate.of(2026, 9, 20), VentanaPrincipal.corteSugerido(LocalDate.of(2026, 10, 18)));
        assertEquals(LocalDate.of(2026, 10, 20), VentanaPrincipal.corteSugerido(LocalDate.of(2026, 10, 20)));
        assertEquals(LocalDate.of(2026, 10, 20), VentanaPrincipal.corteSugerido(LocalDate.of(2026, 10, 21)));
        assertEquals(LocalDate.of(2025, 12, 20), VentanaPrincipal.corteSugerido(LocalDate.of(2026, 1, 5)));
    }

    @Test
    void elCorteSaleDeCatFechas() {
        assertEquals(LocalDate.of(2026, 9, 20), VentanaPrincipal.corte(Optional.of(LocalDate.of(2026, 10, 18))));
    }
}
