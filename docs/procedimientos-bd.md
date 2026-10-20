# Procedimientos de la base Carteras

El validador obtiene los listados con los mismos procedimientos que usa *VbnListadoCarteras*. Cada uno se configura
en `config.properties` (`sp.<clave>`); `:cliente` se reemplaza por el número de cliente y `:corte` por la fecha de
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

Los procedimientos toman la fecha de `catFechas`, no la de la pantalla. Por eso sus columnas calculadas (`SaldaCon`,
`BonificacionMuebles`, `MesesTranscurridos`...) corresponden a la fecha de `catFechas`. El validador recalcula la
bonificación con la fecha de corte de la pantalla (ver [bonificaciones.md](bonificaciones.md)).

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
- `spcQCDineroElectronico` no genera `tmpDineroElectronico3`, porque el flag de configuración 7
  (`fun_ObtenerConfiguracionFlag(7)`) está en NULL: el procedimiento solo crea la tabla si vale 0 o 1. Con el
  `IF OBJECT_ID(...)` el validador lo toma como "sin dinero electrónico" en vez de marcar error.
- `cat_bonificacionescarteras` está vacía, así que los procedimientos devuelven bonificación 0 en muebles y ropa.
  El validador no depende de ese valor (ver [bonificaciones.md](bonificaciones.md)).

Las credenciales del laboratorio no forman parte de este repositorio.
