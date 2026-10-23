package com.coppel.validador.ui;

import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.TablaListado;
import com.coppel.validador.service.DemoEstadoCuentaService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HojasListadosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 22);

    @Test
    void mueblesComoElImpreso() {
        TablaListado muebles = new TablaListado("Muebles",
                List.of("NumeroCliente", "Digito", "NombreCompleto", "NumeroFactura", "DescripcionArticulo", "Fecha",
                        "FechaVenta", "ImporteVenta", "Vencido", "InteresAdicional", "prc_bonificacion05SigMes"),
                List.of(List.of(10000002, 1, "MARIA GOMEZ RUIZ", 200001, "TELCEL", ts(2026, 9, 20), ts(2025, 2, 8),
                        2999, 2496, 164, new BigDecimal("3.9408"))),
                null);
        String html = hoja(expediente(muebles), "Muebles");
        assertTrue(html.contains("Listado de Clientes de la Cartera de Muebles al"));
        assertTrue(html.contains("22-Sep-2026"), "fecha de impresión");
        assertTrue(html.contains("20-Sep-2026"), "actualizados al");
        assertTrue(html.contains("08-Feb-2025"));
        assertTrue(html.contains("2,999"));
        assertTrue(html.contains("2,660"), "el vencido se imprime con el interés adicional");
        assertTrue(html.contains("4%"));
    }

    @Test
    void creditoConPorcentajeYLetraDeLaCobranza() {
        List<String> columnas = new ArrayList<>(List.of("NumeroCliente", "Fecha", "CobranzaTeoricaMuebles",
                "CobranzaRealMuebles", "AcumCobTeoricaMuebSem2", "AcumCobRealMuebSem2", "EficienciaCteMuebles",
                "EficienciaCtePrestamos", "VencidoTotalMuebles", "IntAdicionalDebeMuebles"));
        List<Object> valores = new ArrayList<>(List.of(10000002, ts(2026, 9, 20), 1074, 0, 1620, 936, 10, -1,
                3230, 293));
        String html = hoja(expediente(new TablaListado("Crédito", columnas, List.of(valores), null)), "Crédito");
        assertTrue(html.contains("JUL-DIC 2026") && html.contains("JUL-DIC 2024"));
        assertTrue(html.contains("58%"), "936 de 1,620");
        assertTrue(html.contains(">C<"), "58% es C con los umbrales por omisión");
        assertTrue(html.contains("3,523"), "vencido del semestre actual con interés");
        assertTrue(html.contains("(1)"), "eficiencia -1 de préstamos entre paréntesis");
    }

    @Test
    void semestresYLetras() {
        assertArrayEquals(new String[]{"JUL-DIC 2026", "ENE-JUN 2026", "JUL-DIC 2025", "ENE-JUN 2025", "JUL-DIC 2024"},
                HojasListados.semestres(LocalDate.of(2026, 9, 20)));
        int[] umbrales = {85, 60, 30};
        assertEquals("A", HojasListados.letra(100, umbrales));
        assertEquals("B", HojasListados.letra(61, umbrales));
        assertEquals("C", HojasListados.letra(54, umbrales));
        assertEquals("D", HojasListados.letra(10, umbrales));
    }

    @Test
    void sinListadosNoFalla() {
        ExpedienteCliente demo = new DemoEstadoCuentaService().consultar(DemoEstadoCuentaService.CLIENTE_CON_ATRASO,
                LocalDate.of(2026, 9, 20));
        List<HojasListados.Hoja> hojas = HojasListados.armar(demo, HOY, HojasListados.Colores.CLAROS);
        assertEquals(7, hojas.size());
        assertTrue(hojas.get(0).html().contains("no devolvió"));
    }

    private static String hoja(ExpedienteCliente exp, String titulo) {
        return HojasListados.armar(exp, HOY, HojasListados.Colores.CLAROS).stream()
                .filter(h -> h.titulo().equals(titulo)).findFirst().orElseThrow().html();
    }

    private static ExpedienteCliente expediente(TablaListado... listados) {
        ExpedienteCliente demo = new DemoEstadoCuentaService().consultar(DemoEstadoCuentaService.CLIENTE_CON_ATRASO,
                LocalDate.of(2026, 9, 20));
        return new ExpedienteCliente(demo.fechaCorte(), demo.cliente(), demo.cuentas(), demo.dineroElectronico(),
                demo.situacionesEspeciales(), demo.marcasContacto(), List.of(listados), List.of());
    }

    private static Timestamp ts(int anio, int mes, int dia) {
        return Timestamp.valueOf(LocalDate.of(anio, mes, dia).atStartOfDay());
    }
}
