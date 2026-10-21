# Procedimientos de la base Carteras

El validador obtiene los listados con los mismos procedimientos que usa *VbnListadoCarteras*. Cada uno se configura
en `src/main/resources/procedimientos.properties` (`sp.<clave>`), y `config.properties` puede redefinir cualquiera con la
misma clave sin recompilar; `:cliente` se reemplaza por el número de cliente y `:corte` por la fecha de
corte. El acceso está en `service/SqlServerEstadoCuentaService.java`.

## Resumen

| Clave | Procedimiento | Parámetros | Resultado |
|---|---|---|---|
| `directorio` | `spcQCDirectorio` | `@NumCte INT` | Filas (`tmplistadodirectoriocondigito`) |
| `credito` | `spcQCCredito` | `@NumCte INT` | **Sin filas**: tabla `tmpQCCredito01` |
| `muebles` | `spcQCMuebles` | `@NumCte INT` | Filas |
| `ropa` | `spcQCRopa` | `@NumCte INT` | Filas |
| `prestamos` | `spcQCPrestamos` | `@NumCte INT, @iFechaConCorteAl20 BIT` | Filas (se manda `1`) |
| `tiempoAire` | `spcQCarteraTiempoAire` | `@NumCte INT` | Filas |
| `deudaBancoppel` | `spcListadoClientesDeudaBancoppel` | `@NumCte INT` | Filas |
| `reestructuras` | `spcQCReestructuras` | `@NumCte INT` | Filas |
| `cpMuebles` / `cpRopa` / `cpPrestamos` | `spcQCCPMuebles` / `spcQCCPRopa` / `spcQCCPPrestamos` | `@NumCte INT` | Filas |
| `dineroElectronico` | `spcQCDineroElectronico` | `@NumCte INT` | **Sin filas**: tabla `tmpDineroElectronico3` |
| `informativa` | `proc_carterainformativa` | `@num_cliente INT` | `SELECT 1`; situaciones en `tmp_CteSituacionEsp` |
| `marcasContacto` | `proc_clientenocontactado` | `@num_cliente INT` | **Sin filas**: tabla `tmp_clientenocontactado` |
| `inicialCiudad` | `SELECT` a `maeDirectorio` + `CatCiudades` | `:cliente` | `InicialCiudad` (ACYN) |
| `fechaDatos` | `SELECT` a `catFechas` | — | Fecha de los datos; de ella sale el corte |
| `bonificaciones` | `SELECT` a `cat_bonificacionescarteras` + `cat_bonificacionesPrestamos` | — | Catálogo de bonificaciones, al abrir (ver [bonificaciones.md](bonificaciones.md)) |

Los que dejan el resultado en una tabla se configuran con un `SELECT` en la misma sentencia:

```properties
sp.credito=EXEC dbo.spcQCCredito :cliente; SELECT * FROM dbo.tmpQCCredito01
sp.dineroElectronico=EXEC dbo.spcQCDineroElectronico :cliente; IF OBJECT_ID('dbo.tmpDineroElectronico3') IS NOT NULL SELECT * FROM dbo.tmpDineroElectronico3
sp.informativa=EXEC dbo.proc_carterainformativa :cliente; SELECT * FROM dbo.tmp_CteSituacionEsp
sp.marcasContacto=EXEC dbo.proc_clientenocontactado :cliente; SELECT * FROM dbo.tmp_clientenocontactado
```

El servicio lee todos los conjuntos de resultados que devuelva la sentencia.

## Tablas tmp*, permisos y transacciones

Los procedimientos hacen `DROP TABLE` y `SELECT ... INTO` sobre tablas físicas `tmp*` de `dbo`
(`tmplistadodirectoriocondigito`, `tmpQCPrestamos01`, `tmp_listadocarteraropa`...). Por eso:

- **Un usuario de solo lectura no basta**: necesita ejecutar los procedimientos y crear y borrar esas tablas.
- Son tablas **compartidas**: si dos personas consultan a la vez, una podría leer el resultado de la otra.

Para evitarlo, el servicio ejecuta cada listado con `autoCommit=false` y hace `ROLLBACK` al terminar:

- La base queda como estaba.
- Los bloqueos del `DROP`/`CREATE` se mantienen hasta el `ROLLBACK`, así que una segunda consulta espera a que
  termine la primera (como máximo 60 s por procedimiento).

## Fecha de corte

Los procedimientos no reciben la fecha de corte: toman `catFechas.Fecha`, la fecha a la que están los datos. Sus
saldos, abonos y columnas calculadas (`SaldaCon`, `BonificacionMuebles`, `MesesTranscurridos`...) corresponden a
esa fecha.

Por eso el corte no se captura. Al abrir la ventana y en cada consulta, el validador lee `catFechas`
(`sp.fechaDatos`) y toma como corte el último día 20 que no pase de esa fecha. Con ese corte calcula la
bonificación (ver [bonificaciones.md](bonificaciones.md)), "Favor de pagar antes de" y los asteriscos.

Si `catFechas` es posterior al corte (en el laboratorio es 18/10/2026, así que el corte es 20/09/2026), la pantalla
muestra "Datos al ..." en rojo: los listados incluyen movimientos posteriores al corte, por ejemplo un abono del
29 de septiembre.

## Columnas usadas

Los nombres se buscan sin importar mayúsculas, espacios ni guiones bajos. Si uno cambia, se corrige en
`config.properties` con `columna.<listado>.<campo>=NombreNuevo`, sin recompilar. Los que no se encuentran se reportan
en **Listados > Avisos**.

| Listado | Columnas |
|---|---|
| Directorio | `NombreCompleto` (o `cunombre1`, `cunombre2`, `cuapellidopaterno`, `cuapellidomaterno`), `Digito`, `Calle`, `NumeroCasa`, `DepartamentoCliente`, `Rumbo`, `cuentrecalles`, `cucomplemento` / `Complemento`, `Telefono`, `Colonia`, `Ciudad`, `MunicipioZona`, `NumeroCiudad`, `NumeroColonia`, `EstadoCivil`, `TipoDeCasa`, `Sexo`, `IngresoMensual`, `Antiguedad`, `FechaDeNacimiento`, `jefegrupo`, `supervisor`, `centro`, `NumeroTienda`, `SituacionEspecial`, `CausaSituacionEsp`, `NoEnviarEdoCta`, `NomCompletoRef`, `CalleReferencia`, `CasaDeReferencia`, `ColoniaReferencia`, `CiudadReferencia`, `TelefonoReferencia`, `ComplementoReferencia`, `NomCompletoCony`, `LugarDeTrabajo` |
| Crédito | `PuntualidadCliente`, `PuntualidadActiva` |
| Muebles, Deuda Bancoppel, Reestructuras, CP Muebles | `NumeroTienda`, `NumeroFactura`, `DescripcionArticulo`, `NumeroDeArticulos`, `FechaVenta`, `PlazoVenta`, `InteresSobreCompra`, `AbonoMensual`, `AbonoDelMes`, `SaldoALaFecha`, `InteresAdicionalMesAnt`, `InteresAdicional`, `NuevoInteresCargado`, `Vencido`, `SaldaCon`, `SaldaCon20SigMes`, `FechaUltimoMov`, `FechaDelConvenio`, `FechaConvenioDevolucion` |
| Ropa, Tiempo aire, CP Ropa | `NumeroTienda`, `FechaUltimaCompra`, `idu_Plazo`, `InteresSobreCompra`, `AbonoBase`, `AbonosDelMes`, `ComprasDelMes`, `SaldoALaFecha`, `InteresAdicionalMesAnt`, `InteresAdicionalMesAct`, `NuevoInteresCargado`, `Vencido2` / `Vencido`, `SaldaCon`, `SaldaCon20SigMes`, `FechaUltimoMov`, `fec_sinsaldo`, `MarcaConvDevolucion` |
| Préstamos, CP Préstamos | `NumeroTienda`, `NumeroFactura`, `FechaPrestamo`, `Plazo`, `InteresFinanciamiento`, `AbonoMensual`, `AbonosDelMes`, `PrestamosDelMes`, `SaldoALaFecha`, `InteresAdicional`, `NuevoInteresCargado` / `InteresAdicionalMes`, `Vencido`, `SaldaCon`, `FechaUltimoMov`, `FechaDelConvenio` |
| Dinero electrónico | `SaldoALaFecha`, `Desc_Corte` ("Corte N") |
| Cartera informativa | `Clv_situacion`, `num_causasituacion`, `fec_marcado`, `Nom_descsituacion` |
| Marcas de contacto | `clv_3MSC`, `clv_NTE`, `clv_NCE` |

Notas:

- Las columnas `CHAR` se reciben sin los espacios a la derecha.
- Las fechas `1900-01-01` se tratan como vacías.
- Si la cuenta no trae `NuevoInteresCargado`, el interés del mes se calcula como interés adicional − interés del mes
  anterior.

## Base de laboratorio

Se probó contra un SQL Server de laboratorio en Docker con el esquema completo de Carteras y datos parciales. Ahí:

- Dos clientes de las muestras reproducen el estado de cuenta impreso renglón por renglón.
- `spcQCDineroElectronico` solo genera `tmpDineroElectronico3` si `fun_ObtenerConfiguracionFlag(7)` vale 0 o 1, y
  la función lo lee de `CatFlag`, que llegó vacía. En el laboratorio se agregó `FlagKey = 7, OpcFlag = 0` (lee
  `MaeCarteraDineroElectronico`). Sin ese renglón, el `IF OBJECT_ID(...)` hace que el validador lo tome como
  "sin dinero electrónico" en vez de marcar error.
- Las tablas de leyendas (`catLeyendasCorreos`, `catLeyendasSupervisor`, `catleyendasemision`,
  `equivalenciasleyendas`...) llegaron vacías; ver [pendientes.md](pendientes.md).
- `cat_bonificacionescarteras` está vacía, así que los procedimientos devuelven bonificación 0 en muebles y ropa.
  El validador no depende de ese valor (ver [bonificaciones.md](bonificaciones.md)).

Las credenciales del laboratorio no forman parte de este repositorio.
