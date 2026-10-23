package com.coppel.validador.ui;

import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.TablaListado;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Arma los listados con el mismo título, etiquetas y acomodo que los imprime VbnListadoCarteras
 * (muestras/&lt;cliente&gt;/*.pdf), a partir de las columnas que devolvió cada procedimiento.
 *
 * Solo presenta: lo único que se calcula es lo que agrega el listado impreso (vencido con interés
 * adicional, % y letra de la cobranza). Si un listado no se encuentra (modo demo, procedimiento con
 * error) la hoja lo dice; las columnas tal cual siguen en "Datos crudos".
 */
final class HojasListados {

    /** Una pestaña de la sección Listados. */
    record Hoja(String titulo, String html) {
    }

    /** Colores de la hoja para cada tema. */
    record Colores(String fondo, String texto, String tenue, String linea, String banda, String amarillo,
                   String logo) {
        static final Colores CLAROS = new Colores("#ffffff", "#000000", "#666666", "#000000", "#c0c0c0", "#f2c01e",
                "#1f3fbf");
        static final Colores OSCUROS = new Colores("#2b2d31", "#e6e6e6", "#9a9a9a", "#e6e6e6", "#4a4d55", "#b8901a",
                "#dfe6ff");
    }

    private static final DateTimeFormatter DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MES_DIA_ANIO = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
    private static final ThreadLocal<DecimalFormat> ENTERO = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> DOS_DECIMALES = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)));

    /** maeTelefonos.tipotelefono como lo describe el listado; los demás tipos no aparecen en las muestras. */
    private static final Map<Integer, String> TIPOS_TELEFONO = Map.of(
            2, "Teléfono celular del cliente",
            4, "Teléfono de casa del conyugue o familiar o 1ra referencia del cliente o solicitante");

    /** Listados de cuentas sin muestra impresa: se presentan con sus columnas como etiquetas. */
    private static final List<String> OTRAS_CARTERAS = List.of("Préstamos", "Tiempo aire", "Deuda Bancoppel",
            "Reestructuras", "CP Muebles", "CP Ropa", "CP Préstamos");

    private final ExpedienteCliente exp;
    private final LocalDate hoy;
    private final Colores c;

    private HojasListados(ExpedienteCliente exp, LocalDate hoy, Colores c) {
        this.exp = exp;
        this.hoy = hoy;
        this.c = c;
    }

    /** @param hoy fecha de impresión ("al ..." del título) */
    static List<Hoja> armar(ExpedienteCliente exp, LocalDate hoy, Colores colores) {
        HojasListados h = new HojasListados(exp, hoy, colores);
        List<Hoja> hojas = new ArrayList<>(List.of(
                new Hoja("Directorio", h.directorio()),
                new Hoja("Crédito", h.credito()),
                new Hoja("Muebles", h.muebles()),
                new Hoja("Ropa", h.ropa()),
                new Hoja("Dinero electrónico", h.dineroElectronico()),
                new Hoja("Cartera informativa", h.informativa()),
                new Hoja("Marcas de contacto", h.marcas())));
        for (String cartera : OTRAS_CARTERAS) {
            List<Fila> filas = h.filas(cartera, null);
            if (!filas.isEmpty()) {
                hojas.add(new Hoja(cartera, h.generica(cartera, filas)));
            }
        }
        return hojas;
    }

    // ---------- Directorio (spcQCDirectorio) ----------

    private String directorio() {
        Fila f = primera("Directorio", "DesdeCuandoVive");
        if (f == null) {
            return sinDatos("Listado de Clientes del Directorio");
        }
        StringBuilder sb = inicio();
        sb.append(encabezado("Listado de Clientes del Directorio al", fecha(f, "Fecha")));
        sb.append(subtitulo("Datos Generales"));
        sb.append(renglones(
                fila(dato("Cliente:", f.texto("NumeroCliente") + " -" + f.texto("Digito")), negritas(f.texto("NombreCompleto"), 6)),
                fila(dato("Zona:", f.texto("NumeroCiudad") + " - " + f.texto("NumeroColonia")),
                        dato("Ciudad:", f.texto("Ciudad")), dato("Colonia:", f.texto("Colonia"))),
                fila(dato("Calle:", f.texto("NumeroCalle") + "  " + f.texto("Calle")), dato("Casa:", f.texto("NumeroCasa")),
                        dato("Depto:", f.texto("DepartamentoCliente")), dato("Rumbo:", f.texto("Rumbo"))),
                fila(dato("Complemento Cte:", f.texto("Complemento")), dato("Telefono:", f.texto("TefonoCasaCte")),
                        dato("Celular:", f.texto("TefonoCelCte"))),
                fila(dato("Tipo de Casa:", f.texto("TipoDeCasa")), dato("Desde Cuando Vive:", fecha(f, "DesdeCuandoVive")),
                        dato("Cuantas Personas Viven:", f.texto("NumPersonasEnElDom")),
                        dato("Cuantas Personas Trabajan:", f.texto("NumPersonasTrabajan"))),
                fila(dato("Dependientes:", f.texto("NumDeDependientes")), dato("Sexo:", f.texto("Sexo")),
                        dato("Estado Civil:", f.texto("EstadoCivil")), dato("Reg Fed Elector:", f.texto("RegFederalElector")),
                        dato("Curp:", f.texto("Curp"))),
                fila(dato("Ingreso Mensual:", f.texto("IngresoMensual")), dato("Antiguedad:", fecha(f, "Antiguedad")),
                        dato("Situacion Especial:", f.texto("SituacionEspecial")), dato("Causa:", f.texto("CausaSituacionEsp")),
                        dato("Fecha:", fecha(f, "FechaSituacionEsp")), dato("Veces:", f.texto("VecesMarcadoSitEsp"))),
                fila(dato("Numero Tienda:", entero(f, "NumeroTienda")), dato("Fecha de Nacimiento:", fecha(f, "FechaDeNacimiento")),
                        dato("Limite de Credito:", entero(f, "LimiteDeCredito")), dato("Puesto:", f.texto("Puesto"))),
                fila(dato("Escolaridad:", f.texto("Escolaridad")), dato("RumboZona:", f.texto("rumbozona"))),
                fila(dato("Centro:", f.texto("centro")), dato("Supervisor:", f.texto("supervisor")),
                        dato("JefeGrupo:", f.texto("jefegrupo")), dato("Abogado:", f.texto("Abogado"))),
                fila(dato("Hora de contacto:", f.texto("horacontactoinicial") + " - " + f.texto("horacontactofinal")),
                        dato("Municipio:", f.texto("MunicipioZona")), dato("Poblacion:", f.texto("PoblacionZona"))),
                fila(etiqueta("CLIENTE GOL: " + siNo(f.entero("ClienteGol"))),
                        etiqueta("CLIENTE CONFLICTO: " + siNo(f.entero("FlagClienteConflicto"))))));
        sb.append(subtitulo("Trabajo"));
        sb.append(renglones(
                fila(dato("Lugar:", f.texto("LugarDeTrabajo")), dato("Puesto:", f.texto("Puesto")),
                        dato("Antiguedad:", fecha(f, "AntiguedadDelTrabajo"))),
                fila(dato("Zona:", f.texto("CiudadDelTrabajo") + " - " + f.texto("ColoniaDelTrabajo") + "  "
                                + f.texto("ColoniaTrabajo")),
                        dato("Calle:", f.texto("CalleDelTrabajo") + "  " + f.texto("CalleTrabajo")),
                        dato("Casa:", f.texto("CasaDelTrabajo"))),
                fila(dato("Rumbo:", f.texto("RumboTrabajo")), dato("Telefono:", f.texto("TefonoTrabajoCte")),
                        dato("Complemento:", f.texto("ComplementoTrabajo")))));
        sb.append(subtitulo("Referencia"));
        sb.append(renglones(
                fila(dato("Numero Cliente Referencia:", f.texto("NumeroClienteReferencia"))),
                fila(dato("Zona:", f.texto("CiudadDeReferencia") + " - " + f.texto("ColoniaDeReferencia") + "  "
                                + f.texto("ColoniaReferencia")),
                        dato("Calle:", f.texto("CalleDeReferencia") + "  " + f.texto("CalleReferencia")),
                        dato("Casa:", f.texto("CasaDeReferencia"))),
                fila(dato("Ciudad:", f.texto("CiudadReferencia")), dato("Rumbo Zona:", f.texto("RumboReferencia"))),
                fila(dato("Telefono:", f.texto("TelefonoReferencia")), dato("Complemento:", f.texto("ComplementoReferencia")))));
        sb.append(subtitulo("Conyuge"));
        sb.append(renglones(
                fila(dato("Numero Cliente Conyuge:", f.texto("NumeroClienteConyuge"))),
                fila(dato("Zona:", f.texto("CiudadDeConyuge") + " - " + f.texto("ColoniaDeConyuge") + "  "
                                + f.texto("ColoniaConyuge")),
                        dato("Calle:", f.texto("CalleDeConyuge") + "  " + f.texto("CalleConyuge")),
                        dato("Casa:", f.texto("CasaDeConyuge"))),
                fila(dato("Rumbo:", f.texto("RumboConyuge")), dato("Telefono:", f.texto("TelefonoConyuge")),
                        dato("Complemento:", f.texto("ComplementoConyuge"))),
                fila(dato("Lugar de Trabajo:", f.texto("LugarDeTrabajoConyuge")))));
        sb.append(subtitulo("Otros"));
        sb.append(renglones(
                fila(dato("Empleado Entrego Tarjeta:", f.texto("EmpleadoEntregoTarjeta")),
                        dato("Clave Otros Negocios:", f.texto("ClaveDeNegocio")), dato("Descuento:", "")),
                fila(dato("Empleado Autorizó :", f.texto("EmpleadoAutorizo")),
                        dato("Grupo Semaforo cn:", entero(f, "GrupoSemaforocn"))),
                fila(dato("Empleado Levantó Datos:", f.texto("EmpleadoLevantoDatos")))));
        return fin(sb);
    }

    // ---------- Crédito (spcQCCredito → tmpQCCredito01) ----------

    /** Renglón numérico del listado de crédito: columna del semestre actual y de los anteriores (%d = 4..1). */
    private record Acumulado(String etiqueta, String actual, String anterior) {
    }

    /** Lo que cambia entre las secciones de crédito; los nombres de columna salen de tmpQCCredito01. */
    private record SeccionCredito(String titulo, List<Acumulado> acumulados, String sufijoFechas, String primerCompra,
                                  String ultimaCompra, String ultimaSaldada, String etiquetaTeorica, Acumulado teorica,
                                  Acumulado real, String eficiencia, boolean negativoEntreParentesis) {
    }

    private String credito() {
        Fila f = primera("Crédito", "CobranzaTeoricaMuebles");
        if (f == null) {
            return sinDatos("Listado de Clientes de Crédito");
        }
        LocalDate datos = f.fecha("Fecha") != null ? f.fecha("Fecha") : exp.fechaDatos();
        StringBuilder sb = inicio();
        sb.append(encabezado("Listado de Clientes de Crédito al", fecha(datos)));
        sb.append(renglones(fila(dato("Numero Cliente:", f.texto("NumeroCliente") + "  " + f.texto("Digito")),
                negritas(f.texto("NombreCompleto"), 2), dato("Numero de Tienda:", f.texto("NumeroTienda")))));

        sb.append(seccionCredito(f, datos, new SeccionCredito("CREDITO MUEBLES", List.of(
                new Acumulado("Numero de Compras", "NumeroComprasMuebles", "NumComprasMuebSem%d"),
                new Acumulado("Acumulado de Compras", "ComprasMuebles", "AcumComprasMuebSem%d"),
                new Acumulado("Acumulado de Enganches", "EngancheMuebles", "AcumEngMuebSem%d"),
                new Acumulado("Numero de Abonos", "NumeroAbonosMuebles", "NumAbonosMuebSem%d"),
                new Acumulado("Acumulado de Abonos", "AbonoMuebles", "AcumAbonosMuebSem%d"),
                new Acumulado("Acumulado de Bonificaciones", "BonificacionesMuebles", "AcumBonifMuebSem%d"),
                new Acumulado("Acumulado de Int Adic Pagados", "IntAdicionalPagMuebles", "AcumIntAdicPagMuebSem%d"),
                new Acumulado("Acumulado de Ajustes", "AjustesMuebles", "AcumAjustesMuebSem%d"),
                new Acumulado("Cuentas Activas", "CuentasActivasMuebles", "AcumCtasActMuebSem%d"),
                new Acumulado("Saldo Total", "SaldoTotalMuebles", "AcumSdoTotMuebSem%d"),
                // En el semestre actual el impreso suma el interés adicional al vencido (muestras de sep-2026)
                new Acumulado("Vencido Total", "VencidoTotalMuebles+IntAdicionalDebeMuebles", "AcumVdoTotMuebSem%d"),
                new Acumulado("Interes Adicional", "IntAdicionalDebeMuebles", "AcumIntAdicDebeMuebSem%d"),
                new Acumulado("Num Devoluciones x Cobranza", "DevPorCobranzasMuebles", "AcumDevxCobMuebSem%d"),
                new Acumulado("Num Devoluciones x Tienda", "DevPorTiendaMuebles", "AcumDevxTdaMuebSem%d")),
                "Muebles", "FechaPrimerCompraMuebles", "FechaUltimaCompraMuebles", "FechaUltimaSaldadaMuebles",
                "Cobranza Teórica", new Acumulado("", "CobranzaTeoricaMuebles", "AcumCobTeoricaMuebSem%d"),
                new Acumulado("", "CobranzaRealMuebles", "AcumCobRealMuebSem%d"), "EficienciaCteMuebles", false),
                List.of(new String[]{"Puntualidad del Cliente", f.texto("PuntualidadCliente")},
                        new String[]{"Puntualidad Activa del Cliente", f.texto("PuntualidadActiva")})));

        sb.append(seccionCredito(f, datos, new SeccionCredito("CREDITO ROPA", List.of(
                new Acumulado("Numero de Compras", "NumeroComprasRopa", "NumComprasRopaSem%d"),
                new Acumulado("Acumulado de Compras", "ComprasRopa", "AcumComprasRopaSem%d"),
                new Acumulado("Acumulado de Enganches", "EngancheRopa", "AcumEngRopaSem%d"),
                new Acumulado("Numero de Abonos", "NumeroAbonosRopa", "NumAbonosRopaSem%d"),
                new Acumulado("Acumulado de Abonos", "AbonosRopa", "AcumAbonosRopaSem%d"),
                new Acumulado("Acumulado de Bonificaciones", "BonificacionesRopa", "AcumBonifRopaSem%d"),
                new Acumulado("Acumulado de Int Adic Pagados", "InteresAcumuladoRopa", "AcumIntAdicAcumRopaSem%d"),
                new Acumulado("Acumulado de Ajustes", "AjustesRopa", "AcumAjustesRopaSem%d"),
                new Acumulado("Saldo Total", "SaldoTotalRopa", "AcumSdoTotRopaSem%d"),
                new Acumulado("Vencido Total", "VencidoTotalRopa+IntAdicionalDebeRopa", "AcumVdoTotRopaSem%d"),
                new Acumulado("Interes Adicional", "IntAdicionalDebeRopa", "AcumIntAdicDebeRopaSem%d")),
                "Ropa", "FechaPrimerCompraRopa", "FechaUltimaCompraRopa", "FechaUltimaSaldadaRopa",
                "Cobranza Teorica", new Acumulado("", "CobranzaTeoricaRopa", "AcumCobTeoricaRopaSem%d"),
                new Acumulado("", "CobranzaRealRopa", "AcumCobRealRopaSem%d"), "EficienciaCteRopa", false),
                List.of(new String[]{"Numero de Veces Cuentas Perdidas", f.texto("NumeroVecesCuentasPerdidas")},
                        new String[]{"Ha Sido Cliente Gol", f.entero("NumeroVecesClienteGol") > 0
                                ? f.texto("NumeroVecesClienteGol") : ""})));

        sb.append(seccionCredito(f, datos, new SeccionCredito("CREDITO PRESTAMOS", List.of(
                new Acumulado("Numero de Compras", "NumeroPrestamos", "NumeroPrestamosSem%d"),
                new Acumulado("Acumulado de Compras", "ImportePrestamos", "ImportePrestamosSem%d"),
                new Acumulado("Numero de Abonos", "NumeroAbonosPrestamos", "NumeroAbonosPrestamosSem%d"),
                new Acumulado("Acumulado de Abonos", "AbonosPrestamos", "AbonosPrestamosSem%d"),
                new Acumulado("Acumulado de Bonificaciones", "BonificacionPrestamos", "BonificacionPrestamosSem%d"),
                new Acumulado("Acumulado de Int Adic Pagados", "InteresesPagadosPrestamos", "InteresesPagadosPrestamosSem%d"),
                new Acumulado("Acumulado de Ajustes", "AjustesPrestamos", "AjustesPrestamosSem%d"),
                new Acumulado("Saldo Total", "SaldoTotalPrestamos", "SaldoTotalPrestamosSem%d"),
                new Acumulado("Vencido Total", "VencidoPrestamos", "VencidoPrestamosSem%d"),
                new Acumulado("Interes Adicional", "InteresesDebePrestamos", "InteresesDebePrestamosSem%d")),
                "Prestamos", "FechaDelPrimerPrestamo", "FechaDelUltimoPrestamo", "FechaUltimaSaldadaPrestamos",
                "Cobranza Teorica", new Acumulado("", "CobranzaTeoricaPrestamos", "CobranzaTeoricaPrestamosSem%d"),
                new Acumulado("", "CobranzaRealPrestamos", "CobranzaRealPrestamosSem%d"), "EficienciaCtePrestamos", true),
                List.of()));

        sb.append(seccionCredito(f, datos, new SeccionCredito("CREDITO TIEMPO AIRE", List.of(
                new Acumulado("Numero de Compras", "NumeroComprasTiempoAire", "NumeroComprasTiempoAireSem%d"),
                new Acumulado("Acumulado de Compras", "ComprasTiempoAire", "ComprasTiempoAireSem%d"),
                new Acumulado("Numero de Abonos", "NumeroAbonosTiempoAire", "NumeroAbonosTiempoAireSem%d"),
                new Acumulado("Acumulado de Abonos", "AbonosTiempoAire", "AbonosTiempoAireSem%d"),
                new Acumulado("Acumulado de Bonificaciones", "BonificacionesTiempoAire", "BonificacionesTiempoAireSem%d"),
                new Acumulado("Acumulado de Int Adic Pagados", "InteresAcumuladoTiempoAire", "InteresAcumuladoTiempoAireSem%d"),
                new Acumulado("Acumulado de Ajustes", "AjustesTiempoAire", "AjustesTiempoAireSem%d"),
                new Acumulado("Saldo Total", "SaldoTotalTiempoAire", "SaldoTotalTiempoAireSem%d"),
                new Acumulado("Vencido Total", "VencidoTotalTiempoAire", "VencidoTotalTiempoAireSem%d"),
                new Acumulado("Interes Adicional", "IntAdicionalDebeTiempoAire", "IntAdicionalDebeTiempoAireSem%d")),
                "TiempoAire", "FechaPrimerCompraTiempoAire", "FechaUltimaCompraTiempoAire", "FechaUltimaSaldadaTiempoAire",
                "Cobranza Teorica", new Acumulado("", "CobranzaTeoricaTiempoAire", "CobranzaTeoricaTiempoAireSem%d"),
                new Acumulado("", "CobranzaRealTiempoAire", "CobranzaRealTiempoAireSem%d"), "EficienciaCteTiempoAire", true),
                List.of()));

        sb.append(seccionCredito(f, datos, new SeccionCredito("CREDITO DEUDA BANCOPPEL", List.of(
                new Acumulado("Acumulado de Compras", "ImporteDeudaBancoppel", "AcumComprasDeudaBancoppelSem%d"),
                new Acumulado("Numero de Abonos", "NumeroAbonosDeudaBancoppel", "NumAbonosDeudaBancoppelSem%d"),
                new Acumulado("Acumulado de Abonos", "AbonoDeudaBancoppel", "AcumAbonosDeudaBancoppelSem%d"),
                new Acumulado("Acumulado de Bonificaciones", "BonificacionesDeudaBancoppel", "AcumBonifDeudaBancoppelSem%d"),
                new Acumulado("Acumulado de Int Adic Pagados", "IntAdicionalPagDeudaBancoppel", "AcumIntAdicPagDeudaBancoppelSem%d"),
                new Acumulado("Acumulado de Ajustes", "AjustesDeudaBancoppel", "AcumAjustesDeudaBancoppelSem%d"),
                new Acumulado("Saldo Total", "SaldoTotalDeudaBancoppel", "AcumSdoTotDeudaBancoppelSem%d"),
                new Acumulado("Vencido Total", "VencidoTotalDeudaBancoppel", "AcumVdoTotDeudaBancoppelSem%d"),
                new Acumulado("Interes Adicional", "IntAdicionalDebeDeudaBancoppel", "AcumIntAdicDebeDeudaBancoppelSem%d")),
                "DeudaBancoppel", "FechaPrimerCompraDeudaBancoppel", "FechaUltimaCompraDeudaBancoppel",
                "fechaUltimaSaldadaDeudaBancoppel", "Cobranza Teórica",
                new Acumulado("", "CobranzaTeoricaDeudaBancoppel", "AcumCobTeoricaDeudaBancoppelSem%d"),
                new Acumulado("", "CobranzaRealDeudaBancoppel", "AcumCobRealDeudaBancoppelSem%d"),
                "EficienciaCteDeudaBancoppel", false),
                List.of()));
        return fin(sb);
    }

    private String seccionCredito(Fila f, LocalDate datos, SeccionCredito s, List<String[]> adicionales) {
        String[] semestres = semestres(datos);
        int[] umbrales = {umbral(f, "EficienciaCteA", 85), umbral(f, "EficienciaCteB", 60), umbral(f, "EficienciaCteC", 30)};
        StringBuilder sb = new StringBuilder();
        sb.append("<br><table width='100%' cellspacing='0' cellpadding='1' style='font-size:8pt'>");
        sb.append("<tr><td></td><td colspan='2' align='center' valign='bottom'>SEMESTRE<br>ACTUAL</td>")
                .append("<td colspan='8' align='center' valign='bottom'>S E M E S T R E S &nbsp; A N T E R I O R E S</td></tr>");
        sb.append("<tr><td><b>").append(s.titulo()).append("</b></td>");
        for (String semestre : semestres) {
            sb.append("<td colspan='2' align='center'>").append(semestre).append("</td>");
        }
        sb.append("</tr>").append(regla(11, 1));
        for (Acumulado a : s.acumulados()) {
            sb.append("<tr><td>").append(a.etiqueta()).append("</td>");
            for (long v : valores(f, a)) {
                sb.append("<td align='right'>").append(entero(v)).append("</td><td></td>");
            }
            sb.append("</tr>");
        }
        String suf = s.sufijoFechas();
        sb.append(renglonActual("Fecha de Cuentas Perdidas", fecha(f, "FechaCuentasPerdidas" + suf)));
        sb.append(renglonActual("Saldo de Cuentas Perdidas", entero(f.entero("SaldoCuentasPerdidas" + suf))));
        sb.append(renglonActual("Fecha de Primer Compra", fecha(f, s.primerCompra())));
        sb.append(renglonActual("Fecha de Ultima Compra", fecha(f, s.ultimaCompra())));
        sb.append(renglonActual("Fecha de Ultima Saldada", fecha(f, s.ultimaSaldada())));

        long[] teorica = valores(f, s.teorica());
        long[] real = valores(f, s.real());
        StringBuilder renglonTeorica = new StringBuilder("<tr><td>" + s.etiquetaTeorica() + "</td>");
        StringBuilder renglonReal = new StringBuilder("<tr><td>Cobranza Real</td>");
        for (int i = 0; i < teorica.length; i++) {
            long porcentaje = teorica[i] == 0 ? 0 : Math.round(real[i] * 100.0 / teorica[i]);
            renglonTeorica.append("<td align='right'>").append(entero(teorica[i])).append("</td><td width='45'>&nbsp;&nbsp;")
                    .append(porcentaje).append("%</td>");
            renglonReal.append("<td align='right'>").append(entero(real[i])).append("</td><td align='center'>")
                    .append(real[i] > 0 ? letra(porcentaje, umbrales) : "").append("</td>");
        }
        sb.append(renglonTeorica).append("</tr>").append(renglonReal).append("</tr>");

        long eficiencia = f.entero(s.eficiencia());
        String textoEficiencia = eficiencia < 0 && s.negativoEntreParentesis()
                ? "(" + entero(-eficiencia) + ")" : entero(eficiencia);
        sb.append("<tr><td>Eficiencia en la Cobranza del Cliente</td><td align='right'>").append(textoEficiencia)
                .append("</td><td align='center'>").append(eficiencia >= 0 ? letra(eficiencia, umbrales) : "")
                .append("</td></tr>");
        for (String[] adicional : adicionales) {
            sb.append(renglonActual(adicional[0], adicional[1]));
        }
        return sb.append("</table>").toString();
    }

    /** Semestre actual y los cuatro anteriores (columnas Sem4..Sem1). */
    private static long[] valores(Fila f, Acumulado a) {
        long[] v = new long[5];
        for (String columna : a.actual().split("\\+")) {
            v[0] += f.entero(columna);
        }
        for (int i = 1; i <= 4; i++) {
            v[i] = f.entero(String.format(a.anterior(), 5 - i));
        }
        return v;
    }

    private static String renglonActual(String etiqueta, String valor) {
        return "<tr><td>" + etiqueta + "</td><td align='right'>" + esc(valor) + "</td><td colspan='9'></td></tr>";
    }

    /** JUL-DIC 2026, ENE-JUN 2026, ... a partir de la fecha de los datos. */
    static String[] semestres(LocalDate datos) {
        LocalDate d = datos != null ? datos : LocalDate.now();
        String[] s = new String[5];
        for (int i = 0; i < 5; i++) {
            LocalDate m = d.minusMonths(6L * i);
            s[i] = (m.getMonthValue() <= 6 ? "ENE-JUN " : "JUL-DIC ") + m.getYear();
        }
        return s;
    }

    /** A, B, C o D según los umbrales de tmpQCCredito01 (EficienciaCteA/B/C). */
    static String letra(long porcentaje, int[] umbrales) {
        if (porcentaje >= umbrales[0]) {
            return "A";
        }
        if (porcentaje >= umbrales[1]) {
            return "B";
        }
        return porcentaje >= umbrales[2] ? "C" : "D";
    }

    private static int umbral(Fila f, String columna, int defecto) {
        return f.tiene(columna) ? (int) f.entero(columna) : defecto;
    }

    // ---------- Muebles (spcQCMuebles) ----------

    private String muebles() {
        List<Fila> facturas = filas("Muebles", "NumeroFactura");
        if (facturas.isEmpty()) {
            return sinCuentas("Listado de Clientes de la Cartera de Muebles", "Muebles");
        }
        Fila p = facturas.get(0);
        StringBuilder sb = inicio();
        sb.append(encabezado("Listado de Clientes de la Cartera de Muebles al", fecha(p, "Fecha")));
        sb.append(renglones(fila(dato("Numero Cliente:", p.texto("NumeroCliente") + " - " + p.texto("Digito")),
                negritas(p.texto("NombreCompleto"), 4))));
        for (Fila f : facturas) {
            sb.append("<table width='100%' cellspacing='0' cellpadding='2'>");
            sb.append("<tr><td><b>Numero Factura:</b></td><td colspan='7'>").append(esc(f.texto("NumeroFactura")))
                    .append("&nbsp; ").append(esc(f.texto("DescripcionArticulo"))).append("</td></tr>");
            sb.append(regla(8, 1));
            sb.append(cuatro("Clave Muebles:", f.texto("ClaveyFamilia"), "Numero Tienda:", f.texto("NumeroTienda"),
                    "Importe Venta:", entero(f, "ImporteVenta"), "Fecha de Venta:", fecha(f, "FechaVenta")));
            sb.append(cuatro("Int S/Compra:", entero(f, "InteresSobreCompra"), "Numero de Articulos:",
                    entero(f, "NumeroDeArticulos"), "Plazo Venta:", entero(f, "PlazoVenta"), "Abono Mensual:",
                    entero(f, "AbonoMensual")));
            sb.append(cuatro("Enganche:", entero(f, "Enganche"), "Abonos del Mes:", entero(f, "AbonoDelMes"),
                    "Saldo Mes Ant:", entero(f, "SaldoMesAnterior"), "Saldo a la Fecha:", entero(f, "SaldoALaFecha")));
            // El impreso muestra el vencido con el interés adicional (2,496 + 164 = 2,660 en las muestras)
            sb.append(cuatro("Vencido:", entero(f.entero("Vencido") + f.entero("InteresAdicional")), "Salda Con:",
                    entero(f, "SaldaCon"), "Int Adic Mes Ant:", entero(f, "InteresAdicionalMesAnt"),
                    "Interes Adicional:", entero(f, "InteresAdicional")));
            sb.append(cuatro("Fec Ult Movto:", fecha(f, "FechaUltimoMov"), "Tipo Ult Movto:", f.texto("TipoUltimoMov"),
                    "Imp Ult Movto:", entero(f, "ImporteUltimoMov"), "Fecha Convenio:", fecha(f, "FechaDelConvenio")));
            sb.append(cuatro("Plazo Convenio:", entero(f, "PlazoDelConvenio"), "Impte Convenio:",
                    entero(f, "ImporteDelConvenio"), "Int Cobrado:", entero(f, "InteresCobrado"),
                    "Eficiencia Cobranza Cte:", f.texto("EficienciaCte")));
            sb.append(cuatro("Fec Conv Devol:", fecha(f, "FechaConvenioDevolucion"), "Efectuo Conv:",
                    f.texto("EfectuoConvenio"), "Salda Con 05 Sig Mes:", entero(f, "SaldaCon05SigMes"),
                    "% Bonificacion:", porcentaje(f, "prc_bonificacion05SigMes")));
            sb.append(cuatro("", "", "", "", "Salda Con 20 Sig Mes:", entero(f, "SaldaCon20SigMes"),
                    "% Bonificacion:", porcentaje(f, "prc_bonificacion20SigMes")));
            sb.append(regla(8, 3)).append("</table><br>");
        }
        return fin(sb);
    }

    private static String cuatro(String e1, String v1, String e2, String v2, String e3, String v3, String e4, String v4) {
        return "<tr>" + par(e1, v1) + par(e2, v2) + par(e3, v3) + par(e4, v4) + "</tr>";
    }

    private static String par(String etiqueta, String valor) {
        return "<td><b>" + etiqueta + "</b></td><td align='right'>" + esc(valor) + "&nbsp;&nbsp;</td>";
    }

    private static String parIzquierda(String etiqueta, String valor) {
        return "<td><b>" + etiqueta + "</b></td><td>" + esc(valor) + "</td>";
    }

    // ---------- Ropa (spcQCRopa) ----------

    private String ropa() {
        Fila f = primera("Ropa", "Saldo20MesActual");
        if (f == null) {
            return sinCuentas("Listado de Clientes de la Cartera de Ropa", "Ropa");
        }
        String[][] izquierda = {
                {"Saldo 20 Anterior:", entero(f, "Saldo20MesAnterior")},
                {"Saldo 20 Actual:", entero(f, "Saldo20MesActual")},
                {"Saldo Fecha:", entero(f, "SaldoALaFecha")},
                {"Vencido:", entero(f, "Vencido2")},
                {"Salda Con:", entero(f, "SaldaCon")},
                {"Compras del Mes:", entero(f, "ComprasDelMes")},
                {"Abonos del Mes:", entero(f, "AbonosDelMes")},
                {"Enganches del Mes:", entero(f, "EnganchesDelMes")},
                {"Abono Base:", entero(f, "AbonoBase")},
                {"Minimo Anterior:", entero(f, "MinimoaPagarMesAnt")},
                {"Minimo a Pagar:", entero(f, "MinimoaPagarMesAct")},
                {"Interes Sobre Compras:", entero(f, "InteresSobreCompra")},
                {"Interes Adic Mes Ant:", entero(f, "InteresAdicionalMesAnt")},
                {"Interes Adic Mes Act:", entero(f, "InteresAdicionalMesAct")},
                {"Bonificacion Otorgada:", entero(f, "BonificacionOtorgada")},
                {"Bonificacion Mes 1:", entero(f, "BonificacionMes1")},
                {"Bonificacion Mes 2:", entero(f, "BonificacionMes2")},
                {"Bonificacion Mes 3:", entero(f, "BonificacionMes3")},
                {"Bonificacion Mes 4:", entero(f, "BonificacionMes4")},
                {"Bonificacion Mes 5:", entero(f, "BonificacionMes5")},
                {"Bonificacion Mes 6:", entero(f, "imp_BonificacionMes6")},
                {"Bonificacion Mes 7:", entero(f, "imp_BonificacionMes7")},
                {"Bonificacion Mes 8:", entero(f, "imp_BonificacionMes8")}};
        String[][] derecha = {
                {"Fecha Bonif Mes 2:", fecha(f, "FechaBonificacionMes2"), ""},
                {"Fecha Bonif Mes 3:", fecha(f, "FechaBonificacionMes3"), ""},
                {"Fecha Bonif Mes 4:", fecha(f, "FechaBonificacionMes4"), ""},
                {"Fecha Bonif Mes 5:", fecha(f, "FechaBonificacionMes5"), ""},
                {"Fecha Bonif Mes 6:", fecha(f, "fec_bonificacionmes6"), ""},
                {"Fecha Bonif Mes 7:", fecha(f, "fec_BonificacionMes7"), ""},
                {"Fecha Bonif Mes 8:", fecha(f, "fec_BonificacionMes8"), ""},
                {"Tipo Ultimo Movto:", f.texto("TipoUltimoMov"), ""},
                {"Fecha Ult Movto:", fecha(f, "FechaUltimoMov"), ""},
                {"Importe Ult Movto:", entero(f, "ImporteUltimoMov"), ""},
                {"Fecha del Convenio:", fecha(f, "FechaDelConvenio"), ""},
                {"Plazo Convenio:", entero(f, "PlazoDelConvenio"), ""},
                {"Importe Convenio:", entero(f, "ImporteDelConvenio"), ""},
                {"Efectuo Convenio:", f.texto("EfectuoConvenio"), ""},
                {"Tipo Convenio:", f.texto("TipoDeConvenio"), ""},
                {"Subtipo Convenio:", f.texto("SubtipoDeConvenio"), ""},
                {"Veces del Convenio:", f.texto("VecesRealizoConvenio"), ""},
                {"Fecha Ultima Compra:", fecha(f, "FechaUltimaCompra"), ""},
                {"Convenio Devolucion:", f.texto("MarcaConvDevolucion"), ""},
                {"Interes Cobrado:", entero(f, "InteresCobrado"), "<b>%</b>"},
                {"Fecha Ult Bonificacion:", fecha(f, "FechaUltimaBonificacion"), "<b>Bonificacion</b>"},
                {"Salda Con 05 Sig.Mes :", entero(f, "SaldaCon05SigMes"), porcentaje(f, "proc_bonificacion05")},
                {"Salda Con 20 Sig.Mes :", entero(f, "SaldaCon20SigMes"), porcentaje(f, "proc_bonificacion20")}};

        StringBuilder sb = inicio();
        sb.append(encabezado("Listado de Clientes de la Cartera de Ropa al", fecha(f, "Fecha")));
        sb.append("<table cellspacing='0' cellpadding='1' style='font-size:8pt'>");
        sb.append("<tr><td style='font-size:10pt'><b>Numero Cliente:</b></td><td colspan='3' align='center' "
                        + "style='font-size:10pt'><b>").append(esc(f.texto("NumeroCliente") + " - " + f.texto("Digito")))
                .append("</b></td><td colspan='3' style='font-size:10pt'><b>").append(esc(f.texto("NombreCompleto")))
                .append("</b></td></tr>");
        sb.append("<tr><td><b>Numero Tienda:</b></td><td><u>Cartera</u> ").append(entero(f, "NumeroTienda"))
                .append("</td><td><u>Directorio</u> ").append(entero(f, "TiendaDirectorio"))
                .append("</td><td width='20'></td><td><b>Fecha Bonif Mes 1:</b></td><td align='right'>")
                .append(fecha(f, "FechaBonificacionMes1")).append("</td><td></td></tr>");
        for (int i = 0; i < izquierda.length; i++) {
            sb.append("<tr><td><b>").append(izquierda[i][0]).append("</b></td><td align='right'>")
                    .append(esc(izquierda[i][1])).append("</td><td></td><td></td>");
            sb.append("<td><b>").append(derecha[i][0]).append("</b></td><td align='right'>").append(esc(derecha[i][1]))
                    .append("</td><td align='center'>&nbsp;&nbsp;").append(derecha[i][2]).append("</td></tr>");
        }
        return fin(sb.append("</table>"));
    }

    // ---------- Dinero electrónico (spcQCDineroElectronico → tmpDineroElectronico3) ----------

    private String dineroElectronico() {
        Fila f = primera("Dinero electrónico", "SaldoMesAnterior");
        if (f == null) {
            return sinDatos("Listado de Clientes de la Cartera de Dinero Electronico");
        }
        StringBuilder sb = inicio();
        sb.append(encabezado("Listado de Clientes de la Cartera de Dinero Electronico al", fechaUs(f, "fechacorte")));
        sb.append(renglones(fila(dato("Numero Cliente:", f.texto("NumeroCliente") + "  -  " + f.texto("Digito")))));
        sb.append("<div style='font-size:12pt'>").append(esc(f.texto("NombreCompleto"))).append("</div>");
        sb.append(linea(1));
        String puntuales = f.texto("Desc_Corte").isEmpty() ? "" : esc(f.texto("Desc_Corte")) + " &nbsp; ";
        sb.append("<table width='100%' cellspacing='0' cellpadding='4'>");
        sb.append("<tr>").append(parIzquierda("Fecha Alta:", fechaUs(f, "FechaAlta")))
                .append(parIzquierda("Saldo a la Fecha:", decimales(f, "SaldoALaFecha")))
                .append(parIzquierda("Vigencia:", fechaUs(f, "Vigencia"))).append("</tr>");
        sb.append("<tr>").append(parIzquierda("Fec Ult Movto:", fechaUs(f, "fechaultmov")))
                .append(parIzquierda("Saldo Mes Anterior:", decimales(f, "SaldoMesAnterior")))
                .append(parIzquierda("Puntualidad:", f.texto("Puntualidad"))).append("</tr>");
        sb.append("<tr>").append(parIzquierda("Canjes del Mes:", entero(f, "ComprasdelMes")))
                .append("<td colspan='2' align='center'><b>").append(esc(f.texto("Desc_LeyendaCC")))
                .append("</b></td><td></td><td></td></tr>");
        sb.append("<tr>").append(parIzquierda("Emisiones del Mes:", entero(f, "AbonosdelMes")))
                .append("<td colspan='2' align='center'>").append(puntuales).append("$")
                .append(entero(f, "imp_abonoscorte")).append("</td><td></td><td></td></tr>");
        sb.append(regla(6, 3)).append("</table>");
        return fin(sb);
    }

    // ---------- Cartera informativa (proc_carterainformativa → tmp_*) ----------

    private String informativa() {
        Fila f = primera("Cartera informativa", "CalleLocalizar");
        List<Fila> situaciones = filas("Cartera informativa", "Clv_situacion");
        if (f == null && situaciones.isEmpty()) {
            return sinDatos("CARTERA INFORMATIVA CLIENTES COPPEL");
        }
        Fila d = f != null ? f : situaciones.get(0);
        LocalDate datos = d.fecha("Fecha") != null ? d.fecha("Fecha") : exp.fechaDatos();
        StringBuilder sb = inicio();
        sb.append(encabezado("CARTERA INFORMATIVA CLIENTES COPPEL", datos == null ? "" : datos.format(MES_DIA_ANIO)));
        String cliente = f != null ? f.texto("NumeroCliente") + "-" + f.texto("Digito")
                : exp.cliente().numeroCliente() + "-" + exp.cliente().digito();
        String nombre = f != null ? f.texto("NombreCompleto") : exp.cliente().nombre();
        sb.append("<p><b>Numero Cliente:</b> ").append(esc(cliente)).append("</p><p><b>Nombre:</b> ")
                .append(esc(nombre)).append("</p>").append(linea(2));

        Fila l = f != null ? f : new Fila(List.of(), List.of());
        sb.append(banda("DATOS LOCALIZAR"));
        sb.append("<b><u>CLIENTE</u></b><br><b>Direccion:</b> ")
                .append(esc(direccion(l, "CalleLocalizar", "CasaLocalizar", "ZonaLocalizar", "CiudadLocalizar",
                        "ComplementoLocalizar")))
                .append("<br><b>Telefono:</b> ").append(esc(telefono(l, "TelefonoLocalizar"))).append(linea(1));
        sb.append("<b><u>REFERENCIA:</u></b><br><b>Nombre:</b> ").append(esc(l.texto("NombreReferencia")))
                .append("<br><b>Direccion:</b> ")
                .append(esc(direccion(l, "CalleReferencia", "CasaReferencia", "ZonaReferencia", "CiudadReferencia",
                        "ComplementoReferencia")))
                .append("<br><b>Telefono:</b> ").append(esc(telefono(l, "TelefonoReferencia"))).append(linea(1));
        sb.append("<b><u>TRABAJO</u></b><br><b>Direccion:</b> ")
                .append(esc(direccion(l, "CalleTrabajo", "CasaTrabajo", "ZonaTrabajo", "CiudadTrabajo",
                        "ComplementoTrabajo")))
                .append("<br><b>Telefono:</b> ").append(esc(telefono(l, "TelefonoTrabajo")));

        sb.append(banda("DATOS DE REFERENCIAS"));
        List<Fila> referencias = filas("Cartera informativa", "CasaDeReferencia");
        if (referencias.isEmpty()) {
            referencias = List.of(new Fila(List.of(), List.of()));
        }
        for (Fila r : referencias) {
            sb.append("<b>Nombre:</b> ").append(esc(r.texto("NombreReferencia"))).append("<br><b>Dirección:</b> ")
                    .append(esc(r.texto("NombreCalle") + " #" + numeroCasa(r, "CasaDeReferencia") + ", "
                            + r.texto("NombreZona") + ", " + r.texto("NombreCiudad")))
                    .append("<br><b>Telefono</b>: ").append(esc(telefono(r, "TelefonoReferencia")))
                    .append(" <b>Celular</b>: ").append(esc(telefono(r, "TelefonoCelular"))).append("<br>");
        }

        sb.append(banda("SITUACIONES ESPECIALES"));
        sb.append("<table cellspacing='0' cellpadding='1'><tr><th><u># Situacion</u></th><th><u>Situacion</u></th>")
                .append("<th><u>Causa</u></th><th><u>Fecha Marcado</u></th><th align='left'><u>Descripcion</u></th></tr>");
        for (Fila s : situaciones) {
            sb.append("<tr><td align='center'>").append(esc(s.texto("idu_situacion"))).append("</td><td align='center'>")
                    .append(esc(s.texto("Clv_situacion"))).append("</td><td align='center'>")
                    .append(esc(s.texto("num_causasituacion"))).append("</td><td align='center'>")
                    .append(fechaUs(s, "fec_marcado")).append("</td><td>&nbsp;&nbsp;")
                    .append(esc(s.texto("Nom_descsituacion"))).append("</td></tr>");
        }
        sb.append("</table>");

        sb.append(banda("TELEFONOS DEL CLIENTE"));
        sb.append("<table cellspacing='0' cellpadding='1' style='font-family:SansSerif;font-size:8pt'>")
                .append("<tr><th align='left'><u>Telefono</u></th><th width='40'></th>")
                .append("<th align='left'><u>Tipo de Telefono</u></th></tr>");
        for (Fila t : filas("Cartera informativa", "tipotelefono")) {
            int tipo = (int) t.entero("tipotelefono");
            sb.append("<tr><td>").append(esc(t.texto("numerotelefono"))).append("</td><td></td><td>")
                    .append(esc(TIPOS_TELEFONO.getOrDefault(tipo, "Tipo " + tipo))).append("</td></tr>");
        }
        return fin(sb.append("</table>"));
    }

    /** "calle #casa, colonia, ciudad, complemento" como en el impreso ("#, , , " cuando no hay datos). */
    private static String direccion(Fila f, String calle, String casa, String zona, String ciudad, String complemento) {
        return f.texto(calle) + " #" + numeroCasa(f, casa) + ", " + f.texto(zona) + ", " + f.texto(ciudad) + ", "
                + f.texto(complemento);
    }

    private static String numeroCasa(Fila f, String columna) {
        return f.entero(columna) == 0 ? "" : f.texto(columna);
    }

    private static String telefono(Fila f, String columna) {
        return f.entero(columna) == 0 ? "" : f.texto(columna);
    }

    // ---------- Marcas de contacto (proc_clientenocontactado → tmp_clientenocontactado) ----------

    private String marcas() {
        Fila f = primera("Marcas de contacto", "clv_3MSC");
        if (f == null) {
            return sinDatos("Marcas de Contacto Estado De Cuenta Coppel");
        }
        StringBuilder sb = inicio();
        sb.append(encabezado("Marcas de Contacto Estado De Cuenta Coppel &nbsp;al", fechaUs(f, "fec_corte")));
        sb.append(renglones(fila(dato("Numero Cliente:", "<b>" + esc(f.texto("num_cliente")) + "  -  "
                + esc(f.texto("num_digito")) + "</b>"))));
        sb.append("<b>").append(esc(f.texto("nom_cliente"))).append("</b>").append(linea(1));
        sb.append("<p>");
        for (String marca : exp.marcasContacto()) {
            sb.append("<b>- ").append(esc(marca)).append("</b><br>");
        }
        sb.append("<br></p>").append(linea(3));
        return fin(sb);
    }

    // ---------- Otras carteras (sin muestra impresa) ----------

    private String generica(String cartera, List<Fila> filas) {
        StringBuilder sb = inicio();
        Fila p = filas.get(0);
        sb.append(encabezado("Listado de Clientes de la Cartera de " + cartera + " al", fecha(p, "Fecha")));
        sb.append("<p style='color:").append(c.tenue()).append("'>No hay muestra impresa de este listado: ")
                .append("se presentan las columnas del procedimiento.</p>");
        for (Fila f : filas) {
            sb.append("<table width='100%' cellspacing='0' cellpadding='2'>");
            List<String> columnas = f.columnas();
            for (int i = 0; i < columnas.size(); i += 4) {
                sb.append("<tr>");
                for (int j = i; j < i + 4; j++) {
                    if (j < columnas.size()) {
                        Object v = f.valor(columnas.get(j));
                        String texto = f.fecha(columnas.get(j)) != null ? fecha(f, columnas.get(j))
                                : v instanceof Number && !(v instanceof BigDecimal) ? entero(f, columnas.get(j))
                                : Fila.aTexto(v);
                        sb.append(par(esc(columnas.get(j)) + ":", texto));
                    } else {
                        sb.append("<td></td><td></td>");
                    }
                }
                sb.append("</tr>");
            }
            sb.append(regla(8, 3)).append("</table><br>");
        }
        return fin(sb);
    }

    // ---------- Piezas comunes ----------

    private StringBuilder inicio() {
        return new StringBuilder("<html><body style='font-family:Serif;font-size:9pt;margin:12px;background-color:")
                .append(c.fondo()).append(";color:").append(c.texto()).append("'>");
    }

    private static String fin(StringBuilder sb) {
        return sb.append("</body></html>").toString();
    }

    /** Título con la fecha de impresión, "Actualizados al" con la de los datos, y la franja amarilla. */
    private String encabezado(String titulo, String actualizados) {
        return "<table width='100%' cellspacing='0' cellpadding='0' style='font-size:12pt'><tr><td><b>" + titulo
                + "</b></td><td align='right'>" + hoy.format(DIA_MES_ANIO) + " &nbsp;<b>Actualizados al</b> &nbsp;"
                + actualizados + "</td></tr></table>"
                + "<table width='100%' cellspacing='0' cellpadding='0'><tr><td bgcolor='" + c.amarillo()
                + "' style='font-family:SansSerif;font-size:7pt;color:" + c.logo() + "'>&nbsp;Coppel.</td></tr></table>";
    }

    private String sinDatos(String titulo) {
        StringBuilder sb = inicio();
        sb.append(encabezado(titulo + " al", fecha(exp.fechaDatos())));
        sb.append("<p style='color:").append(c.tenue()).append("'>El procedimiento no devolvió este listado ")
                .append("(ver Avisos y Datos crudos).</p>");
        return fin(sb);
    }

    private String sinCuentas(String titulo, String cartera) {
        StringBuilder sb = inicio();
        sb.append(encabezado(titulo + " al", fecha(exp.fechaDatos())));
        sb.append("<p style='color:").append(c.tenue()).append("'>El cliente no tiene cuentas de ")
                .append(cartera.toLowerCase(Locale.ROOT)).append(".</p>");
        return fin(sb);
    }

    private String subtitulo(String texto) {
        return "<p style='font-size:10pt;margin-top:8px'><b><u>" + texto + "</u></b></p>";
    }

    private String banda(String texto) {
        return "<table width='100%' cellspacing='0' cellpadding='0' style='margin-top:6px'><tr><td bgcolor='"
                + c.banda() + "'><b>" + texto + "</b></td></tr></table>";
    }

    private String linea(int grosor) {
        return "<table width='100%' cellspacing='0' cellpadding='0'>" + regla(1, grosor) + "</table>";
    }

    /** Renglón que dibuja una línea horizontal del ancho de la tabla. */
    private String regla(int columnas, int grosor) {
        return "<tr><td colspan='" + columnas + "' bgcolor='" + c.linea() + "' style='font-size:" + grosor
                + "px'>&nbsp;</td></tr>";
    }

    /** Renglones de "etiqueta: valor" que fluyen como en el directorio. */
    private static String renglones(String... filas) {
        return "<table cellspacing='0' cellpadding='3'>" + String.join("", filas) + "</table>";
    }

    private static String fila(String... datos) {
        return "<tr><td>" + String.join("&nbsp;&nbsp;&nbsp;&nbsp;", datos) + "</td></tr>";
    }

    private static String dato(String etiqueta, String valor) {
        String v = valor.startsWith("<b>") ? valor : esc(valor);
        return "<b>" + etiqueta + "</b>&nbsp; " + v;
    }

    private static String etiqueta(String texto) {
        return "<b>" + esc(texto) + "</b>";
    }

    private static String negritas(String texto, int espacios) {
        return "&nbsp;".repeat(espacios) + "<b>" + esc(texto) + "</b>";
    }

    private static String siNo(long bandera) {
        return bandera != 0 ? "SI" : "NO";
    }

    private Fila primera(String listado, String columna) {
        List<Fila> filas = filas(listado, columna);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /**
     * Filas del primer resultado del listado (por título, "Muebles", "Muebles (2)"...) que tenga la columna;
     * con columna null, del primer resultado con filas.
     */
    private List<Fila> filas(String listado, String columna) {
        for (TablaListado t : exp.listados()) {
            boolean esDelListado = t.nombre().equals(listado) || t.nombre().startsWith(listado + " (");
            if (!esDelListado || t.error() != null || t.filas().isEmpty()) {
                continue;
            }
            if (columna == null || t.columnas().stream().anyMatch(col -> col.equalsIgnoreCase(columna))) {
                return t.filas().stream().map(valores -> new Fila(t.columnas(), valores)).toList();
            }
        }
        return List.of();
    }

    private static String entero(Fila f, String columna) {
        return entero(f.entero(columna));
    }

    private static String entero(long valor) {
        return ENTERO.get().format(valor);
    }

    private static String decimales(Fila f, String columna) {
        return DOS_DECIMALES.get().format(f.decimal(columna));
    }

    /** Porcentaje ya expresado en cientos (prc_bonificacion 2.46 → 2%). */
    private static String porcentaje(Fila f, String columna) {
        return Math.round(f.decimal(columna)) + "%";
    }

    private static String fecha(Fila f, String columna) {
        return fecha(f.fecha(columna));
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(DIA_MES_ANIO);
    }

    private static String fechaUs(Fila f, String columna) {
        LocalDate d = f.fecha(columna);
        return d == null ? "" : d.format(MES_DIA_ANIO);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Una fila de un procedimiento con búsqueda de columnas sin distinguir mayúsculas. */
    static final class Fila {
        private final List<String> columnas;
        private final Map<String, Object> valores = new HashMap<>();

        Fila(List<String> columnas, List<Object> fila) {
            this.columnas = columnas;
            for (int i = 0; i < columnas.size() && i < fila.size(); i++) {
                valores.putIfAbsent(columnas.get(i).toLowerCase(Locale.ROOT), fila.get(i));
            }
        }

        List<String> columnas() {
            return columnas;
        }

        boolean tiene(String columna) {
            return valores.containsKey(columna.toLowerCase(Locale.ROOT));
        }

        Object valor(String columna) {
            return valores.get(columna.toLowerCase(Locale.ROOT));
        }

        String texto(String columna) {
            return aTexto(valor(columna));
        }

        long entero(String columna) {
            return Math.round(decimal(columna));
        }

        double decimal(String columna) {
            Object v = valor(columna);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return v == null ? 0 : Double.parseDouble(v.toString().replace(",", "").trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        LocalDate fecha(String columna) {
            Object v = valor(columna);
            if (v instanceof java.sql.Timestamp t) {
                return t.toLocalDateTime().toLocalDate();
            }
            if (v instanceof java.sql.Date d) {
                return d.toLocalDate();
            }
            if (v instanceof LocalDateTime d) {
                return d.toLocalDate();
            }
            return v instanceof LocalDate d ? d : null;
        }

        static String aTexto(Object v) {
            if (v == null) {
                return "";
            }
            if (v instanceof BigDecimal bd) {
                return bd.stripTrailingZeros().toPlainString();
            }
            return v.toString().trim();
        }
    }
}
