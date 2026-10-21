package com.coppel.validador.service;

import com.coppel.validador.model.ExpedienteCliente;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Acceso a los datos del cliente para validar su estado de cuenta.
 * Es la única puerta hacia la base de datos: la interfaz gráfica y las reglas no hacen SQL.
 */
public interface EstadoCuentaService {

    /** Ejecuta los procedimientos de los listados y arma el expediente del cliente. */
    ExpedienteCliente consultar(long numeroCliente, LocalDate fechaCorte) throws Exception;

    /**
     * Fecha a la que están los datos de la base (catFechas). Los procedimientos calculan con ella, no con la
     * fecha de corte que reciben. Vacío si el origen no la tiene (modo demo): entonces la captura la persona.
     */
    Optional<LocalDate> fechaDatos() throws Exception;

    /** Texto corto para la barra de estado (base de datos o modo demo). */
    String origen();
}
