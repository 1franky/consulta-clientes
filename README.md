# Validador de estados de cuenta

Aplicación de escritorio (Java 21 + Swing) para que Control de Calidad valide las muestras impresas
del **Estado de Cuenta Correos** contra los listados de la base **Carteras** (SQL Server), siguiendo el
manual *"1. Estado de Cuenta Correos"*.

La validación la hace una persona: el sistema muestra la muestra escaneada junto al estado de cuenta
**esperado** (calculado con las reglas del manual, con el mismo acomodo que el impreso) para compararlos a la vista.

Se distribuye como un `.exe` para Windows **con Java embebido**: quien la usa no necesita instalar nada.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/reglas-validacion.md](docs/reglas-validacion.md) | Cómo se calcula cada dato del estado de cuenta y de dónde sale |
| [docs/procedimientos-bd.md](docs/procedimientos-bd.md) | Procedimientos de Carteras: parámetros, columnas, tablas `tmp*` y permisos |
| [docs/bonificaciones.md](docs/bonificaciones.md) | Cálculo de bonificaciones, catálogo de la base y `bonificaciones.csv` de respaldo |
| [docs/pendientes.md](docs/pendientes.md) | Reglas por confirmar, mejoras propuestas y riesgos |

---

## Para usuarios (Control de Calidad)

1. Abrir `ValidadorEdoCta.exe`.
2. **Abrir muestra...** y elegir el PDF escaneado (o una imagen). Se ve a la izquierda; con ◀ ▶ se cambia de página y con − + el zoom.
3. Escribir el **número de cliente** y pulsar **Consultar**. La **fecha de corte** no se captura: se muestra junto al
   botón y es el último día 20 hasta `catFechas` (en modo demo, 20/09/2026). Si `catFechas` es posterior al corte,
   aparece en rojo "Datos al ...": los listados incluyen movimientos posteriores al corte y pueden no coincidir con
   el impreso.
4. Pestaña **Estado de cuenta esperado**: cómo debería verse el impreso. Abajo vienen las notas del cálculo
   (asteriscos, aviso importante, citatorio, situación especial) y el cálculo de bonificaciones.
   Los recuadros rojos son alertas (por ejemplo, una situación especial a la que no se le genera estado de cuenta).
5. Pestaña **Listados**: lo que devolvió cada procedimiento, sin interpretar, y los avisos de la consulta.
6. Botón **Modo oscuro / Modo claro** (arriba a la derecha): cambia los colores al momento. La elección se
   recuerda por usuario de Windows.

La **leyenda de mercadotecnia** no se calcula: se valida contra la carpeta compartida
`\Calidad Sist Carteras\Productos Calidad\México\Especiales\Leyendas Mercadotecnia\<año>` (las capturadas del 16 al 20).

---

## Arquitectura

Un solo jar (monolito) con capas separadas por paquete, al estilo Spring pero sin Spring: `App` crea los objetos
y los conecta (inyección de dependencias manual).

```
com.coppel.validador
├── App.java                         ← punto de entrada y "contenedor": arma service, reglas y pantalla
├── config/                          ← config.properties y logs
├── model/                           ← datos: Cliente, Cuenta, ExpedienteCliente, TablaListado
├── service/                         ← TODO el acceso a la base de datos
│   ├── EstadoCuentaService          ← interfaz que usa la pantalla
│   ├── SqlServerEstadoCuentaService ← ejecuta los procedimientos y mapea columnas → modelo
│   ├── SqlServerBonificacionesService ← catálogo de bonificaciones de la base
│   ├── DemoEstadoCuentaService      ← datos de ejemplo sin BD (modo=demo)
│   └── Registro                     ← búsqueda tolerante de columnas
├── validacion/                      ← reglas del manual, sin BD ni pantallas (se prueban con JUnit)
│   ├── CalculadoraEstadoCuenta      ← estado de cuenta esperado: [5]–[24]
│   ├── CatalogoBonificaciones       ← factores de bonificación (base o bonificaciones.csv)
│   └── Formato, EstadoCuentaEsperado
└── ui/                              ← solo Swing: no hace SQL ni cálculos
    ├── VentanaPrincipal
    ├── PanelMuestra                 ← visor del PDF escaneado (PDFBox)
    ├── PanelEstadoCuenta            ← estado de cuenta esperado
    ├── PanelListados                ← resultado crudo de los procedimientos
    └── Tema                         ← modo claro / oscuro (FlatLaf)
```

Regla: `ui` solo habla con `service` (datos) y `validacion` (reglas). `validacion` no conoce ni la BD ni Swing.

### Reglas implementadas (números del manual)

| Punto | Regla |
|---|---|
| [2] | Solo incluye abonos hasta = fecha de corte |
| [3] | Domicilio = calle # casa + depto. + rumbo + entre calles + complemento, partido a 50 caracteres; colonia + municipio en el último renglón. Nombre, ciudad abreviada (`ACYN`, de `CatCiudades`), situación especial impresa (`I35`). Datos generales del directorio, cadena de cobranza (`AACPF0322` = puntualidad, puntualidad activa, estado civil, tipo de casa, sexo, ingreso, antigüedad), jefe de grupo, supervisor, centro (`5000-01`), zona (`100-0200`) |
| [5] | Fecha de compra = fecha de venta (muebles) o fecha última compra (ropa) |
| [6] | Usted debe = saldo a la fecha + interés adicional. Usted debía = saldo + int. adic. mes anterior + abonos − compras. Interés adicional del renglón = `NuevoInteresCargado` |
| [7] | Páguelo con solo = usted debe − bonificación, con bonificación = round(Int. s/compra × factor), tope el saldo a la fecha (`fnGeSaldaCon`). Factor del catálogo por cartera, plazo, días = (corte + 1 mes) − fecha de compra y vigencia (`fec_iniciobloque`). Préstamos: base `InteresFinanciamiento`, factor nuevo desde 20-jun-2024. Ropa sin saldo hace ≤ 30 días (`fec_sinsaldo`): interés completo. Sin bonificación con atraso |
| [8]–[10] | Abono mensual; atrasado = vencido + interés adicional (tope usted debe); pase a pagar = abono + atrasado (tope usted debe) |
| [11] | Fecha de último abono = fec. ult. movto (vacía si el último movimiento fue la compra; `CP` en cuentas perdidas) |
| [12]–[13] | Totales de todas las cuentas (se pintan hasta 5) |
| [15] | INMEDIATO si puntualidad B, C, D, Z con vencido ≥ abono total, o A con vencido ≥ 2 abonos |
| [16] | Asteriscos: `*` al corriente, `**` nuevo del mes, `***` A con atraso del 50% al 75% de dos abonos |
| [18] | Citatorio CIT # 1/2/3/4/7 según abonos vencidos y puntualidad (solo como nota: el formato 2026 no lo imprime) |
| Aviso importante | Marca 3MSC/NCE y leyenda según puntualidad y marca (deducido de las muestras) |
| [19] | Tienda (4) + factura (6) `*` artículos de todas las cuentas menos ropa |
| [20] | IR$ / IM$ / IB$ ... = suma de interés adicional por cartera |
| [21]–[25] | Domiciliada, leyendas, página, monedas de abono puntual, QR (revisión visual) |
| Alertas | Situación especial L, M, X, F, P23, P71–P74; `NoEnviarEdoCta` sin vencido; convenio de devolución; más de 5 cuentas |

Las pruebas (`src/test`) comparan el cálculo contra lo **impreso** en `MUESTRAC COPPEL_VERACRUZ.pdf` para los
dos clientes (con datos personales ficticios). Además se verificó contra una base Carteras de laboratorio: dos
clientes de las muestras reproducen el impreso renglón por renglón (importes, totales, domicilio, cadena, zona,
facturas e intereses).

---

## Para desarrolladores

### Requisitos (solo en la máquina donde se compila)

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | **21** | `jpackage` existe desde Java 14. |
| Maven | 3.9+ | https://maven.apache.org/download.cgi — descomprimir y agregar `bin` al `PATH`. Verificar con `mvn -v`. |

### Compilar y generar el .exe (Windows)

1. Editar `JAVA_HOME` y `MAVEN_HOME` en `build.bat`.
2. En `cmd`, desde la carpeta del proyecto: `build.bat`

Resultado:

```
target\installer\ValidadorEdoCta\
├── ValidadorEdoCta.exe      ← lo que abre el usuario
├── config.properties        ← modo y credenciales (editable)
├── bonificaciones.csv       ← catálogo de respaldo si la base no tiene bonificaciones
├── logo.png                 ← logo (opcional)
├── logs\                    ← se crea al abrir la app
├── app\                     ← jar de la aplicación
└── runtime\                 ← Java embebido
```

### Probar sin generar el .exe

```bat
mvn clean package
java -jar target\dist\validador-edocta.jar
```

Con `config.properties` en la carpeta desde donde se ejecuta el comando. Para probar **sin base de datos**,
poner `modo=demo` y consultar los clientes ficticios `10000001` (al corriente) o `10000002` (con atraso) con corte `20/09/2026`.

---

## Configuración (`config.properties`)

```properties
modo=sqlserver
sqlserver.url=jdbc:sqlserver://servidor-sql:1433;databaseName=Carteras;encrypt=true;trustServerCertificate=true
sqlserver.usuario=usuario_lectura
sqlserver.password=cambiar_esto
```

El archivo se busca en: `-Dconfig=...`, la carpeta del `.exe` y la carpeta de trabajo.

Claves opcionales, con su valor por omisión: `sqlserver.nombre=Carteras`, `logo.archivo=logo.png`,
`bonificaciones.archivo=bonificaciones.csv`, `columna.<listado>.<campo>=` (ver Columnas) y `sp.<clave>=` (ver Procedimientos).

### Procedimientos

Las sentencias van dentro del programa, en `src/main/resources/procedimientos.properties`. Para cambiar una sin
recompilar, se escribe la misma clave en `config.properties`, que tiene prioridad.

Todos reciben `@NumCte INT` (préstamos además `@iFechaConCorteAl20 BIT`, se manda 1). Algunos no devuelven filas:
dejan el resultado en una tabla `tmp*` que se lee con un `SELECT` en la misma sentencia:

| Clave | Sentencia |
|---|---|
| `sp.credito` | `EXEC dbo.spcQCCredito :cliente; SELECT * FROM dbo.tmpQCCredito01` |
| `sp.dineroElectronico` | `EXEC dbo.spcQCDineroElectronico :cliente; IF OBJECT_ID('dbo.tmpDineroElectronico3') IS NOT NULL SELECT * FROM dbo.tmpDineroElectronico3` |
| `sp.informativa` | `EXEC dbo.proc_carterainformativa :cliente; SELECT * FROM dbo.tmp_CteSituacionEsp` |
| `sp.marcasContacto` | `EXEC dbo.proc_clientenocontactado :cliente; SELECT * FROM dbo.tmp_clientenocontactado` |

**Permisos:** los procedimientos hacen `DROP TABLE` / `SELECT INTO` sobre tablas `tmp*` de `dbo`, así que un usuario
de solo lectura no basta. El validador ejecuta cada listado en una transacción que se deshace (`ROLLBACK`) al
terminar: la base queda igual y, si dos personas consultan a la vez, la segunda espera a la primera en lugar de
leer tablas `tmp*` ajenas.

Los procedimientos toman la fecha de corte de `catFechas`. Por eso el validador lee `catFechas`
(`SELECT TOP 1 Fecha FROM dbo.catFechas`) y toma como corte el último día 20 hasta esa fecha.

### Columnas

Los nombres reales de las columnas ya están mapeados (`SaldoALaFecha`, `NuevoInteresCargado`, `Vencido2`, `idu_Plazo`,
`InteresFinanciamiento`, `NumeroCiudad`-`NumeroColonia`...). Si algún procedimiento cambia y en **Listados > Avisos**
aparece `No se encontró la columna para muebles.saldoFecha`, se indica el nombre nuevo en `config.properties`
sin recompilar: `columna.muebles.saldoFecha=NombreNuevo`. Si un procedimiento falla, su pestaña dice "(error)" y el
detalle queda en `logs\errores-0.log`.

### Bonificaciones

Con `modo=sqlserver`, al abrir la aplicación se lee el catálogo de `cat_bonificacionescarteras` y
`cat_bonificacionesPrestamos` (`sp.bonificaciones`), el mismo que usan los procedimientos. Si no hay conexión o las
tablas están vacías, se usa `bonificaciones.csv`. La barra de estado indica qué catálogo se cargó.

El respaldo `bonificaciones.csv` está junto al `.exe`:

```
cartera,plazo,dias_transcurridos,factor,vigente_desde
MUEBLES,12,290,0.0587,1901-01-01
PRESTAMOS,12,350,0.0015,2024-06-20
```

- Para cada compra se usa el bloque con la `vigente_desde` más reciente que sea menor o igual a la fecha de compra
  (igual que `fec_iniciobloque` en `cat_bonificacionescarteras`).
- Si cambian los factores, **agregar** renglones con la nueva `vigente_desde`; las compras anteriores conservan sus
  factores. Guardar y volver a abrir la aplicación.
- Si el archivo no existe se usa la copia incluida en el programa (`src/main/resources/bonificaciones.csv`, que
  `build.bat` copia junto al `.exe`). Otra ruta: `bonificaciones.archivo` en `config.properties`.

### Logs

`logs\validador-0.log` (todo) y `logs\errores-0.log` (solo advertencias y errores), junto al `.exe`.
Rotan a 5 MB, se guardan hasta 5. **Nunca** se registra la contraseña.

### Seguridad

- La contraseña queda en texto plano: usar un usuario con permisos solo sobre lo necesario (ejecutar los
  procedimientos y crear/borrar sus tablas `tmp*`).
- Cada listado se deshace con `ROLLBACK`. Límites: 10 s para conectar, 60 s por procedimiento, 500 filas.
