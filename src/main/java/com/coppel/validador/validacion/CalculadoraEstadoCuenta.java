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

        // [15] Favor de pagar antes de
        boolean inmediato = switch (puntualidad) {
            case "A" -> totales.abonoMensual() > 0 && vencidoTotal >= 2 * totales.abonoMensual();
            case "B", "C", "D", "Z" -> totales.abonoMensual() > 0 && vencidoTotal >= totales.abonoMensual();
            default -> false;
        };
        String favorPagar = inmediato ? "INMEDIATO" : Formato.fechaLarga(siguienteCorte);

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

        // [19] Tienda + factura * artículos (todas las cuentas menos ropa)
        String facturas = renglones.stream()
                .map(Renglon::cuenta)
                .filter(c -> c.tipo() != TipoCartera.ROPA)
                .map(c -> String.format("%04d%06d*%d", c.numeroTienda(), c.numeroFactura(), c.numeroArticulos()))
                .collect(Collectors.joining(" "));

        // [20] Interés adicional por cartera
        Map<Character, Long> intereses = new LinkedHashMap<>();
        for (Renglon r : renglones) {
            if (r.cuenta().interesAdicional() > 0) {
                intereses.merge(r.cuenta().tipo().letraInteres(), r.cuenta().interesAdicional(), Long::sum);
            }
        }
        String interesesCartera = intereses.entrySet().stream()
                .map(e -> "I" + e.getKey() + "$" + Formato.importeConCero(e.getValue()))
                .collect(Collectors.joining("   "));

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
                asteriscos,
                explicacion,
                renglones,
                totales,
                facturas,
                interesesCartera,
                citatorio,
                explicacionCit,
                exp.dineroElectronico().saldo(),
                exp.dineroElectronico().abonosPuntuales(),
                alertas);
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
        if (c.vencido() > 0) {
            return new Bonificacion(0, 0, c.plazo(), 0, 0, "Con atraso: sin bonificación");
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
