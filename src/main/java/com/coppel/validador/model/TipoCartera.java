package com.coppel.validador.model;

/**
 * Carteras que pueden aparecer en el estado de cuenta.
 *
 * - claveCatalogo: llave en bonificaciones.csv (null si la cartera no usa el catálogo).
 * - letraInteres: letra del recuadro de interés adicional [20] (IR$, IM$, IB$...).
 */
public enum TipoCartera {
    ROPA("Ropa", "ROPA", 'R'),
    MUEBLES("Muebles", "MUEBLES", 'M'),
    PRESTAMOS("Préstamos", "PRESTAMOS", 'P'),
    TIEMPO_AIRE("Tiempo aire", null, 'T'),
    DEUDA_BANCOPPEL("Deuda Bancoppel", null, 'B'),
    REESTRUCTURA("Reestructura", null, 'E');

    private final String nombre;
    private final String claveCatalogo;
    private final char letraInteres;

    TipoCartera(String nombre, String claveCatalogo, char letraInteres) {
        this.nombre = nombre;
        this.claveCatalogo = claveCatalogo;
        this.letraInteres = letraInteres;
    }

    public String nombre() {
        return nombre;
    }

    public String claveCatalogo() {
        return claveCatalogo;
    }

    public char letraInteres() {
        return letraInteres;
    }
}
