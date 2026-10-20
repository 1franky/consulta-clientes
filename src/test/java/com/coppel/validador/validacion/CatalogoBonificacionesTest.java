package com.coppel.validador.validacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoBonificacionesTest {

    @Test
    void prestamosUsanFactorNuevoDesdeJunio2024() throws IOException {
        CatalogoBonificaciones c = CatalogoBonificaciones.cargar();
        // cat_bonificacionesPrestamos: 350 días, plazo 12 → prc_bonificacion 0.0014, prc_bonificacionNueva 0.0015
        assertEquals(0.0014, c.factor("PRESTAMOS", 12, 350, LocalDate.of(2024, 6, 19)).getAsDouble(), 1e-9);
        assertEquals(0.0015, c.factor("PRESTAMOS", 12, 350, LocalDate.of(2024, 6, 20)).getAsDouble(), 1e-9);
    }

    @Test
    void pasadoElPlazoNoHayBonificacionYPlazoDesconocidoNoTieneFactor() throws IOException {
        CatalogoBonificaciones c = CatalogoBonificaciones.cargar();
        assertEquals(0, c.factor("ROPA", 12, 400, LocalDate.of(2025, 1, 1)).getAsDouble());
        assertTrue(c.factor("ROPA", 99, 10, LocalDate.of(2025, 1, 1)).isEmpty());
    }

    @Test
    void archivoExternoReemplazaAlIncluidoYRespetaVigencias(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("bonificaciones.csv");
        Files.writeString(csv, """
                # catálogo de prueba
                cartera,plazo,dias_transcurridos,factor,vigente_desde
                MUEBLES,12,0,0.50,1901-01-01
                MUEBLES,12,1,0.40,1901-01-01
                MUEBLES,12,0,0.90,2027-01-01
                MUEBLES,12,1,0.80,2027-01-01
                """);
        CatalogoBonificaciones c = CatalogoBonificaciones.cargar(csv);

        assertEquals(csv.toAbsolutePath().toString(), c.origen());
        assertEquals(0.40, c.factor("MUEBLES", 12, 1, LocalDate.of(2026, 12, 31)).getAsDouble(), 1e-9);
        assertEquals(0.80, c.factor("MUEBLES", 12, 1, LocalDate.of(2027, 1, 1)).getAsDouble(), 1e-9);
        assertTrue(c.factor("ROPA", 12, 1, LocalDate.of(2026, 1, 1)).isEmpty(), "solo trae lo del archivo externo");
    }

    @Test
    void sinArchivoExternoUsaElIncluido(@TempDir Path dir) throws IOException {
        CatalogoBonificaciones c = CatalogoBonificaciones.cargar(dir.resolve("no-existe.csv"));
        assertEquals("incluido en el programa", c.origen());
        assertEquals(0.0587, c.factor("MUEBLES", 12, 290, LocalDate.of(2026, 1, 3)).getAsDouble(), 1e-9);
    }
}
