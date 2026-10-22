# Bonificaciones

## Cómo se calcula

El validador recalcula la bonificación por su cuenta; **no** usa la que devuelven los procedimientos.

```
días         = (fecha de corte + 1 mes) − fecha de compra
factor       = catálogo de la base, o bonificaciones.csv sin base (cartera, plazo, días, vigencia)
bonificación = round(base × factor), sin pasar del saldo a la fecha
páguelo      = usted debe − bonificación
```

Las reglas detalladas están en [reglas-validacion.md](reglas-validacion.md#7-bonificación). Reproducen lo que hacen en
la base `fun_calculabonificacion`, `fun_obtieneporcentajebonificacion`, `fnGeSaldaCon` y
`proc_gecalculabonificacionprestamos`.

## Por qué no usa la bonificación del procedimiento

Los procedimientos devuelven dos tipos de columnas:

1. **Datos de la cuenta**, leídos tal cual de las tablas de cartera: `InteresSobreCompra`, `FechaVenta`,
   `PlazoVenta`, `SaldoALaFecha`, `InteresAdicional`, `Vencido`. No dependen del catálogo de bonificaciones.
2. **Resultados calculados** con el catálogo de la base: `BonificacionMuebles`, `SaldaCon`, `SaldaCon20SigMes`,
   `prc_bonificacion`.

El validador toma solo los datos del punto 1 y hace el cálculo con el catálogo. Los del punto 2 aparecen solo como
referencia en "Origen del valor esperado". Así el validador no copia el resultado del sistema que está validando:
si el procedimiento se equivoca, la diferencia aparece contra el impreso.

## De dónde sale el catálogo

Al abrir la aplicación (`modo=sqlserver`) se leen las mismas tablas que usa `fun_obtieneporcentajebonificacion`,
con la sentencia `sp.bonificaciones`:

| Cartera | Tabla | Renglones |
|---|---|---|
| Ropa (`idu_cartera` 1) y muebles (2) | `cat_bonificacionescarteras` | Los diarios: `num_mesestranscurridos = 0`, por `num_diastranscurridos`. |
| Préstamos | `cat_bonificacionesPrestamos` | `prc_bonificacion`; y `prc_bonificacionNueva` para compras desde el `fec_corte` del renglón −1 (bloque 0). |

Si no hay conexión o la sentencia no devuelve renglones, se usa `bonificaciones.csv`.

### Muebles por tipo de producto

Desde dic-2025 `spcQCMuebles` calcula la bonificación de muebles con `proc_calculabonificacionmuebles`, y el validador
hace lo mismo con la configuración de `sp.muebles*`:

1. **Plazo:** se agrupa como `fun_validarplazo` (menos de 18 → 12, 18–23 → 18, 24–29 → 24, 30–35 → 30, 36 o más → 36).
2. **Fecha de compra:** día de pago = día siguiente a la venta + 1 mes (`fun_calculardiadepago`; venta futura: + 2 meses
   y al menos 60 días). Si el siguiente corte no pasa del día de pago, la fecha se recorre esa diferencia menos los
   días del mes del pago. Si pasa: venta futura (`cat_plazosmuebles.clv_flagventafuturo`) + 1 mes; tipo de producto
   con días de ajuste (`cat_configuracion_muebles_beneficios`) + esos días; si no, la fecha de venta.
3. **Tabla:**

| Caso | Tabla | Redondeo |
|---|---|---|
| Negocio afiliado: tipo de producto de `catnegocios` con `TipoCta = 2`, venta desde `FECHA_BONIFICACION_NA_MP` y sin `clv_bonifMuebles` vigente en `catTipoProducto` | `cat_bonificacionescoppelpay` | Trunca |
| Tipo con 0 días de ajuste (y no moto) | `cat_bonificacion_muebles_beneficios` | Redondea |
| Moto (tipo 55) o tipo con días de ajuste | `CAT_BONIFICACIONESMOTOS` | Redondea |
| Los demás | `cat_bonificacionescarteras` (muebles, al menos 1 día) | Redondea |

En `CAT_BONIFICACIONESMOTOS`, plazo 12, días 31 a 92 hay dos renglones con factor distinto (bloques 0 y 1). La función
toma el primero que encuentra la base; en el laboratorio es el mayor (1.0), y el validador usa ese. Comprobado con la
moto de una muestra: 8,400 × 0.0073 = 61, igual que el impreso. En la base de laboratorio el
catálogo de la base y el CSV dan el mismo "Páguelo con solo" para los 12 clientes de prueba. El CSV difiere en ropa
plazo 12: días 7 a 30 (el CSV trae 0.9 fijo; la base baja de 0.898 a 0.852), día 59 y día 118. La base es la que
vale, porque es la que usan los procedimientos.

### Excepciones

- **Deuda Bancoppel, Reestructura o un plazo que no está en el catálogo:** no hay factor, así que se usa
  `usted debe − SaldaCon` del listado. Aquí **sí** depende del procedimiento, y la pantalla lo indica con una nota
  para verificarlo a mano.

## El archivo `bonificaciones.csv` (respaldo)

```
cartera,plazo,dias_transcurridos,factor,vigente_desde
ROPA,12,37,0.82,1901-01-01
MUEBLES,12,290,0.0587,1901-01-01
PRESTAMOS,12,350,0.0014,1901-01-01
PRESTAMOS,12,350,0.0015,2024-06-20
```

| Columna | Significado |
|---|---|
| `cartera` | `ROPA`, `MUEBLES` o `PRESTAMOS`. |
| `plazo` | Plazo en meses (`idu_plazo` / `num_plazo`). |
| `dias_transcurridos` | Días desde la compra hasta el siguiente corte. Pasado el último día del plazo, el factor es 0. |
| `factor` | Porcentaje de bonificación como fracción (0.0587 = 5.87%). |
| `vigente_desde` | `fec_iniciobloque`: el bloque aplica a compras con fecha ≥ a esta. |

El contenido inicial se generó del Excel *BONIFICACIONES ROPA, MUEBLES Y PRESTAMOS NUEVAS.xlsx*:

- **Ropa:** hoja "Ropa", plazo 12.
- **Muebles:** hoja "Muebles", renglones por días (`num_mesestranscurridos = 0`), plazos 12, 18, 24, 30 y 36.
- **Préstamos:** hoja "Nueva prestamos". `prc_bonificacion` va vigente desde 1901-01-01 y `prc_bonificacionNueva`
  desde 2024-06-20 (`fec_corte` del renglón −1 de `cat_bonificacionesPrestamos`), plazos 12, 18 y 24.

## Actualizar el respaldo (sin recompilar)

1. Abrir `bonificaciones.csv` junto a `ValidadorEdoCta.exe`.
2. **Agregar** los renglones nuevos con la fecha desde la que aplican en `vigente_desde`. No borrar los anteriores:
   las compras hechas antes de esa fecha los siguen usando.
3. Guardar y volver a abrir la aplicación. La barra de estado dice qué catálogo se cargó.

Con base de datos no hace falta: el catálogo se lee de las tablas cada vez que se abre la aplicación.

Si el archivo no existe, se usa la copia incluida en el programa (`src/main/resources/bonificaciones.csv`). Si un
renglón es inválido, la aplicación no abre y dice qué renglón falló. Para usar otra ruta:
`bonificaciones.archivo=` en `config.properties`.

Para que la copia incluida también cambie, editar `src/main/resources/bonificaciones.csv` y recompilar; `build.bat`
la copia junto al `.exe`.
