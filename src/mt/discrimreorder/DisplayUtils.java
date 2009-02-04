package mt.discrimreorder;

import java.io.*;

import mt.train.*;

/**
 * This class collects utils that can display information in
 * {@link AlignmentMatrix}.
 *
 * Some code are the same as in {@link mt.train.transtb.AlignmentUtils}
 * @author Pi-Chuan Chang
 */

public class DisplayUtils {
  public static void printAlignmentMatrixHeader() {
    printAlignmentMatrixHeader(new PrintWriter(System.out, true));
  }

  public static void printAlignmentMatrixHeader(PrintWriter pw) {
    pw.println("<br></body></html>");
    pw.println("<html><head><style type=\"text/css\"> table {border-collapse: collapse;} td { padding: 4px; border: 1px solid black } </style>");
  }

  public static void printAlignmentMatrixBottom() {
    printAlignmentMatrixBottom(new PrintWriter(System.out, true));
  }

  public static void printAlignmentMatrixBottom(PrintWriter pw) {
    pw.println("<br></body></html>");
  }

  public static void printAlignmentMatrix(AlignmentMatrix am) {
    printAlignmentMatrix(am, new PrintWriter(System.out, true));
  }

  public static void printAlignmentMatrix(AlignmentMatrix am, PrintWriter pw) {
    pw.println("<table>");
    pw.println("<tr><td></td>");
    for(int i = 0; i < am.f.length; i++) {
      pw.printf("<td>(%d) %s</td>\n", i, escapeHtml(am.f[i]));
    }
    for (int eidx = 0; eidx < am.e.length; eidx++) {
      pw.printf("<tr><td>(%d) %s</td>\n", eidx, escapeHtml(am.e[eidx]));
      for (int fidx = 0; fidx < am.f.length; fidx++) {
        if (am.fe[fidx][eidx]) {
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", fidx, eidx);
        } else {
          pw.println("  <td>&nbsp;</td>");
        }
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  private static String escapeHtml(String str) {
    str = str.replaceAll("<", "&lt;");
    str = str.replaceAll(">", "&gt;");
    return str;
  }
}