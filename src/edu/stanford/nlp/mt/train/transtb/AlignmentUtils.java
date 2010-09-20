package edu.stanford.nlp.mt.train.transtb;

import edu.stanford.nlp.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.*;


/**
 * This class collects a lot of static functions, most of
 * which are related to processing the alignment grid.
 * Some processes are with respect to the En/Ch trees;
 * some are just looking at the grid.
 * This is like a preprocessing and cleaning when we merge
 * the Chinese and English sentences(trees) and the alignment
 * grid.
 *
 * It'll be useful to make this class more organized.
 *
 * @author Pi-Chuan Chang
 */

public class AlignmentUtils {
  private static boolean DEBUG = false;

  public static void printAlignmentGridHeader() {
    printAlignmentGridHeader(new PrintWriter(System.out, true));
  }

  public static void printAlignmentGridHeader(PrintWriter pw) {
    pw.println("<br></body></html>");
    pw.println("<html><head><style type=\"text/css\"> table {border-collapse: collapse;} td { padding: 4px; border: 1px solid black } </style>");
  }

  public static void printAlignmentGridBottom() {
    printAlignmentGridBottom(new PrintWriter(System.out, true));
  }

  public static void printAlignmentGridBottom(PrintWriter pw) {
    pw.println("<br></body></html>");
  }

  public static void printAlignmentGrids(Collection<TranslationAlignment> tas) {
    printAlignmentGridHeader();
    for(TranslationAlignment ta : tas) {
      printAlignmentGrid(ta);
    }
    printAlignmentGridBottom();
  }

  public static void printAlignmentGrid(TranslationAlignment ta) {
    printAlignmentGrid(ta, new PrintWriter(System.out, true));
  }

  public static void printNPTree(Tree t, PrintWriter pw) {
    pw.println("<pre>");
    if (t != null) {
      t.pennPrint(pw);
    } else {
      pw.println("No Tree");
    }
    pw.println("</pre>");
  }

  public static boolean checkDeIsOf(String[] translation, String[] source, int[][] matrix, int deIdx) {
    boolean set = false;
    int deEidx = -1;
    for (int eidx = 0; eidx < translation.length; eidx++) {
      if (matrix[eidx][deIdx] > 0) {
        if (set) return false;
        deEidx = eidx;
        set = true;
      }
    }
    return set && translation[deEidx].equals("of");
  }

  public static boolean checkDeIsPrep(String[] translation, String[] source, int[][] matrix, int deIdx) {
    boolean set = false;
    int deEidx = -1;
    for (int eidx = 0; eidx < translation.length; eidx++) {
      if (matrix[eidx][deIdx] > 0) {
        if (set) return false;
        deEidx = eidx;
        set = true;
      }
    }
    return set && isPrep(translation[deEidx]);
  }

  private static boolean isPrep(String word) {
    return word.equals("with") ||
        word.equals("within") ||
        word.equals("from") ||
        word.equals("in") ||
        word.equals("inside") ||
        word.equals("for") ||
        word.equals("on") ||
        word.equals("between") ||
        word.equals("by") ||
        word.equals("among") ||
        word.equals("at") ||
        word.equals("under");

  }

  public static void printGridNoNull(String[] translation, String[] source, int[][] matrix, PrintWriter pw) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { System.err.println("printGridNoNull FAILED."); return; }

    pw.println("<table>");
    pw.println("<tr><td></td>");
    for (String aSource : source) {
      pw.printf("<td>%s</td>\n", aSource);
    }
    
    for(int tidx = 0; tidx < translation.length; tidx++) {
      pw.printf("<tr><td>%s</td>\n", translation[tidx]);
      for(int sidx = 0; sidx < source.length; sidx++) {
        if (matrix[tidx][sidx] == 0)
          pw.println("  <td>&nbsp;</td>");
        else if (matrix[tidx][sidx] > 0)
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", tidx, sidx);
        else if (matrix[tidx][sidx] == -1)
          pw.printf("    <td bgcolor=\"green\">%d</td>\n", matrix[tidx][sidx]);
        else
          pw.printf("    <td bgcolor=\"red\">%d</td>\n", matrix[tidx][sidx]);
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  public static void printAlignmentGrid(TranslationAlignment ta, PrintWriter pw) {
    pw.println("<table>");
    pw.println("<tr><td></td>");
    for(int i = 0; i <= ta.source_.length; i++) {
      pw.printf("<td>%s</td>\n", ta.getSource(i));
    }

    for(int tidx = 0; tidx <= ta.translation_.length; tidx++) {
      pw.printf("<tr><td>%s</td>\n", ta.getTranslation(tidx));
      for(int sidx = 0; sidx <= ta.source_.length; sidx++) {
        if (ta.matrix_[tidx][sidx] == 0)
          pw.println("  <td>&nbsp;</td>");
        else if (ta.matrix_[tidx][sidx] == 1)
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", tidx, sidx);
        else if (ta.matrix_[tidx][sidx] == 2)
          pw.printf("    <td bgcolor=\"red\">%d,%d</td>\n", tidx, sidx);
        else if (ta.matrix_[tidx][sidx] == 3)
          pw.printf("    <td bgcolor=\"green\">%d,%d</td>\n", tidx, sidx);
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  public static List<TranslationAlignment> readFromFile(String filename)
  throws IOException {
    File file = new File(filename);
    return readFromFile(file);
  }

  public static List<TranslationAlignment> readFromFile(File file) 
  throws IOException {
    List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();

    if (file.exists()) {
      String content = IOUtils.slurpFile(file);
      String[] sents = content.split("</seg>");
      for (String sent : sents) {
        sent = sent.trim();
        if (sent.length()>0) {
          TranslationAlignment ta = new TranslationAlignment(sent);
          if (ta.isWellFormed()) {
            alignment_list.add(ta);
          } else {
            //System.err.println("Ill-formed.");
          }
        }
      }
    }
    return alignment_list;
  }

  public static List<TranslationAlignment> readFromSentenceAlignedFiles(String cFile, String eFile) 
  throws IOException {
    List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();

    String[] cLines = IOUtils.slurpFile(cFile).split("\\n");
    String[] eLines = IOUtils.slurpFile(eFile).split("\\n");
    if(eLines.length != cLines.length)
      throw new RuntimeException(String.format("Two files with different number of lines: %s %s\n",cFile,eFile));
    for(int i=0; i<eLines.length; ++i) {
      String[] cLine = cLines[i].trim().split("\\s+");
      String[] eLine = eLines[i].trim().split("\\s+");
      TranslationAlignment ta = new TranslationAlignment(cLine,eLine);
      alignment_list.add(ta);
    }
    return alignment_list;
  }

  public static TranslationAlignment fixAlignmentGridWithEnglishTree(
    TranslationAlignment ta, List<Tree> enTrees) {
    List<String> leaveslist = new ArrayList<String>();
    
    for(Tree eT : enTrees) {
      ArrayList<Label> sentence = eT.yield();
      for (Label hw : sentence) {
        leaveslist.add(hw.value());
      }
    }

    String[] leaves = new String[leaveslist.size()];
    for(int i = 0; i < leaves.length; i++) leaves[i] = leaveslist.get(i);
    
    ta = fixAlignmentGrid_Scores(ta, leaves);
    ta = fixAlignmentGridOnTranslation_Poss_Neg(ta, leaves);
    String[] translation = ta.translation_;


    String str1 = StringUtils.join(leaves, "");
    String str2 = StringUtils.join(translation, "");
    if (!str1.equals(str2)) {
      throw new RuntimeException("\n"+str1+"\n != \n"+str2);
    }

    List<List<Integer>> indexgroups = getIndexGroups(leaves, translation);

    int[][] newMatrix = new int[ta.matrix_.length][];
    int translationEnd = ta.matrix_.length;
    int sourceEnd = ta.matrix_[0].length;

    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        newMatrix[i] = new int[ta.matrix_[i].length];
      }
    }

    boolean diff = false;
    for (int j = 0; j < sourceEnd; j++) {
      for(List<Integer> idxgroup : indexgroups) {
        boolean result = false;
        for(Integer idx : idxgroup) {
          if (ta.matrix_[idx][j] > 1) {
            result = true;
          }
        }
        for(Integer idx : idxgroup) {
          if (result && ta.matrix_[idx][j] == 0) {
            newMatrix[idx][j] = 3; // make it green
            diff = true;
          } else {
            newMatrix[idx][j] = ta.matrix_[idx][j];
          }
        }
      }
    }
    
    if (diff) {
      System.out.println("<p>Before<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        System.arraycopy(newMatrix[i], 0, ta.matrix_[i], 0, sourceEnd);
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        System.arraycopy(newMatrix[i], 0, ta.matrix_[i], 0, sourceEnd);
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
    }


    return ta;
  }
  
  // Cases like "6:3". In the alignment file, it's separated
  public static TranslationAlignment fixAlignmentGrid_Scores(TranslationAlignment ta, String[] leaves) {
    String regex = "^(\\d+):(\\d+)$";
    Pattern pattern = Pattern.compile(regex);
    int needFix = -1;
    String first = null, second = null;
    for (int i = 0; i < ta.translation_.length; i++) {
      String t = ta.translation_[i];
      Matcher matcher = pattern.matcher(t);
      if (matcher.find() && !t.equals(leaves[i])) {
        first = matcher.group(1);
        second = matcher.group(2);
        needFix = i+1; // add one because 0 is NULL
        break;
      }
    }
    boolean fixed = false;
    while(needFix >= 0) {
      fixed = true;
      int[][] newMatrix = new int[ta.matrix_.length+2][];
      String[] newTranslation = new String[ta.translation_.length+2];
      for(int i = 0; i < newMatrix.length; i++) {
        newMatrix[i] = new int[ta.matrix_[0].length];
        for(int j = 0; j < ta.matrix_[0].length; j++) {
          if (i <= needFix) {
            newMatrix[i][j] = ta.matrix_[i][j];
          } else if (i == needFix+1 || i == needFix+2) {
            newMatrix[i][j] = ta.matrix_[needFix][j];
          } else {
            newMatrix[i][j] = ta.matrix_[i-2][j];
          }
        }
      }

      int needFixInTranslation = needFix - 1; // in translation_, NULL is not there
      for(int i = 0; i < newTranslation.length; i++) {
        if (i < needFixInTranslation) {
          newTranslation[i] = ta.translation_[i];
        } else if (i==needFixInTranslation) {
          newTranslation[i] = first;
        } else if (i==needFixInTranslation+1) {
          newTranslation[i] = ":";
        } else if (i==needFixInTranslation+2) {
          newTranslation[i] = second;
        } else {
          newTranslation[i] = ta.translation_[i-2];
        }
      }
      ta = new TranslationAlignment(ta.source_, newTranslation, newMatrix);
      needFix = -1;
      first = second = null;
      for (int i = 0; i < ta.translation_.length; i++) {
        String t = ta.translation_[i];
        Matcher matcher = pattern.matcher(t);
        if (matcher.find() && !t.equals(leaves[i])) {
          first = matcher.group(1);
          second = matcher.group(2);
          needFix = i+1; // add one because 0 is NULL
          break;
        }
      }
    }
    if (fixed && DEBUG) { 
      System.err.println("matrix changed on 'fixAlignmentGrid_Scores'"); 
      System.err.println(StringUtils.join(ta.translation_, " "));
    }
    return ta;
  }

  public static TranslationAlignment fixAlignmentGridOnTranslation_Poss_Neg(TranslationAlignment ta, String[] leaves) {
    // check if there's "'s" case
    int needFix = -1;
    boolean cannot = false;

    //System.err.println("alignment="+StringUtils.join(ta.translation_, " ")+"<br>");
    //System.err.println("from tree="+StringUtils.join(leaves, " ")+"<br>");
    for (int i = 0; i < ta.translation_.length; i++) {
      String t = ta.translation_[i];
      if (i < leaves.length && !t.equals(leaves[i])) {
        if (t.endsWith("'s") && !t.equals("'s")) { needFix = i+1 ; break; } // add one because 0 is NULL
        if (t.equals("cannot")) { needFix = i+1; cannot = true; break; }
      }
    }

    int[][] newMatrix = new int[ta.matrix_.length+1][];
    String[] newTranslation = new String[ta.translation_.length+1];
    boolean fixed = false;
    while (needFix >= 0) {
      fixed = true;
      // insert a row after 'needFix'
      for(int i = 0; i < newMatrix.length; i++) {
        newMatrix[i] = new int[ta.matrix_[0].length];
        for(int j = 0; j < ta.matrix_[0].length; j++) {
          if (i <= needFix) {
            newMatrix[i][j] = ta.matrix_[i][j];
          } else if (i==needFix+1) {
            newMatrix[i][j] = ta.matrix_[needFix][j];
          } else {
            newMatrix[i][j] = ta.matrix_[i-1][j];
          }
        }
      }
      int needFixInTranslation = needFix - 1; // in translation_, NULL is not there
      for(int i = 0; i < newTranslation.length; i++) {
        if (i < needFixInTranslation) {
          newTranslation[i] = ta.translation_[i];
        } else if (i==needFixInTranslation) {
          if (cannot) {
            newTranslation[i] = "can";
          } else {
            newTranslation[i] = ta.translation_[i].substring(0, ta.translation_[i].length()-2);
          }
        } else if (i==needFixInTranslation+1) {
          if (cannot)
            newTranslation[i] = "not";
          else
            newTranslation[i] = "'s";
        } else {
          newTranslation[i] = ta.translation_[i-1];
        }
      }
      ta = new TranslationAlignment(ta.source_, newTranslation, newMatrix);
      needFix = -1;
      for (int i = 0; i < ta.translation_.length; i++) {
        String t = ta.translation_[i];
        if (t.endsWith("'s") && !t.equals("'s")) { needFix = i+1 ; break; } // add one because 0 is NULL 
      }
    }
    if (fixed && DEBUG) { 
      System.err.println("matrix changed on 'fixAlignmentGridOnTranslation_Poss_Neg'");
      System.err.println(StringUtils.join(ta.translation_, " "));
    }
    return ta;
  }

  public static TranslationAlignment fixAlignmentGridMergingChinese(
    TranslationAlignment ta, List<Tree> chTrees) {
    ArrayList<Label> sentence = chTrees.get(0).yield();
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      Label hw = sentence.get(i);
      leaves[i] = hw.value();
    }
    String[] source = ta.source_;
    List<List<Integer>> indexgroups = getIndexGroups(leaves, source);

    int translationEnd = ta.matrix_.length;
    int sourceEnd = leaves.length+1;
    //int[][] newMatrix = new int[ta.matrix_.length][];
    int[][] newMatrix = new int[translationEnd][];
    for (int i = 0; i < translationEnd; i++) {
      newMatrix[i] = new int[sourceEnd];
    }
    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        if (j==0) {
          newMatrix[i][0] = ta.matrix_[i][0];
        } else {
          int result = 0;
          for(Integer idx : indexgroups.get(j-1)) {
            if (ta.matrix_[i][idx] > 0) result = 1;
          }
          newMatrix[i][j] = result;
        }
      }
    }
    ta = new TranslationAlignment(leaves, ta.translation_, newMatrix);
    return ta;
  }


  public static TranslationAlignment fixAlignmentGridMergingEnglish(
    TranslationAlignment ta, List<Tree> enTrees) {
    ArrayList<Label> sentence = new ArrayList<Label>();
    for (Tree enT : enTrees) {
      sentence.addAll(enT.yield());
    }
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      Label hw = sentence.get(i);
      leaves[i] = hw.value();
    }
    String[] translation = ta.translation_;
    List<List<Integer>> indexgroups = getIndexGroups(leaves, translation);

    int translationEnd = leaves.length+1;
    int sourceEnd = ta.matrix_[0].length;
    int[][] newMatrix = new int[translationEnd][];
    for (int i = 0; i < translationEnd; i++) {
      newMatrix[i] = new int[sourceEnd];
    }
    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        if (i==0) {
          newMatrix[0][j] = ta.matrix_[0][j];
        } else {
          int result = 0;
          for(Integer idx : indexgroups.get(i-1)) {
            if (ta.matrix_[idx][j] > 0) result = 1;
          }
          newMatrix[i][j] = result;
        }
      }
    }
    ta = new TranslationAlignment(ta.source_, leaves, newMatrix);
    return ta;
  }

  
  public static TranslationAlignment fixAlignmentGridWithChineseTree(
    TranslationAlignment ta, List<Tree> chTrees) {
    if (chTrees.size() > 1) {
      throw new RuntimeException("more than one chTree");
    }
    ArrayList<Label> sentence = chTrees.get(0).yield();
    String[] leaves = new String[sentence.size()];
    if (DEBUG) System.err.print("leaves=");
    for (int i = 0; i < sentence.size(); i++) {
      Label hw = sentence.get(i);
      leaves[i] = hw.value();
      if (DEBUG) System.err.print(leaves[i]+" ");
    }
    if (DEBUG) System.err.println();
    
    String[] source = ta.source_;
    if (DEBUG) {
      System.err.print("source=");
      for(String w : source) {
        System.err.print(w+" ");
      }
      System.err.println();
    }
    List<List<Integer>> indexgroups = getIndexGroups(leaves, source);

    int[][] newMatrix = new int[ta.matrix_.length][];
    int translationEnd = ta.matrix_.length;
    int sourceEnd = ta.matrix_[0].length;

    for (int i = 0; i < translationEnd; i++) {
      for (int j = 0; j < sourceEnd; j++) {
        newMatrix[i] = new int[ta.matrix_[i].length];
      }
    }

    boolean diff = false;
    for (int i = 0; i < translationEnd; i++) {
      for(List<Integer> idxgroup : indexgroups) {
        boolean result = false;
        for(Integer idx : idxgroup) {
          if (ta.matrix_[i][idx] > 1) {
            result = true;
          }
        }
        for(Integer idx : idxgroup) {
          if (result && ta.matrix_[i][idx] != 1) {
            newMatrix[i][idx] = 2; // make it red
            diff = true;
          } else {
            newMatrix[i][idx] = ta.matrix_[i][idx];
          }
        }
      }
    }
    if (DEBUG) {
      if (diff) {
      System.out.println("<p>Before<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        System.arraycopy(newMatrix[i], 0, ta.matrix_[i], 0, sourceEnd);
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
      }
    }
    
    return ta;
  }

  public static void checkTranslationAlignmentAndEnTrees(TranslationAlignment ta, List<Tree> enTrees) {
    String[] enFromAlignment = ta.translation_;
    ArrayList<Label> enFromTrees = new ArrayList<Label>();
    for (Tree eT : enTrees) {
      enFromTrees.addAll(eT.yield());
    }
    if (enFromAlignment.length != enFromTrees.size()) {
      System.out.println("Check failed.<br>");
      System.out.println("ALGN: "+StringUtils.join(enFromAlignment, " ")+"<br>");
      System.out.println("TREE: "+StringUtils.join(enFromTrees, " ")+"<br>");
    } else {
      for (int i = 0; i < enFromTrees.size(); i++) {
        if (!enFromTrees.get(i).value().equals(enFromAlignment[i])) {
          System.out.println("Check failed.<br>");
          System.out.println("ALGN: "+StringUtils.join(enFromAlignment, " ")+"<br>");
          System.out.println("TREE: "+StringUtils.join(enFromTrees, " ")+"<br>");
          break;
        }
      }
    }
  }

  public static void checkTranslationAlignmentAndChTrees(TranslationAlignment ta, List<Tree> chTrees) {
    String[] chFromAlignment = ta.source_;
    ArrayList<Label> chFromTrees = chTrees.get(0).yield();
    if (chFromAlignment.length != chFromTrees.size()) {
      System.out.println("Check failed.<br>");
      System.out.println("ALGN: "+StringUtils.join(chFromAlignment, " ")+"<br>");
      System.out.println("TREE: "+StringUtils.join(chFromTrees, " ")+"<br>");
    } else {
      for (int i = 0; i < chFromTrees.size(); i++) {
        if (!chFromTrees.get(i).value().equals(chFromAlignment[i])) {
          System.out.println("Check failed.<br>");
          System.out.println("ALGN: "+StringUtils.join(chFromAlignment, " ")+"<br>");
          System.out.println("TREE: "+StringUtils.join(chFromTrees, " ")+"<br>");
          break;
        }
      }
    }
  }

  private static List<List<Integer>> getIndexGroups(String[] leaves, String[] source) {
    List<List<Integer>> indexgroups = new ArrayList<List<Integer>>();

    int tidx = 0;
    for (String leave : leaves) {
      List<Integer> indexgroup = new ArrayList<Integer>();
      String leaf = leave;
      if (DEBUG) System.err.println("LEAF=" + leaf);
      StringBuilder chunk = new StringBuilder();
      while (!leaf.equals(chunk.toString())) {
        //while(!ExperimentUtils.tokenEquals(leaf, chunk.toString())) {
        chunk.append(source[tidx]);
        indexgroup.add(tidx + 1); // have to offset by 1, because 0 is NULL
        if (DEBUG) System.err.println("CHUNK=" + chunk.toString());
        tidx++;
      }
      indexgroups.add(indexgroup);
    }
    return indexgroups;
  }

  public static Tree getTreeWithEdges(Tree root, int leftEdge, int rightEdge) {
    Queue<Tree> queue = new LinkedList<Tree>();
    queue.add(root);
    if (leftEdge == 0 && rightEdge == root.yield().size()) {
      return root;
    }

    while(queue.size() > 0) {
      Tree t = queue.remove();
      Tree[] children = t.children();
      for (Tree c : children) {
        int left = Trees.leftEdge(c, root);
        int right = Trees.rightEdge(c, root);
        if (c.numChildren()==1) c = c.firstChild();

        if (left==leftEdge && right==rightEdge) {
          return c;
        }
        if (left <= leftEdge || right >= rightEdge) {
          queue.add(c);
        }
      }
    }
    return null;
  }
}
