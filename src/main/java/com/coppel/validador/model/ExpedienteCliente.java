package com.coppel.validador.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Todo lo que se consultó de un cliente para validar su estado de cuenta: el equivalente
 * al "listado de las cuentas del cliente" que operación pasa a Control de Calidad.
 *
 * @param listados las tablas tal como las devolvieron los procedimientos, para revisión manual
 * @param avisos   problemas detectados al consultar (procedimiento con error, columna no encontrada...)
 * @param situacionesImpresas situaciones que la emisión imprime junto al centro (Mae_ClientesSituaciones filtrada
 *                            por Ctl_SubprocesosSituaciones), o null si el origen no las consulta (modo demo)
 * @param abonosYMesesVencidos abonos vencidos + meses vencidos por cuenta (llave {@link #llave}), como los calcula
 *                             proc_clientenocontactado; vacío si el origen no los consulta
 * @param fechaDatos           catFechas: fecha de los datos de los listados, o null si no se conoce
 * @param contactoAlCorte      datos para rehacer las marcas de contacto al corte, o null si el origen no los consulta
 */
public record ExpedienteCliente(
        LocalDate fechaCorte,
        Cliente cliente,
        List<Cuenta> cuentas,
        DineroElectronico dineroElectronico,
        List<SituacionEspecial> situacionesEspeciales,
        List<String> marcasContacto,
        List<TablaListado> listados,
        List<String> avisos,
        List<String> situacionesImpresas,
        Map<String, Integer> abonosYMesesVencidos,
        LocalDate fechaDatos,
        ContactoAlCorte contactoAlCorte) {

    public ExpedienteCliente(LocalDate fechaCorte, Cliente cliente, List<Cuenta> cuentas,
                             DineroElectronico dineroElectronico, List<SituacionEspecial> situacionesEspeciales,
                             List<String> marcasContacto, List<TablaListado> listados, List<String> avisos) {
        this(fechaCorte, cliente, cuentas, dineroElectronico, situacionesEspeciales, marcasContacto, listados, avisos,
                null, Map.of(), null, null);
    }

    public ExpedienteCliente(LocalDate fechaCorte, Cliente cliente, List<Cuenta> cuentas,
                             DineroElectronico dineroElectronico, List<SituacionEspecial> situacionesEspeciales,
                             List<String> marcasContacto, List<TablaListado> listados, List<String> avisos,
                             List<String> situacionesImpresas, Map<String, Integer> abonosYMesesVencidos) {
        this(fechaCorte, cliente, cuentas, dineroElectronico, situacionesEspeciales, marcasContacto, listados, avisos,
                situacionesImpresas, abonosYMesesVencidos, null, null);
    }

    /** Llave de abonosYMesesVencidos: cartera y factura (0 en ropa y tiempo aire, que no tienen factura). */
    public static String llave(TipoCartera tipo, long factura) {
        return tipo.name() + ":" + factura;
    }

    /**
     * @param saldoMesAnterior saldo al cierre del mes anterior (SaldoMesAnterior), o null si el origen no lo trae
     */
    public record DineroElectronico(long saldo, int abonosPuntuales, Long saldoMesAnterior) {
        public static final DineroElectronico VACIO = new DineroElectronico(0, 0);

        public DineroElectronico(long saldo, int abonosPuntuales) {
            this(saldo, abonosPuntuales, null);
        }
    }

    /**
     * Lo que proc_clientenocontactado revisa para las marcas, sin lo que pasó después del corte.
     *
     * @param ultimoMovimiento    FechaUltimoMov o FechaDelConvenio más reciente hasta el corte (vigentes y saldadas)
     * @param movimientoPosterior alguna cuenta tiene FechaUltimoMov después del corte: la fecha anterior se perdió
     * @param situacionAbogado    situación de abogado (área 5, proceso 90) marcada hasta el corte
     * @param conflicto           FlagClienteConflicto
     */
    public record ContactoAlCorte(LocalDate ultimoMovimiento, boolean movimientoPosterior, boolean situacionAbogado,
                                  boolean telefonoVerificado, boolean correoConfirmado, boolean conflicto) {
    }

    public record SituacionEspecial(String situacion, String causa, LocalDate fecha, String descripcion) {
    }
}
