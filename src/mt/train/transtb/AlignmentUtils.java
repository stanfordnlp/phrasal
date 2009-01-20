package mt.train.transtb;

import edu.stanford.nlp.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.process.*;


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

  private static void fixWhichWhere(String[] translation, String[] source, int[][] matrix, Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, List<Integer> deIndices) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { throw new RuntimeException("printGridNoNull FAILED."); }

    if (deIndices.size() > 1) {
      // skip this case because it's multi-DEs
      return;
    }

    for (int cidx = deIndices.get(0)+1; cidx < source.length; cidx++) {
      boolean isNonWhichWhere = false;
      if (rangeB.second < translation.length && rangeB.second > 0) {
        for (int eidx = 0; eidx < rangeB.second; eidx++) {
          if ( (!translation[eidx].equals("which") && !translation[eidx].equals("where")) &&
               matrix[eidx][cidx] > 0) {
            isNonWhichWhere = true;
          }
        }
        if ((translation[rangeB.second].equals("which") || translation[rangeB.second].equals("where"))
            && matrix[rangeB.second][cidx] > 0 && isNonWhichWhere) {
          matrix[rangeB.second][cidx] = -1;
        }
      }
    }
  }

  private static void fixDeterminerOrOfOrWith(String[] translation, String[] source, int[][] matrix) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { throw new RuntimeException("printGridNoNull FAILED."); }
    
    for (int cidx = 0; cidx < source.length; cidx++) {
      // check if the word align to a determiner as well as something later.
      // but check backwards
      boolean isNonDT = false;
      for (int eidx = translation.length-1; eidx >= 0; eidx--) {
        if ( !isDeterminerOrOfOrWith(translation[eidx]) && matrix[eidx][cidx] > 0) { 
          isNonDT = true; 
        }
        else if ( matrix[eidx][cidx] > 0 && 
                  isDeterminerOrOfOrWith(translation[eidx]) && isNonDT) {
          // here it means that source[cidx] has linked to another word later than
          // the current eidx (which is a determiner).
          // We can clear the eidx entry in matrix
          matrix[eidx][cidx] = -1;
        }
      }
    }
  }

  
  private static boolean isDeterminerOrOfOrWith(String word) {
    word = word.toLowerCase();
    if (word.equals("the") || word.equals("a") || 
        word.equals("its") || word.equals("their") ||
        word.equals("an") || word.equals("with"))
      return true;
    return false;
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
        if (!set) { deEidx = eidx; set = true; }
      }
    }
    if (set && translation[deEidx].equals("of")) return true;
    return false;
  }

  public static boolean checkDeIsPrep(String[] translation, String[] source, int[][] matrix, int deIdx) {
    boolean set = false;
    int deEidx = -1;
    for (int eidx = 0; eidx < translation.length; eidx++) {
      if (matrix[eidx][deIdx] > 0) {
        if (set) return false;
        if (!set) { deEidx = eidx; set = true; }
      }
    }
    if (set && isPrep(translation[deEidx])) return true;
    return false;
  }

  private static boolean isPrep(String word) {
    if (word.equals("with") ||
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
        word.equals("under")) 
      return true;
    
    return false;
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
    for(int i = 0; i < source.length; i++) {
      pw.printf("<td>%s</td>\n", source[i]);
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
      String content = StringUtils.slurpFile(file);
      String[] sents = content.split("</seg>");
      for (String sent : sents) {
        sent = sent.trim();;
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

  public static TranslationAlignment fixAlignmentGridWithEnglishTree(
    TranslationAlignment ta, List<Tree> enTrees) {
    int totalEnWords = 0;
    List<String> leaveslist = new ArrayList<String>();
    
    for(Tree eT : enTrees) {
      Sentence<HasWord> sentence = eT.yield();
      for (int i = 0; i < sentence.size(); i++) {
        HasWord hw = sentence.get(i);
        leaveslist.add(hw.word());
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
        for (int j = 0; j < sourceEnd; j++) {
          ta.matrix_[i][j] = newMatrix[i][j];
        }
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
      for (int i = 0; i < translationEnd; i++) {
        for (int j = 0; j < sourceEnd; j++) {
          ta.matrix_[i][j] = newMatrix[i][j];
        }
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
    Sentence<HasWord> sentence = chTrees.get(0).yield();
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      HasWord hw = sentence.get(i);
      leaves[i] = hw.word();
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
    Sentence<HasWord> sentence = new Sentence<HasWord>();
    for (Tree enT : enTrees) {
      sentence.addAll(enT.yield());
    }
    String[] leaves = new String[sentence.size()];
    for (int i = 0; i < sentence.size(); i++) {
      HasWord hw = sentence.get(i);
      leaves[i] = hw.word();
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
    Sentence<HasWord> sentence = chTrees.get(0).yield();
    String[] leaves = new String[sentence.size()];
    if (DEBUG) System.err.print("leaves=");
    for (int i = 0; i < sentence.size(); i++) {
      HasWord hw = sentence.get(i);
      leaves[i] = hw.word();
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
        for (int j = 0; j < sourceEnd; j++) {
          ta.matrix_[i][j] = newMatrix[i][j];
        }
      }
      System.out.println("<p>After<p>\n");
      printAlignmentGrid(ta);
      }
    }
    
    return ta;
  }

  public static void checkTranslationAlignmentAndEnTrees(TranslationAlignment ta, List<Tree> enTrees) {
    String[] enFromAlignment = ta.translation_;
    Sentence<HasWord> enFromTrees = new Sentence<HasWord>();
    for (Tree eT : enTrees) {
      enFromTrees.addAll(eT.yield());
    }
    if (enFromAlignment.length != enFromTrees.size()) {
      System.out.println("Check failed.<br>");
      System.out.println("ALGN: "+StringUtils.join(enFromAlignment, " ")+"<br>");
      System.out.println("TREE: "+StringUtils.join(enFromTrees, " ")+"<br>");
    } else {
      for (int i = 0; i < enFromTrees.size(); i++) {
        if (!enFromTrees.get(i).word().equals(enFromAlignment[i])) {
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
    Sentence<HasWord> chFromTrees = chTrees.get(0).yield();
    if (chFromAlignment.length != chFromTrees.size()) {
      System.out.println("Check failed.<br>");
      System.out.println("ALGN: "+StringUtils.join(chFromAlignment, " ")+"<br>");
      System.out.println("TREE: "+StringUtils.join(chFromTrees, " ")+"<br>");
    } else {
      for (int i = 0; i < chFromTrees.size(); i++) {
        if (!chFromTrees.get(i).word().equals(chFromAlignment[i])) {
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
    for(int lidx = 0; lidx < leaves.length; lidx++) {
      List<Integer> indexgroup = new ArrayList<Integer>();
      String leaf = leaves[lidx];
      if (DEBUG) System.err.println("LEAF="+leaf);
      StringBuilder chunk = new StringBuilder();
      while(!leaf.equals(chunk.toString())) {
        //while(!ExperimentUtils.tokenEquals(leaf, chunk.toString())) {
        chunk.append(source[tidx]);
        indexgroup.add(tidx+1); // have to offset by 1, because 0 is NULL
        if (DEBUG) System.err.println("CHUNK="+chunk.toString());
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

  private static Tree getTreeWithEdges(List<Tree> ts, int leftEdge, int rightEdge) {
    int[] startIndices = new int[ts.size()];

    for(int i = 0; i < ts.size(); i++) {
      if (i==0) startIndices[i] = 0;
      else {
        startIndices[i] = startIndices[i-1]+ts.get(i-1).yield().size();
      }
    }

    int pickTreeIdx = -1;
    
    for(int i = 0; i < startIndices.length; i++) {
      if (leftEdge >= startIndices[i] && 
          (i==startIndices.length-1 || rightEdge <= startIndices[i+1])) {
        pickTreeIdx = i;
      }
    }
    if (pickTreeIdx == -1) {
      return null;
    }

    Tree root = ts.get(pickTreeIdx);
    Queue<Tree> queue = new LinkedList<Tree>();
    queue.add(root);

    while(queue.size() > 0) {
      Tree t = queue.remove();
      Tree[] children = t.children();
      for (Tree c : children) {
        int left = Trees.leftEdge(c, root);
        int right = Trees.rightEdge(c, root);

        if (left==leftEdge-startIndices[pickTreeIdx] && 
            right==rightEdge-startIndices[pickTreeIdx]) {
          return c;
        }
        if (left <= leftEdge-startIndices[pickTreeIdx] ||
            right >= rightEdge-startIndices[pickTreeIdx]) {
          queue.add(c);
        }
      }
    }
    return null;
  }

  private static Pair<Integer,Integer> getRangeA(String[] subtranslation, String[] subsource, int[][] submatrix, int deIdx) {
    int min = subtranslation.length;
    int max = -1;
    boolean setMin = false, setMax = false;
    for(int sidx = 0; sidx < deIdx; sidx++) {
      for(int tidx = 0; tidx < subtranslation.length; tidx++) {
        if (submatrix[tidx][sidx] > 0) {
          if (tidx < min) { min = tidx; setMin = true; }
          if (tidx > max) { max = tidx; setMax = true; }
        }
      }
    }
    if (!setMin || !setMax) { min = max = -1; }
    return new Pair<Integer,Integer>(min,max);
  }

  private static Pair<Integer,Integer> getRangeB(String[] subtranslation, String[] subsource, int[][] submatrix, int deIdx) {
    int min = subtranslation.length;
    int max = -1;
    boolean setMin = false, setMax = false;
    for(int sidx = deIdx+1; sidx < subsource.length; sidx++) {
      for(int tidx = 0; tidx < subtranslation.length; tidx++) {
        if (submatrix[tidx][sidx] > 0) {
          if (tidx < min) { min = tidx; setMin = true; }
          if (tidx > max) { max = tidx; setMax = true; }
        }
      }
    }
    if (!setMin || !setMax) { min = max = -1; }
    return new Pair<Integer,Integer>(min,max);
  }



  private static int getDEeidx(String[] subtranslation, String[] subsource, int[][] submatrix, int deIdx) {
    boolean set = false;
    int deEidx = -1;
    for (int eidx = 0; eidx < subtranslation.length; eidx++) {
      if (submatrix[eidx][deIdx] > 0) {
        if (set) return -1;
        if (!set) { deEidx = eidx; set = true; }
      }
    }
    if (set) return deEidx;
    return -1;
  }

  private static void printRangeAandB(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, String[] subtranslation, String[] subsource) {
    if (rangeA.first >= 0 && rangeA.first < subtranslation.length &&
        rangeA.second >= 0 && rangeA.second < subtranslation.length) {
      System.err.print("RangeA = ");
      for (int i = rangeA.first; i <= rangeA.second; i++) {
        System.err.print(subtranslation[i]+" ");
      }
      System.err.println();
    } else {
      System.err.println("RangeA = NULL");
    }
    if (rangeB.first >= 0 && rangeB.first < subtranslation.length &&
        rangeB.second >= 0 && rangeB.second < subtranslation.length) {
      System.err.print("RangeB = ");
      for (int i = rangeB.first; i <= rangeB.second; i++) {
        System.err.print(subtranslation[i]+" ");
      }
      System.err.println();
    } else {
      System.err.println("RangeB = NULL");
    }
  }

  private static boolean onRangeEdge(int i, Pair<Integer,Integer> range) {
    if (i==-1) return false;
    if (range.second==-1) return false;
    if (range.first==i || range.second==i) return true;
    return false;
  }

  private static boolean inRange(int i, Pair<Integer,Integer> range) {
    if (i==-1) return false;
    if (range.second==-1) return false;
    if (range.first<=i && range.second>=i) return true;
    return false;
  }

  private static boolean checkOrderedAdverb(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    // check if the first part is an adjective
    Tree adj = getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (adj != null && (adj.value().startsWith("RB"))) {
      return true;
    }
    return false;
  }

  private static boolean checkOrderedAdjective(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    // check if the first part is an adjective
    Tree adj = getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (adj != null && (adj.value().startsWith("ADJP") || adj.value().startsWith("JJ"))) {
      return true;
    }

    // check if the first part is 2 subtrees, with forms like:
    // RB JJ, JJ JJ, NP JJ, RBR JJ, etc
    for (int sep = rangeA.first; sep <= rangeA.second; sep++) {
      Tree adj1 = getTreeWithEdges(enTree, rangeA.first, sep+1);
      Tree adj2 = getTreeWithEdges(enTree, sep+1, rangeA.second+1);
      if (adj1 != null && adj2 != null) {
        String adj1str = adj1.value();
        String adj2str = adj2.value();
        if (adj2str.startsWith("JJ") || adj2str.equals("ADJP")) {
          if (adj1str.startsWith("RB") || (adj1str.startsWith("JJ") || (adj1str.startsWith("N")))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static String checkOrderedOtherModifier(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return null;
    Tree adj = getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (adj != null && (adj.value().startsWith("VBN") || adj.value().startsWith("VBG") || adj.value().startsWith("PRP$"))) {
      return adj.value();
    }
    return null;
  }

  private static boolean checkOrderedNoun(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    boolean firstNP = false;
    boolean secondNP = false;


    Tree np = getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (np != null && 
        (np.value().startsWith("N"))) {
      firstNP = true;
    }

    // check if the first part has a modifier and then a noun
    if (!firstNP) {
      for (int sep = rangeA.first; sep <= rangeA.second; sep++) {
        Tree n1 = getTreeWithEdges(enTree, rangeA.first, sep+1);
        Tree n2 = getTreeWithEdges(enTree, sep+1, rangeA.second+1);
        if (n1 != null && n2 != null) {
          String n1str = n1.value();
          String n2str = n2.value();
        if (n1str.equals("JJ") || n1str.startsWith("N")) {
          if (n2str.startsWith("N")) {
            firstNP = true;
            break;
          }
        }
        }
      }
    }

    Tree bT = getTreeWithEdges(enTree, rangeB.first, rangeB.second+1);
    if (bT != null &&
        (bT.value().startsWith("N"))) {
      secondNP = true;
    }

    // check if the second part is a compound noun
    if (!secondNP)
      for (int sep = rangeB.first; sep <= rangeB.second; sep++) {
        Tree n1 = getTreeWithEdges(enTree, rangeB.first, sep+1);
        Tree n2 = getTreeWithEdges(enTree, sep+1, rangeB.second+1);
        if (n1 != null && n2 != null) {
          String n1str = n1.value();
          String n2str = n2.value();
          if (n1str.startsWith("N") && n2str.startsWith("N")) {
            secondNP = true;
            break;
          }
        }
      }
    
    
    if (firstNP && secondNP) return true;

    return false;
  }

  private static boolean checkSecondVP(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    Tree bT = getTreeWithEdges(enTree, rangeB.first, rangeB.second+1);
    if (bT != null &&
        (bT.value().startsWith("VP"))) {
      return true;
    }

    return false;
  }


  private static boolean checkFlippedRelativeClause(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;
    Tree relc = getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (relc != null && 
        (relc.value().startsWith("VP") || 
         relc.value().startsWith("SBAR") || 
         // this second case here is for cases like:
         //(WHNP (WDT which))
         // (S
         // (VP (VBD won)
         // (NP (DT the) (JJ national)
         // (NX (JJ important) (NN invention))
         // (NN award))))))
         (relc.value().startsWith("S") && relc.firstChild().value().startsWith("VP")))) {
         
      return true;
    }
    return false;
  }

}
