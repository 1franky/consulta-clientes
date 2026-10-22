package com.coppel.validador.model;

/**
 * Un renglón de catLeyendasCorreos: la emisión elige la leyenda por letra (puntualidad, W, S o X), número y clave.
 *
 * @param letra   PuntualidadCliente: A, B, C, D, N, Z; W leyendas especiales, S recoger/definir, X ultimátum
 * @param numero  AbonosMesesVencidos (o número de leyenda especial)
 * @param clave   ClaveLeyenda: "1" o "2" en las leyendas de 0 y 1 abonos vencidos, vacía en las demás
 * @param linea1  Leyenda1 (62 caracteres)
 * @param linea2  Leyenda2 (62 caracteres)
 */
public record LeyendaCorreo(String letra, int numero, String clave, String linea1, String linea2) {

    /** Las dos líneas como se leen en el estado de cuenta. */
    public String texto() {
        return (linea1.strip() + " " + linea2.strip()).strip();
    }
}
