package com.coppel.validador.validacion;

import com.coppel.validador.model.LeyendaCorreo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * catLeyendasCorreos: textos de la leyenda de "AVISO IMPORTANTE" por letra, número y clave.
 * Sin base (modo demo) queda vacío y el validador solo indica qué renglón del catálogo corresponde.
 */
public final class CatalogoLeyendas {

    public static final CatalogoLeyendas VACIO = new CatalogoLeyendas(List.of(), "sin catálogo de leyendas");

    private final Map<String, LeyendaCorreo> leyendas = new HashMap<>();
    private final String origen;

    public CatalogoLeyendas(List<LeyendaCorreo> renglones, String origen) {
        for (LeyendaCorreo l : renglones) {
            leyendas.putIfAbsent(llave(l.letra(), l.numero(), l.clave()), l);
        }
        this.origen = origen;
    }

    public String origen() {
        return origen;
    }

    public boolean vacio() {
        return leyendas.isEmpty();
    }

    /** El renglón con esa clave; si no existe, el de clave vacía (las leyendas de 2 o más abonos no llevan clave). */
    public Optional<LeyendaCorreo> buscar(String letra, int numero, String clave) {
        LeyendaCorreo l = leyendas.get(llave(letra, numero, clave));
        if (l == null) {
            l = leyendas.get(llave(letra, numero, ""));
        }
        return Optional.ofNullable(l);
    }

    private static String llave(String letra, int numero, String clave) {
        return letra.strip().toUpperCase() + "|" + numero + "|" + clave.strip();
    }
}
