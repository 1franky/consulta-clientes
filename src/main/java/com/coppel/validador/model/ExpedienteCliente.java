package com.coppel.validador.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que se consultó de un cliente para validar su estado de cuenta: el equivalente
 * al "listado de las cuentas del cliente" que operación pasa a Control de Calidad.
 *
 * @param listados las tablas tal como las devolvieron los procedimientos, para revisión manual
 * @param avisos   problemas detectados al consultar (procedimiento con error, columna no encontrada...)
 * @param situacionesImpresas situaciones que la emisión imprime junto al centro (Mae_ClientesSituaciones filtrada
 *                            por Ctl_SubprocesosSituaciones), o null si el origen no las consulta (modo demo)
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
        List<String> situacionesImpresas) {

    public ExpedienteCliente(LocalDate fechaCorte, Cliente cliente, List<Cuenta> cuentas,
                             DineroElectronico dineroElectronico, List<SituacionEspecial> situacionesEspeciales,
                             List<String> marcasContacto, List<TablaListado> listados, List<String> avisos) {
        this(fechaCorte, cliente, cuentas, dineroElectronico, situacionesEspeciales, marcasContacto, listados, avisos,
                null);
    }

    public record DineroElectronico(long saldo, int abonosPuntuales) {
        public static final DineroElectronico VACIO = new DineroElectronico(0, 0);
    }

    public record SituacionEspecial(String situacion, String causa, LocalDate fecha, String descripcion) {
    }
}
