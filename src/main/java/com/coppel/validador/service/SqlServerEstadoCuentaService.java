package com.coppel.validador.service;

import com.coppel.validador.config.Config;
import com.coppel.validador.config.Config.ConexionDb;
import com.coppel.validador.model.Cliente;
import com.coppel.validador.model.Cuenta;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.model.ExpedienteCliente.DineroElectronico;
import com.coppel.validador.model.ExpedienteCliente.SituacionEspecial;
import com.coppel.validador.model.TablaListado;
import com.coppel.validador.model.TipoCartera;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta los listados del cliente en SQL Server ejecutando los procedimientos de
 * VbnListadoCarteras (spcQCDirectorio, spcQCMuebles, spcQCRopa...).
 *
 * Cada procedimiento se configura en config.properties (sp.&lt;clave&gt;) y puede usar los
 * parámetros :cliente y :corte. Algunos (spcQCCredito, spcQCDineroElectronico...) dejan su
 * resultado en una tabla tmp*, por eso la sentencia configurada puede incluir un SELECT después
 * del EXEC.
 *
 * Los procedimientos crean y borran tablas tmp* compartidas, así que cada listado se ejecuta en
 * una transacción que se deshace (ROLLBACK) al terminar: la base queda igual y, si dos personas
 * consultan a la vez, la segunda espera a que termine la primera en lugar de leer datos ajenos.
 * Si uno falla, se registra el error y se sigue con los demás.
 */
public final class SqlServerEstadoCuentaService implements EstadoCuentaService {

    private static final Logger LOG = Logger.getLogger(SqlServerEstadoCuentaService.class.getName());

    private static final int TIMEOUT_CONEXION_SEG = 10;
    private static final int TIMEOUT_CONSULTA_SEG = 60;
    private static final int MAX_FILAS = 500;

    private static final Pattern PARAMETRO = Pattern.compile(":(cliente|corte)\\b");
    private static final Pattern NUMERO = Pattern.compile("\\d+");

    /** Listados de cuentas: clave en config, cartera y si son cuentas perdidas. */
    private static final List<ListadoCartera> CARTERAS = List.of(
            new ListadoCartera("muebles", "Muebles", TipoCartera.MUEBLES, false),
            new ListadoCartera("ropa", "Ropa", TipoCartera.ROPA, false),
            new ListadoCartera("prestamos", "Préstamos", TipoCartera.PRESTAMOS, false),
            new ListadoCartera("tiempoAire", "Tiempo aire", TipoCartera.TIEMPO_AIRE, false),
            new ListadoCartera("deudaBancoppel", "Deuda Bancoppel", TipoCartera.DEUDA_BANCOPPEL, false),
            new ListadoCartera("reestructuras", "Reestructuras", TipoCartera.REESTRUCTURA, false),
            new ListadoCartera("cpMuebles", "CP Muebles", TipoCartera.MUEBLES, true),
            new ListadoCartera("cpRopa", "CP Ropa", TipoCartera.ROPA, true),
            new ListadoCartera("cpPrestamos", "CP Préstamos", TipoCartera.PRESTAMOS, true));

    /** Banderas de tmp_clientenocontactado (proc_clientenocontactado) y su leyenda. */
    private static final Map<String, String> MARCAS_CONTACTO = Map.of(
            "clv_3MSC", "3MSC: 3 MESES SIN CONTACTO",
            "clv_NTE", "NTE: NO TIENE TELEFONO VERIFICADO",
            "clv_NCE", "NCE: NO CUENTA CON CORREO ELECTRONICO CONFIRMADO");

    static {
        DriverManager.setLoginTimeout(TIMEOUT_CONEXION_SEG);
    }

    private final Config config;
    private final ConexionDb db;

    public SqlServerEstadoCuentaService(Config config) {
        this.config = config;
        this.db = config.conexion();
    }

    @Override
    public String origen() {
        return db.nombre();
    }

    @Override
    public Optional<LocalDate> fechaDatos() throws SQLException {
        String sql = config.procedimiento("fechaDatos");
        if (sql == null) {
            return Optional.empty();
        }
        try (Connection con = DriverManager.getConnection(db.url(), db.usuario(), db.password());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setQueryTimeout(TIMEOUT_CONSULTA_SEG);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getTimestamp(1) == null) {
                    LOG.warning("[" + db.nombre() + "] catFechas no devolvió fecha");
                    return Optional.empty();
                }
                return Optional.of(rs.getTimestamp(1).toLocalDateTime().toLocalDate());
            }
        }
    }

    @Override
    public ExpedienteCliente consultar(long numeroCliente, LocalDate fechaCorte) throws SQLException {
        LOG.info("[" + db.nombre() + "] Consultando cliente " + numeroCliente + " corte " + fechaCorte);
        long inicio = System.nanoTime();
        List<TablaListado> listados = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        Map<String, String> forzadas = config.columnas();
        LocalDate fechaDatos = fechaDatos().orElse(null);

        try (Connection con = DriverManager.getConnection(db.url(), db.usuario(), db.password())) {
            con.setAutoCommit(false);
            Consulta consulta = new Consulta(con, numeroCliente, fechaCorte, listados, avisos, forzadas);

            List<Registro> directorio = consulta.ejecutar("directorio", "Directorio");
            List<Registro> credito = consulta.ejecutar("credito", "Crédito");
            List<Registro> ciudad = consulta.ejecutar("inicialCiudad", "Ciudad");
            // Sin directorio no tiene caso avisar de cada columna faltante
            List<String> avisosCliente = directorio.isEmpty() ? new ArrayList<>() : avisos;
            if (directorio.isEmpty()) {
                avisos.add("El listado del directorio no devolvió datos para el cliente " + numeroCliente);
            }
            Cliente cliente = mapearCliente(numeroCliente, unir("directorio", directorio, forzadas, avisosCliente),
                    unir("credito", credito, forzadas, avisosCliente),
                    unir("inicialCiudad", ciudad, forzadas, avisosCliente));

            List<Cuenta> cuentas = new ArrayList<>();
            for (ListadoCartera listado : CARTERAS) {
                for (Registro r : consulta.ejecutar(listado.clave(), listado.titulo())) {
                    Cuenta c = mapearCuenta(r, listado);
                    if (c != null) {
                        cuentas.add(c);
                    }
                }
            }

            List<Registro> de = consulta.ejecutar("dineroElectronico", "Dinero electrónico");
            DineroElectronico dinero = de.isEmpty() ? DineroElectronico.VACIO : mapearDineroElectronico(de.get(0));

            List<SituacionEspecial> situaciones = new ArrayList<>();
            for (Registro r : consulta.ejecutar("informativa", "Cartera informativa")) {
                SituacionEspecial s = mapearSituacion(r);
                if (s != null) {
                    situaciones.add(s);
                }
            }

            List<String> marcas = new ArrayList<>();
            for (Registro r : consulta.ejecutar("marcasContacto", "Marcas de contacto")) {
                for (String marca : mapearMarcas(r)) {
                    if (!marcas.contains(marca)) {
                        marcas.add(marca);
                    }
                }
            }

            List<String> situacionesImpresas = new ArrayList<>();
            for (Registro r : consulta.ejecutar("situacionesImpresas", "Situaciones impresas")) {
                String situacion = Registro.aTexto(r.opcional("situacion", "situacion")).strip();
                if (!situacion.isEmpty()) {
                    situacionesImpresas.add(situacion);
                }
            }

            Map<String, Integer> abonosYMesesVencidos = new HashMap<>();
            for (Registro r : consulta.ejecutar("abonosYMesesVencidos", "Abonos y meses vencidos")) {
                String cartera = Registro.aTexto(r.opcional("cartera", "cartera")).strip();
                long factura = Registro.aEntero(r.opcional("factura", "factura"));
                int vencidos = (int) Registro.aEntero(r.opcional("abonosYMesesVencidos", "abonosYMesesVencidos"));
                try {
                    abonosYMesesVencidos.merge(ExpedienteCliente.llave(TipoCartera.valueOf(cartera), factura),
                            vencidos, Math::max);
                } catch (IllegalArgumentException e) {
                    avisos.add("Abonos y meses vencidos: cartera desconocida " + cartera);
                }
            }

            LOG.info("[" + db.nombre() + "] Cliente " + numeroCliente + ": " + cuentas.size() + " cuenta(s), "
                    + avisos.size() + " aviso(s) en " + (System.nanoTime() - inicio) / 1_000_000 + " ms");
            for (String aviso : avisos) {
                LOG.warning("[" + numeroCliente + "] " + aviso);
            }
            return new ExpedienteCliente(fechaCorte, cliente, cuentas, dinero, situaciones, marcas, listados, avisos,
                    situacionesImpresas, abonosYMesesVencidos, fechaDatos);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[" + db.nombre() + "] Error de conexión consultando el cliente " + numeroCliente
                    + " (SQLState " + e.getSQLState() + ", código " + e.getErrorCode() + ")", e);
            throw e;
        }
    }

    // ---------- Mapeo de columnas a modelo ----------
    // Los primeros alias son las columnas reales de los procedimientos de Carteras.

    /** dir = spcQCDirectorio, cred = spcQCCredito (tmpQCCredito01), cd = CatCiudades. */
    private static Cliente mapearCliente(long numeroCliente, Registro dir, Registro cred, Registro cd) {
        // NombreCompleto queda en NULL si falta el segundo nombre: se arma igual que el SP, con la parte vacía
        Object completo = dir.opcional("nombre", "NombreCompleto", "NombreCliente");
        String nombre = completo != null ? Registro.aTexto(completo)
                : String.join(" ", Registro.aTexto(dir.opcional("nombre1", "cunombre1", "Nombre")),
                        Registro.aTexto(dir.opcional("nombre2", "cunombre2")),
                        Registro.aTexto(dir.opcional("apellidoPaterno", "cuapellidopaterno", "ApellidoPaterno")),
                        Registro.aTexto(dir.opcional("apellidoMaterno", "cuapellidomaterno", "ApellidoMaterno"))).strip();

        Object puntualidad = cred.opcional("puntualidad", "PuntualidadCliente", "Puntualidad");
        if (puntualidad == null) {
            puntualidad = dir.valor("puntualidad", "PuntualidadCliente", "Puntualidad");
        }
        Object activa = cred.opcional("puntualidadActiva", "PuntualidadActiva", "PuntualidadActivaCliente");
        if (activa == null) {
            activa = dir.valor("puntualidadActiva", "PuntualidadActiva");
        }

        // La zona del estado de cuenta es ciudad-colonia: 100-0300
        String ciudad = Registro.aTexto(dir.opcional("zonaCiudad", "NumeroCiudad"));
        String colonia = Registro.aTexto(dir.opcional("zonaColonia", "NumeroColonia"));
        String zona = !ciudad.isEmpty() && !colonia.isEmpty() ? ciudad + "-" + colonia
                : dir.texto("zona", "Zona", "NumeroZona");

        // Solo el teléfono de casa: en las muestras no se imprime el celular
        String telefono = dir.texto("telefono", "Telefono", "TefonoCasaCte");

        Cliente.Referencia referencia = new Cliente.Referencia(
                dir.texto("referencia.nombre", "NomCompletoRef", "NombreReferencia").replaceAll("\\s+", " "),
                dir.texto("referencia.calle", "CalleReferencia"),
                dir.texto("referencia.casa", "CasaDeReferencia", "CasaReferencia"),
                dir.texto("referencia.zona", "ColoniaReferencia", "ZonaReferencia"),
                dir.texto("referencia.ciudad", "CiudadReferencia", "PoblacionReferencia"),
                dir.texto("referencia.telefono", "TelefonoReferencia"),
                dir.texto("referencia.complemento", "ComplementoReferencia"));

        return new Cliente(
                numeroCliente,
                dir.texto("digito", "Digito", "DigitoVerificador"),
                nombre,
                dir.texto("calle", "Calle", "NombreCalle"),
                dir.texto("casa", "NumeroCasa", "Casa"),
                dir.texto("departamento", "DepartamentoCliente", "Departamento"),
                Registro.aTexto(dir.opcional("entreCalles", "cuentrecalles", "EntreCalles")),
                dir.texto("rumbo", "Rumbo"),
                dir.texto("complemento", "cucomplemento", "Complemento", "ComplementoCte"),
                telefono.equals("0") ? "" : telefono,
                dir.texto("colonia", "Colonia", "NombreColonia"),
                dir.texto("ciudad", "Ciudad", "NombreCiudad"),
                Registro.aTexto(cd.opcional("inicialCiudad", "InicialCiudad")),
                dir.texto("municipio", "MunicipioZona", "Municipio", "PoblacionZona"),
                zona,
                dir.texto("lugarTrabajo", "LugarDeTrabajo", "LugarTrabajo"),
                dir.texto("conyuge", "NomCompletoCony", "NombreConyuge").replaceAll("\\s+", " "),
                Registro.aTexto(puntualidad),
                Registro.aTexto(activa),
                dir.texto("estadoCivil", "EstadoCivil"),
                dir.texto("tipoCasa", "TipoDeCasa", "TipoCasa"),
                dir.texto("sexo", "Sexo"),
                dir.texto("ingresoMensual", "IngresoMensual"),
                dir.fecha("antiguedad", "Antiguedad", "FechaAlta"),
                dir.fecha("fechaNacimiento", "FechaDeNacimiento", "FechaNacimiento"),
                dir.texto("jefeGrupo", "jefegrupo", "JefeGrupo"),
                dir.texto("supervisor", "supervisor", "Supervisor"),
                dir.texto("centro", "centro", "Centro"),
                dir.entero("numeroTienda", "NumeroTienda", "Tienda"),
                dir.texto("situacionEspecial", "SituacionEspecial", "Situacion"),
                dir.texto("causa", "CausaSituacionEsp", "Causa"),
                Registro.aBooleano(dir.opcional("domicilioCertificado", "DomicilioCertificado", "Certificado")),
                Registro.aBooleano(dir.opcional("domiciliado", "Domiciliado", "ClienteDomiciliado")),
                Registro.aBooleano(dir.opcional("noEnviarEdoCta", "NoEnviarEdoCta")),
                referencia);
    }

    /** Devuelve null para filas sin cuenta (por ejemplo "CLIENTE SALDÓ CUENTA PERDIDA"). */
    private static Cuenta mapearCuenta(Registro r, ListadoCartera listado) {
        TipoCartera tipo = listado.tipo();
        // Ropa y tiempo aire son carteras revolventes: sin factura y con abono base
        boolean revolvente = tipo == TipoCartera.ROPA || tipo == TipoCartera.TIEMPO_AIRE;

        long saldo = r.entero("saldoFecha", "SaldoALaFecha", "SaldoFecha");
        long interes = revolvente
                ? r.entero("interesAdicional", "InteresAdicionalMesAct", "InteresAdicMesAct")
                : r.entero("interesAdicional", "InteresAdicional");
        long vencido = Registro.aEntero(r.opcional("vencido", "Vencido", "Vencido2", "VencidoTotal"));
        long factura = revolvente ? 0 : r.entero("numeroFactura", "NumeroFactura", "Factura");
        if (saldo == 0 && interes == 0 && vencido == 0 && factura == 0) {
            return null;
        }

        // Interés adicional cargado en el mes; si el listado no lo trae, la diferencia contra el mes anterior
        // Préstamos no trae el interés del mes anterior: se deduce del total menos el cargado en el mes
        Object anterior = r.opcional("interesAdicionalMesAnterior", "InteresAdicionalMesAnt", "IntAdicMesAnt");
        Object nuevoInteres = r.opcional("interesAdicionalMes", "NuevoInteresCargado", "InteresAdicionalMes");
        if (anterior == null && nuevoInteres == null) {
            r.valor("interesAdicionalMesAnterior", "InteresAdicionalMesAnt"); // reporta la columna faltante
        }
        long interesMes = Registro.aEntero(nuevoInteres);
        long interesAnterior = anterior != null ? Registro.aEntero(anterior) : Math.max(0, interes - interesMes);
        if (nuevoInteres == null) {
            interesMes = Math.max(0, interes - interesAnterior);
        }

        String articulo = switch (tipo) {
            case ROPA -> "ROPA";
            case TIEMPO_AIRE -> "TIEMPO AIRE";
            case PRESTAMOS -> Registro.aTexto(r.opcional("articulo", "DescripcionArticulo")).isEmpty()
                    ? "PRESTAMO" : Registro.aTexto(r.opcional("articulo", "DescripcionArticulo"));
            default -> r.texto("articulo", "DescripcionArticulo", "Articulo");
        };
        Object convenioDevolucion = r.opcional("convenioDevolucion", "FechaConvenioDevolucion", "MarcaConvDevolucion",
                "FecConvDevol");

        return new Cuenta(
                tipo,
                listado.cuentaPerdida(),
                r.entero("numeroTienda", "NumeroTienda", "Tienda"),
                factura,
                articulo,
                (int) Registro.aEntero(r.opcional("numeroArticulos", "NumeroDeArticulos", "NumeroArticulos")),
                r.fecha("fechaCompra", "FechaVenta", "FechaUltimaCompra", "FechaPrestamo"),
                (int) Registro.aEntero(r.opcional("plazo", "PlazoVenta", "idu_Plazo", "Plazo")),
                Registro.aEntero(r.opcional("interesSobreCompra", tipo == TipoCartera.PRESTAMOS
                        ? new String[]{"InteresFinanciamiento", "InteresCobrado"}
                        : new String[]{"InteresSobreCompra", "IntSCompra"})),
                r.entero("abonoMensual", revolvente ? new String[]{"AbonoBase"} : new String[]{"AbonoMensual"}),
                r.entero("abonosMes", "AbonoDelMes", "AbonosDelMes"),
                Registro.aEntero(r.opcional("comprasMes", "ComprasDelMes", "PrestamosDelMes")),
                saldo,
                interesAnterior,
                interes,
                interesMes,
                vencido,
                Registro.aEntero(r.opcional("saldaCon", "SaldaCon")),
                Registro.aEntero(r.opcional("saldaCon20", "SaldaCon20SigMes")),
                Registro.aEntero(r.opcional("bonificacionMes", "BonificacionMes", "BonificacionMensual")),
                r.fecha("fechaUltimoMovimiento", "FechaUltimoMov", "FecUltMovto"),
                Registro.aFecha(r.opcional("fechaSinSaldo", "fec_sinsaldo")),
                Registro.aFecha(r.opcional("fechaConvenio", "FechaDelConvenio", "FechaConvenio")) != null,
                Registro.aFecha(convenioDevolucion) != null || Registro.aBooleano(convenioDevolucion),
                Registro.aBooleano(r.opcional("domiciliada", "Domiciliada", "CuentaDomiciliada")),
                Registro.aEntero(r.opcional("interesPrimerMes", "InteresAdicionalPrimerMes")),
                Registro.aEntero(r.opcional("importeVenta", "ImporteVenta")),
                Registro.aEntero(r.opcional("enganche", "Enganche")),
                Registro.aTexto(r.opcional("tipoUltimoMovimiento", "TipoUltimoMov")),
                Registro.aFecha(r.opcional("fechaConvenio", "FechaDelConvenio", "FechaConvenio")),
                Registro.aEntero(r.opcional("importeConvenio", "ImporteDelConvenio")),
                (int) Registro.aEntero(r.opcional("plazoConvenio", "PlazoDelConvenio")),
                (int) Registro.aEntero(r.opcional("tipoProducto", "TipoProducto")));
    }

    /** tmpDineroElectronico3: saldo y "Corte N" de abonos puntuales. */
    private static DineroElectronico mapearDineroElectronico(Registro r) {
        int abonosPuntuales = 0;
        Matcher m = NUMERO.matcher(Registro.aTexto(r.opcional("abonosPuntuales", "Desc_Corte", "AbonosPuntuales")));
        if (m.find()) {
            abonosPuntuales = Integer.parseInt(m.group());
        }
        return new DineroElectronico(r.entero("saldo", "SaldoALaFecha", "SaldoFecha"), abonosPuntuales);
    }

    /** tmp_CteSituacionEsp (proc_carterainformativa). */
    private static SituacionEspecial mapearSituacion(Registro r) {
        String situacion = Registro.aTexto(r.opcional("situacion", "Clv_situacion", "SituacionEspecial"));
        if (situacion.isBlank()) {
            return null;
        }
        return new SituacionEspecial(situacion,
                Registro.aTexto(r.opcional("causa", "num_causasituacion", "Causa")),
                Registro.aFecha(r.opcional("fecha", "fec_marcado", "FechaMarcado")),
                Registro.aTexto(r.opcional("descripcion", "Nom_descsituacion", "Descripcion")));
    }

    /** tmp_clientenocontactado (proc_clientenocontactado): una bandera por marca. */
    private static List<String> mapearMarcas(Registro r) {
        List<String> marcas = new ArrayList<>();
        MARCAS_CONTACTO.forEach((columna, leyenda) -> {
            if (Registro.aBooleano(r.opcional(columna, columna))) {
                marcas.add(leyenda);
            }
        });
        marcas.sort(String::compareTo);
        return marcas;
    }

    /** Une en un solo registro las columnas de todas las filas (el directorio llega en varias secciones). */
    private static Registro unir(String listado, List<Registro> filas, Map<String, String> forzadas,
                                 List<String> avisos) {
        Map<String, Object> todo = new LinkedHashMap<>();
        for (Registro r : filas) {
            r.valores().forEach(todo::putIfAbsent);
        }
        return new Registro(listado, todo, forzadas, avisos);
    }

    // ---------- Ejecución de procedimientos ----------

    private record ListadoCartera(String clave, String titulo, TipoCartera tipo, boolean cuentaPerdida) {
    }

    /** Ejecuta los procedimientos de una consulta reutilizando la misma conexión. */
    private final class Consulta {
        private final Connection con;
        private final long cliente;
        private final LocalDate corte;
        private final List<TablaListado> listados;
        private final List<String> avisos;
        private final Map<String, String> forzadas;

        Consulta(Connection con, long cliente, LocalDate corte, List<TablaListado> listados, List<String> avisos,
                 Map<String, String> forzadas) {
            this.con = con;
            this.cliente = cliente;
            this.corte = corte;
            this.listados = listados;
            this.avisos = avisos;
            this.forzadas = forzadas;
        }

        /** Filas de todos los conjuntos de resultados; lista vacía si no está configurado o falla. */
        List<Registro> ejecutar(String clave, String titulo) {
            String sentencia = config.procedimiento(clave);
            if (sentencia == null) {
                return List.of();
            }
            List<String> orden = new ArrayList<>();
            Matcher m = PARAMETRO.matcher(sentencia);
            while (m.find()) {
                orden.add(m.group(1));
            }
            String sql = PARAMETRO.matcher(sentencia).replaceAll("?");

            long inicio = System.nanoTime();
            List<Registro> registros = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(TIMEOUT_CONSULTA_SEG);
                ps.setMaxRows(MAX_FILAS);
                for (int i = 0; i < orden.size(); i++) {
                    if (orden.get(i).equals("cliente")) {
                        ps.setLong(i + 1, cliente);
                    } else {
                        ps.setDate(i + 1, java.sql.Date.valueOf(corte));
                    }
                }
                int conjunto = 0;
                boolean hayResultados = ps.execute();
                while (true) {
                    if (hayResultados) {
                        try (ResultSet rs = ps.getResultSet()) {
                            conjunto++;
                            leer(rs, conjunto == 1 ? titulo : titulo + " (" + conjunto + ")", clave, registros);
                        }
                    } else if (ps.getUpdateCount() == -1) {
                        break;
                    }
                    hayResultados = ps.getMoreResults();
                }
                LOG.info("[" + clave + "] " + registros.size() + " fila(s) en "
                        + (System.nanoTime() - inicio) / 1_000_000 + " ms");
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "[" + clave + "] Error ejecutando: " + sql + " (SQLState " + e.getSQLState()
                        + ", código " + e.getErrorCode() + ")", e);
                avisos.add("Error en el listado " + titulo + ": " + e.getMessage());
                listados.add(TablaListado.conError(titulo, e.getMessage()));
            } finally {
                deshacer(clave);
            }
            return registros;
        }

        /** Deshace lo que el procedimiento haya escrito (tablas tmp*) y libera sus bloqueos. */
        private void deshacer(String clave) {
            try {
                con.rollback();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "[" + clave + "] No se pudo hacer ROLLBACK", e);
            }
        }

        private void leer(ResultSet rs, String titulo, String clave, List<Registro> registros) throws SQLException {
            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();
            List<String> columnas = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                columnas.add(meta.getColumnLabel(i));
            }
            List<List<Object>> filas = new ArrayList<>();
            while (rs.next()) {
                List<Object> fila = new ArrayList<>();
                Map<String, Object> valores = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) {
                    Object v = rs.getObject(i);
                    if (v instanceof String texto) {
                        v = texto.trim(); // CHAR de SQL Server viene con espacios a la derecha
                    }
                    fila.add(v);
                    valores.putIfAbsent(columnas.get(i - 1), v);
                }
                filas.add(fila);
                registros.add(new Registro(clave, valores, forzadas, avisos));
            }
            listados.add(new TablaListado(titulo, columnas, filas, null));
        }
    }
}
