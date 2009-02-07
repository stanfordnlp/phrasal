package mt.discrimreorder;

import edu.stanford.nlp.stats.*;

import java.io.*;
import java.util.*;

import mt.train.*;

/**
 * This class collects utils that can display information in
 * {@link AlignmentMatrix}.
 *
 * Some code are the same as in {@link mt.train.transtb.AlignmentUtils}
 * 
 * This class also collects other displaying utils, such as
 * outputing the confusion matrix.
 * @author Pi-Chuan Chang
 */

public class DisplayUtils {
  public static void printExamples(TrainingExamples examples) {
    printExamples(examples, new PrintWriter(System.out, true));
  }

  public static void printExamples(TrainingExamples examples, PrintWriter pw) {
    pw.println("<table>");
    pw.println("<tr>");
    pw.println("<td> i </td>");
    pw.println("<td> j </td>");
    pw.println("<td> j' </td>");
    pw.println("<td> class </td>");
    pw.println("</tr>");
    for(TrainingExample example : examples.examples) {
      pw.println("<tr>");
      pw.printf("<td> %d </td>\n", example.tgt_i);
      pw.printf("<td> %d </td>\n", example.src_j);
      pw.printf("<td> %d </td>\n", example.src_jprime);
      pw.printf("<td> %s </td>\n", example.type);
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

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

  static void printConfusionMatrix(TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> m) {
    System.out.println("==================Confusion Matrix==================");
    System.out.print("->real");
    TreeSet<TrainingExamples.ReorderingTypes> firstKeySet = new TreeSet<TrainingExamples.ReorderingTypes>();
    firstKeySet.addAll(m.firstKeySet());
    TreeSet<TrainingExamples.ReorderingTypes> secondKeySet = new TreeSet<TrainingExamples.ReorderingTypes>();
    secondKeySet.addAll(m.secondKeySet());
    for (TrainingExamples.ReorderingTypes k : firstKeySet) {
      System.out.printf("\t"+k);
    }
    System.out.println();
    for (TrainingExamples.ReorderingTypes k2 : secondKeySet) {
      System.out.print(k2+"\t");
      for (TrainingExamples.ReorderingTypes k1 : firstKeySet) {
        System.out.print((int)m.getCount(k1,k2)+"\t");
      }
      System.out.println();
    }

    System.out.println("----------------------------------------------------");
    System.out.print("total\t");
    for (TrainingExamples.ReorderingTypes k1 : firstKeySet) {
      System.out.print((int)m.totalCount(k1)+"\t");
    }
    System.out.println();
    System.out.println("====================================================");
    System.out.println();
  }

  static void resultSummary(TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> confusionMatrix) {
    double totalNum = 0;
    double totalDenom = confusionMatrix.totalCount();
    Map<TrainingExamples.ReorderingTypes,Integer> TP
      = new HashMap<TrainingExamples.ReorderingTypes,Integer>();
    Map<TrainingExamples.ReorderingTypes,Integer> FP
      = new HashMap<TrainingExamples.ReorderingTypes,Integer>();
    Map<TrainingExamples.ReorderingTypes,Integer> FN
      = new HashMap<TrainingExamples.ReorderingTypes,Integer>();

    // for every possible labels
    for (TrainingExamples.ReorderingTypes i : confusionMatrix.firstKeySet()) {
      //Counter<TrainingExamples.ReorderingTypes> tp = new IntCounter<TrainingExamples.ReorderingTypes>();
      //Counter<TrainingExamples.ReorderingTypes> fp = new IntCounter<TrainingExamples.ReorderingTypes>();
      //Counter<TrainingExamples.ReorderingTypes> fn = new IntCounter<TrainingExamples.ReorderingTypes>();
      int tp = 0, fp = 0, fn = 0;
      
      for (TrainingExamples.ReorderingTypes k : confusionMatrix.secondKeySet()) {
        if (k==i) tp += confusionMatrix.getCount(k, k);
        else      fn += confusionMatrix.getCount(i, k);
      }
      for (TrainingExamples.ReorderingTypes t : confusionMatrix.firstKeySet()) {
        if (t!=i) fp += confusionMatrix.getCount(t, i);
      }
      TP.put(i, tp);
      FP.put(i, fp);
      FN.put(i, fn);
    }

    // Now computing Micro-averaged F-measure
    double nom = 0.0, p_denom = 0.0, r_denom = 0.0;
    for (TrainingExamples.ReorderingTypes i : TP.keySet()) {
      nom += TP.get(i);
      p_denom += TP.get(i);
      r_denom += TP.get(i);
      System.err.printf("TP(%s)=%d\n", i, TP.get(i));
    }
    for (TrainingExamples.ReorderingTypes i : FP.keySet()) {
      p_denom += FP.get(i);
      System.err.printf("FP(%s)=%d\n", i, FP.get(i));
    }
    for (TrainingExamples.ReorderingTypes i : FN.keySet()) {
      r_denom += FN.get(i);
      System.err.printf("FN(%s)=%d\n", i, FN.get(i));
    }
    double micro_precision = nom / p_denom;
    double micro_recall = nom / r_denom;
    double micro_F = 2*micro_precision*micro_recall/(micro_precision+micro_recall);

    double total_F = 0.0;
    int count_F = 0;
    
    Map<TrainingExamples.ReorderingTypes,Double> F
      = new HashMap<TrainingExamples.ReorderingTypes,Double>();

    for (TrainingExamples.ReorderingTypes i : confusionMatrix.firstKeySet()) {
      double precision = (double)TP.get(i) / (TP.get(i)+FP.get(i));
      double recall    = (double)TP.get(i) / (TP.get(i)+FN.get(i));
      double f = 2*precision*recall/(precision+recall);
      F.put(i, f);
      total_F += f;
      count_F++;
    }
    double macro_F = total_F / count_F;

    for (TrainingExamples.ReorderingTypes i : confusionMatrix.firstKeySet()) {
      totalNum += confusionMatrix.getCount(i, i);
    }

    System.out.printf("#total = %d |\tAcc = %2.2f ; F(%s) = %2.2f ; F(%s) = %2.2f ; Macro-F = %2.2f\n", 
                      (int)totalDenom, 100.0*totalNum/totalDenom, 
                      TrainingExamples.ReorderingTypes.ordered, 100*F.get(TrainingExamples.ReorderingTypes.ordered),
                      TrainingExamples.ReorderingTypes.distorted, 100*F.get(TrainingExamples.ReorderingTypes.distorted),
                      100*macro_F);
  }
}