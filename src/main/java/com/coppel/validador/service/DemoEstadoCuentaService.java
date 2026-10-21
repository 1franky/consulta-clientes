package com.coppel.validador.service;

import com.coppel.validador.model.Cliente;
import com.coppel.validador.model.Cuenta;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.ExpedienteCliente.DineroElectronico;
import com.coppel.validador.model.ExpedienteCliente.SituacionEspecial;
import com.coppel.validador.model.TablaListado;
import com.coppel.validador.model.TipoCartera;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Datos de ejemplo sin base de datos (modo=demo en config.properties) para probar la pantalla y las reglas.
 * Los nombres, números y domicilios son FICTICIOS; los importes y fechas de las cuentas reproducen dos
 * estados de cuenta reales de las muestras (corte al 20-Sep-2026), para que el cálculo se pueda comparar:
 *  - 10000001: al corriente, con bonificación en ropa y muebles.
 *  - 10000002: con atraso, "Favor de pagar INMEDIATO".
 */
public final class DemoEstadoCuentaService implements EstadoCuentaService {

    public static final long CLIENTE_AL_CORRIENTE = 10000001L;
    public static final long CLIENTE_CON_ATRASO = 10000002L;

    private static final Logger LOG = Logger.getLogger(DemoEstadoCuentaService.class.getName());

    @Override
    public Optional<LocalDate> fechaDatos() {
        return Optional.empty();
    }

    @Override
    public String origen() {
        return "DEMO (sin base de datos)";
    }

    @Override
    public ExpedienteCliente consultar(long numeroCliente, LocalDate fechaCorte) {
        LOG.info("[demo] Consultando cliente " + numeroCliente);
        if (numeroCliente == CLIENTE_AL_CORRIENTE) {
            return alCorriente(fechaCorte);
        }
        if (numeroCliente == CLIENTE_CON_ATRASO) {
            return conAtraso(fechaCorte);
        }
        throw new IllegalArgumentException("En modo demo solo existen los clientes " + CLIENTE_AL_CORRIENTE
                + " y " + CLIENTE_CON_ATRASO);
    }

    private static ExpedienteCliente alCorriente(LocalDate corte) {
        Cliente cliente = new Cliente(CLIENTE_AL_CORRIENTE, "3", "ANA PEREZ LOPEZ", "PRINCIPAL", "1", "", "CALLE 2/CALLE 3",
                "", "CASA AZUL", "", "CENTRO", "CIUDAD DEMO", "CDMO", "MUNICIPIO DEMO", "100-200", "Otros",
                "JUAN PEREZ RUIZ", "A", "A", "C", "P", "F", "3", LocalDate.of(2022, 1, 30),
                LocalDate.of(1980, 1, 1), "90000001", "90000002", "500001", 1000, "I", "23", false, false, false,
                new Cliente.Referencia("", "", "", "", "", "", ""));
        List<Cuenta> cuentas = List.of(
                new Cuenta(TipoCartera.MUEBLES, false, 1000, 100001, "TV QLED", 2, LocalDate.of(2026, 1, 3), 12,
                        609, 170, 170, 0, 664, 0, 0, 0, 0, 628, 650, 0, LocalDate.of(2026, 9, 1), null,
                        false, false, false),
                new Cuenta(TipoCartera.ROPA, false, 1000, 0, "ROPA", 0, LocalDate.of(2026, 9, 13), 12,
                        148, 43, 0, 527, 527, 0, 0, 0, 0, 379, 406, 0, LocalDate.of(2026, 9, 13), null,
                        false, false, false));
        List<SituacionEspecial> situaciones = List.of(
                new SituacionEspecial("S", "50", LocalDate.of(2022, 1, 30), "Supervisar, fue alta directa en Tienda"),
                new SituacionEspecial("I", "23", LocalDate.of(2022, 7, 13), "Sin Horario de Contacto"));
        return armar(corte, cliente, cuentas, new DineroElectronico(9, 1), situaciones, List.of());
    }

    private static ExpedienteCliente conAtraso(LocalDate corte) {
        Cliente cliente = new Cliente(CLIENTE_CON_ATRASO, "1", "MARIA GOMEZ RUIZ", "HIDALGO", "1", "0",
                "entre juarez y morelos", "", "CASA VERDE", "", "COLONIA DEMO", "CIUDAD DEMO", "CDMO", "MUNICIPIO DEMO",
                "100-300", "Otros", "", "C", "D", "S", "P", "F", "6", LocalDate.of(2024, 10, 29),
                LocalDate.of(2000, 1, 1), "90000001", "90000002", "500001", 1000, "I", "23", false, false, false,
                new Cliente.Referencia("", "", "", "", "", "", ""));
        LocalDate ultimoAbono = LocalDate.of(2025, 4, 29);
        List<Cuenta> cuentas = List.of(
                new Cuenta(TipoCartera.MUEBLES, false, 2000, 200001, "TELCEL", 2, LocalDate.of(2025, 2, 8), 12,
                        957, 252, 0, 0, 2496, 114, 164, 50, 2496, 2660, 2660, 0, ultimoAbono, null, false, false, false),
                new Cuenta(TipoCartera.MUEBLES, false, 600, 200002, "VENTILADOR", 2, LocalDate.of(2024, 11, 11), 12,
                        301, 106, 0, 0, 734, 114, 129, 15, 734, 863, 863, 0, ultimoAbono, null, false, false, false),
                new Cuenta(TipoCartera.ROPA, false, 2000, 0, "ROPA", 0, LocalDate.of(2025, 2, 27), 12,
                        376, 106, 0, 0, 1162, 50, 73, 23, 1162, 1235, 1235, 0, ultimoAbono,
                        LocalDate.of(2025, 1, 31), false, false, false));
        List<SituacionEspecial> situaciones = List.of(
                new SituacionEspecial("S", "50", LocalDate.of(2024, 10, 29), "Supervisar, fue alta directa en Tienda"),
                new SituacionEspecial("I", "23", LocalDate.of(2024, 10, 31), "Sin Horario de Contacto"));
        return armar(corte, cliente, cuentas, DineroElectronico.VACIO, situaciones,
                List.of("3MSC: 3 MESES SIN CONTACTO"));
    }

    private static ExpedienteCliente armar(LocalDate corte, Cliente cliente, List<Cuenta> cuentas,
                                           DineroElectronico dinero, List<SituacionEspecial> situaciones,
                                           List<String> marcas) {
        List<TablaListado> listados = List.of(
                tabla("Directorio (demo)", List.of(cliente)),
                tabla("Cuentas (demo)", cuentas),
                tabla("Dinero electrónico (demo)", List.of(dinero)),
                tabla("Situaciones especiales (demo)", situaciones));
        return new ExpedienteCliente(corte, cliente, cuentas, dinero, situaciones, marcas, listados,
                List.of("Modo demo: los datos son de ejemplo, no se consultó la base de datos"));
    }

    /** Muestra los records como si fueran el resultado de un procedimiento. */
    private static TablaListado tabla(String nombre, List<? extends Record> registros) {
        if (registros.isEmpty()) {
            return new TablaListado(nombre, List.of(), List.of(), null);
        }
        RecordComponent[] componentes = registros.get(0).getClass().getRecordComponents();
        List<String> columnas = Arrays.stream(componentes).map(RecordComponent::getName).toList();
        List<List<Object>> filas = new ArrayList<>();
        for (Record r : registros) {
            List<Object> fila = new ArrayList<>();
            for (RecordComponent c : componentes) {
                try {
                    fila.add(c.getAccessor().invoke(r));
                } catch (ReflectiveOperationException e) {
                    fila.add(e.getMessage());
                }
            }
            filas.add(fila);
        }
        return new TablaListado(nombre, columnas, filas, null);
    }
}
