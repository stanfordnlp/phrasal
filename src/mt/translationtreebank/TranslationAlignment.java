package mt.translationtreebank;

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


public class TranslationAlignment {
  String source_raw_;
  String[] source_;
  String translation_raw_;
  String[] translation_;
  int[][] matrix_;
  boolean wellformed_ = true;

  private static boolean DEBUG = false;

  private ChineseEscaper ce_ = new ChineseEscaper();
  private PTBEscapingProcessor ptbe_ = new PTBEscapingProcessor();

  public boolean isWellFormed() {
    return wellformed_;
  }

  private Pair<String[], int[][]> fixTranslationWithMatrix(String[] t, int[][] m) {
      /* fix errors in translation_ : all 'Ltd.' become 'Lt.'. */
      t = fixTranslationWords(t);
      if (m.length != t.length+1) {
        m = trimMatrixRow(m, t.length+1);
      }
      return new Pair<String[], int[][]>(t, m);
  }

  private String[] fixTranslationWords(String[] t) {
    String[] newT = new String[t.length];
    // fix 'Ltd.'
    for(int i = 0; i < newT.length; i++) {
      if (t[i].equals("Lt."))
        newT[i] = "Ltd.";
      else if (t[i].equals("etc"))
        newT[i] = "etc.";
      else 
        newT[i] = t[i];
    }

    // fix 'etc .' at the end
    
    return newT;
  }

  private int[][] trimMatrixRow(int[][] m, int newRowLength) {
    if(newRowLength != m.length-1) throw new RuntimeException("new row length should be just one less");
    int[][] newM = new int[newRowLength][];
    for(int i = 0; i < newRowLength; i++) {
      for(int j = 0; j < m[i].length; j++) {
        newM[i][j] = m[i][j];
      }
    }
    return newM;  
  }

  public String getTranslation(int i) {
    if (i == 0) return "NULL";
    return translation_[i-1];
  }

  public String getSource(int i) {
    if (i == 0) return "NULL";
    return source_[i-1];
  }

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


  public String normalizeSourceSentence(String sent) {
    List<HasWord> words = new ArrayList<HasWord>();
    words.add(new Word(sent));
    words = ce_.apply(words);
    String output = words.get(0).word();
    output = output.replaceAll("―", "—");
    output = output.replaceAll("・", "·");
    return output;
  }

  public String[] normalizeTranslationSentence(String[] sents) {
    List<HasWord> words = new ArrayList<HasWord>();
    for(String w : sents) words.add(new Word(w));
    words = ptbe_.apply(words);
    String[] newSent = new String[words.size()];
    for(int i = 0; i < newSent.length; i++) {
      newSent[i] = words.get(i).word();
    }
    return newSent;
  }

  public TreeSet<Integer> mapChineseToEnglish(Pair<Integer,Integer> ip) {
    TreeSet<Integer> english = new TreeSet<Integer>();
    for (int i = ip.first; i <= ip.second; i++) {
      int matrixSource = i+1;
      for (int tidx = 1; tidx < translation_.length+1; tidx++) {
        if (matrix_[tidx][matrixSource] > 0) {
          english.add(tidx-1);
        }
      }
    }
    return english;
  }

  public TreeSet<Integer> mapChineseToEnglish_FillGap(Pair<Integer,Integer> ip, TreeSet<Integer> enRange) {
    boolean contiguous = true;
    int prevI = -1;
    TreeSet<Integer> nullgaps = new TreeSet<Integer>();
    List<Pair<Integer,Integer>> gaps = new ArrayList<Pair<Integer,Integer>>();
    
    for (Integer i : enRange) {
      //System.out.println("eni = "+i+"<br>");
      if (prevI != -1) {
        if (i > prevI+1) {
          Pair<Integer,Integer> gap = new Pair<Integer,Integer>(prevI+1, i-1);
          //System.out.println("Add = "+gap+"<br>");
          gaps.add(gap);
        }
      }
      prevI = i;
    }
    
    for(Pair<Integer,Integer> gap : gaps) {
      for (int eni = gap.first ; eni<=gap.second; eni++) {
        // Note: eni is 0-based, so we need to add 1
        boolean add = true;
        // It doesn't matter if the word aligns to NULL: matrix_[eni+1][0] can be whatever
        for (int chi = 1; chi < source_.length+1; chi++) {
          if (matrix_[eni+1][chi] > 0) {
            add = false;
            break;
          }
        }
        if (add) nullgaps.add(eni);
      }
    }
    return nullgaps;
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

  public TranslationAlignment(String[] source, String[] translation, int[][] matrix) {
    this.source_ = new String[source.length];
    this.translation_ = new String[translation.length];
    this.matrix_ = new int[translation.length+1][];

    for(int i = 0; i < translation.length; i++) {
      this.translation_[i] = translation[i];
    }
    for(int j = 0; j < source.length; j++) {
      this.source_[j] = source[j];
    }

    for(int i = 0; i < translation.length+1; i++) {
      this.matrix_[i] = new int[source.length+1];
      for(int j = 0; j < source.length+1; j++) {
        this.matrix_[i][j] = matrix[i][j];
      }
    }
  }

  public TranslationAlignment(String dataStr) {
    String regex
      = "<source_raw>(.*)</source_raw>\\n"+
      "<source>(.*)</source>\\n"+
      "<translation_raw>(.*)</translation_raw>\\n"+
      "<translation>(.*)</translation>\\n"+
      "<matrix>(.*)</matrix>";

    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(dataStr);

    if (matcher.find()) {
      source_raw_ = normalizeSourceSentence(matcher.group(1));

      String sourceStr = matcher.group(2);
      sourceStr = sourceStr.trim();
      sourceStr = normalizeSourceSentence(sourceStr);
      if (sourceStr.length()==0) { wellformed_ = false; return; }
      source_ = sourceStr.split("\\s+");

      translation_raw_ = matcher.group(3);

      String translationStr = matcher.group(4);
      translationStr = translationStr.trim();
      if (translationStr.length()==0) { wellformed_ = false; return; }
      translation_ = translationStr.split("\\s+");
      translation_ = normalizeTranslationSentence(translation_);

      // Read in the 2D matrix
      String matrixStr = matcher.group(5);
      matrixStr = matrixStr.trim();
      if (matrixStr.length()==0) { wellformed_ = false; return; }
      String[] rows = matrixStr.split("\\n");
      if (rows.length != translation_.length+1) {
        System.err.println("Ill-formed:");
        System.err.println(dataStr);
        wellformed_ = false; return;
      }
      matrix_ = new int[translation_.length+1][];
      for (int r_i = 0; r_i < rows.length; r_i++) {
        String rowStr = rows[r_i];
        int[] row = new int[source_.length+1];
        rowStr = rowStr.trim();
        String[] elements = rowStr.split("\\s+");
        if (elements.length != source_.length+1) {
          System.err.println("Ill-formed:");
          System.err.println(dataStr);
          System.err.println(elements.length+"\t"+source_.length);
        }

        for(int e_i = 0; e_i < elements.length; e_i++) {
          int element = Integer.parseInt(elements[e_i]);
          row[e_i] = element;
          // ignore "possible" alignemt
          if (element == 2) { row[e_i] = 0; }
          if (element > 2) throw new RuntimeException("In alignment file: Bigger than 2?");

        }
        matrix_[r_i] = row;
      }

      // this fixes translation_ and matrix_ at the same time,
      // because sometimes some changes in translation_ might 
      // need to adjust matrix_ as well
      Pair<String[], int[][]> tm = fixTranslationWithMatrix(translation_, matrix_);
      translation_ = tm.first;
      matrix_ = tm.second;
    } else {
      System.err.println("Ill-formed:");
      System.err.println(dataStr);
      wellformed_ = false; return;
    }
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

  static void checkTranslationAlignmentAndEnTrees(TranslationAlignment ta, List<Tree> enTrees) {
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

  static void checkTranslationAlignmentAndChTrees(TranslationAlignment ta, List<Tree> chTrees) {
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



  private static String analyzeNPwithDE(int deIdxInSent, TreePair tp, PrintWriter pw) throws IOException {
    List<Pair<Integer,Integer>> englishNP = tp.getNPEnglishTranslation(deIdxInSent);
    Pair<Integer,Integer> np = tp.NPwithDEs_deIdx.get(deIdxInSent);

    //List<Pair<Integer,Integer>> englishNP = tp.NPwithDEs.get(np);
    if (englishNP.size() != 1) {
      return "fragmented";
    }

    Tree chTree = tp.chTrees.get(0);
    Tree chNPTree = getTreeWithEdges(chTree,np.first, np.second+1);
    if (chNPTree == null) {
      throw new RuntimeException("chNPTree shouldn't be null");
    }

    Tree enNPTree = getTreeWithEdges(tp.enTrees, englishNP.get(0).first, englishNP.get(0).second+1);
    if (enNPTree == null) {
      System.err.println("enNPTree: NULL");
    } else {
      System.err.println("enNPTree: Found");
      //enNPTree.pennPrint(System.err);
    }

    // if there's only one chunk of English, get the submatrix and subsource & subtranslation
    Pair<Integer,Integer> ennp = englishNP.get(0);
    int nplength = np.second-np.first+1;
    int ennplength = ennp.second-ennp.first+1;
    String[] subsource      = new String[nplength];
    String[] subtranslation = new String[ennplength];
    int[][] submatrix = new int[ennplength][nplength];

    for(int tidx = ennp.first; tidx <= ennp.second; tidx++) {
      for(int sidx = np.first; sidx <= np.second; sidx++) {
        // This really can be improved. Note that in matrix_, 0 --> NULL
        submatrix[tidx-ennp.first][sidx-np.first] = tp.alignment.matrix_[tidx+1][sidx+1];
      }
    }

    //locate the "的"
    for(int tidx = ennp.first; tidx <= ennp.second; tidx++) {
      subtranslation[tidx-ennp.first] = tp.alignment.translation_[tidx];
    }
    List<Integer> deIndices = new ArrayList<Integer>();
    for(int sidx = np.first; sidx <= np.second; sidx++) {
      subsource[sidx-np.first] = tp.alignment.source_[sidx];
      if (tp.alignment.source_[sidx].equals("的") ||
          tp.alignment.source_[sidx].equals("之")) {
        // TODO: should get rid of the second condition
        deIndices.add(sidx-np.first);
      }
    }

    if (deIndices.size() > 1) {
      return "multi-DEs";
    }
    if (deIndices.size() == 0) {
      return "no DE?";
    }

    // Now it's the case with only one DE
    // find the mapping to English of the first part, 
    // and find the mapping to the 2nd part
    int deIdx = deIndices.get(0);


    // The manual alignment like to align the determiners with the noun,
    // which cause lots of "undecided" type. Try to eliminate this case
    fixDeterminerOrOfOrWith(subtranslation, subsource, submatrix);

    // for "A de B"
    // get the translation range of A
    Pair<Integer,Integer> rangeA = getRangeA(subtranslation, subsource, submatrix, deIdx);
    Pair<Integer,Integer> rangeB = getRangeB(subtranslation, subsource, submatrix, deIdx);
    int deEidx = getDEeidx(subtranslation, subsource, submatrix, deIdx);
    // based one the range, fix 'which' clause, and update rangeA & rangeB
    fixWhichWhere(subtranslation, subsource, submatrix, rangeA, rangeB, deIndices);
    rangeA = getRangeA(subtranslation, subsource, submatrix, deIdx);
    rangeB = getRangeB(subtranslation, subsource, submatrix, deIdx);

    // From here, we have 'rangeA' and 'rangeB'
    //printRangeAandB(rangeA, rangeB, subtranslation, subsource);

    // Print out the sub-grid to file
    printGridNoNull(subtranslation, subsource, submatrix, pw);
    // Print related chNPTree & enNPTree, if exist
    printNPTree(chNPTree, pw);
    printNPTree(enNPTree, pw);


    // if there is A and B,
    // but either no rangeA or rangeB
    // then return "other - A/B not aligned"
    if (deIdx > 0 && deIdx < subsource.length-1) {
      if (rangeA.second==-1) { return "other - A not aligned"; }
      if (rangeB.second==-1) { return "other - B not aligned"; }
    }

    if (deIdx == subsource.length - 1) {
      if (enNPTree != null && enNPTree.value().equals("VP")) { return "no B - VP"; }
      return "no B";
    }

    if (rangeA.second==-1) { return "other - no en range A"; }
    if (rangeB.second==-1) { return "other - no en range B"; }


    if (rangeA.second < rangeB.first) {
      // starting from the end of rangeA, because 's could be on the edge
      for(int eidx = rangeA.second; eidx <= rangeB.first-1; eidx++) {
        if (submatrix[eidx][deIdx] > 0) {
          String deWord = subtranslation[eidx];
          if (deWord.equals("'s")) {
            return "A 's B";
          }
        }
      }

      // check if A becomes an adjective
      if (checkSecondVP(rangeA, rangeB, enNPTree)) return "other - VP";

      // For the following cases, we have to first check that
      // DE does not align to something inside (not on edge) rangeA & rangeB
      if (deEidx == -1 ||
          ((onRangeEdge(deEidx, rangeA) || !inRange(deEidx, rangeA)) &&
           (onRangeEdge(deEidx, rangeB) || !inRange(deEidx, rangeB)))) {
        if (deEidx != -1 && subtranslation[deEidx].equals("of")) return "A of B";
        if (deEidx != -1 && isPrep(subtranslation[deEidx])) return "A prep B";
        if (checkOrderedAdjective(rangeA, rangeB, enNPTree)) return "A B (adj)";
        if (checkOrderedAdverb(rangeA, rangeB, enNPTree)) return "A B (adv)";
        String mod = checkOrderedOtherModifier(rangeA, rangeB, enNPTree);
        if (mod != null) return "A B ("+mod.toLowerCase()+")";
        if (checkOrderedNoun(rangeA, rangeB, enNPTree)) return "A B (n)";
      } else {
        return "other - mixed";
      }

      // if no enNPTRee, give it a "ordered - no enNPtree" category
      if (enNPTree==null) return "ordered - no enNPtree";
      return "ordered";
    }
    if (rangeB.second < rangeA.first) {
      String boundaryWord = subtranslation[rangeA.first];
      if (boundaryWord.equals("of") ||
          boundaryWord.equals("to")) {
        return "B "+boundaryWord+" A";
      }
      if (isPrep(boundaryWord)) {
        return "B prep A";
      }
      if (boundaryWord.equals("that") || boundaryWord.equals("which")) {
        return "relative clause";
      }

      String deMappedWord = null;
      // check if deIdx aligns to somewhere in between max(rangeB) and min(rangeA)
      for(int eidx = rangeB.second+1; eidx <= rangeA.first-1; eidx++) {
        if (submatrix[eidx][deIdx] > 0) {
          String deWord = subtranslation[eidx];
          if (deWord.equals("of") ||
              deWord.equals("to")) {
            return "B "+deWord+" A";
          }
          if (isPrep(deWord)) {
            return "B prep A";
          }
          if (deWord.equals("that") || deWord.equals("which")) {
            return "relative clause";
          }
          deMappedWord = deWord;
          break;
        }
      }

      // This is flipped case, but we don't know what it is yet.
      // If enNPTree exists, 
      // we can check if range A is a VP and range B is an NP
      if (checkFlippedRelativeClause(rangeA, rangeB, enNPTree)) return "relative clause";//"flipped (relc)";

      return "flipped";
    }
    return "undecided";
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

  private static void printNPwithDEtoFile(int fileidx, int npidx, PrintWriter npPW, int deIdx, TreePair tp, String type) {
    //List<Pair<Integer,Integer>> englishNP = tp.NPwithDEs.get(np);
    Pair<Integer, Integer> np = tp.NPwithDEs_deIdx.get(deIdx);
    List<Pair<Integer,Integer>> englishNP = tp.getNPEnglishTranslation(deIdx);
    List<String> ch = new ArrayList<String>();
    for (int i = np.first; i<=np.second; i++) {
      ch.add(tp.alignment.source_[i]);
    }
    String chStr = StringUtils.join(ch, " ");

    List<String> en = new ArrayList<String>();
    for (Pair<Integer,Integer> enNP : englishNP) {
      StringBuilder ensb = new StringBuilder();
      for (int i = enNP.first; i<=enNP.second; i++) {
        ensb.append(tp.alignment.translation_[i]).append(" ");
      }
      en.add(ensb.toString());
    }
    String enStr = StringUtils.join(en, "|| ");

    npPW.printf("%d\t%d\t%s\t%d\t%s\t%s\n", fileidx, npidx, type, englishNP.size(), chStr, enStr);
  }

  // testing only
  public static void main(String[] args) throws IOException {
    int validAlignments = 0;
    int numtreepairs = 0;
    int numNPwithDE = 0;
    int numNPwithDE_contiguous = 0;
    int numNPwithDE_fragmented = 0;
    int FIDX = Integer.parseInt(args[0]);
    Properties props =StringUtils.argsToProperties(args);
    Counter typeCounter = new ClassicCounter<String>();

    // For this to run on both NLP machine and my computer
    String dirname = "/u/nlp/scr/data/ldc/LDC2006E93/GALE-Y1Q4/word_alignment/data/chinese/nw/";
    File dir = new File(dirname);
    if (!dir.exists()) {
      dirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2006E93\\GALE-Y1Q4\\word_alignment\\data\\chinese\\nw\\";
      dir = new File(dirname);
      if (!dir.exists()) {
        throw new RuntimeException("LDC2006E93 doesn't exist in either of the hard-coded locations.");
      }
    }

    for(int fileidx = FIDX; fileidx <= FIDX; fileidx++) {
      // (1) Read alignment files
      String aname = String.format("%schtb_%03d.txt", dirname, fileidx);
      File file = new File(aname);
      List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();
      if (file.exists()) {
        System.err.println("Processing  "+fileidx);
        alignment_list = TranslationAlignment.readFromFile(file);
      } else {
        System.err.println("Skip "+fileidx);
      }

      // (2) Read Chinese Trees
      ChineseTreeReader ctr = new ChineseTreeReader();
      String ctbdirname = "/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/";
      File ctbdir = new File(ctbdirname);
      if (!ctbdir.exists()) {
        ctbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\CTB6\\data\\utf8\\bracketed\\";
        ctbdir = new File(ctbdirname);
        if (!ctbdir.exists())
          throw new RuntimeException("CTB6.0 doesn't exist in either of the hard-coded locations.");
      }
      String ctbname =
        String.format("%schtb_%04d.fid", ctbdirname, fileidx);
      ctr.readMoreTrees(ctbname);

      // (3) Read English Trees
      EnglishTreeReader etr = new EnglishTreeReader();
      String ectbdirname = "/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/";
      File ectbdir = new File(ectbdirname);
      if (!ectbdir.exists()) {
        ectbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2007T02-EnglishChineseTranslationTreebankV1.0\\data\\pennTB-style-trees\\";
        ectbdir = new File(ectbdirname);
        if (!ectbdir.exists()) {
          throw new RuntimeException("EnglishChineseTranslationTreebankV1.0 doesn't exist in either of the hard-coded locations.");
        }
      }
      String ename =
        String.format("%schtb_%03d.mrg.gz",
                      ectbdirname, fileidx);
      etr.readMoreTrees(ename);

      // (4) Going through entries in (1) and check if they exist in (2)
      // (5) Going through entries in (1) and check if they exist in (3)
      // (6) also, if the tests passed, output various information

      List<TreePair> treepairs = new ArrayList<TreePair>();

      // open output file for NP list. This format is easy to import to Excel
      String npOutputDir = props.getProperty("npOutputDir", null);
      PrintWriter npPW = null;
      PrintWriter npgridPW = null;
      if (npOutputDir != null) {
        String filename = npOutputDir+"/"+fileidx+".np";
        System.err.println("Output NPs to "+filename);
        npPW= new PrintWriter(new BufferedWriter(new FileWriter(filename)));

        filename = npOutputDir+"/grids/"+fileidx+".npgrid.html";
        System.err.println("Output NP Grids to "+filename);
        npgridPW= new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      }
      // open output file for orginal Translation Alignment file.
      // In this format, every token is as defined in the manual word_alignment file.
      // So, Chinese tokens are characters.
      // The reason of having this option is mostly for debugging.
      // If you find any final grid in the tree-aligned version somewhat weird,
      // you can print out the original grid and see if it makes sense.
      String origTAdir = props.getProperty("origTAdir", null);
      PrintWriter origtaPW = null;
      if (origTAdir != null) {
        String filename = origTAdir+"/"+fileidx+".origta.html";
        System.err.println("Output original grids to "+filename);
        origtaPW = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      }

      int npCount = 1;
      if (origtaPW != null) {printAlignmentGridHeader(origtaPW);}
      if (npgridPW != null) {printAlignmentGridHeader(npgridPW);}
      for (TranslationAlignment ta : alignment_list) {
        if (origtaPW != null) {
          printAlignmentGrid(ta, origtaPW);
        }

        List<Tree> chTrees = ctr.getTreesWithWords(ta.source_);
        if (chTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in CTB: %s\n", fileidx, StringUtils.join(ta.source_, " "));
          System.err.println(StringUtils.join(ta.source_, " "));

          continue;
          // skip for now
        } else if (chTrees.size() > 1) {
          //System.err.printf("i=%d: Multiple trees: %s\n", fileidx, StringUtils.join(ta.source_, " "));
          throw new RuntimeException("i="+fileidx+": Multiple trees.");
        }
        
        List<Tree> enTrees = etr.getTreesWithWords(ta.translation_);
        if (enTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in PTB: %s\n", fileidx, StringUtils.join(ta.translation_, " "));
          System.err.println(StringUtils.join(ta.translation_, " "));
          continue;
          // skip for now
        } else if (enTrees.size() > 1) {
          System.err.printf("i=%d: Multiple trees: %s\n", fileidx, StringUtils.join(ta.translation_, " "));
        }
        
        // Fix the Translation Alignment before adding to the TreePair
        if (DEBUG) System.err.println("i="+fileidx);
        ta = fixAlignmentGridWithChineseTree(ta, chTrees);
        ta = fixAlignmentGridMergingChinese(ta, chTrees);
        ta = fixAlignmentGridWithEnglishTree(ta, enTrees);
        ta = fixAlignmentGridMergingEnglish(ta, enTrees);
        checkTranslationAlignmentAndEnTrees(ta, enTrees);
        checkTranslationAlignmentAndChTrees(ta, chTrees);
        TreePair tp = new TreePair(ta, enTrees, chTrees);
        //numNPwithDE += tp.NPwithDEs.size();
        numNPwithDE += tp.numNPwithDE();
        
        //for (Pair<Integer,Integer> NPwithDE : tp.NPwithDEs.keySet()) {
        for(int deIdxInSent : tp.NPwithDEs_deIdx_set) {
          Pair<Integer,Integer> NPwithDE = tp.NPwithDEs_deIdx.get(deIdxInSent);
          //List<Pair<Integer,Integer>> englishNP = tp.NPwithDEs.get(NPwithDE);
          List<Pair<Integer,Integer>> englishNP = tp.getNPEnglishTranslation(deIdxInSent);
          if (englishNP.size()==1) {
            numNPwithDE_contiguous++;
          } else {
            numNPwithDE_fragmented++;
          }
          
          if (npPW != null) {
            printAlignmentGridHeader(npgridPW);
            String type = analyzeNPwithDE(deIdxInSent, tp, npgridPW);
            typeCounter.incrementCount(type);
            printAlignmentGridBottom(npgridPW);
            printNPwithDEtoFile(fileidx, npCount, npPW, deIdxInSent, tp, type);
            npCount++;
          }
        }
        
        treepairs.add(tp);
      }
      if (npgridPW != null) { printAlignmentGridBottom(npgridPW); npgridPW.close(); }
      if (origtaPW != null) { printAlignmentGridBottom(origtaPW); origtaPW.close(); }
      
      numNPwithDE  += TreePair.printAllwithDE(treepairs);
      numtreepairs += treepairs.size();
      
      if (npPW != null) {
        npPW.close();
      }
      validAlignments += alignment_list.size();
    }
    
    // count Countiguous NPs & Fragmented NPs
    
    System.err.println("# valid translation alignment = "+validAlignments);
    System.err.println("# Tree Pairs = "+numtreepairs);
    System.err.println("# NPs with DE = "+numNPwithDE);
    System.err.println("# NPs with DE (contiguous)= "+numNPwithDE_contiguous);
    System.err.println("# NPs with DE (fragmented)= "+numNPwithDE_fragmented);
    System.err.println(typeCounter);
  }
}
