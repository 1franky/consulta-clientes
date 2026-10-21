package com.coppel.validador.model;

import java.time.LocalDate;

/**
 * Un renglón del catálogo de bonificaciones: porcentaje (como fracción) para una cartera, plazo y
 * días transcurridos, vigente para compras desde una fecha (fec_iniciobloque).
 */
public record FactorBonificacion(String cartera, int plazo, int dias, double factor, LocalDate vigenteDesde) {
}
