package com.coppel.validador.validacion;

/**
 * Un punto que la persona de Calidad corrobora contra la muestra impresa.
 * El sistema propone el valor esperado; el resultado y la observación los captura la persona.
 *
 * @param clave  número del punto en el manual ("[6]") o "QR", "Marca", etc.
 * @param regla  de dónde sale el valor esperado
 * @param visual true si no hay dato contra qué compararlo y solo se revisa a la vista
 */
public final class PuntoRevision {

    public enum Resultado {
        PENDIENTE("Pendiente"), OK("Correcto"), ERROR("Error"), NO_APLICA("No aplica");

        private final String texto;

        Resultado(String texto) {
            this.texto = texto;
        }

        @Override
        public String toString() {
            return texto;
        }
    }

    private final String clave;
    private final String seccion;
    private final String descripcion;
    private final String esperado;
    private final String regla;
    private final boolean visual;
    private Resultado resultado = Resultado.PENDIENTE;
    private String observacion = "";

    public PuntoRevision(String clave, String seccion, String descripcion, String esperado, String regla,
                         boolean visual) {
        this.clave = clave;
        this.seccion = seccion;
        this.descripcion = descripcion;
        this.esperado = esperado == null || esperado.isBlank() ? "(vacío)" : esperado;
        this.regla = regla == null ? "" : regla;
        this.visual = visual;
    }

    public String clave() {
        return clave;
    }

    public String seccion() {
        return seccion;
    }

    public String descripcion() {
        return descripcion;
    }

    public String esperado() {
        return esperado;
    }

    public String regla() {
        return regla;
    }

    public boolean visual() {
        return visual;
    }

    public Resultado resultado() {
        return resultado;
    }

    public void resultado(Resultado resultado) {
        this.resultado = resultado;
    }

    public String observacion() {
        return observacion;
    }

    public void observacion(String observacion) {
        this.observacion = observacion == null ? "" : observacion;
    }
}
