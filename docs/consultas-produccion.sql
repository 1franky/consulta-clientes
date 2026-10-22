-- Consultas de solo lectura para traer de producción lo que el laboratorio no tiene.
-- Reemplazar <clientes> por la lista de clientes de las muestras (por ejemplo 11111111, 22222222).
-- Los resultados traen datos personales: guardarlos fuera del repositorio.

-- =====================================================================================
-- 1. SQL Server, base Carteras
-- =====================================================================================

-- 1.1 Catálogo del que la emisión toma la leyenda de AVISO IMPORTANTE (fnPc2GeCalculaLeyendas).
--     El del laboratorio no tiene los textos de 2026 ni los renglones S (recoger / definir).
SELECT * FROM dbo.catLeyendasCorreos ORDER BY PuntualidadCliente, AbonosMesesVencidos, ClaveLeyenda;

-- 1.2 Otras leyendas que usa la emisión (área B Bancoppel, P publicidad, etc.).
SELECT * FROM dbo.CatLeyendasEmision ORDER BY ClaveArea, ClaveLeyenda;

-- 1.3 Fechas de corte de la emisión (spGeFechaCorte); vacía en el laboratorio.
SELECT * FROM dbo.catFechasCorte;
SELECT * FROM dbo.catFechas;

-- 1.4 Salario para pasar a pesos las cuentas perdidas y parámetros de ropa.
SELECT * FROM dbo.cat_salariosminimosctasperdidas ORDER BY fec_salario DESC;
SELECT * FROM dbo.cat_parametrosprocesos WHERE idu_parametro IN (2, 37, 38);

-- 1.5 Tablas que deja la emisión correos (spPc2GeEmisionCorreos) si producción las conserva hasta el
--     siguiente corte: traen el cálculo exacto de la emisión por cliente y por cuenta.
IF OBJECT_ID('dbo.edcEmisionCorreos') IS NOT NULL
    SELECT * FROM dbo.edcEmisionCorreos WHERE NumeroCliente IN (<clientes>);
IF OBJECT_ID('dbo.tmpPc2GeCalculaLeyendas') IS NOT NULL
    SELECT * FROM dbo.tmpPc2GeCalculaLeyendas WHERE NumeroCliente IN (<clientes>);
IF OBJECT_ID('dbo.tmpPc2GeCarVdoCalculosEdoCta') IS NOT NULL
    SELECT * FROM dbo.tmpPc2GeCarVdoCalculosEdoCta WHERE NumeroCliente IN (<clientes>);
SELECT name, crdate FROM sysobjects WHERE name IN ('edcEmisionCorreos', 'tmpPc2GeCalculaLeyendas',
    'tmpPc2GeCarVdoCalculosEdoCta');

-- =====================================================================================
-- 2. PostgreSQL, base del impresor (la que lee ImpresionXCentro)
-- =====================================================================================

-- 2.1 Encabezado tal como se imprimió. Cada renglón trae dos clientes: los campos normales y los mod0*.
--     leyenda = AVISO IMPORTANTE; datospuntualidadcliente posición 12 = asteriscos (1 *, 2 **, 3 ***);
--     leyinvitacion* / invitaciontipologica = invitaciones que reemplazan el aviso.
SELECT numerociudad, centro, num_hoja, num_folio,
       numerocliente, folioestadodecuenta, datospuntualidadcliente,
       substring(datospuntualidadcliente, 12, 1) AS asteriscos,
       clv_clientenocontactado, leyenda, leyinvitacionsdo1vdp1, leyinvitacionsdo0vdp0,
       invitaciontipologica, leyinvitacion, leypub, tipoencuesta, flagpreautorizado,
       ir, ip, ib, im, ie, it, m1r, m1m, m1e,
       usteddebia, usteddebe, paguelaconsolo, abonomensual, atrasado, paseapagar,
       mod0numerocliente, mod0folioestadodecuenta, mod0datospuntualidadcliente,
       substring(mod0datospuntualidadcliente, 12, 1) AS mod0asteriscos,
       mod0leyenda, mod0leyinvitacionsdo1vdp1, mod0leyinvitacionsdo0vdp0, mod0invitaciontipologica
  FROM emisionfinalreportetotal
 WHERE numerocliente IN (<clientes>) OR mod0numerocliente IN (<clientes>)
 ORDER BY num_hoja, num_folio;

-- 2.2 Detalle por cuenta tal como se imprimió (mismo orden que el impresor).
SELECT *
  FROM saldosemisioncorreos
 WHERE numerocliente IN (<clientes>)
 ORDER BY numerocliente, orden, fechacompra DESC, numerofactura DESC;

-- 2.3 Rangos de folios y hojas por centro (para revisar el consecutivo).
SELECT *
  FROM tmptabuladoedocta
 WHERE (numerociudad, centro) IN (SELECT numerociudad, centro FROM emisionfinalreportetotal
                                   WHERE numerocliente IN (<clientes>) OR mod0numerocliente IN (<clientes>));
