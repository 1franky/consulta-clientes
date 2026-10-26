package com.coppel.validador.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarpetaMuestrasTest {

    @Test
    void buscaPdfEImagenesEnSubcarpetasOrdenadosPorRuta(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("10000002"));
        Files.createDirectories(dir.resolve("10000001"));
        Files.createFile(dir.resolve("10000002/10000002_Credito.pdf"));
        Files.createFile(dir.resolve("10000001/10000001_Muebles.PDF"));
        Files.createFile(dir.resolve("10000001/notas.txt"));
        Files.createFile(dir.resolve("Estado de cuenta.pdf"));
        Files.createFile(dir.resolve("escaneo.jpg"));

        List<String> nombres = CarpetaMuestras.buscar(dir).stream().map(CarpetaMuestras.Muestra::nombre).toList();
        assertEquals(List.of("10000001/10000001_Muebles.PDF", "10000002/10000002_Credito.pdf", "escaneo.jpg",
                "Estado de cuenta.pdf"), nombres);
    }
}
