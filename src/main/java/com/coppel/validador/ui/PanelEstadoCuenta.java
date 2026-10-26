package com.coppel.validador.ui;

import com.coppel.validador.model.Cliente;
import com.coppel.validador.model.ExpedienteCliente;
import com.coppel.validador.validacion.CalculadoraEstadoCuenta;
import com.coppel.validador.validacion.EstadoCuentaEsperado;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Bonificacion;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Renglon;
import com.coppel.validador.validacion.EstadoCuentaEsperado.Totales;
import com.coppel.validador.validacion.Formato;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.util.Locale;

/**
 * Dibuja el estado de cuenta ESPERADO con el mismo acomodo que el impreso, para compararlo
 * a la vista contra la muestra. Solo presenta datos: los cálculos vienen de la capa de validación.
 */
public class PanelEstadoCuenta extends JPanel {

    /** Colores del estado de cuenta para cada tema. */
    private record Paleta(String azul, String gris, String amarillo, String recuadro, String texto, String tenue,
                          String alertaFondo, String alertaTexto) {
        static final Paleta CLARA = new Paleta("#1f3fbf", "#e6e8ee", "#fff6c7", "#ffffff", "#000000", "#666666",
                "#fde2e2", "#8a1010");
        static final Paleta OSCURA = new Paleta("#3b5bdb", "#3a3d44", "#4a4228", "#2b2d31", "#e6e6e6", "#9a9a9a",
                "#5a2626", "#ffb4b4");

        static Paleta actual() {
            return FlatLaf.isLafDark() ? OSCURA : CLARA;
        }
    }

    private final JEditorPane html = new JEditorPane("text/html", "");
    private ExpedienteCliente expediente;
    private EstadoCuentaEsperado esperado;

    /** La leyenda de mercadotecnia [17] cambia cada mes y se valida contra la carpeta compartida. */
    private static final String CARPETA_MERCADOTECNIA =
            "\\Calidad Sist Carteras\\Productos Calidad\\México\\Especiales\\Leyendas Mercadotecnia\\<año>";

    public PanelEstadoCuenta() {
        super(new BorderLayout());
        html.setEditable(false);
        add(new JScrollPane(html), BorderLayout.CENTER);
        limpiar();
    }

    public void limpiar() {
        expediente = null;
        esperado = null;
        pintar();
    }

    public void mostrar(ExpedienteCliente exp, EstadoCuentaEsperado esp) {
        expediente = exp;
        esperado = esp;
        pintar();
    }

    /** Al cambiar de tema (FlatLaf.updateUI) se vuelve a pintar con los colores nuevos. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (html != null) { // updateUI() también se llama desde el constructor de JPanel
            pintar();
        }
    }

    private void pintar() {
        Paleta p = Paleta.actual();
        if (expediente == null) {
            html.setText("<html><body style='font-family:sans-serif;padding:16px;color:" + p.tenue() + "'>"
                    + "Consulta un cliente para ver su estado de cuenta esperado.</body></html>");
        } else {
            html.setText(armar(expediente, esperado, p));
        }
        html.setCaretPosition(0);
    }

    private static String armar(ExpedienteCliente exp, EstadoCuentaEsperado esp, Paleta p) {
        Cliente c = exp.cliente();
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:10px;margin:8px;color:")
                .append(p.texto()).append("'>");

        for (String alerta : esp.alertas()) {
            sb.append("<div style='background:").append(p.alertaFondo()).append(";color:").append(p.alertaTexto())
                    .append(";padding:4px;margin-bottom:4px'><b>⚠ ").append(e(alerta)).append("</b></div>");
        }

        // Encabezado: datos del cliente | QR | Pase a pagar
        sb.append("<table width='100%' cellspacing='0' cellpadding='3'><tr>");
        sb.append("<td valign='top' width='55%'>")
                .append("<font size='5' color='").append(p.azul()).append("'><b>Coppel</b></font>")
                .append("&nbsp;&nbsp;&nbsp;<font size='4'><b>Estado de Cuenta</b></font> <font size='5'><b>")
                .append(e(esp.asteriscos())).append("</b></font><br><br>")
                .append("<b>").append(fijo(c.nombre())).append("</b>");
        for (String renglon : Formato.domicilio(c)) {
            sb.append("<br>").append(fijo(renglon));
        }
        // El teléfono del cliente no se imprime: el impresor solo pinta nombre, direccion1 y direccion2, y la única
        // muestra con teléfono de casa no lo trae
        // Tres renglones de claves, como en el impreso. El hueco de en medio es el folio (FolioEstadoDeCuenta)
        sb.append("<br><br><table cellspacing='0' cellpadding='0'><tr>")
                .append(clave(c.numeroCliente() + c.digito())).append(clave(Formato.cadenaCobranza(c)))
                .append(clave(c.jefeGrupo())).append("<td></td></tr><tr>")
                .append(clave(c.supervisor())).append(clave("    "))
                .append(clave(Formato.centro(c.centro())))
                .append("<td nowrap>&nbsp;&nbsp;&nbsp;&nbsp;").append(e(esp.situacionEspecial())).append("</td></tr><tr>")
                .append(clave(c.inicialCiudad().isEmpty() ? "?" : c.inicialCiudad())).append(clave(Formato.zona(c.zona())))
                .append("<td></td><td></td></tr></table></td>");
        sb.append("<td valign='middle' align='center' width='12%' style='border:1px dashed #999'>[ QR ]</td>");
        sb.append("<td valign='top' width='33%'><table width='100%' cellspacing='0' cellpadding='3' bgcolor='")
                .append(p.gris()).append("'>")
                .append("<tr><td align='center'><b>Pase a pagar</b></td></tr>")
                .append("<tr><td align='center' bgcolor='" + p.recuadro() + "'><font size='6'><b>$ ")
                .append(Formato.importeConCero(esp.totales().paseAPagar())).append("</b></font></td></tr>")
                .append("<tr><td bgcolor='" + p.recuadro() + "'>")
                .append(esp.favorPagarAntesDe().isEmpty() ? "&nbsp;"
                        : esp.favorPagarAntesDe().equals("INMEDIATO") ? "Favor de pagar de <b>INMEDIATO</b>"
                        : "Favor de pagar antes de: <b>" + e(esp.favorPagarAntesDe()) + "</b>")
                .append("</td></tr><tr><td bgcolor='" + p.recuadro() + "'>")
                .append(esp.favorPagarAntesDe().isEmpty() ? "Fecha: <b>" : "Solo incluye abonos hasta: <b>")
                .append(e(esp.soloIncluyeAbonosHasta())).append("</b></td></tr>")
                .append("<tr><td align='center'><b>Dinero Electrónico</b></td></tr>")
                .append("<tr><td align='center' bgcolor='" + p.recuadro() + "'>Saldo actual<br><b>")
                .append(esp.saldoDineroElectronico() == 0 ? "&nbsp;" : "$ " + Formato.importeConCero(esp.saldoDineroElectronico()))
                .append("</b><br>Meses de abono puntual: ").append(monedas(esp.monedasAbonoPuntual()))
                .append("</td></tr></table></td>");
        sb.append("</tr></table>");

        // Sus compras al detalle
        String[] titulos = {"Artículos", "Fecha de compra", "Usted debía", "Menos abonos", "Más sus compras",
                "Interés adicional", "Usted debe", "Páguelo con solo", "Abono mensual", "Atrasado", "Pase a pagar",
                "Fecha de último abono"};
        sb.append("<br><table width='100%' cellspacing='0' cellpadding='3' border='0'><tr>");
        for (int i = 0; i < titulos.length; i++) {
            boolean destacado = i == 0 || i == 7 || i == 8 || i == 10;
            sb.append("<th bgcolor='").append(destacado ? p.azul() : p.gris()).append("'><font color='")
                    .append(destacado ? "white" : p.texto()).append("'>").append(titulos[i]).append("</font></th>");
        }
        sb.append("</tr>");
        for (Renglon r : esp.renglonesVisibles()) {
            sb.append("<tr>")
                    .append(celda(r.articulo() + (r.cuenta().domiciliada() ? " *" : ""), "left", p.amarillo()))
                    .append(celda(r.fechaCompra(), "center", null))
                    .append(celda(Formato.importe(r.ustedDebia()), "right", null))
                    .append(celda(Formato.importe(r.menosAbonos()), "right", null))
                    .append(celda(Formato.importe(r.masCompras()), "right", null))
                    .append(celda(Formato.importe(r.interesAdicional()), "right", null))
                    .append(celda(Formato.importe(r.ustedDebe()), "right", null))
                    .append(celda(Formato.importe(r.pagueloConSolo()), "right", p.amarillo()))
                    .append(celda(Formato.importe(r.abonoMensual()), "right", null))
                    .append(celda(Formato.importe(r.atrasado()), "right", null))
                    .append(celda(Formato.importe(r.paseAPagar()), "right", p.amarillo()))
                    .append(celda(r.fechaUltimoAbono(), "center", null))
                    .append("</tr>");
        }
        Totales t = esp.totales();
        sb.append("<tr><td colspan='2' align='right'><b>TOTALES $</b></td>")
                .append(total(t.ustedDebia())).append(total(t.menosAbonos())).append(total(t.masCompras()))
                .append(total(t.interesAdicional())).append(total(t.ustedDebe())).append(total(t.pagueloConSolo()))
                .append(total(t.abonoMensual())).append(total(t.atrasado())).append(total(t.paseAPagar()))
                .append("<td>").append(esp.domiciliada() ? "*_Domiciliada" : "").append("</td></tr>");
        sb.append("</table>");
        if (esp.renglones().size() > EstadoCuentaEsperado.MAX_RENGLONES) {
            sb.append("<i>Se muestran ").append(EstadoCuentaEsperado.MAX_RENGLONES).append(" de ")
                    .append(esp.renglones().size()).append(" cuentas; los totales incluyen todas.</i>");
        }

        // Pie
        sb.append("<br><table width='100%' cellspacing='4' cellpadding='4'><tr>")
                .append("<td width='50%' bgcolor='").append(p.gris()).append("'><font face='monospaced'>")
                .append(e(esp.facturas())).append("</font>")
                .append(esp.datosConvenio().isEmpty() ? "" : "&nbsp;&nbsp;&nbsp;&nbsp;" + e(esp.datosConvenio()))
                .append("</td>")
                .append("<td width='50%' bgcolor='").append(p.gris()).append("'><font face='monospaced'>")
                .append(e(esp.interesesCartera()).replace(" ", "&nbsp;")).append("</font></td></tr><tr>")
                .append("<td valign='top' bgcolor='").append(p.gris()).append("'>");
        if (!esp.marcaContacto().isEmpty()) {
            sb.append(e(esp.marcaContacto())).append("<br>");
        }
        // Los textos del aviso de las muestras 2026 no están en catLeyendasCorreos (la emisión los toma de otra fuente):
        // se valida a ojo, como la leyenda de mercadotecnia. Para retomar el aviso calculado con el catálogo:
        // sb.append("AVISO IMPORTANTE:<br>").append(esp.avisoImportante().isEmpty()
        //         ? "<font color='" + p.tenue() + "'><i>(" + e(esp.explicacionAviso()) + ")</i></font>"
        //         : e(esp.avisoImportante()));
        sb.append("AVISO IMPORTANTE:<br>")
                .append("<font color='").append(p.tenue()).append("'><i>Leyenda de cobranza: se valida contra la ")
                .append("muestra impresa; debe corresponder al atraso del cliente (al corriente: cliente cumplido; ")
                .append("con atraso: retraso, buró o área legal según los abonos vencidos).</i></font>");
        sb.append("</td><td valign='top' bgcolor='").append(p.gris()).append("'>")
                .append("<font color='").append(p.tenue()).append("'><i>Leyenda de mercadotecnia: se valida contra ")
                .append("la carpeta compartida ").append(e(CARPETA_MERCADOTECNIA))
                .append(" (las capturadas del 16 al 20 del mes).</i></font>")
                .append("<br><br>Dudas o comentarios llama al Centro de Atención al Cliente:800 220 7735</td></tr></table>");

        // Lo que no se imprime pero ayuda a revisar
        sb.append("<br><b>Notas del cálculo</b><ul>")
                .append("<li>Favor de pagar [15]: ").append(e(esp.explicacionFavorPagar())).append("</li>")
                .append("<li>Asteriscos [16]: ").append(e(esp.explicacionAsteriscos())).append("</li>")
                .append("<li>Aviso importante (catLeyendasCorreos, solo de referencia): ")
                .append(e(esp.explicacionAviso())).append("</li>")
                .append("<li>Invitaciones: si la emisión le asignó una invitación al cliente, el impresor la pinta ")
                .append("en lugar de \"AVISO IMPORTANTE\" y de la marca (con atrasado, <i>leyinvitacionsdo1vdp1</i>; ")
                .append("sin atrasado, <i>leyinvitacionsdo0vdp0</i>), y con invitación tipológica cambia la leyenda de ")
                .append("mercadotecnia por la de préstamos. Esos datos solo están en Emision20: si la muestra trae una ")
                .append("invitación, el aviso no se puede comparar.</li>")
                .append("<li>Citatorio [18] según el manual: ")
                .append(esp.citatorio().isEmpty() ? "no aplica" : e(esp.citatorio()))
                .append(" (").append(e(esp.explicacionCitatorio())).append(").</li>")
                .append("<li>Situación especial en el directorio: ")
                .append(e(c.situacionEspecial() + c.causaSituacion()))
                .append(esp.situacionEspecial().isEmpty() ? " (no se imprime)" : "").append("</li>")
                .append("<li>El hueco entre supervisor y centro es el folio del estado de cuenta ")
                .append("(<i>FolioEstadoDeCuenta</i>): un consecutivo por ciudad y centro que asigna Emision20. ")
                .append("Depende del lote, no se valida.</li>")
                .append("</ul>");

        // Detalle de bonificaciones
        sb.append("<br><b>Cálculo de bonificaciones [7]</b><table cellspacing='0' cellpadding='2'>")
                .append("<tr><th align='left'>Cuenta</th><th>Base</th><th>Plazo</th><th>Días</th><th>Factor</th>")
                .append("<th>Bonificación</th><th align='left'>&nbsp;&nbsp;&nbsp;Fórmula</th><th align='left'>Nota</th></tr>");
        for (Renglon r : esp.renglones()) {
            Bonificacion b = r.bonificacion();
            sb.append("<tr><td>").append(e(r.articulo())).append("</td>")
                    .append("<td align='right'>").append(Formato.importeConCero(b.base())).append("</td>")
                    .append("<td align='right'>").append(b.plazo()).append("</td>")
                    .append("<td align='right'>").append(b.dias()).append("</td>")
                    .append("<td align='right'>").append(String.format(Locale.US, "%.4f", b.factor())).append("</td>")
                    .append("<td align='right'>").append(Formato.importeConCero(b.importe())).append("</td>")
                    .append("<td>&nbsp;&nbsp;&nbsp;").append(e(formula(b))).append("</td>")
                    .append("<td>").append(e(b.nota())).append("</td></tr>");
        }
        sb.append("</table>");
        if (!esp.notas().isEmpty()) {
            sb.append("<font color='").append(p.tenue()).append("'><ul>");
            for (String nota : esp.notas()) {
                sb.append("<li>").append(e(nota)).append("</li>");
            }
            sb.append("</ul></font>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String celda(String texto, String alineacion, String fondo) {
        return "<td nowrap align='" + alineacion + "'" + (fondo != null ? " bgcolor='" + fondo + "'" : "")
                + " style='font-size:11px'><b>" + e(texto) + "</b></td>";
    }

    private static String total(long valor) {
        return "<td nowrap align='right' style='font-size:11px'><b>" + Formato.importe(valor) + "</b></td>";
    }

    /** Una clave del bloque de datos del cliente, con separación a la derecha. */
    private static String clave(String texto) {
        return "<td nowrap><font face='monospaced'>" + fijo(texto) + "</font>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>";
    }

    /** Texto que conserva los espacios dobles del impreso. */
    private static String fijo(String texto) {
        return e(texto).replace("  ", "&nbsp; ");
    }

    private static String monedas(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CalculadoraEstadoCuenta.MAX_MONEDAS; i++) {
            sb.append(i < n ? "● " : "○ ");
        }
        return sb.toString();
    }

    /** Escapa texto para HTML. */
    private static String e(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    /** Base × factor con el resultado exacto (préstamos trunca, ropa y muebles redondean; ver la columna Bonificación). */
    static String formula(Bonificacion b) {
        if (b.base() == 0 && b.factor() == 0) {
            return "";
        }
        return Formato.importeConCero(b.base()) + " × " + String.format(Locale.US, "%.4f", b.factor()) + " = "
                + String.format(Locale.US, "%,.2f", b.base() * b.factor());
    }
}
