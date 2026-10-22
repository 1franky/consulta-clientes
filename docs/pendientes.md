# Pendientes y decisiones abiertas

## Reglas por confirmar con Control de Calidad

| Tema | Situación actual |
|---|---|
| Cuentas perdidas (CP) | Se arman como la emisión y cuadran con las 4 muestras, pero con dos diferencias contra el código del laboratorio: el saldo en pesos sale truncado (el laboratorio redondea) y el abono mensual se imprime (el laboratorio lo deja en 0). Confirmar el salario de `cat_salariosminimosctasperdidas` en producción. |
| Orden del detalle | Falta confirmar el lugar de tiempo aire y reestructura, y de préstamos frente a Deuda Bancoppel (ninguna muestra los tiene juntos). |
| Motos con factor repetido | `CAT_BONIFICACIONESMOTOS` (plazo 12, días 31–92) tiene dos factores por día; la base toma uno sin orden definido. El validador usa el mayor, que es el que devuelve el laboratorio. Confirmar en producción. |
| Deuda Bancoppel y Reestructura | No están en el catálogo de bonificaciones; se usa el Salda Con del listado (`SaldaCon20SigMes` si los datos son del mes del corte). Con datos posteriores al corte el `SaldaCon` es a la fecha de los datos, no al siguiente corte. |
| Fecha de la bonificación de ropa | `spGeCartRopaVencBonifMasivo` calcula con `catFechasCorte` (identificador 1) y el de muebles le suma un mes. Confirmar qué fecha guarda `catFechasCorte` en producción (vacía en el laboratorio); las muestras coinciden con el siguiente corte. |
| Citatorio (CIT) [18] | El validador lo calcula según el manual, pero ninguna muestra del formato 2026 lo imprime. Confirmar si sigue vigente. |
| Cadena de cobranza | El manual menciona el año de nacimiento, pero las muestras no lo imprimen. |
| Situación especial | Se lee de la configuración `Ctl_SubprocesosSituaciones` con área 5, proceso 18 (solo I35, como las muestras). Confirmar que es el proceso de la emisión correos; el catálogo `Cat_ProcesosSituaciones` está vacío en el laboratorio. |
| Aviso importante | El validador elige el renglón de `catLeyendasCorreos` con la lógica de `fnPc2GeCalculaLeyendas`. El catálogo del laboratorio no reproduce los textos de las muestras 2026 (por ejemplo, Z imprime el texto de A/10 y A el de "LLEVAR LO QUE GUSTE A CREDITO", que no está): confirmar que producción tiene el catálogo actualizado. Faltan los renglones S (recoger y definir); se usa "DEFINIR CON PAGOS". Sin datos en el laboratorio: cliente nuevo (W/19), ultimátum (X/69), Enrique Coppel. En el convenio (W/0) X3, X5 y X6 quedan sin llenar. |
| Aviso importante, leyenda "recoger" | Las 3 muestras que cumplen la condición de "recoger" (S/15) imprimen "DEFINIR CON PAGOS"; la de puntualidad Z que también la cumple imprime el texto legal. Confirmar el texto de S/15 y S/16 en producción. |
| Tipo de encuesta y "CTE PREAUTORIZADO" | El impresor los pinta (`tipoencuesta`, `flagpreautorizado`), pero los listados no traen esos datos. |
| Teléfono en el complemento | Una muestra omite un teléfono de 10 dígitos capturado en el complemento; se generalizó a todos. |
| Domiciliado [21] y domicilio certificado | El impresor pinta "*_Domiciliada" cuando la descripción del artículo empieza con `*`; los listados del laboratorio no la traen así. Falta saber de dónde pone la emisión ese `*`. Domicilio certificado: sin columna identificada. |
| Asteriscos [16] | La regla 3* del manual es ambigua ("atraso ≥ 50% del abono base y ≤ 75% dos abonos base"). Se interpretó como 50% de un abono a 75% de dos abonos. |
| Atrasado [9] | Se usa `fnGeVencidoEmision` (vencido + interés adicional, sin tope). Una cuenta sin vencido pero con interés adicional imprimiría ese interés como atrasado; ninguna muestra tiene ese caso. |
| Abono base de ropa | `fun_minimoabonosaldoteoricoemision` recalcula el abono base (saldo / plazo + 0.5), pero una muestra de ropa (compra de $527 a 12 meses) imprime 43 donde el recálculo daría 44: el validador usa el `AbonoBase` del listado con los topes de la función. En tiempo aire sí se replica el recálculo (saldo / 5), sin muestras que lo confirmen. |
| Interés moratorio | La emisión resta el moratorio en "usted debía", calculado con `TasaInteres`, que ya no existe en las carteras. Se usa el interés cargado en el mes; las muestras cuadran así. |

## Mejoras propuestas

1. **Avisar cuando no coincidan:** si la bonificación del validador difiere del `SaldaCon20SigMes` del procedimiento,
   mostrar una advertencia en el punto [7]. Solo tiene sentido contra producción.
2. **Listado de domiciliación [21]:** cotejar automáticamente contra el listado de clientes domiciliados.

## Riesgos conocidos

- Los procedimientos usan tablas `tmp*` compartidas. El `ROLLBACK` por listado evita lecturas cruzadas, pero una
  consulta larga hace esperar a las demás (como máximo 60 s por procedimiento).
- Los procedimientos usan la fecha de `catFechas`. Si `catFechas` es posterior al corte, los listados incluyen
  movimientos posteriores al corte; la pantalla lo avisa con "Datos al ...". No se puede validar un corte anterior.
- En diciembre los procedimientos usan el día 24 en algunos cálculos; el impresor siempre pone el día 20 en "Favor de pagar antes de", igual que el validador.
