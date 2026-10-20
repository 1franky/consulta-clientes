package com.coppel.validador.model;

import java.util.List;

/** Resultado crudo de un procedimiento: se muestra tal cual en la pestaña "Listados". */
public record TablaListado(String nombre, List<String> columnas, List<List<Object>> filas, String error) {

    public static TablaListado conError(String nombre, String error) {
        return new TablaListado(nombre, List.of("Error"), List.of(List.of(error)), error);
    }
}
