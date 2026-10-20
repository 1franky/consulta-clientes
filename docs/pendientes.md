# Pendientes y decisiones abiertas

## Reglas por confirmar con Control de Calidad

| Tema | Situación actual |
|---|---|
| Cuentas perdidas (CP) | Se calculan igual que las normales, sin bonificación y con `CP` en "fecha de último abono". En una muestra, el "usted debe" de una cuenta perdida no coincide con ninguna columna del listado. |
| Deuda Bancoppel y Reestructura | No están en el catálogo de bonificaciones; se usa el `SaldaCon` del listado. El Excel de Calidad trae un factor de ejemplo (8.01% y 0.41) sin tabla completa. |
| Citatorio (CIT) [18] | El validador lo calcula según el manual, pero ninguna muestra del formato 2026 lo imprime. Confirmar si sigue vigente. |
| Cadena de cobranza | El manual menciona el año de nacimiento, pero las muestras no lo imprimen. |
| Situación especial | Solo se imprime I35 en las muestras (I23, I41 y S50 no). Falta la lista completa de las que se imprimen (`SITUACIONES_IMPRESAS` en `CalculadoraEstadoCuenta`). |
| Aviso importante | Las leyendas salen de 12 muestras (puntualidad Z, marcas 3MSC/NCE, puntualidad A). El catálogo de la emisión es `catLeyendasCorreos` (`PuntualidadCliente`, `AbonosMesesVencidos`, `Leyenda1`, `Leyenda2`, `ClaveLeyenda`), pero en la base de laboratorio está vacío. Pedir `SELECT * FROM dbo.catLeyendasCorreos` a producción para confirmar las reglas y cubrir B/C/D sin marca y NTE. |
| Teléfono en el complemento | Una muestra omite un teléfono de 10 dígitos capturado en el complemento; se generalizó a todos. |
| Domiciliado [21] y domicilio certificado | No se identificaron las columnas de origen. |
| Asteriscos [16] | La regla 3* del manual es ambigua ("atraso ≥ 50% del abono base y ≤ 75% dos abonos base"). Se interpretó como 50% de un abono a 75% de dos abonos. |
| Atrasado [9] | El manual dice "= VENCIDO", pero las muestras muestran vencido + interés adicional (con tope en "usted debe"). Se implementó lo de las muestras. |

## Mejoras propuestas

1. **Leer el catálogo de bonificaciones de la base** (`cat_bonificacionescarteras`, `cat_bonificacionesPrestamos`)
   y usar el CSV solo si la tabla está vacía o no hay conexión. Evita tener dos copias del catálogo.
2. **Avisar cuando no coincidan:** si la bonificación del validador difiere del `SaldaCon20SigMes` del procedimiento,
   mostrar una advertencia en el punto [7]. Solo tiene sentido contra producción.
3. **Leyenda de mercadotecnia [17]:** hoy se captura a mano en `config.properties`. No está en la base Carteras: el
   manual dice que se toma de la carpeta `Calidad Sist Carteras\Productos Calidad\México\Especiales\Leyendas
   Mercadotecnia\<año>`. Podría leerse de ahí.
4. **Listado de domiciliación [21]:** cotejar automáticamente contra el listado de clientes domiciliados.

## Riesgos conocidos

- Los procedimientos usan tablas `tmp*` compartidas. El `ROLLBACK` por listado evita lecturas cruzadas, pero una
  consulta larga hace esperar a las demás (como máximo 60 s por procedimiento).
- Los procedimientos usan la fecha de `catFechas`. Si se valida un corte distinto al de `catFechas`, los datos de
  la cuenta corresponden a `catFechas` aunque la pantalla diga otra fecha.
