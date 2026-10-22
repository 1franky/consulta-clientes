package com.coppel.validador.validacion;

import com.coppel.validador.model.Cliente;
import com.coppel.validador.model.Cuenta;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.ExpedienteCliente.SituacionEspecial;
import com.coppel.validador.model.ReglasMuebles;
import com.coppel.validador.model.TipoCartera;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Bonificacion;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Renglon;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Totales;

import java.time.LocalDate;
import java.time.YearMonth;
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
    private final ReglasMuebles reglasMuebles;
    private final CatalogoLeyendas leyendas;

    /** Texto de S/16 en las muestras; el catálogo del laboratorio no tiene los renglones S. */
    static final String AVISO_DEFINIR_PAGOS = "DEFINIR CON PAGOS";

    /** spPc2GeInsertaPrioridadesPorPunt: prioridad base de la leyenda normal de cobranzas. */
    private static final Map<String, Integer> PRIORIDAD_PUNTUALIDAD =
            Map.of("N", 100, "A", 200, "B", 300, "C", 400, "D", 500, "Z", 600);
    /** fnPc2GeCalculaLeyNormalDeCobranzas topa los abonos y meses vencidos en 12. */
    private static final int MAX_VENCIDOS_LEYENDA = 12;
    /** Meses de corte en los que las leyendas de 0 y 1 vencidos usan la clave 1 (los demás, la 2). */
    private static final Set<Integer> MESES_CLAVE_1 = Set.of(1, 2, 3, 7, 8, 9);
    /** fnPc2GeCalculaLeyendaDeCobranzas: mes de corte → leyenda W de temporada. */
    private static final Map<Integer, Integer> LEYENDA_TEMPORADA = Map.of(4, 24, 6, 25, 11, 27, 12, 28);
    /** fnPc2GeRegresaInicialesMes */
    private static final List<String> INICIALES_MES =
            List.of("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC");

    /** Sin consulta a la base (modo demo): las muestras solo imprimen I35; I23, I41 y S50 no. */
    private static final Set<String> SITUACIONES_IMPRESAS = Set.of("I35");

    /**
     * Orden de las carteras en el detalle. Las muestras confirman ropa → préstamos y Deuda Bancoppel → muebles;
     * tiempo aire y reestructura no aparecen en ninguna, su lugar es supuesto.
     */
    private static final List<TipoCartera> ORDEN_CARTERA = List.of(TipoCartera.ROPA, TipoCartera.PRESTAMOS,
            TipoCartera.DEUDA_BANCOPPEL, TipoCartera.TIEMPO_AIRE, TipoCartera.REESTRUCTURA, TipoCartera.MUEBLES);

    /** El impresor tiene lugar para 4 monedas de abono puntual. */
    public static final int MAX_MONEDAS = 4;
    /** fnGeConvenioVigente: días que dura vigente un convenio. */
    private static final int DIAS_CONVENIO_VIGENTE = 45;

    public CalculadoraEstadoCuenta(CatalogoBonificaciones catalogo, ReglasMuebles reglasMuebles,
                                   CatalogoLeyendas leyendas) {
        this.catalogo = catalogo;
        this.reglasMuebles = reglasMuebles;
        this.leyendas = leyendas;
    }

    /** Sin la configuración de muebles ni el catálogo de leyendas (modo demo, catálogo CSV). */
    public CalculadoraEstadoCuenta(CatalogoBonificaciones catalogo) {
        this(catalogo, ReglasMuebles.NINGUNA, CatalogoLeyendas.VACIO);
    }

    /** De dónde se leyó el catálogo de leyendas. */
    public String origenLeyendas() {
        return leyendas.origen();
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
            Renglon r = renglon(c, siguienteCorte, saldaConDelSiguienteCorte(exp, siguienteCorte));
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
        String marca = marcaContacto(exp.marcasContacto());
        String marcaListado = marca;
        if (totales.ustedDebe() == 0 || atrasado == 0) {
            marca = "";
        }
        if (puntualidad.equals("Z")) {
            marca = ""; // con puntualidad Z las muestras no imprimen la marca
        }
        // AVISO IMPORTANTE: renglón de catLeyendasCorreos que elige la emisión (fnPc2GeCalculaLeyendas, espacio 1)
        Leyenda leyenda = leyendaAviso(renglones, exp, puntualidad, corte);
        String aviso = leyenda.texto();
        String explicacionAviso = leyenda.explicacion();
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
     * Leyenda elegida para "AVISO IMPORTANTE": renglón de catLeyendasCorreos (letra, número, clave) y su prioridad.
     *
     * @param motivo por qué se eligió, para la explicación
     */
    record Leyenda(String letra, int numero, String clave, int prioridad, String motivo, String texto,
                   String explicacion) {

        static final Leyenda NINGUNA = new Leyenda("", -1, "", 0, "", "", "");

        Leyenda(String letra, int numero, String clave, int prioridad, String motivo) {
            this(letra, numero, clave, prioridad, motivo, "", "");
        }

        String renglon() {
            return letra + "/" + numero + (clave.isEmpty() ? "" : " clave " + clave);
        }
    }

    /**
     * fnPc2GeCalculaLeyendas, espacio 1 (posición 3 = AVISO IMPORTANTE): cada cuenta con saldo propone una leyenda con
     * prioridad y el cliente se queda con la de mayor prioridad (spPc2GeCalculaLeyendas). Por cuenta, en el orden de
     * la emisión, una leyenda solo reemplaza a otra de menor prioridad:
     * <ol>
     *   <li>Convenio vigente (fnPc2GeCalculaLeyendaConvenios): W/0, prioridad 850.</li>
     *   <li>Temporada (fnPc2GeCalculaLeyendaDeCobranzas): puntualidad A (207) o N (107) con 1 o menos abonos y meses
     *       vencidos en abril, junio, noviembre o diciembre: W/24, W/25, W/27, W/28.</li>
     *   <li>Normal de cobranzas (fnPc2GeCalculaLeyNormalDeCobranzas): puntualidad / abonos y meses vencidos (máximo
     *       12), prioridad = puntualidad (N 100 … Z 600) + vencidos × 5 + 1. Con 0 o 1 vencidos, clave 1 en enero,
     *       febrero, marzo, julio, agosto y septiembre, y clave 2 los demás meses.</li>
     *   <li>Recoger / definir con pagos (fnPc2GeCalLeyRecogerDefinir): S/15 (2100) o S/16 (2000).</li>
     * </ol>
     * No se replican, porque el laboratorio no tiene sus datos: cliente nuevo (W/19, 2000), ultimátum (X/69, 1000) y
     * Enrique Coppel (W/40 y W/42).
     */
    private Leyenda leyendaAviso(List<Renglon> renglones, ExpedienteCliente exp, String puntualidad, LocalDate corte) {
        Leyenda elegida = Leyenda.NINGUNA;
        boolean aproximado = false;
        for (Renglon r : renglones) {
            if (r.cuenta().cuentaPerdida()) {
                continue; // la emisión excluye las cuentas perdidas (NumeroFactura = -1)
            }
            Integer vencidos = abonosYMesesVencidos(exp, r.cuenta());
            if (vencidos == null) {
                aproximado = true;
                vencidos = r.cuenta().abonoMensual() > 0 ? (int) (r.cuenta().vencido() / r.cuenta().abonoMensual()) : 0;
            }
            Leyenda propuesta = leyendaCuenta(r, vencidos, puntualidad, corte);
            if (propuesta.prioridad() > elegida.prioridad()
                    || (propuesta.prioridad() == elegida.prioridad() && propuesta.numero() > elegida.numero())) {
                elegida = propuesta;
            }
        }
        if (elegida == Leyenda.NINGUNA) {
            return elegida;
        }

        String texto;
        String nota;
        var renglon = leyendas.buscar(elegida.letra(), elegida.numero(), elegida.clave());
        if (renglon.isPresent()) {
            texto = renglon.get().texto();
            nota = "catLeyendasCorreos " + elegida.renglon();
            if (elegida.letra().equals("W") && elegida.numero() == 0) {
                texto = conveniosConcatenados(texto, renglones, corte);
                nota += " (X3, X5 y X6 los llena la emisión con quién recibió el convenio y su vencimiento)";
            }
        } else if (elegida.letra().equals("S")) {
            // El catálogo del laboratorio no trae los renglones S; las muestras imprimen este texto con S/16 y
            // también en las 3 que cumplen la condición de "recoger" (S/15)
            texto = AVISO_DEFINIR_PAGOS;
            nota = elegida.renglon() + " no está en catLeyendasCorreos: texto tomado de las muestras";
        } else {
            texto = "";
            nota = leyendas.vacio() ? "Sin catálogo de leyendas: corresponde el renglón " + elegida.renglon()
                    : "El renglón " + elegida.renglon() + " no está en catLeyendasCorreos: revisar a mano";
        }
        String explicacion = elegida.motivo() + ". " + nota
                + (aproximado ? ". Abonos vencidos aproximados con vencido / abono (no se consultaron)" : "");
        return new Leyenda(elegida.letra(), elegida.numero(), elegida.clave(), elegida.prioridad(), elegida.motivo(),
                texto, explicacion);
    }

    /** Propuesta de una cuenta con saldo, en el orden y con las prioridades de fnPc2GeCalculaLeyendas. */
    static Leyenda leyendaCuenta(Renglon r, int vencidos, String puntualidad, LocalDate corte) {
        Cuenta c = r.cuenta();
        int mes = corte.getMonthValue();
        Leyenda elegida = Leyenda.NINGUNA;
        if (convenioVigente(c, corte)) {
            elegida = new Leyenda("W", 0, "", 850, "Convenio vigente del " + Formato.fechaCorta(c.fechaConvenio())
                    + " en " + r.articulo().strip());
        }
        int prioridadTemporada = puntualidad.equals("A") ? 207 : 107;
        if (vencidos <= 1 && (puntualidad.equals("A") || puntualidad.equals("N"))
                && LEYENDA_TEMPORADA.containsKey(mes) && elegida.prioridad() < prioridadTemporada) {
            elegida = new Leyenda("W", LEYENDA_TEMPORADA.get(mes), "", prioridadTemporada,
                    "Leyenda de temporada: puntualidad " + puntualidad + " con " + vencidos
                            + " abonos y meses vencidos en el mes " + mes);
        }
        Integer base = PRIORIDAD_PUNTUALIDAD.get(puntualidad);
        if (base != null) {
            int numero = Math.min(vencidos, MAX_VENCIDOS_LEYENDA);
            int prioridad = base + numero * 5 + 1;
            if (elegida.prioridad() < prioridad) {
                String clave = numero <= 1 ? (MESES_CLAVE_1.contains(mes) ? "1" : "2") : "";
                elegida = new Leyenda(puntualidad, numero, clave, prioridad, "Puntualidad " + puntualidad + " con "
                        + vencidos + " abonos y meses vencidos" + (vencidos > MAX_VENCIDOS_LEYENDA ? " (tope 12)" : "")
                        + " en " + r.articulo().strip());
            }
        }
        int recogerDefinir = recogerDefinir(r, vencidos, corte);
        if (elegida.prioridad() < 2000 && recogerDefinir == 1) {
            elegida = new Leyenda("S", 15, "", 2100, "Leyenda recoger: " + r.articulo().strip()
                    + " debe 50% o más de la venta con " + vencidos + " abonos y meses vencidos");
        } else if (elegida.prioridad() < 2000 && recogerDefinir == 2) {
            elegida = new Leyenda("S", 16, "", 2000, r.articulo().strip() + " a plazo de 12 meses comprada el "
                    + Formato.fechaCorta(c.fechaCompra()) + " (14 o más meses)");
        }
        return elegida;
    }

    /**
     * fnPc2GeDeterminaLeyRecogerDefinirPagos: 1 = recoger, 2 = definir con pagos, 0 = ninguna. Recoger: muebles que
     * debe 50% o más de la venta (importe + interés sobre compra, como spPc2GeCarMuebVdoCalEdoCta) con 3 o más abonos y meses vencidos sin abonar desde la venta y sin convenio, o 5 o
     * más con abonos o convenio vigente. Definir: plazo de 12 (fun_validarplazo) y 14 o más meses desde la venta.
     */
    static int recogerDefinir(Renglon r, int vencidos, LocalDate corte) {
        Cuenta c = r.cuenta();
        if (r.ustedDebe() <= 0) {
            return 0;
        }
        int flag = 0;
        if (c.tipo() == TipoCartera.MUEBLES) {
            long venta = c.importeVenta() + c.interesSobreCompra(); // spPc2GeCarMuebVdoCalEdoCta
            boolean abono = !((c.fechaCompra() != null && c.fechaCompra().equals(c.fechaUltimoMovimiento())
                    && !c.tipoUltimoMovimiento().strip().equals("5"))
                    || venta - c.enganche() == c.saldoFecha());
            boolean convenio = convenioVigente(c, corte);
            long porcentajeDeuda = venta > 0 ? (long) (r.ustedDebe() * 100.0 / venta) : 0;
            if (porcentajeDeuda >= 50 && ((vencidos >= 3 && !abono && !convenio)
                    || (vencidos >= 5 && (abono || convenio)))) {
                flag = 1;
            }
        }
        if (flag != 1 && c.fechaCompra() != null && mesesTranscurridos(c.fechaCompra(), corte) >= 14
                && plazoCatalogo(c.plazo()) == 12) {
            flag = 2;
        }
        return flag;
    }

    /**
     * fnGeConvenioVigente: convenio con importe y plazo de hace 45 días o menos. Un convenio posterior al corte no
     * existía al emitir (los datos pueden ser de días después del corte).
     */
    private static boolean convenioVigente(Cuenta c, LocalDate corte) {
        return c.fechaConvenio() != null && c.importeConvenio() > 0 && c.plazoConvenio() > 0
                && !c.fechaConvenio().isAfter(corte)
                && ChronoUnit.DAYS.between(c.fechaConvenio(), corte) <= DIAS_CONVENIO_VIGENTE;
    }

    /** W/0: "EL DIA X1 DE X2 ... ABONAR $X4 ..." con el día, el mes y el importe del convenio más reciente. */
    private static String conveniosConcatenados(String texto, List<Renglon> renglones, LocalDate corte) {
        return renglones.stream().map(Renglon::cuenta).filter(c -> convenioVigente(c, corte))
                .max(Comparator.comparing(Cuenta::fechaConvenio))
                .map(c -> texto.replace("X1", String.valueOf(c.fechaConvenio().getDayOfMonth()))
                        .replace("X2", INICIALES_MES.get(c.fechaConvenio().getMonthValue() - 1))
                        .replace("X4", String.valueOf(c.importeConvenio())))
                .orElse(texto);
    }

    private static Integer abonosYMesesVencidos(ExpedienteCliente exp, Cuenta c) {
        Map<String, Integer> vencidos = exp.abonosYMesesVencidos();
        if (vencidos == null || vencidos.isEmpty()) {
            return null;
        }
        Integer v = vencidos.get(ExpedienteCliente.llave(c.tipo(), c.numeroFactura()));
        return v != null ? v : vencidos.get(ExpedienteCliente.llave(c.tipo(), 0));
    }

    /** fnGeMesesTranscurridos: diferencia de meses calendario, sin contar días. */
    static long mesesTranscurridos(LocalDate venta, LocalDate corte) {
        return Math.max(0, (corte.getYear() - venta.getYear()) * 12L + corte.getMonthValue() - venta.getMonthValue());
    }

    /**
     * clv_clientenocontactado como lo arma la emisión (spccreaDirectorioCompleto03): "NTE", luego se agrega " NCE",
     * y 3MSC reemplaza todo. El impresor lo pinta tal cual, así que NTE y NCE juntas salen "NTE NCE".
     */
    static String marcaContacto(List<String> marcas) {
        List<String> claves = marcas.stream().map(m -> m.split(":")[0].trim()).toList();
        if (claves.contains("3MSC")) {
            return "3MSC";
        }
        return ((claves.contains("NTE") ? "NTE" : "") + (claves.contains("NCE") ? " NCE" : "")).trim();
    }

    /**
     * Orden del impresor (saldosemisioncorreos: orden, fecha de compra desc, factura desc): por cartera y, dentro de
     * cada una, de la compra más reciente a la más antigua. Las cuentas perdidas no van al final.
     */
    static List<Cuenta> ordenar(List<Cuenta> cuentas) {
        List<Cuenta> ordenadas = new ArrayList<>(cuentas);
        ordenadas.sort(Comparator.comparingInt((Cuenta c) -> ORDEN_CARTERA.indexOf(c.tipo()))
                .thenComparing(Cuenta::fechaCompra, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(Cuenta::numeroFactura).reversed()));
        return ordenadas;
    }

    /** Descripción como la imprime el impresor: 8 caracteres (10 en Deuda Bancoppel). */
    static String descripcion(Cuenta c) {
        String articulo = c.articulo().strip();
        int largo = c.tipo() == TipoCartera.DEUDA_BANCOPPEL ? 10 : 8;
        return articulo.length() > largo ? articulo.substring(0, largo) : articulo;
    }

    /**
     * Los listados de Deuda Bancoppel y reestructura traen SaldaCon a la fecha de catFechas y SaldaCon20SigMes al 20
     * (24 en diciembre) del mes siguiente. Si los datos son del mes del corte, SaldaCon20SigMes es el del siguiente
     * corte; si no, el más cercano es SaldaCon.
     */
    static boolean saldaConDelSiguienteCorte(ExpedienteCliente exp, LocalDate siguienteCorte) {
        return exp.fechaDatos() != null
                && YearMonth.from(exp.fechaDatos().plusMonths(1)).equals(YearMonth.from(siguienteCorte));
    }

    private Renglon renglon(Cuenta c, LocalDate siguienteCorte, boolean saldaCon20) {
        // [6] USTED DEBE = SALDO A LA FECHA + INTERÉS ADICIONAL
        long debe = c.saldoFecha() + c.interesAdicional();
        long interesMes = c.interesAdicionalMes();
        long debia = Math.max(0, c.saldoFecha() + c.interesAdicionalMesAnterior() + c.abonosMes() - c.comprasMes());

        // [7] PÁGUELO CON SOLO = USTED DEBE - bonificación
        Bonificacion bonificacion = bonificacion(c, debe, siguienteCorte, saldaCon20);
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

        return new Renglon(c, descripcion(c), Formato.fechaCorta(c.fechaCompra()), debia, c.abonosMes(), c.comprasMes(),
                interesMes, debe, paguelo, c.abonoMensual(), atrasado, pase, ultimoAbono, bonificacion);
    }

    private Bonificacion bonificacion(Cuenta c, long debe, LocalDate siguienteCorte, boolean saldaCon20) {
        if (c.cuentaPerdida()) {
            return new Bonificacion(0, 0, c.plazo(), 0, 0, "Cuenta perdida: sin bonificación");
        }
        if (c.tipo() == TipoCartera.TIEMPO_AIRE) {
            return new Bonificacion(c.bonificacionMes(), 0, c.plazo(), 1, Math.min(c.bonificacionMes(), debe),
                    "Tiempo aire: bonificación del mes × 1");
        }
        if (c.tipo() == TipoCartera.MUEBLES && c.fechaCompra() != null) {
            Bonificacion muebles = bonificacionMuebles(c, siguienteCorte);
            if (muebles != null) {
                return muebles;
            }
        }
        long dias = c.fechaCompra() == null ? -1 : ChronoUnit.DAYS.between(c.fechaCompra(), siguienteCorte);
        // fun_calculabonificacion: ropa que se quedó sin saldo hace 30 días o menos bonifica el interés completo
        if (c.tipo() == TipoCartera.ROPA && c.fechaSinSaldo() != null
                && ChronoUnit.DAYS.between(c.fechaSinSaldo(), siguienteCorte) <= 30) {
            long importe = topeSaldo(c.interesSobreCompra(), c);
            return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), 1, importe,
                    "Ropa sin saldo el " + Formato.fechaCorta(c.fechaSinSaldo())
                            + " (30 días o menos): se bonifica el interés sobre compra completo"
                            + notaTope(c.interesSobreCompra(), c));
        }
        String clave = c.tipo().claveCatalogo();
        long saldaCon = saldaCon20 ? c.saldaCon20SiguienteMes() : c.saldaCon();
        String columna = saldaCon20 ? "SaldaCon20SigMes" : "SaldaCon (a la fecha de los datos)";
        if (clave == null || c.fechaCompra() == null) {
            long importe = Math.max(0, debe - saldaCon);
            return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), 0, importe,
                    "Sin catálogo para " + c.tipo().nombre() + ": se toma " + columna + " del listado");
        }
        OptionalDouble factor = catalogo.factor(clave, c.plazo(), dias, c.fechaCompra());
        if (factor.isEmpty()) {
            long importe = Math.max(0, debe - saldaCon);
            return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), 0, importe,
                    "Plazo " + c.plazo() + " no está en el catálogo de " + c.tipo().nombre()
                            + ": se toma " + columna + " del listado");
        }
        long bruto = Math.round(c.interesSobreCompra() * factor.getAsDouble());
        return new Bonificacion(c.interesSobreCompra(), dias, c.plazo(), factor.getAsDouble(), topeSaldo(bruto, c),
                notaTope(bruto, c).replaceFirst("^\\. ", ""));
    }

    /**
     * proc_calculabonificacionmuebles (desde dic-2025): ajusta la fecha de compra y escoge la tabla por tipo de
     * producto. Devuelve null si la tabla no está en el catálogo (se usa el Salda Con del listado).
     */
    private Bonificacion bonificacionMuebles(Cuenta c, LocalDate fechaSaldaCon) {
        int plazo = plazoCatalogo(c.plazo());
        boolean ventaFutura = reglasMuebles.plazosVentaFutura().contains(c.plazo());
        int diasAjuste = reglasMuebles.diasAjuste().getOrDefault(c.tipoProducto(), -1);
        LocalDate venta = c.fechaCompra();
        LocalDate pago = diaDePago(ventaFutura, venta);
        LocalDate compra;
        if (!fechaSaldaCon.isAfter(pago)) {
            compra = venta.plusDays(ChronoUnit.DAYS.between(venta, pago) - pago.lengthOfMonth());
        } else if (ventaFutura) {
            compra = venta.plusMonths(1);
        } else if (diasAjuste > 0) {
            compra = venta.plusDays(diasAjuste);
        } else {
            compra = venta;
        }
        long dias = ChronoUnit.DAYS.between(compra, fechaSaldaCon);
        String ajuste = compra.equals(venta) ? "" : ", fecha de compra ajustada al " + Formato.fechaCorta(compra);

        String tabla;
        String nota;
        LocalDate vigencia = compra;
        long diasTabla = dias;
        boolean trunca = false;
        if (reglasMuebles.negocioAfiliado(c.tipoProducto(), venta)) {
            tabla = "COPPELPAY";
            nota = "Negocio afiliado (cat_bonificacionescoppelpay)";
            LocalDate minima = catalogo.primeraVigencia(tabla);
            if (minima != null && compra.isBefore(minima)) {
                vigencia = minima;
            }
            diasTabla = Math.max(0, dias);
            trunca = true; // CAST(... AS INT) sin redondear
        } else if (diasAjuste == 0 && c.tipoProducto() != ReglasMuebles.TIPO_MOTO) {
            tabla = "MUEBLES_BENEFICIOS";
            nota = "Producto con beneficio (cat_bonificacion_muebles_beneficios)";
        } else if (diasAjuste > 0 || c.tipoProducto() == ReglasMuebles.TIPO_MOTO) {
            tabla = "MOTOS";
            nota = "Moto o producto con días de ajuste (CAT_BONIFICACIONESMOTOS)";
        } else {
            tabla = "MUEBLES";
            nota = "";
            diasTabla = Math.max(1, dias); // fun_calculabonificacion: en muebles al menos 1 día
        }
        OptionalDouble factor;
        if (diasTabla < 0) {
            factor = OptionalDouble.of(0); // la tabla no tiene días negativos: sin bonificación
        } else {
            factor = catalogo.factor(tabla, plazo, diasTabla, vigencia);
        }
        if (factor.isEmpty()) {
            return null;
        }
        double bruto = c.interesSobreCompra() * factor.getAsDouble();
        long importe = trunca ? (long) bruto : Math.round(bruto);
        return new Bonificacion(c.interesSobreCompra(), diasTabla, plazo, factor.getAsDouble(), topeSaldo(importe, c),
                (nota + ajuste).replaceFirst("^, ", "") + notaTope(importe, c));
    }

    /**
     * fnGeSaldaCon: si la bonificación pasa del saldo a la fecha no se aplica (Salda Con = saldo + interés adicional).
     */
    static long topeSaldo(long bonificacion, Cuenta c) {
        return bonificacion > c.saldoFecha() ? 0 : bonificacion;
    }

    private static String notaTope(long bonificacion, Cuenta c) {
        return bonificacion > c.saldoFecha() ? ". La bonificación ($" + Formato.importeConCero(bonificacion)
                + ") pasa del saldo ($" + Formato.importeConCero(c.saldoFecha()) + "): fnGeSaldaCon no la aplica" : "";
    }

    /** fun_validarplazo: plazos de muebles agrupados en 12, 18, 24, 30 y 36. */
    static int plazoCatalogo(int plazo) {
        if (plazo < 18) {
            return 12;
        }
        if (plazo < 24) {
            return 18;
        }
        if (plazo < 30) {
            return 24;
        }
        return plazo < 36 ? 30 : 36;
    }

    /** fun_calculardiadepago: día siguiente a la venta más 1 mes (venta futura: 2 meses y al menos 60 días). */
    static LocalDate diaDePago(boolean ventaFutura, LocalDate venta) {
        LocalDate pago = venta.plusDays(1).plusMonths(ventaFutura ? 2 : 1);
        long dias = ChronoUnit.DAYS.between(venta, pago);
        return ventaFutura && dias < 60 ? pago.plusDays(60 - dias) : pago;
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
