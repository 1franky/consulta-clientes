package com.coppel.validador.model;

import java.time.LocalDate;

/** Datos generales del cliente (listado del directorio y de crédito). */
public record Cliente(
        long numeroCliente,
        String digito,
        String nombre,
        String calle,
        String numeroCasa,
        String departamento,
        String entreCalles,
        String rumbo,
        String complemento,
        String telefono,
        String colonia,
        String ciudad,
        String inicialCiudad,
        String municipio,
        String zona,
        String lugarTrabajo,
        String conyuge,
        String puntualidad,
        String puntualidadActiva,
        String estadoCivil,
        String tipoCasa,
        String sexo,
        String ingresoMensual,
        LocalDate antiguedad,
        LocalDate fechaNacimiento,
        String jefeGrupo,
        String supervisor,
        String centro,
        long numeroTienda,
        String situacionEspecial,
        String causaSituacion,
        boolean domicilioCertificado,
        boolean domiciliado,
        boolean noEnviarEdoCta,
        Referencia referencia) {

    /** Persona de referencia del cliente (punto [4]). */
    public record Referencia(String nombre, String calle, String numeroCasa, String zona, String ciudad,
                             String telefono, String complemento) {
    }
}
