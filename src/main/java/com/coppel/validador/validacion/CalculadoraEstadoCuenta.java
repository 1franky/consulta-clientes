package com.coppel.validador.validacion;

import com.coppel.validador.model.Cliente;
import com.coppel.validador.model.Cuenta;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.ExpedienteCliente.SituacionEspecial;
import com.coppel.validador.model.TipoCartera;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Bonificacion;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Renglon;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Totales;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reglas del manual "Estado de Cuenta Correos" (los números entre corchetes son los del manual).
 * No consulta la base de datos ni pinta pantallas: recibe el expediente y devuelve el estado de
 * cuenta esperado, para que la regla se pueda probar sola.
 */
public final class CalculadoraEstadoCuenta {

    /** Situaciones especiales a las que no se les genera estado de cuenta. */
    private static final Set<String> SITUACIONES_EXCLUIDAS = Set.of("L", "M", "X", "F");
    private static final Set<String> CAUSAS_P_EXCLUIDAS = Set.of("23", "71", "72", "73", "74");

    private final CatalogoBonificaciones catalogo;

    static final String AVISO_LEGAL = "SU CUENTA ESTA A PUNTO DE PASAR AL DEPARTAMENTO LEGAL.URGE QUE RESPONDA. "
            + "DE NO HACERLO, PRONTO SERA BOLETINADO AL BURO DE CREDITO";
    static final String AVISO_DEFINIR_PAGOS = "DEFINIR CON PAGOS";
    static final String AVISO_INTERESES = "AL CLIENTE YA COMENZAMOS A CARGARLE INTERESES ADICIONALES";
    static final String AVISO_PUNTUALIDAD = "POR SU PUNTUALIDAD USTED PUEDE LLEVAR LO QUE GUSTE A CREDITO. "
            + "LO FELICITAMOS POR SER UN CLIENTE CUMPLIDO.";

    /** Sin consulta a la base (modo demo): las muestras solo imprimen I35; I23, I41 y S50 no. */
    private static final Set<String> SITUACIONES_IMPRESAS = Set.of("I35");

    /** El impresor tiene lugar para 4 monedas de abono puntual. */
    public static final int MAX_MONEDAS = 4;
    /** fnGeConvenioVigente: días que dura vigente un convenio. */
    private static final int DIAS_CONVENIO_VIGENTE = 45;

    public CalculadoraEstadoCuenta(CatalogoBonificaciones catalogo) {
        this.catalogo = catalogo;
    }

    /** De dónde se leyó el catálogo de bonificaciones. */
    public String origenCatalogo() {
        return catalogo.origen();
    }

    public EstadoCuentaEsperado calcular(ExpedienteCliente exp) {
        LocalDate corte = exp.fechaCorte();
        LocalDate siguienteCorte = corte.plusMonths(1);
        Cliente cliente = exp.cliente();
        List<String> alertas = new ArrayList<>();

        List<Renglon> renglones = new ArrayList<>();
        for (Cuenta c : ordenar(exp.cuentas())) {
            if (c.convenioDevolucion()) {
                alertas.add("Cuenta " + c.articulo() + " con convenio de devolución: no se considera en el estado de cuenta");
                continue;
            }
            Renglon r = renglon(c, siguienteCorte);
            if (r.ustedDebe() > 0) {
                renglones.add(r);
            }
        }

        Totales totales = totales(renglones);
        long vencidoTotal = exp.cuentas().stream().filter(c -> !c.convenioDevolucion()).mapToLong(Cuenta::vencido).sum();
        String puntualidad = cliente.puntualidad().toUpperCase();

        // [15] Favor de pagar antes de: el impresor compara el atrasado impreso contra el abono mensual
        long atrasado = totales.atrasado();
        long abono = totales.abonoMensual();
        String favorPagar;
        String explicacionFavor;
        if (totales.paseAPagar() == 0) {
            favorPagar = "";
            explicacionFavor = "Sin pase a pagar: solo se imprime \"Fecha:\" con el corte";
        } else if ((puntualidad.equals("A") && atrasado >= 2 * abono)
                || (Set.of("B", "C", "D", "Z", "N").contains(puntualidad) && atrasado >= abono)) {
            favorPagar = "INMEDIATO";
            explicacionFavor = "Puntualidad " + puntualidad + ", atrasado $" + Formato.importeConCero(atrasado)
                    + (puntualidad.equals("A") ? " ≥ 2 abonos" : " ≥ abono") + " ($" + Formato.importeConCero(abono) + ")";
        } else {
            favorPagar = Formato.fechaLarga(siguienteCorte);
            explicacionFavor = puntualidad.equals("A") && atrasado > abono && atrasado < 2 * abono
                    ? "Puntualidad A con atraso entre 1 y 2 abonos: el impresor deja este renglón en blanco en los "
                    + "estados de cuenta 1 y 3 de la hoja y pone la fecha en el 2 y el 4"
                    : "Día 20 del mes siguiente";
        }

        // [16] Asteriscos
        String asteriscos;
        String explicacion;
        LocalDate antiguedad = cliente.antiguedad();
        if (antiguedad != null && antiguedad.isAfter(corte.minusMonths(1)) && !antiguedad.isAfter(corte)) {
            asteriscos = "**";
            explicacion = "Cliente nuevo del mes (antigüedad " + Formato.fechaCorta(antiguedad) + ")";
        } else if (vencidoTotal == 0) {
            asteriscos = "*";
            explicacion = "Cliente al corriente (sin vencido)";
        } else if (puntualidad.equals("A") && totales.abonoMensual() > 0
                && vencidoTotal * 100 >= 50 * totales.abonoMensual()
                && vencidoTotal * 100 <= 75 * 2 * totales.abonoMensual()) {
            asteriscos = "***";
            explicacion = "Puntualidad A con atraso entre 50% del abono y 75% de dos abonos";
        } else {
            asteriscos = "";
            explicacion = "Con atraso (vencido $" + Formato.importeConCero(vencidoTotal) + "): sin asteriscos";
        }

        // [18] Citatorio
        String citatorio = "";
        String explicacionCit;
        boolean conConvenio = exp.cuentas().stream().anyMatch(Cuenta::convenioActivo);
        long abonosVencidos = totales.abonoMensual() > 0 ? vencidoTotal / totales.abonoMensual() : 0;
        if (conConvenio) {
            explicacionCit = "Cliente con convenio activo: no se le pinta citatorio";
        } else {
            if (abonosVencidos >= 7) {
                citatorio = "CIT # 4";
            } else if (abonosVencidos >= 5) {
                citatorio = "CIT # 3";
            } else if (abonosVencidos >= 3) {
                citatorio = "CIT # 2";
            } else if (abonosVencidos >= 1 && (puntualidad.equals("A") || puntualidad.equals("B"))) {
                citatorio = "CIT # 1";
            } else if (abonosVencidos >= 1 && (puntualidad.equals("C") || puntualidad.equals("D"))) {
                citatorio = "CIT # 7";
            }
            explicacionCit = abonosVencidos + " abono(s) vencido(s), puntualidad " + puntualidad;
        }

        // Marca de contacto: el impresor solo la pinta si el cliente debe y tiene atrasado
        String marca = exp.marcasContacto().isEmpty() ? "" : exp.marcasContacto().get(0).split(":")[0].trim();
        String marcaListado = marca;
        if (totales.ustedDebe() == 0 || atrasado == 0) {
            marca = "";
        }
        // AVISO IMPORTANTE: textos de las muestras de 2026; "DEFINIR CON PAGOS" con la regla de la emisión
        Renglon cuentaDefinir = cuentaDefinirConPagos(renglones, corte);
        String aviso;
        String explicacionAviso;
        if (puntualidad.equals("Z")) {
            marca = ""; // con puntualidad Z las muestras no imprimen la marca
            aviso = AVISO_LEGAL;
            explicacionAviso = "Puntualidad Z";
        } else if (cuentaDefinir != null || marca.equals("3MSC")) {
            // Las 4 muestras con esta leyenda traen 3MSC; la regla de la emisión explica 3 de ellas
            aviso = AVISO_DEFINIR_PAGOS;
            explicacionAviso = cuentaDefinir != null
                    ? cuentaDefinir.articulo() + " a plazo de 12 meses comprada el " + cuentaDefinir.fechaCompra()
                    + " (14 o más meses)" + (marca.equals("3MSC") ? " y marca 3MSC" : "")
                    : "Marca 3MSC";
        } else if (marca.equals("NCE")) {
            aviso = AVISO_INTERESES;
            explicacionAviso = "Marca NCE";
        } else if (puntualidad.equals("A")) {
            aviso = AVISO_PUNTUALIDAD;
            explicacionAviso = "Puntualidad A";
        } else {
            aviso = "";
            explicacionAviso = "Puntualidad " + puntualidad + (marca.isEmpty() ? " sin marca" : " con marca " + marca)
                    + ": no hay muestra con este caso, revisar la leyenda a mano";
        }
        if (!marcaListado.isEmpty() && marca.isEmpty() && !puntualidad.equals("Z")) {
            explicacionAviso += ". La marca " + marcaListado + " no se imprime porque el cliente no tiene atrasado";
        }

        String situacionImpresa;
        if (exp.situacionesImpresas() != null) {
            situacionImpresa = String.join(",", exp.situacionesImpresas());
        } else {
            String situacion = cliente.situacionEspecial().trim() + cliente.causaSituacion().trim();
            situacionImpresa = SITUACIONES_IMPRESAS.contains(situacion) ? situacion : "";
        }

        // [19] Tienda + factura * artículos (todas las cuentas menos ropa)
        String facturas = renglones.stream()
                .map(Renglon::cuenta)
                .filter(c -> c.tipo() != TipoCartera.ROPA)
                .map(c -> String.format("%04d%06d*%d", c.numeroTienda(), c.numeroFactura(), c.numeroArticulos()))
                .collect(Collectors.joining(" "));

        // [20] Interés adicional por cartera y del primer mes, en el orden del impresor
        Map<String, Long> intereses = new LinkedHashMap<>();
        for (String clave : List.of("IR", "IP", "IB", "IM", "IE", "IT", "R1", "M1", "E1")) {
            intereses.put(clave, 0L);
        }
        for (Renglon r : renglones) {
            Cuenta c = r.cuenta();
            intereses.merge("I" + c.tipo().letraInteres(), c.interesAdicional(), Long::sum);
            String primerMes = switch (c.tipo()) {
                case ROPA -> "R1";
                case MUEBLES -> "M1";
                case REESTRUCTURA -> "E1";
                default -> null;
            };
            if (primerMes != null) {
                intereses.merge(primerMes, c.interesPrimerMes(), Long::sum);
            }
        }
        String interesesCartera = intereses.entrySet().stream()
                .filter(e -> e.getValue() != 0)
                .map(e -> e.getKey() + "$" + Formato.importeConCero(e.getValue()))
                .collect(Collectors.joining("   "));

        // [21] El impresor pinta "*_Domiciliada" si algún artículo del detalle empieza con "*"
        boolean domiciliada = renglones.subList(0, Math.min(EstadoCuentaEsperado.MAX_RENGLONES, renglones.size()))
                .stream().anyMatch(r -> r.articulo().strip().startsWith("*") || r.cuenta().domiciliada());

        // Dinero electrónico: sin saldo no se rellena ninguna moneda
        long saldoDe = exp.dineroElectronico().saldo();
        int monedas = saldoDe > 0 ? Math.min(MAX_MONEDAS, exp.dineroElectronico().abonosPuntuales()) : 0;

        alertas.addAll(alertasSituacion(cliente, exp.situacionesEspeciales()));
        if (cliente.noEnviarEdoCta() && vencidoTotal == 0) {
            alertas.add("El cliente pidió no recibir estado de cuenta y no tiene vencido: no se le debe generar");
        }
        if (renglones.isEmpty()) {
            alertas.add("El cliente no tiene saldo: no se le debe generar estado de cuenta");
        }
        if (renglones.size() > EstadoCuentaEsperado.MAX_RENGLONES) {
            alertas.add("El cliente tiene " + renglones.size() + " cuentas: se pintan " + EstadoCuentaEsperado.MAX_RENGLONES
                    + " pero el PASE A PAGAR y los TOTALES consideran todas");
        }

        return new EstadoCuentaEsperado(
                Formato.fechaLarga(corte),
                favorPagar,
                explicacionFavor,
                asteriscos,
                explicacion,
                renglones,
                totales,
                facturas,
                interesesCartera,
                citatorio,
                explicacionCit,
                marca,
                aviso,
                explicacionAviso,
                situacionImpresa,
                saldoDe,
                monedas,
                domiciliada,
                alertas);
    }

    /**
     * Primera cuenta con la leyenda "DEFINIR CON PAGOS" según fnPc2GeDeterminaLeyRecogerDefinirPagos: plazo menor a
     * 18 meses (fun_validarplazo = 12) y 14 o más meses desde la venta (fnGeMesesTranscurridos). No aplica a la
     * cuenta de muebles que ya lleva la leyenda "recoger": debe 50% o más de la venta y tiene 3 o más abonos
     * vencidos sin abonar desde la venta, o 5 o más con abonos o convenio vigente. Los abonos vencidos se aproximan
     * con vencido / abono mensual (la emisión usa AbonosYMesesVencidos). Sin cuentas perdidas.
     */
    private static Renglon cuentaDefinirConPagos(List<Renglon> renglones, LocalDate corte) {
        for (Renglon r : renglones) {
            Cuenta c = r.cuenta();
            if (c.cuentaPerdida() || r.ustedDebe() <= 0 || c.fechaCompra() == null || c.plazo() >= 18
                    || mesesTranscurridos(c.fechaCompra(), corte) < 14) {
                continue;
            }
            if (c.tipo() == TipoCartera.MUEBLES) {
                boolean abono = !((c.fechaCompra().equals(c.fechaUltimoMovimiento())
                        && !c.tipoUltimoMovimiento().strip().equals("5"))
                        || c.importeVenta() - c.enganche() == c.saldoFecha());
                boolean convenio = c.fechaConvenio() != null && c.importeConvenio() > 0 && c.plazoConvenio() > 0
                        && ChronoUnit.DAYS.between(c.fechaConvenio(), corte) <= DIAS_CONVENIO_VIGENTE;
                long porcentajeDeuda = c.importeVenta() > 0 ? (long) (r.ustedDebe() * 100.0 / c.importeVenta()) : 0;
                long abonosVencidos = c.abonoMensual() > 0 ? c.vencido() / c.abonoMensual() : 0;
                if (porcentajeDeuda >= 50 && ((abonosVencidos >= 3 && !abono && !convenio)
                        || (abonosVencidos >= 5 && (abono || convenio)))) {
                    continue; // leyenda "recoger"
                }
            }
            return r;
        }
        return null;
    }

    /** fnGeMesesTranscurridos: diferencia de meses calendario, sin contar días. */
    static long mesesTranscurridos(LocalDate venta, LocalDate corte) {
        return Math.max(0, (corte.getYear() - venta.getYear()) * 12L + corte.getMonthValue() - venta.getMonthValue());
    }

    /** Ropa primero y después las demás cuentas en el orden de los listados. */
    private static List<Cuenta> ordenar(List<Cuenta> cuentas) {
        List<Cuenta> ordenadas = new ArrayList<>(cuentas);
        ordenadas.sort(Comparator.comparing((Cuenta c) -> c.tipo() != TipoCartera.ROPA)
                .thenComparing(Cuenta::cuentaPerdida));
        return ordenadas;
    }

    private Renglon renglon(Cuenta c, LocalDate siguienteCorte) {
        // [6] USTED DEBE = SALDO A LA FECHA + INTERÉS ADICIONAL
        long debe = c.saldoFecha() + c.interesAdicional();
        long interesMes = c.interesAdicionalMes();
        long debia = Math.max(0, c.saldoFecha() + c.interesAdicionalMesAnterior() + c.abonosMes() - c.comprasMes());

        // [7] PÁGUELO CON SOLO = USTED DEBE - bonificación
        Bonificacion bonificacion = bonificacion(c, debe, siguienteCorte);
        long paguelo = debe - bonificacion.importe();

        // [9] ATRASADO = VENCIDO (en las muestras incluye el interés adicional, tope USTED DEBE)
        long atrasado = c.vencido() > 0 ? Math.min(c.vencido() + c.interesAdicional(), debe) : 0;
        // [10] PASE A PAGAR = ABONO MENSUAL + ATRASADO (tope USTED DEBE)
        long pase = Math.min(c.abonoMensual() + atrasado, debe);

        // [11] FECHA DE ÚLTIMO ABONO = FECHA ULT MVTO; en cuentas perdidas se pinta "CP"
        String ultimoAbono;
        if (c.cuentaPerdida()) {
            ultimoAbono = "CP";
        } else if (c.comprasMes() > 0 && c.fechaUltimoMovimiento() != null
                && c.fechaUltimoMovimiento().equals(c.fechaCompra())) {
            ultimoAbono = ""; // el último movimiento fue la compra, no un abono
        } else {
            ultimoAbono = Formato.fechaCorta(c.fechaUltimoMovimiento());
        }

        return new Renglon(c, c.articulo(), Formato.fechaCorta(c.fechaCompra()), debia, c.abonosMes(), c.comprasMes(),
                interesMes, debe, paguelo, c.abonoMensual(), atrasado, pase, ultimoAbono, bonificacion);
    }

    private Bonificacion bonificacion(Cuenta c, long debe, LocalDate siguienteCorte) {
        if (c.cuentaPerdida()) {
            return new Bonificacion(0, 0, c.plazo(), 0, 0, "Cuenta perdida: sin bonificación");
        }
        if (c.tipo() == TipoCartera.TIEMPO_AIRE) {
            return new Bonificacion(c.bonificacionMes(), 0, c.plazo(), 1, Math.min(c.bonificacionMes(), debe),
                    "Tiempo aire: bonificación del mes × 1");
        }
        long dias = c.fechaCompra() == null ? -1 : ChronoUnit.DAYS.between(c.fechaCompra(), siguienteCorte);
        if (c.tipo() == TipoCartera.MUEBLES && dias >= 0 && dias < 1) {
            dias = 1; // fun_calculabonificacion: en muebles al menos 1 día
        }
        // fun_calculabonificacion: ropa que se quedó sin saldo hace 30 días o menos bonifica el interés completo
        if (c.tipo() == TipoCartera.ROPA && c.fechaSinSaldo() != null
                && ChronoUnit.DAYS.between(c.fechaSinSaldo(), siguienteCorte) <= 30) {
            long importe = Math.min(c.interesSobreCompra(), c.saldoFecha());
            return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), 1, importe,
                    "Ropa sin saldo el " + Formato.fechaCorta(c.fechaSinSaldo())
                            + " (30 días o menos): se bonifica el interés sobre compra completo");
        }
        String clave = c.tipo().claveCatalogo();
        if (clave == null || c.fechaCompra() == null) {
            long importe = Math.max(0, debe - c.saldaCon());
            return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), 0, importe,
                    "Sin catálogo para " + c.tipo().nombre() + ": se toma el Salda Con del listado. "
                            + "Verificar el factor con la calculadora de Calidad");
        }
        OptionalDouble factor = catalogo.factor(clave, c.plazo(), dias, c.fechaCompra());
        if (factor.isEmpty()) {
            long importe = Math.max(0, debe - c.saldaCon());
            return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), 0, importe,
                    "Plazo " + c.plazo() + " no está en el catálogo de " + c.tipo().nombre()
                            + ": se toma el Salda Con del listado");
        }
        // fnGeSaldaCon: la bonificación no puede pasar del saldo a la fecha
        long importe = Math.min(Math.round(c.interesSobreCompra() * factor.getAsDouble()), c.saldoFecha());
        return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), factor.getAsDouble(), importe, "");
    }

    private static Totales totales(List<Renglon> renglones) {
        long debia = 0, abonos = 0, compras = 0, interes = 0, debe = 0, paguelo = 0, abono = 0, atrasado = 0, pase = 0;
        for (Renglon r : renglones) {
            debia += r.ustedDebia();
            abonos += r.menosAbonos();
            compras += r.masCompras();
            interes += r.interesAdicional();
            debe += r.ustedDebe();
            paguelo += r.pagueloConSolo();
            abono += r.abonoMensual();
            atrasado += r.atrasado();
            pase += r.paseAPagar();
        }
        return new Totales(debia, abonos, compras, interes, debe, paguelo, abono, atrasado, pase);
    }

    private static List<String> alertasSituacion(Cliente cliente, List<SituacionEspecial> situaciones) {
        List<String> alertas = new ArrayList<>();
        List<String[]> todas = new ArrayList<>();
        todas.add(new String[]{cliente.situacionEspecial(), cliente.causaSituacion()});
        for (SituacionEspecial s : situaciones) {
            todas.add(new String[]{s.situacion(), s.causa()});
        }
        for (String[] s : todas) {
            String situacion = s[0] == null ? "" : s[0].trim().toUpperCase();
            String causa = s[1] == null ? "" : s[1].trim();
            if (SITUACIONES_EXCLUIDAS.contains(situacion)
                    || (situacion.equals("P") && CAUSAS_P_EXCLUIDAS.contains(causa))) {
                String texto = "Situación especial " + situacion + (situacion.equals("P") ? causa : "")
                        + ": a este cliente NO se le debe generar estado de cuenta";
                if (!alertas.contains(texto)) {
                    alertas.add(texto);
                }
            }
        }
        return alertas;
    }
}
