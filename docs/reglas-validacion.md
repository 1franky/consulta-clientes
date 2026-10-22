# Reglas de validación

Cómo calcula el validador cada dato del **Estado de Cuenta Correos** y de dónde sale. Los números entre corchetes
son los puntos del "Chequeo" del manual *1. Estado de Cuenta Correos*.

- El cálculo está en `validacion/CalculadoraEstadoCuenta.java`.
- Los formatos impresos (fechas, importes, cadena, centro, zona, domicilio) están en `validacion/Formato.java`.

Todas las reglas se compararon contra estados de cuenta impresos y escaneados (corte al 20-Sep-2026). Las que
**no** vienen así en el manual, sino que se dedujeron de las muestras o del código de los procedimientos, se marcan
con *(deducida)*. Las marcadas *(impresor)* salen del código del programa que imprime los estados de cuenta
(`EstadoCta`, `ImpresionXCentro.cs`), que solo acomoda en la hoja lo que la emisión ya calculó.

## Fechas

| Dato | Regla |
|---|---|
| Fecha de corte | Último día 20 hasta `catFechas` (la fecha de los datos). No se captura; en modo demo es 20/09/2026. |
| [2] Solo incluye abonos hasta | Fecha de corte, formato `20-Septiembre-2026`. |
| Siguiente corte | Fecha de corte + 1 mes. Se usa en [7] y [15]. |

## Datos del cliente [3]

| Dato | Origen (`spcQCDirectorio`) |
|---|---|
| Nombre | `NombreCompleto`, con sus espacios dobles. Si viene en NULL (el SP lo deja así cuando falta `cunombre2`), se arma con `cunombre1`, `cunombre2`, `cuapellidopaterno` y `cuapellidomaterno`. |
| Domicilio *(deducida)* | `Calle # NumeroCasa DepartamentoCliente Rumbo cuentrecalles cucomplemento`, separados por un espacio aunque la parte esté vacía (`# 110   loc`). Del complemento se quitan los teléfonos de 10 dígitos. Se comprobó en las 12 muestras. |
| Domicilio, renglones *(impresor)* | Si pasa de 50 caracteres se corta en el último espacio de los primeros 50; el resto va en el renglón siguiente, hasta 45 caracteres y con `*` al final si no cabe. |
| Domicilio, colonia *(deducida)* | `Colonia` + `MunicipioZona`, partido igual que el domicilio si pasa de 50. |
| Teléfono *(deducida)* | Solo `Telefono` (casa); las muestras no imprimen el celular. |
| Número de cliente | Número + `Digito`. |
| Cadena de cobranza | Puntualidad + puntualidad activa (`spcQCCredito`) + `EstadoCivil` + `TipoDeCasa` + `Sexo` + `IngresoMensual` (2 dígitos) + año de `Antiguedad` (2 dígitos). Ej. `AACPF0322`. |
| Bloque de claves | Renglón 1: cliente + dígito, cadena, jefe de grupo. Renglón 2: supervisor, consecutivo, centro y situación especial. Renglón 3: ciudad abreviada, zona y tipo de encuesta (`tipoencuesta`, sin dato en los listados). |
| Jefe de grupo / supervisor | `jefegrupo` / `supervisor`. |
| Consecutivo *(deducida)* | Número entre supervisor y centro (2, 393, 1175...). Es el orden del estado de cuenta en el lote de impresión (sube con la zona y el domicilio), así que no se puede validar por cliente: el validador deja el hueco vacío. |
| Ciudad abreviada | `CatCiudades.InicialCiudad` de la ciudad del cliente: `ACYN` = Acayucan (`sp.inicialCiudad`). |
| Centro | `centro` con guion antes de los dos últimos dígitos: `500001` → `5000-01`. |
| Zona *(deducida)* | `NumeroCiudad-NumeroColonia`, con la colonia a 4 dígitos: `100-0200`. |
| Situación especial | Como `proc_generacadenasituacionescliente`: situaciones de `Mae_ClientesSituaciones` de tipo 1 o incluidas en `Ctl_SubprocesosSituaciones` para el proceso, con clave + causa + motivo + persona, separadas por coma (`sp.situacionesImpresas`). Se usa área 5, proceso 18, que es la configuración que coincide con las muestras (solo `I35`); falta confirmar el proceso de la emisión correos. |
| [4] Referencia | `NomCompletoRef`, `CalleReferencia`, `CasaDeReferencia`... Solo si el cliente tiene referencia. |

## Sus compras al detalle

Se pintan hasta 5 cuentas; ropa primero y después las demás en el orden de los listados. Los totales y el pase a
pagar consideran **todas** las cuentas. Las cuentas con convenio de devolución no se consideran.

| Columna | Regla |
|---|---|
| [5] Fecha de compra | `FechaVenta` (muebles), `FechaUltimaCompra` (ropa), `FechaPrestamo` (préstamos). |
| Usted debía *(deducida)* | Saldo a la fecha + int. adic. mes anterior + abonos del mes − compras del mes. |
| Menos abonos | `AbonoDelMes` / `AbonosDelMes`. |
| Más sus compras | `ComprasDelMes` / `PrestamosDelMes`. |
| Interés adicional *(deducida)* | `NuevoInteresCargado` (interés cargado en el mes). |
| [6] Usted debe | `SaldoALaFecha` + interés adicional total. |
| [7] Páguelo con solo | Usted debe − bonificación (ver abajo). |
| [8] Abono mensual | `AbonoMensual` (ropa y tiempo aire: `AbonoBase`). |
| [9] Atrasado *(deducida)* | Si hay vencido: vencido + interés adicional, con tope en "usted debe". Ropa usa `Vencido2`. |
| [10] Pase a pagar *(deducida en el tope)* | Abono mensual + atrasado, con tope en "usted debe". |
| [11] Fecha de último abono | `FechaUltimoMov`. Vacía si el último movimiento fue la compra del mes; `CP` en cuentas perdidas. |
| [12] TOTALES $ | Suma de cada columna (todas las cuentas). |

### [7] Bonificación

```
días         = (fecha de corte + 1 mes) − fecha de compra
factor       = cat_bonificacionescarteras / cat_bonificacionesPrestamos (bonificaciones.csv sin base)
bonificación = round(base × factor), sin pasar del saldo a la fecha
páguelo      = usted debe − bonificación
```

- **Base:** `InteresSobreCompra`; en préstamos, `InteresFinanciamiento`.
- **Vigencia:** se usa el bloque más reciente con `vigente_desde` ≤ fecha de compra (`fec_iniciobloque`).
  En préstamos, el factor nuevo (`prc_bonificacionNueva`) aplica a préstamos desde el 20-jun-2024.
- **Muebles:** al menos 1 día transcurrido.
- **Ropa:** si la cuenta se quedó sin saldo (`fec_sinsaldo`) 30 días o menos antes del siguiente corte, se bonifica
  el interés completo.
- **Cuenta perdida:** sin bonificación. El atraso **no** quita la bonificación: los procedimientos no revisan el
  vencido y las muestras traen cuentas con atraso y bonificación. Las cuentas viejas dan 0 porque ya pasó el plazo.
- **Muebles desde dic-2025** (`proc_calculabonificacionmuebles`): ajusta la fecha de compra por tipo de producto y
  usa funciones propias para motos y negocios afiliados. **Pendiente:** el validador usa el cálculo general.
- **Tiempo aire:** bonificación del mes × 1.
- **Sin catálogo** (Deuda Bancoppel, Reestructura, o un plazo que no está en el catálogo): se toma el `SaldaCon` del
  listado y la pantalla lo indica para verificarlo a mano.

Estas reglas salen de `fun_calculabonificacion`, `fun_obtieneporcentajebonificacion`, `fnGeSaldaCon` y
`proc_gecalculabonificacionprestamos`. Ver [bonificaciones.md](bonificaciones.md).

## Encabezado y pie

| Punto | Regla |
|---|---|
| [13] Pase a pagar | Total de la columna "Pase a pagar". |
| [15] Favor de pagar antes de *(impresor)* | `INMEDIATO` si la puntualidad es B, C, D, Z o N y el **atrasado** total es ≥ al abono total, o si es A y es ≥ 2 abonos. Si no, el día 20 del mes siguiente (también en diciembre). Sin pase a pagar solo se imprime "Fecha:" con el corte. Con A y atraso entre 1 y 2 abonos, el impresor deja el renglón en blanco en los estados de cuenta 1 y 3 de la hoja y pone la fecha en el 2 y el 4. |
| [16] Asteriscos | `**` cliente nuevo del mes; `*` al corriente (sin vencido); `***` puntualidad A con atraso entre 50% del abono y 75% de dos abonos; nada en otro caso. |
| Aviso importante *(deducida)* | En este orden: puntualidad Z: "SU CUENTA ESTA A PUNTO DE PASAR AL DEPARTAMENTO LEGAL..." y no se imprime la marca. "DEFINIR CON PAGOS" si alguna cuenta con saldo tiene plazo menor a 18 meses y 14 o más meses desde la compra (`fnPc2GeDeterminaLeyRecogerDefinirPagos`; no cuenta la de muebles que lleva la leyenda "recoger") o si se imprime la marca 3MSC (las 4 muestras con esta leyenda la traen; la regla de meses explica 3). Marca NCE: "AL CLIENTE YA COMENZAMOS A CARGARLE INTERESES ADICIONALES". Puntualidad A: "POR SU PUNTUALIDAD USTED PUEDE LLEVAR LO QUE GUSTE A CREDITO...". Otros casos: sin muestra, se revisa a mano. Coincide con las 12 muestras. |
| [18] Citatorio | Abonos vencidos = vencido ÷ abono total. A/B con 1–2: CIT # 1; C/D con 1–2: CIT # 7; 3–4: CIT # 2; 5–6: CIT # 3; 7 o más: CIT # 4. No aplica con convenio activo. **En el formato 2026 no se observa: pendiente de confirmar.** |
| [19] Facturas | Tienda (4 dígitos) + factura (6 dígitos) `*` número de artículos, de todas las cuentas menos ropa. |
| [20] Interés adicional por cartera *(impresor)* | En este orden: `IR`, `IP`, `IB`, `IM`, `IE`, `IT` (interés adicional total de ropa, préstamos, Deuda Bancoppel, muebles, reestructura y tiempo aire) y `R1`, `M1`, `E1` (`InteresAdicionalPrimerMes` de ropa, muebles y reestructura). Solo los que no son 0. |
| [21] Domiciliada *(impresor)* | `*_Domiciliada` bajo los totales si la descripción de algún artículo del detalle empieza con `*` (o la cuenta trae la columna `Domiciliada`). En los listados del laboratorio no aparece. |
| [22] Centro de Atención | Texto fijo: `Dudas o comentarios llama al Centro de Atención al Cliente:800 220 7735`. |
| [24] Monedas *(impresor)* | Número de abonos puntuales (`Desc_Corte` = "Corte N"), máximo 4. Sin saldo de dinero electrónico no se rellena ninguna. |
| Dinero electrónico | `SaldoALaFecha` de `tmpDineroElectronico3`; con saldo 0 no se imprime importe. |
| Marca de contacto *(impresor)* | `3MSC`, `NTE` o `NCE` según las banderas de `tmp_clientenocontactado`. Solo se imprime si el cliente debe y tiene atrasado. |
| [17] Leyenda de mercadotecnia | Se valida contra la carpeta compartida `\Calidad Sist Carteras\Productos Calidad\México\Especiales\Leyendas Mercadotecnia\<año>` (las capturadas del 16 al 20); el validador solo lo indica. |
| [14], [23], [25] | Revisión visual: teléfono de tienda, número de página y código QR. |

## Alertas

Se muestran en rojo arriba del estado de cuenta esperado:

- Situación especial L, M, X, F, P23 o P71–P74: no se le debe generar estado de cuenta.
- `NoEnviarEdoCta` sin vencido: el cliente pidió no recibirlo.
- Cuenta con convenio de devolución: no se considera.
- Sin saldo: no se le debe generar estado de cuenta.
- Más de 5 cuentas: se pintan 5, pero los totales incluyen todas.
