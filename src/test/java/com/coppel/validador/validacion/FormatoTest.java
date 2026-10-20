package com.coppel.validador.validacion;

import com.coppel.validador.model.Cliente;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Domicilio como se imprime; las reglas salen de las muestras, los datos son ficticios. */
class FormatoTest {

    @Test
    void domicilioCortoVaEnUnRenglonMasColoniaYMunicipio() {
        Cliente c = cliente("PRINCIPAL", "1", "EXT", "N", "", "A UN LADO DE LA ESC RUMBO A", "CENTRO", "PUEBLO");
        assertEquals(List.of("PRINCIPAL # 1 EXT N  A UN LADO DE LA ESC RUMBO A", "CENTRO PUEBLO"),
                Formato.domicilio(c));
    }

    @Test
    void partesVaciasConservanSuEspacio() {
        Cliente c = cliente("SANTA ROSA", "110", "", "", "loc santa rosa", "BGV", "COLONIA", "MUNICIPIO");
        assertEquals("SANTA ROSA # 110   loc santa rosa BGV", Formato.domicilio(c).get(0));
    }

    @Test
    void domicilioLargoSeCortaEnElUltimoEspacioAntesDe50() {
        // 50 caracteres justos: "... MORELOS, CASARUSTICA" también se corta antes de CASARUSTICA
        Cliente c = cliente("SANTA ROSA", "110", "0", "", "PROMOCION MORELOS, CASARUSTICA",
                "LOC. SANTA ROSA DE AMAPA", "STA ROSA", "MUNICIPIO");
        assertEquals(List.of("SANTA ROSA # 110 0  PROMOCION MORELOS,", "CASARUSTICA LOC. SANTA ROSA DE AMAPA",
                "STA ROSA MUNICIPIO"), Formato.domicilio(c));
    }

    @Test
    void elComplementoNoImprimeTelefonos() {
        Cliente c = cliente("JUAREZ", "5", "EXT", "", "", "KSA AZUL 9240000000 COL CENTRO", "CENTRO", "PUEBLO");
        assertEquals("JUAREZ # 5 EXT   KSA AZUL COL CENTRO", Formato.domicilio(c).get(0));
    }

    private static Cliente cliente(String calle, String casa, String depto, String rumbo, String entreCalles,
                                   String complemento, String colonia, String municipio) {
        return new Cliente(1, "0", "NOMBRE", calle, casa, depto, entreCalles, rumbo, complemento, "", colonia,
                "CIUDAD", "CDAD", municipio, "1-1", "", "", "A", "A", "S", "P", "F", "1", null, null, "", "", "",
                0, "", "", false, false, false, new Cliente.Referencia("", "", "", "", "", "", ""));
    }
}
