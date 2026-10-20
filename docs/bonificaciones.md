# Bonificaciones

## Cómo se calcula

El validador recalcula la bonificación por su cuenta; **no** usa la que devuelven los procedimientos.

```
días         = (fecha de corte + 1 mes) − fecha de compra
factor       = bonificaciones.csv (cartera, plazo, días, vigencia)
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

El validador toma solo los datos del punto 1 y hace el cálculo con el CSV. Los del punto 2 aparecen solo como
referencia en "Origen del valor esperado". Así:

- El validador no copia el resultado del sistema que está validando: si el procedimiento se equivoca, la
  diferencia aparece contra el impreso.
- Funciona aunque `cat_bonificacionescarteras` esté vacía (como en la base de laboratorio).

### Excepciones

- **Deuda Bancoppel, Reestructura o un plazo que no está en el CSV:** no hay factor, así que se usa
  `usted debe − SaldaCon` del listado. Aquí **sí** depende del procedimiento, y la pantalla lo indica con una nota
  para verificarlo a mano.
- **Dos copias del catálogo:** la base usa `cat_bonificacionescarteras` / `cat_bonificacionesPrestamos` y el
  validador usa `bonificaciones.csv`. Si cambian los factores en la base y no se actualiza el CSV, el validador
  marcaría como error un estado de cuenta correcto. Ver [pendientes.md](pendientes.md).

## El archivo `bonificaciones.csv`

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

## Actualizar los factores (sin recompilar)

1. Abrir `bonificaciones.csv` junto a `ValidadorEdoCta.exe`.
2. **Agregar** los renglones nuevos con la fecha desde la que aplican en `vigente_desde`. No borrar los anteriores:
   las compras hechas antes de esa fecha los siguen usando.
3. Guardar y volver a abrir la aplicación. La barra de estado dice qué catálogo se cargó.

Si el archivo no existe, se usa la copia incluida en el programa (`src/main/resources/bonificaciones.csv`). Si un
renglón es inválido, la aplicación no abre y dice qué renglón falló. Para usar otra ruta:
`bonificaciones.archivo=` en `config.properties`.

Para que la copia incluida también cambie, editar `src/main/resources/bonificaciones.csv` y recompilar; `build.bat`
la copia junto al `.exe`.
