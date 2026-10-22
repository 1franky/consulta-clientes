package com.coppel.validador.validacion;

import com.coppel.validador.model.Cuenta;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.FactorBonificacion;
import com.coppel.validador.model.ReglasMuebles;
import com.coppel.validador.model.TipoCartera;
import com.coppel.validador.service.DemoEstadoCuentaService;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Renglon;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Totales;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compara el estado de cuenta calculado contra lo IMPRESO en dos muestras reales (corte al 20-Sep-2026).
 * Los datos del cliente del modo demo son ficticios; los importes y fechas de las cuentas son los reales.
 */
class CalculadoraEstadoCuentaTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 9, 20);

    private static CalculadoraEstadoCuenta calculadora;
    private final DemoEstadoCuentaService demo = new DemoEstadoCuentaService();

    @BeforeAll
    static void cargarCatalogo() throws IOException {
        calculadora = new CalculadoraEstadoCuenta(CatalogoBonificaciones.cargar());
    }

    @Test
    void clienteAlCorrienteConBonificaciones() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE);
        EstadoCuentaEsperado esp = calculadora.calcular(exp);

        List<Renglon> r = esp.renglones();
        assertEquals("ROPA", r.get(0).articulo(), "ropa va primero");
        // ROPA 13-Sep-26: más sus compras 527, usted debe 527, páguelo 406, abono 43, pase 43
        assertRenglon(r.get(0), "13-Sep-26", 0, 0, 527, 0, 527, 406, 43, 0, 43, "");
        // TV QLED 03-Ene-26: debía 834, abonos 170, debe 664, páguelo 628, abono 170, pase 170, 01-Sep-26
        assertRenglon(r.get(1), "03-Ene-26", 834, 170, 0, 0, 664, 628, 170, 0, 170, "01-Sep-26");

        assertTotales(esp.totales(), 834, 170, 527, 0, 1191, 1034, 213, 0, 213);
        assertEquals("20-Octubre-2026", esp.favorPagarAntesDe());
        assertEquals("20-Septiembre-2026", esp.soloIncluyeAbonosHasta());
        assertEquals("*", esp.asteriscos());
        assertEquals("1000100001*2", esp.facturas());
        assertEquals("", esp.interesesCartera());
        assertEquals(9, esp.saldoDineroElectronico());
        assertEquals(1, esp.monedasAbonoPuntual());
        assertTrue(esp.alertas().isEmpty(), esp.alertas().toString());
        assertEquals(CalculadoraEstadoCuenta.AVISO_PUNTUALIDAD, esp.avisoImportante());
        assertEquals("", esp.marcaContacto());
        assertEquals("", esp.situacionEspecial(), "I23 no se imprime");
    }

    @Test
    void bonificacionSegunCatalogo() {
        EstadoCuentaEsperado esp = calculadora.calcular(demo.consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE));
        EstadoCuentaEsperado.Bonificacion ropa = esp.renglones().get(0).bonificacion();
        EstadoCuentaEsperado.Bonificacion muebles = esp.renglones().get(1).bonificacion();
        // 13-Sep-26 → 20-Oct-26 = 37 días, factor 0.82: 148 × 0.82 = 121
        assertEquals(37, ropa.dias());
        assertEquals(0.82, ropa.factor(), 1e-9);
        assertEquals(121, ropa.importe());
        // 03-Ene-26 → 20-Oct-26 = 290 días, factor 0.0587: 609 × 0.0587 = 36 (ejemplo del Excel de Calidad)
        assertEquals(290, muebles.dias());
        assertEquals(0.0587, muebles.factor(), 1e-9);
        assertEquals(36, muebles.importe());
    }

    @Test
    void clienteConAtraso() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_CON_ATRASO, CORTE);
        EstadoCuentaEsperado esp = calculadora.calcular(exp);

        List<Renglon> r = esp.renglones();
        assertRenglon(r.get(0), "27-Feb-25", 1212, 0, 0, 23, 1235, 1235, 106, 1235, 1235, "29-Abr-25");
        assertRenglon(r.get(1), "08-Feb-25", 2610, 0, 0, 50, 2660, 2660, 252, 2660, 2660, "29-Abr-25");
        assertRenglon(r.get(2), "11-Nov-24", 848, 0, 0, 15, 863, 863, 106, 863, 863, "29-Abr-25");

        assertTotales(esp.totales(), 4670, 0, 0, 88, 4758, 4758, 464, 4758, 4758);
        assertEquals("INMEDIATO", esp.favorPagarAntesDe());
        assertEquals("", esp.asteriscos());
        assertEquals("2000200001*2 0600200002*2", esp.facturas());
        assertEquals("IR$73   IM$293", esp.interesesCartera());
        assertFalse(esp.citatorio().isEmpty());
        assertEquals("3MSC", esp.marcaContacto());
        assertEquals(CalculadoraEstadoCuenta.AVISO_DEFINIR_PAGOS, esp.avisoImportante());
    }

    @Test
    void ropaSinSaldoEnLosUltimos30DiasBonificaElInteresCompleto() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE);
        Cuenta ropa = exp.cuentas().get(1);
        // Igual que la ropa del cliente al corriente, pero se quedó sin saldo el 25-Sep-26 (25 días antes del 20-Oct-26)
        Cuenta sinSaldo = new Cuenta(ropa.tipo(), false, ropa.numeroTienda(), 0, "ROPA", 0, ropa.fechaCompra(),
                ropa.plazo(), 148, ropa.abonoMensual(), 0, 527, 527, 0, 0, 0, 0, 0, 0, 0,
                ropa.fechaUltimoMovimiento(), LocalDate.of(2026, 9, 25), false, false, false);
        ExpedienteCliente otro = new ExpedienteCliente(CORTE, exp.cliente(), List.of(sinSaldo),
                exp.dineroElectronico(), List.of(), List.of(), List.of(), List.of());

        Renglon r = calculadora.calcular(otro).renglones().get(0);
        assertEquals(148, r.bonificacion().importe());
        assertEquals(527 - 148, r.pagueloConSolo());
    }

    @Test
    void definirConPagosPorPlazoDe12MesesCon14MesesDesdeLaCompraAunqueNoHayaMarca() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_CON_ATRASO, CORTE);
        ExpedienteCliente sinMarca = new ExpedienteCliente(CORTE, exp.cliente(), exp.cuentas(),
                exp.dineroElectronico(), List.of(), List.of(), List.of(), List.of());
        EstadoCuentaEsperado esp = calculadora.calcular(sinMarca);
        // ROPA comprada el 27-Feb-25: 19 meses al 20-Sep-26, plazo 12
        assertEquals(CalculadoraEstadoCuenta.AVISO_DEFINIR_PAGOS, esp.avisoImportante());
        assertTrue(esp.explicacionAviso().startsWith("ROPA"), esp.explicacionAviso());
        assertEquals(19, CalculadoraEstadoCuenta.mesesTranscurridos(LocalDate.of(2025, 2, 27), CORTE));
    }

    @Test
    void laMarcaNoSeImprimeSinAtrasadoYElDineroElectronicoSinSaldoNoLlevaMonedas() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE);
        ExpedienteCliente conMarca = new ExpedienteCliente(CORTE, exp.cliente(), exp.cuentas(),
                new ExpedienteCliente.DineroElectronico(0, 3), List.of(), List.of("NCE: NO CUENTA CON CORREO"),
                List.of(), List.of());
        EstadoCuentaEsperado esp = calculadora.calcular(conMarca);
        assertEquals("", esp.marcaContacto());
        assertTrue(esp.explicacionAviso().contains("NCE"), esp.explicacionAviso());
        assertEquals(0, esp.monedasAbonoPuntual());
    }

    @Test
    void situacionesImpresasDeLaBase() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE);
        ExpedienteCliente otro = new ExpedienteCliente(CORTE, exp.cliente(), exp.cuentas(), exp.dineroElectronico(),
                List.of(), List.of(), List.of(), List.of(), List.of("I35", "S50AB"));
        assertEquals("I35,S50AB", calculadora.calcular(otro).situacionEspecial());
    }

    @Test
    void motosUsanSuPropiaTablaYLosProductosConAjusteRecorrenLaFechaDeCompra() {
        LocalDate desde = LocalDate.of(1901, 1, 1);
        CatalogoBonificaciones catalogo = CatalogoBonificaciones.de(List.of(
                new FactorBonificacion("MOTOS", 12, 338, 0.0073, desde),
                new FactorBonificacion("MOTOS", 12, 308, 0.0200, desde),
                new FactorBonificacion("MUEBLES", 12, 338, 0.0087, desde)), "prueba");
        ReglasMuebles reglas = new ReglasMuebles(Map.of(56, 30), Set.of(), Set.of(), Map.of(), null);
        CalculadoraEstadoCuenta conReglas = new CalculadoraEstadoCuenta(catalogo, reglas);
        LocalDate venta = LocalDate.of(2025, 11, 16); // 338 días al 20-Oct-26

        // Moto (tipo 55): 8,400 × 0.0073 = 61, no el 0.0087 de muebles
        assertEquals(61, bonificacion(conReglas, muebles(venta, 8400, 55)).importe());
        // Tipo 56: la fecha de compra se recorre 30 días → 308 días en la tabla de motos
        EstadoCuentaEsperado.Bonificacion ajustada = bonificacion(conReglas, muebles(venta, 1000, 56));
        assertEquals(308, ajustada.dias());
        assertEquals(20, ajustada.importe());
        // Tipo sin configuración: cálculo general de muebles
        assertEquals(3, bonificacion(conReglas, muebles(venta, 344, 0)).importe());
    }

    @Test
    void ordenDelImpresorPorCarteraYFechaYDescripcionA8Caracteres() {
        // Como la muestra: PRESTAMO 23-Sep-25, ESTUFA 16-Nov-25 (factura 14861), MOTOCICL 16-Nov-25 (14859), BAFLE
        Cuenta bafle = muebles(LocalDate.of(2025, 6, 14), 0, 0, "BAFLE 15\"", 7608);
        Cuenta moto = muebles(LocalDate.of(2025, 11, 16), 0, 55, "MOTOCICLET", 14859);
        Cuenta estufa = muebles(LocalDate.of(2025, 11, 16), 0, 0, "ESTUFA", 14861);
        Cuenta prestamo = new Cuenta(TipoCartera.PRESTAMOS, false, 797, 1093, "PRESTAMO", 0, LocalDate.of(2025, 9, 23),
                12, 0, 182, 0, 0, 1215, 0, 0, 0, 0, 0, 0, 0, null, null, false, false, false);
        List<Cuenta> orden = CalculadoraEstadoCuenta.ordenar(List.of(bafle, moto, estufa, prestamo));
        assertEquals(List.of(prestamo, estufa, moto, bafle), orden);
        assertEquals("MOTOCICL", CalculadoraEstadoCuenta.descripcion(moto));
    }

    @Test
    void diaDePagoYPlazoDelCatalogo() {
        assertEquals(LocalDate.of(2025, 12, 17), CalculadoraEstadoCuenta.diaDePago(false, LocalDate.of(2025, 11, 16)));
        assertEquals(LocalDate.of(2026, 1, 17), CalculadoraEstadoCuenta.diaDePago(true, LocalDate.of(2025, 11, 16)));
        assertEquals(12, CalculadoraEstadoCuenta.plazoCatalogo(15));
        assertEquals(18, CalculadoraEstadoCuenta.plazoCatalogo(19));
        assertEquals(36, CalculadoraEstadoCuenta.plazoCatalogo(48));
    }

    private static EstadoCuentaEsperado.Bonificacion bonificacion(CalculadoraEstadoCuenta calc, Cuenta cuenta) {
        ExpedienteCliente exp = new DemoEstadoCuentaService().consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE);
        ExpedienteCliente uno = new ExpedienteCliente(CORTE, exp.cliente(), List.of(cuenta), exp.dineroElectronico(),
                List.of(), List.of(), List.of(), List.of());
        return calc.calcular(uno).renglones().get(0).bonificacion();
    }

    /** Venta de muebles a 12 meses con saldo de 20,000 y el interés sobre compra indicado. */
    private static Cuenta muebles(LocalDate venta, long interesSobreCompra, int tipoProducto) {
        return muebles(venta, interesSobreCompra, tipoProducto, "ARTICULO", 1);
    }

    private static Cuenta muebles(LocalDate venta, long interesSobreCompra, int tipoProducto, String articulo,
                                  long factura) {
        return new Cuenta(TipoCartera.MUEBLES, false, 1, factura, articulo, 1, venta, 12, interesSobreCompra, 1000, 0, 0,
                20000, 0, 0, 0, 0, 0, 0, 0, venta, null, false, false, false,
                0, 0, 0, "", null, 0, 0, tipoProducto);
    }

    @Test
    void situacionEspecialExcluida() {
        ExpedienteCliente exp = demo.consultar(DemoEstadoCuentaService.CLIENTE_AL_CORRIENTE, CORTE);
        var cliente = exp.cliente();
        var excluido = new com.coppel.validador.model.Cliente(cliente.numeroCliente(), cliente.digito(),
                cliente.nombre(), cliente.calle(), cliente.numeroCasa(), cliente.departamento(), cliente.entreCalles(),
                cliente.rumbo(), cliente.complemento(),
                cliente.telefono(), cliente.colonia(), cliente.ciudad(), cliente.inicialCiudad(), cliente.municipio(), cliente.zona(),
                cliente.lugarTrabajo(), cliente.conyuge(), cliente.puntualidad(), cliente.puntualidadActiva(),
                cliente.estadoCivil(), cliente.tipoCasa(), cliente.sexo(), cliente.ingresoMensual(),
                cliente.antiguedad(), cliente.fechaNacimiento(), cliente.jefeGrupo(), cliente.supervisor(),
                cliente.centro(), cliente.numeroTienda(), "P", "71", false, false, false, cliente.referencia());
        ExpedienteCliente otro = new ExpedienteCliente(CORTE, excluido, exp.cuentas(), exp.dineroElectronico(),
                List.of(), List.of(), List.of(), List.of());
        assertTrue(calculadora.calcular(otro).alertas().stream().anyMatch(a -> a.contains("P71")));
    }

    private static void assertRenglon(Renglon r, String fecha, long debia, long abonos, long compras, long interes,
                                      long debe, long paguelo, long abono, long atrasado, long pase, String ultimo) {
        String c = r.articulo() + ": ";
        assertEquals(fecha, r.fechaCompra(), c + "fecha de compra");
        assertEquals(debia, r.ustedDebia(), c + "usted debía");
        assertEquals(abonos, r.menosAbonos(), c + "menos abonos");
        assertEquals(compras, r.masCompras(), c + "más sus compras");
        assertEquals(interes, r.interesAdicional(), c + "interés adicional");
        assertEquals(debe, r.ustedDebe(), c + "usted debe");
        assertEquals(paguelo, r.pagueloConSolo(), c + "páguelo con solo");
        assertEquals(abono, r.abonoMensual(), c + "abono mensual");
        assertEquals(atrasado, r.atrasado(), c + "atrasado");
        assertEquals(pase, r.paseAPagar(), c + "pase a pagar");
        assertEquals(ultimo, r.fechaUltimoAbono(), c + "fecha último abono");
    }

    private static void assertTotales(Totales t, long debia, long abonos, long compras, long interes, long debe,
                                      long paguelo, long abono, long atrasado, long pase) {
        assertEquals(debia, t.ustedDebia(), "total debía");
        assertEquals(abonos, t.menosAbonos(), "total abonos");
        assertEquals(compras, t.masCompras(), "total compras");
        assertEquals(interes, t.interesAdicional(), "total interés");
        assertEquals(debe, t.ustedDebe(), "total debe");
        assertEquals(paguelo, t.pagueloConSolo(), "total páguelo");
        assertEquals(abono, t.abonoMensual(), "total abono");
        assertEquals(atrasado, t.atrasado(), "total atrasado");
        assertEquals(pase, t.paseAPagar(), "total pase a pagar");
    }
}
