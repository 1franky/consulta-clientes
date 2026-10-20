package com.coppel.validador.validacion;

import com.coppel.validador.model.Cliente;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.ExpedienteCliente.SituacionEspecial;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Bonificacion;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Renglon;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Totales;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Arma la lista de puntos a revisar en la muestra, siguiendo el "Chequeo" del manual. */
public final class GeneradorPuntosRevision {

    private static final String DATOS = "Datos del cliente";
    private static final String DETALLE = "Sus compras al detalle";
    private static final String ENCABEZADO = "Pase a pagar";
    private static final String PIE = "Pie del estado de cuenta";
    private static final String DINERO = "Dinero electrónico";

    private static final String CENTRO_ATENCION = "Dudas o comentarios llama al Centro de Atención al Cliente:800 220 7735";

    private GeneradorPuntosRevision() {
    }

    /**
     * @param leyendaMercadotecnia leyenda capturada del 16 al 20 del mes [17]; vacía si no se configuró
     */
    public static List<PuntoRevision> generar(ExpedienteCliente exp, EstadoCuentaEsperado esp,
                                              String leyendaMercadotecnia) {
        List<PuntoRevision> p = new ArrayList<>();
        Cliente c = exp.cliente();

        // ---------- [3] Datos generales ----------
        p.add(punto("[3]", DATOS, "Nombre del cliente", c.nombre(), "Directorio: nombre"));
        p.add(punto("[3]", DATOS, "Domicilio (calle, casa, depto., rumbo, entre calles, complemento)",
                Formato.domicilio(c), "Directorio: calle " + c.calle() + ", casa " + c.numeroCasa() + ", depto. "
                        + c.departamento() + ", rumbo " + c.rumbo() + ", entre calles " + c.entreCalles()
                        + ", complemento " + c.complemento()));
        p.add(punto("[3]", DATOS, "Colonia y municipio", unir(" ", c.colonia(), c.municipio()),
                "Directorio: colonia y municipio (ciudad del directorio: " + c.ciudad() + ")"));
        p.add(punto("[3]", DATOS, "Teléfono", c.telefono().isBlank() || c.telefono().equals("0") ? "(sin teléfono)"
                : c.telefono(), "Directorio: teléfono"));
        p.add(punto("[3]", DATOS, "Número de cliente + dígito", c.numeroCliente() + c.digito(),
                "Directorio: cliente y dígito"));
        p.add(punto("[3]", DATOS, "Cadena de cobranza", Formato.cadenaCobranza(c),
                "Puntualidad " + c.puntualidad() + ", puntualidad activa " + c.puntualidadActiva()
                        + ", estado civil " + c.estadoCivil() + ", tipo de casa " + c.tipoCasa() + ", sexo " + c.sexo()
                        + ", ingreso " + c.ingresoMensual() + ", antigüedad " + Formato.anio(c.antiguedad())
                        + " (el manual también menciona nacimiento " + Formato.anio(c.fechaNacimiento()) + ")"));
        p.add(punto("[3]", DATOS, "Jefe de grupo", c.jefeGrupo(), "Directorio: jefe grupo"));
        p.add(punto("[3]", DATOS, "Supervisor", c.supervisor(), "Directorio: supervisor"));
        p.add(punto("[3]", DATOS, "Centro", Formato.centro(c.centro()), "Directorio: centro " + c.centro()));
        p.add(punto("[3]", DATOS, "Zona", Formato.zona(c.zona()), "Directorio: zona " + c.zona()));
        p.add(punto("[3]", DATOS, "Situación especial y causa", c.situacionEspecial() + c.causaSituacion(),
                "Directorio: situación especial " + c.situacionEspecial() + ", causa " + c.causaSituacion()
                        + situaciones(exp.situacionesEspeciales())));
        if (c.domicilioCertificado()) {
            p.add(punto("[3]", DATOS, "Clave de domicilio certificado", "K", "Directorio: domicilio certificado"));
        }
        if (!c.referencia().nombre().isBlank() && !c.referencia().nombre().equals("0")) {
            Cliente.Referencia r = c.referencia();
            p.add(punto("[4]", DATOS, "Datos de la referencia",
                    unir(", ", r.nombre(), unir(" # ", r.calle(), r.numeroCasa()), r.zona(), r.ciudad()),
                    "Directorio: sección referencia"));
        }
        p.add(visual("[25]", DATOS, "Código QR a un lado de los datos del cliente", "Debe mostrarse",
                "Escanear con GcDigital > Entrega de correo (puntos [26]-[34])"));

        // ---------- Encabezado ----------
        p.add(punto("[16]", ENCABEZADO, "Asteriscos junto a \"Estado de Cuenta\"",
                esp.asteriscos().isEmpty() ? "(ninguno)" : esp.asteriscos(), esp.explicacionAsteriscos()));
        p.add(punto("[13]", ENCABEZADO, "Pase a pagar", "$ " + Formato.importeConCero(esp.totales().paseAPagar()),
                "Fila TOTALES $, columna PASE A PAGAR"));
        p.add(punto("[15]", ENCABEZADO, "Favor de pagar antes de", esp.favorPagarAntesDe(),
                "INMEDIATO si puntualidad B, C, D, Z con vencido ≥ abono total, o A con vencido ≥ 2 abonos"));
        p.add(punto("[2]", ENCABEZADO, "Solo incluye abonos hasta", esp.soloIncluyeAbonosHasta(), "Fecha de corte"));

        // ---------- Dinero electrónico ----------
        p.add(punto("DE", DINERO, "Saldo actual de dinero electrónico",
                esp.saldoDineroElectronico() == 0 ? "(vacío)" : "$ " + Formato.importeConCero(esp.saldoDineroElectronico()),
                "Listado de dinero electrónico: saldo a la fecha"));
        p.add(punto("[24]", DINERO, "Monedas de abono puntual",
                esp.monedasAbonoPuntual() == 0 ? "Ninguna moneda marcada" : esp.monedasAbonoPuntual() + " moneda(s) marcada(s)",
                "Listado de dinero electrónico: \"Corte N\" de abonos puntuales"));

        // ---------- Detalle por cuenta ----------
        List<Renglon> visibles = esp.renglonesVisibles();
        for (int i = 0; i < visibles.size(); i++) {
            Renglon r = visibles.get(i);
            String cuenta = "Renglón " + (i + 1) + " " + r.articulo();
            p.add(punto("[5]", DETALLE, cuenta + ": fecha de compra", r.fechaCompra(),
                    "Fecha de venta (muebles) o fecha última compra (ropa)"));
            p.add(punto("[6]", DETALLE, cuenta + ": usted debía", Formato.importe(r.ustedDebia()),
                    "Saldo + int. adic. mes ant. + abonos del mes − compras del mes"));
            if (r.menosAbonos() != 0 || r.masCompras() != 0) {
                p.add(punto("[6]", DETALLE, cuenta + ": menos abonos / más sus compras",
                        Formato.importeConCero(r.menosAbonos()) + " / " + Formato.importeConCero(r.masCompras()),
                        "Abonos del mes / compras del mes"));
            }
            p.add(punto("[20]", DETALLE, cuenta + ": interés adicional", Formato.importe(r.interesAdicional()),
                    "Interés adicional − int. adic. mes anterior"));
            p.add(punto("[6]", DETALLE, cuenta + ": usted debe", Formato.importe(r.ustedDebe()),
                    "Saldo a la fecha " + Formato.importeConCero(r.cuenta().saldoFecha()) + " + interés adicional "
                            + Formato.importeConCero(r.cuenta().interesAdicional())));
            p.add(punto("[7]", DETALLE, cuenta + ": páguelo con solo", Formato.importe(r.pagueloConSolo()),
                    reglaBonificacion(r)));
            p.add(punto("[8]", DETALLE, cuenta + ": abono mensual", Formato.importe(r.abonoMensual()),
                    "Abono base / abono mensual del listado"));
            p.add(punto("[9]", DETALLE, cuenta + ": atrasado", Formato.importe(r.atrasado()),
                    "Vencido " + Formato.importeConCero(r.cuenta().vencido())
                            + (r.cuenta().vencido() > 0 ? " + interés adicional (tope usted debe)" : "")));
            p.add(punto("[10]", DETALLE, cuenta + ": pase a pagar", Formato.importe(r.paseAPagar()),
                    "Abono mensual + atrasado (tope usted debe)"));
            p.add(punto("[11]", DETALLE, cuenta + ": fecha de último abono", r.fechaUltimoAbono(),
                    r.cuenta().cuentaPerdida() ? "Cuenta perdida: se pinta CP"
                            : "Fec ult movto " + Formato.fechaCorta(r.cuenta().fechaUltimoMovimiento())
                            + " (vacía si el último movimiento fue la compra)"));
            if (r.cuenta().domiciliada()) {
                p.add(punto("[21]", DETALLE, cuenta + ": marca de cuenta domiciliada", "*", "Cuenta domiciliada"));
            }
        }

        Totales t = esp.totales();
        p.add(punto("[12]", DETALLE, "TOTALES $",
                Stream.of(t.ustedDebia(), t.menosAbonos(), t.masCompras(), t.interesAdicional(), t.ustedDebe(),
                                t.pagueloConSolo(), t.abonoMensual(), t.atrasado(), t.paseAPagar())
                        .map(v -> v == 0 ? "–" : Formato.importeConCero(v))
                        .collect(Collectors.joining("  ")),
                "Debía, abonos, compras, interés, debe, páguelo, abono, atrasado, pase (suma de TODAS las cuentas)"));
        p.add(punto("[21]", DETALLE, "Marca *_Domiciliada en totales", c.domiciliado() ? "*_Domiciliada" : "(no debe aparecer)",
                "Cliente domiciliado; cotejar con el listado de Domiciliación"));

        // ---------- Pie ----------
        p.add(punto("[19]", PIE, "Tienda + factura * artículos", esp.facturas(),
                "Todas las cuentas menos ropa"));
        p.add(punto("[20]", PIE, "Interés adicional por cartera", esp.interesesCartera(),
                "Suma de INTERÉS ADICIONAL de cada cartera (R ropa, M muebles, B Bancoppel, P préstamos)"));
        p.add(punto("[18]", PIE, "Citatorio", esp.citatorio().isEmpty() ? "(ninguno)" : esp.citatorio(),
                esp.explicacionCitatorio() + ". En el formato 2026 no se observa citatorio: confirmar si sigue vigente"));
        p.add(punto("Marca", PIE, "Marca de contacto en AVISO IMPORTANTE",
                exp.marcasContacto().isEmpty() ? "(ninguna)" : exp.marcasContacto().stream()
                        .map(m -> m.split(":")[0].trim()).collect(Collectors.joining(" ")),
                exp.marcasContacto().isEmpty() ? "Listado de marcas de contacto"
                        : String.join("; ", exp.marcasContacto())));
        p.add(leyendaMercadotecnia == null || leyendaMercadotecnia.isBlank()
                ? visual("[17]", PIE, "Leyenda de mercadotecnia", "La capturada del 16 al 20 del mes",
                "Calidad Sist Carteras\\Productos Calidad\\México\\Especiales\\Leyendas Mercadotecnia")
                : punto("[17]", PIE, "Leyenda de mercadotecnia", leyendaMercadotecnia,
                "Leyenda configurada en config.properties"));
        p.add(punto("[22]", PIE, "Leyenda del Centro de Atención", CENTRO_ATENCION, "Texto fijo"));
        p.add(visual("[14]", PIE, "Teléfono de tienda", "Siempre debe traer uno", ""));
        p.add(visual("[23]", PIE, "Número de página", "Debe aparecer", "No tiene contra qué verificarse"));
        return p;
    }

    private static PuntoRevision punto(String clave, String seccion, String descripcion, String esperado, String regla) {
        return new PuntoRevision(clave, seccion, descripcion, esperado, regla, false);
    }

    private static PuntoRevision visual(String clave, String seccion, String descripcion, String esperado,
                                        String regla) {
        return new PuntoRevision(clave, seccion, descripcion, esperado, regla, true);
    }

    private static String reglaBonificacion(Renglon r) {
        Bonificacion b = r.bonificacion();
        String base = "Usted debe " + Formato.importeConCero(r.ustedDebe()) + " − bonificación "
                + Formato.importeConCero(b.importe());
        if (!b.nota().isEmpty()) {
            return base + ". " + b.nota();
        }
        String calculo = String.format(Locale.US, "%s × %.4f (plazo %d, %d días) = %d", Formato.importeConCero(b.base()),
                b.factor(), b.plazo(), b.dias(), b.importe());
        String listado = "Salda Con del listado " + Formato.importeConCero(r.cuenta().saldaCon());
        if (r.cuenta().saldaCon20SiguienteMes() != 0) {
            listado += ", Salda Con 20 sig. mes " + Formato.importeConCero(r.cuenta().saldaCon20SiguienteMes());
        }
        return base + " (" + calculo + "). " + listado;
    }

    private static String situaciones(List<SituacionEspecial> lista) {
        if (lista.isEmpty()) {
            return "";
        }
        return ". Cartera informativa: " + lista.stream()
                .map(s -> s.situacion() + s.causa() + " " + s.descripcion())
                .collect(Collectors.joining("; "));
    }

    private static String unir(String separador, String... partes) {
        return Stream.of(partes).filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(separador));
    }
}
